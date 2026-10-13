package de.eltouro.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import com.getcapacitor.JSObject
import com.getcapacitor.PermissionState
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import com.getcapacitor.annotation.Permission
import com.getcapacitor.annotation.PermissionCallback
import de.eltouro.app.diag.Diag

/**
 * Bridge between the ElTouro web app (assets/native_tracker.js) and the foreground service. The methods and arguments
 * the server's page already uses stay exactly as they were – old pages keep working with this app and vice versa.
 *   start({baseUrl, csrf, session?, title, text})  asks for location (and notification) permission, starts the service
 *   setSession({session, csrf}) / clearSession()   switch the recording on / off while the service keeps running
 *   stop()                                         flushes what is left and stops the service
 *   batterySettings()                              system dialog to switch battery optimization off for the app
 *   openExternal({url}), getRemember/setRemember/clearRemember  sign-in helpers
 * New (M1): log({msg}) adds a line to the diagnostics log, status() → what display-off rides still need,
 *   diagnostics() opens the diagnostics screen. A page uses them only when present.
 * Event "location": {lat, lng, acc, heading, speedMs, t}
 */
@CapacitorPlugin(
    name = "EltouroTracker",
    permissions = [
        Permission(strings = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION], alias = "location"),
        Permission(strings = [Manifest.permission.POST_NOTIFICATIONS], alias = "notifications"),
    ],
)
class TrackerPlugin : Plugin() {
    private var pageLogs = 0
    private var pageLogMinute = 0L

    override fun load() {
        RideState.plugin = this
    }

    fun emitLocation(o: JSObject) {
        notifyListeners("location", o)
    }

    @PluginMethod
    fun start(call: PluginCall) {
        RideState.baseUrl = call.getString("baseUrl", "") ?: ""
        RideState.csrf = call.getString("csrf", "") ?: ""
        RideState.title = call.getString("title", "ElTouro") ?: "ElTouro"
        RideState.text = call.getString("text", "") ?: ""
        RideState.session = call.getLong("session", 0L) ?: 0L
        Diag.pageCall("start(session=${RideState.session})")
        if (getPermissionState("location") != PermissionState.GRANTED) {
            requestPermissionForAlias("location", call, "afterLocation")
            return
        }
        afterLocation(call)
    }

    @PermissionCallback
    private fun afterLocation(call: PluginCall) {
        if (getPermissionState("location") != PermissionState.GRANTED) {
            Diag.log("page: start refused – no location permission")
            call.reject("location_denied")
            return
        }
        if (Build.VERSION.SDK_INT >= 33 && getPermissionState("notifications") != PermissionState.GRANTED) {
            requestPermissionForAlias("notifications", call, "afterNotifications")
            return
        }
        begin(call)
    }

    @PermissionCallback
    private fun afterNotifications(call: PluginCall) {
        begin(call)   // without the notification permission the service still runs, only the status entry is hidden
    }

    private fun begin(call: PluginCall) {
        try {
            RideState.active = true
            RideState.save(context)
            ContextCompat.startForegroundService(context, Intent(context, TrackerService::class.java))
            call.resolve()
            activity?.let { a -> a.runOnUiThread { Setup.maybeShowHint(a) } }
        } catch (e: Exception) {
            Diag.log("page: service start failed – ${e.javaClass.simpleName}: ${e.message}")
            call.reject("service_failed", e)
        }
    }

    @PluginMethod
    fun setSession(call: PluginCall) {
        val csrf = call.getString("csrf")
        if (!csrf.isNullOrEmpty()) RideState.csrf = csrf
        RideState.session = call.getLong("session", 0L) ?: 0L
        RideState.save(context)
        Diag.pageCall("setSession(${RideState.session})")
        call.resolve()
    }

    /** Uploads what is left before the page finishes the recording – up to three tries within about 9 s (dead zone at the end). */
    private fun flushBeforeFinish(svc: TrackerService?) {
        if (svc == null || RideState.baseUrl.isEmpty()) return
        for (attempt in 1..3) {
            if (svc.flushNow()) return
            if (attempt < 3) Thread.sleep(3000)
        }
        Diag.log("page: ride ends with ${Diag.bufferSize} points not uploaded – they go out with the next ride if the server still takes them")
    }

    @PluginMethod
    fun clearSession(call: PluginCall) {
        val svc = RideState.service
        Diag.pageCall("clearSession")
        Thread {
            flushBeforeFinish(svc)
            RideState.session = 0
            RideState.save(context)
            call.resolve()
        }.start()
    }

    @PluginMethod
    fun stop(call: PluginCall) {
        val svc = RideState.service
        Diag.pageCall("stop")
        Thread {
            flushBeforeFinish(svc)
            RideState.session = 0
            RideState.active = false
            RideState.save(context)
            RideWatchdog.cancel(context)
            context.stopService(Intent(context, TrackerService::class.java))
            call.resolve()
        }.start()
    }

    /** Opens a page in the system browser (Chrome Custom Tab) – used for "Sign in with Google", which Google forbids in a WebView. */
    @PluginMethod
    fun openExternal(call: PluginCall) {
        val url = call.getString("url", "") ?: ""
        if (!url.startsWith("https://")) {
            call.reject("bad_url")
            return
        }
        try {
            val tab = CustomTabsIntent.Builder().build()
            tab.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            tab.launchUrl(context, Uri.parse(url))
            call.resolve()
        } catch (e: Exception) {
            call.reject("no_browser", e)
        }
    }

    // "Stay signed in" token (see app_login.php?r=): kept in the app's private storage, in case the WebView loses its cookies
    private fun prefs() = context.getSharedPreferences("eltouro", Context.MODE_PRIVATE)

    @PluginMethod
    fun getRemember(call: PluginCall) {
        val o = JSObject()
        o.put("token", prefs().getString("remember", "") ?: "")
        call.resolve(o)
    }

    @PluginMethod
    fun setRemember(call: PluginCall) {
        val t = call.getString("token", "") ?: ""
        if (t.matches(Regex("[0-9a-f]{64}"))) prefs().edit().putString("remember", t).apply()
        call.resolve()
    }

    @PluginMethod
    fun clearRemember(call: PluginCall) {
        prefs().edit().remove("remember").apply()
        val ctx = context
        Thread { DeviceKey.revoke(ctx) }.start()   // signed out: the device key goes as well (the server revokes it on logout anyway)
        call.resolve()
    }

    @PluginMethod
    fun batterySettings(call: PluginCall) {
        val a = activity
        if (a != null) Setup.requestIgnoreBatteryOptimization(a) else Setup.openAppDetails(context)
        call.resolve()
    }

    /** A line from the page into the diagnostics log (at most 60 per minute, 300 characters each). */
    @PluginMethod
    fun log(call: PluginCall) {
        val minute = System.currentTimeMillis() / 60_000
        if (minute != pageLogMinute) {
            pageLogMinute = minute
            pageLogs = 0
        }
        val msg = (call.getString("msg", "") ?: "").take(300)
        if (msg.isNotEmpty() && ++pageLogs <= 60) Diag.log("page: $msg")
        call.resolve()
    }

    /** What a ride with the display off still needs – lets the page show its own hint later. */
    @PluginMethod
    fun status(call: PluginCall) {
        val ctx = context
        val o = JSObject()
        o.put("preciseLocation", Setup.hasPreciseLocation(ctx))
        o.put("backgroundLocation", Setup.hasBackgroundLocation(ctx))
        o.put("notifications", Setup.hasNotifications(ctx))
        o.put("batteryUnrestricted", Setup.ignoresBatteryOptimization(ctx) && !Setup.backgroundRestricted(ctx))
        o.put("complete", Setup.complete(ctx))
        o.put("serviceRunning", RideState.service != null)
        o.put("version", 2)
        call.resolve(o)
    }

    @PluginMethod
    fun diagnostics(call: PluginCall) {
        val i = Intent(context, DiagnoseActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(i)
        call.resolve()
    }
}
