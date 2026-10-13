package de.eltouro.app

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.PowerManager
import android.webkit.CookieManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.getcapacitor.JSObject
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import de.eltouro.app.diag.Diag
import de.eltouro.app.ride.Http
import de.eltouro.app.ride.PointStore
import de.eltouro.app.ride.SqlitePointStore
import de.eltouro.app.ride.TrackPoint
import de.eltouro.app.ride.Transport
import de.eltouro.app.ride.UploadResult
import de.eltouro.app.ride.Uploader
import kotlin.math.roundToInt

/**
 * Keeps the position coming while the display is off: a foreground service (visible notification) with fused location.
 * While a recording session is set, every point goes into a SQLite buffer on the phone (M2) and from there to
 * /api/track – with the app's device key, falling back to the WebView's cookie + CSRF. Nothing is lost in a dead zone,
 * when the process is killed or after a reboot; the watchdog brings the service back. The page gets every fix as an
 * event as long as it is alive.
 *
 * Diagnostics (M1): every lifecycle step, gap, upload failure and system state change goes to [Diag], plus one summary
 * line per minute – that log is what tells us why rides with the display off had holes.
 */
class TrackerService : Service() {
    companion object {
        const val CHANNEL = "eltouro_ride_v2"          // new id: the importance of an existing channel cannot be raised
        private const val OLD_CHANNEL = "eltouro_ride"
        private const val NOTIFICATION_ID = 4711
        private const val UPLOAD_EVERY_MS = 15_000L
        private const val TICK_MS = 60_000L
        private const val MAX_STORED = 50_000                // about a day of riding at one point every 2 s
        private const val WAKE_TIMEOUT_MS = 30 * 60_000L   // renewed every minute while the ride runs
    }

    private var client: FusedLocationProviderClient? = null
    private var callback: LocationCallback? = null
    private lateinit var thread: HandlerThread
    private lateinit var handler: Handler
    private var wakeLock: PowerManager.WakeLock? = null
    private var receiver: BroadcastReceiver? = null
    private val store: PointStore by lazy { SqlitePointStore(this) }
    private val uploader by lazy { Uploader(store, Transport { session, json -> send(session, json) }, { Diag.log(it) }) }
    private val uploadLock = Any()
    private var lastKept: Location? = null
    private var lastKeptAt = 0L
    @Volatile private var uploadedPoints = 0
    private var lastLoggedUpload = ""

    private val uploadTick = object : Runnable {
        override fun run() {
            flushNow()
            handler.postDelayed(this, UPLOAD_EVERY_MS)
        }
    }

    private val minuteTick = object : Runnable {
        override fun run() {
            minuteSummary()
            renewWakeLock()
            handler.postDelayed(this, TICK_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        thread = HandlerThread("eltouro-ride").also { it.start() }
        handler = Handler(thread.looper)
        RideState.service = this
        Diag.serviceSince = System.currentTimeMillis()
        Diag.log("service: created")
        registerSystemReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            RideState.load(this)
            val n = Diag.countRestart(this)
            Diag.log("service: restarted by the system (sticky, #$n) active=${RideState.active} session=${RideState.session}")
            if (!RideState.active) {
                stopSelf()
                return START_NOT_STICKY
            }
        } else {
            Diag.log("service: start session=${RideState.session}" + (intent.getStringExtra("restart")?.let { " (restart: $it)" } ?: ""))
        }
        createChannel()
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } else {
                startForeground(NOTIFICATION_ID, buildNotification())
            }
        } catch (e: Exception) {
            // Android 12+: no foreground service from the background without an exemption (battery optimization off),
            // Android 14+: type location only with location permission at this moment
            Diag.log("service: startForeground refused – ${e.javaClass.simpleName}: ${e.message}")
            stopSelf()
            return START_NOT_STICKY
        }
        renewWakeLock()
        startLocation()
        RideWatchdog.arm(this)
        handler.post {
            Diag.bufferSize = store.count()
            if (Diag.bufferSize > 0) Diag.log("buffer: ${Diag.bufferSize} points from before waiting for upload")
            DeviceKey.ensure(this, appVersion())
        }
        handler.removeCallbacks(uploadTick)
        handler.postDelayed(uploadTick, 2000)
        handler.removeCallbacks(minuteTick)
        handler.postDelayed(minuteTick, TICK_MS)
        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startLocation() {
        if (client != null) return
        val c = LocationServices.getFusedLocationProviderClient(this)
        val cb = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val list = result.locations
                if (list.size > 1) Diag.log("location: ${list.size} fixes delivered at once (batched)")
                for (l in list) onLocation(l)
            }

            override fun onLocationAvailability(availability: LocationAvailability) {
                Diag.log("location: available=${availability.isLocationAvailable}")
            }
        }
        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
            .setMinUpdateIntervalMillis(1000)
            .setMaxUpdateDelayMillis(0)            // no batching: we want every fix when it happens
            .setWaitForAccurateLocation(false)
            .build()
        try {
            c.requestLocationUpdates(req, cb, thread.looper)
            client = c
            callback = cb
            Diag.log("location: updates requested (precise=${Setup.hasPreciseLocation(this)} background=${Setup.hasBackgroundLocation(this)})")
        } catch (e: SecurityException) {
            Diag.log("location: refused – ${e.message}")
            stopSelf()
        }
    }

    private fun onLocation(l: Location) {
        val gap = Diag.fixes.onFix(System.currentTimeMillis(), l.accuracy)
        if (gap > 0) Diag.log("location: gap of ${gap / 1000} s ended (screen ${screenState()})")
        Diag.lastAccuracyM = l.accuracy
        RideState.plugin?.let { p ->
            val o = JSObject()
            o.put("lat", l.latitude)
            o.put("lng", l.longitude)
            o.put("acc", l.accuracy.toDouble())
            o.put("heading", (if (l.hasBearing()) l.bearing.toDouble() else null) as Any?)
            o.put("speedMs", (if (l.hasSpeed()) l.speed.toDouble() else null) as Any?)
            o.put("t", l.time)
            p.emitLocation(o)
        }
        if (RideState.session <= 0) return
        // like the web recorder: at most one point every 2 s, and only when we have moved – standing at lights fills nothing
        val now = l.time
        if (now - lastKeptAt < 2000) return
        val prev = lastKept
        if (prev != null && l.distanceTo(prev) < 5f) return
        lastKept = l
        lastKeptAt = now
        try {
            store.add(RideState.session, TrackPoint(now, (l.latitude * 1e6).roundToInt() / 1e6, (l.longitude * 1e6).roundToInt() / 1e6,
                l.accuracy.roundToInt(), if (l.hasSpeed()) (l.speed * 3.6 * 10).roundToInt() / 10.0 else null))
            Diag.bufferSize++
        } catch (e: Exception) {
            Diag.log("buffer: point not stored – ${e.javaClass.simpleName}")
        }
    }

    /** Sends what is buffered. Blocks; never call on the main thread. Returns true when nothing is left behind. */
    fun flushNow(): Boolean {
        synchronized(uploadLock) {
            if (RideState.baseUrl.isEmpty()) return false
            val out = try {
                uploader.flush()
            } catch (e: Exception) {
                Diag.log("upload: error – ${e.javaClass.simpleName}: ${e.message}")
                return false
            }
            val now = System.currentTimeMillis()
            uploadedPoints += out.sent
            Diag.bufferSize = try { store.count() } catch (_: Exception) { Diag.bufferSize }
            val r = out.last ?: return out.ok          // nothing was waiting
            Diag.lastUploadAt = now
            Diag.lastUploadCode = r.code
            Diag.lastUploadError = r.error.ifEmpty { r.body.take(120) }
            if (out.ok) Diag.lastUploadOkAt = now else Diag.failedUploads++
            // log changes, not every round: 401 = key/session gone, 400 = CSRF, 302 = access gate, -1 = no network
            val key = if (out.ok) "ok" else "${r.code} ${r.error}"
            if (key != lastLoggedUpload) {
                Diag.log(if (out.ok) "upload: ok (HTTP ${r.code}, ${out.sent} points)"
                    else "upload: failed – ${if (r.code > 0) "HTTP ${r.code}" else "no response"} ${r.error} · ${Diag.bufferSize} points kept")
                lastLoggedUpload = key
            }
            return out.ok
        }
    }

    /** One upload request: device key first; without key (or when the server no longer knows it) cookie + CSRF as before. */
    private fun send(session: Long, pointsJson: String): UploadResult {
        val body = "{\"action\":\"points\",\"session_id\":$session,\"points\":$pointsJson}"
        val url = RideState.baseUrl + "/api/track"
        DeviceKey.get(this)?.let { key ->
            val r = Http.postJson(url, body, DeviceKey.authHeaders(key))
            if (r.code != 401) return r
            DeviceKey.forget(this, "server answered 401")
        }
        val h = HashMap<String, String>()
        h["X-CSRF"] = RideState.csrf
        CookieManager.getInstance().getCookie(RideState.baseUrl)?.let { h["Cookie"] = it }
        return Http.postJson(url, body, h)
    }

    private fun appVersion(): String = (application as? ElTouroApp)?.appVersion() ?: "?"

    private fun minuteSummary() {
        val m = Diag.fixes.takeMinute()
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
        Diag.log("minute: fixes=${m.fixes} acc=${m.meanAccuracyM?.roundToInt() ?: "-"}m buffer=${Diag.bufferSize} uploaded=$uploadedPoints" +
            " session=${RideState.session} screen=${screenState()} doze=${pm?.isDeviceIdleMode} powersave=${pm?.isPowerSaveMode} wake=${wakeLockHeld()}" +
            " key=${DeviceKey.get(this) != null}")
        uploadedPoints = 0
        if (RideState.active) RideWatchdog.arm(this)
        try {
            val dropped = store.trim(MAX_STORED)
            if (dropped > 0) Diag.log("buffer: full – $dropped oldest points dropped")
        } catch (_: Exception) { }
        DeviceKey.ensure(this, appVersion())
    }

    private fun renewWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        val wl = wakeLock ?: pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "eltouro:ride").also {
            it.setReferenceCounted(false)
            wakeLock = it
        }
        wl.acquire(WAKE_TIMEOUT_MS)   // a dead ride lets it lapse after 30 minutes at the latest
    }

    fun wakeLockHeld(): Boolean = wakeLock?.isHeld == true

    private fun screenState(): String {
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
        return if (pm?.isInteractive == true) "on" else "off"
    }

    private fun registerSystemReceiver() {
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        }
        val r = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                Diag.log("system: " + when (intent.action) {
                    Intent.ACTION_SCREEN_ON -> "screen on"
                    Intent.ACTION_SCREEN_OFF -> "screen off"
                    PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED -> "doze=${pm?.isDeviceIdleMode}"
                    PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> "power save=${pm?.isPowerSaveMode}"
                    else -> intent.action
                })
            }
        }
        ContextCompat.registerReceiver(this, r, f, ContextCompat.RECEIVER_NOT_EXPORTED)
        receiver = r
    }

    fun updateNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        nm.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (nm.getNotificationChannel(OLD_CHANNEL) != null) nm.deleteNotificationChannel(OLD_CHANNEL)
        if (nm.getNotificationChannel(CHANNEL) != null) return
        val ch = NotificationChannel(CHANNEL, getString(R.string.channel_ride), NotificationManager.IMPORTANCE_HIGH)
        ch.description = getString(R.string.channel_ride_desc)
        ch.setSound(null, null)      // high importance so the system treats the ride as important – but silent
        ch.enableVibration(false)
        ch.setShowBadge(false)
        nm.createNotificationChannel(ch)
    }

    private fun buildNotification(): Notification {
        val open = Intent(this, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val diag = PendingIntent.getActivity(this, 1, Intent(this, DiagnoseActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle(RideState.title)
            .setContentText(RideState.text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(pi)
            .addAction(0, getString(R.string.diag_title), diag)
            .build()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Diag.log("service: app swiped away from recents (service keeps running)")
        super.onTaskRemoved(rootIntent)
    }

    override fun onTrimMemory(level: Int) {
        Diag.log("system: trim memory level $level")
        super.onTrimMemory(level)
    }

    override fun onDestroy() {
        Diag.log("service: destroyed (buffer ${Diag.bufferSize}, ride active=${RideState.active})")
        if (!RideState.active) RideWatchdog.cancel(this)   // killed by the system mid-ride: the alarm stays and brings us back
        val c = client
        val cb = callback
        if (c != null && cb != null) c.removeLocationUpdates(cb)
        client = null
        handler.removeCallbacks(uploadTick)
        handler.removeCallbacks(minuteTick)
        receiver?.let { try { unregisterReceiver(it) } catch (_: Exception) { } }
        receiver = null
        val t = thread
        handler.post {           // the last upload must not run on the main thread (it always failed there before)
            flushNow()
            t.quitSafely()
        }
        wakeLock?.let { if (it.isHeld) it.release() }
        RideState.service = null
        Diag.serviceSince = 0L
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
