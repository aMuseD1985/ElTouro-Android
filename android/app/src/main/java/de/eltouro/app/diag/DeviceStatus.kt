package de.eltouro.app.diag

import android.app.NotificationManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import de.eltouro.app.DeviceKey
import de.eltouro.app.ElTouroApp
import de.eltouro.app.Remember
import de.eltouro.app.R
import de.eltouro.app.RideState
import de.eltouro.app.Setup
import de.eltouro.app.TrackerService
import java.util.concurrent.TimeUnit

/** One line on the diagnostics screen: ok = true (fine), false (a problem), null (just information). */
data class StatusLine(val ok: Boolean?, val label: String, val value: String) {
    fun asText() = (when (ok) { true -> "✓"; false -> "✗"; null -> "·" }) + " " + label + ": " + value
}

object DeviceStatus {
    /**
     * "Pfad der Aufzeichnung" – tells the three causes of a straight line apart at a glance:
     * (a) the page records in the browser, (b) GPS delivers nothing with the display off, (c) fixes come, points are lost.
     */
    fun path(ctx: Context): List<StatusLine> {
        val now = System.currentTimeMillis()
        val s = mutableListOf<StatusLine>()
        val running = RideState.service != null
        fun ago(t: Long) = if (t <= 0) "—" else ctx.getString(R.string.diag_ago, formatDuration(now - t))
        val p = Diag.probe

        // (a)
        s += when {
            Diag.pageStartAt > 0 -> StatusLine(true, ctx.getString(R.string.path_page),
                if (Diag.pageStartSession > 0 || RideState.session > 0) ctx.getString(R.string.path_page_started, ago(Diag.pageStartAt), maxOf(RideState.session, Diag.pageStartSession).toString())
                else ctx.getString(R.string.path_page_started_norec, ago(Diag.pageStartAt)))
            p != null && p.ridePage && !p.available -> StatusLine(false, ctx.getString(R.string.path_page), ctx.getString(R.string.path_page_browser))
            p != null && p.ridePage -> StatusLine(null, ctx.getString(R.string.path_page), ctx.getString(R.string.path_page_open))
            else -> StatusLine(null, ctx.getString(R.string.path_page), ctx.getString(R.string.path_page_none))
        }
        s += if (p == null) StatusLine(null, ctx.getString(R.string.path_bridge), "—")
        else StatusLine(if (p.ridePage) p.available else null, ctx.getString(R.string.path_bridge),
            ctx.getString(R.string.path_bridge_value, p.path, yn(ctx, p.available), yn(ctx, p.capacitor), ago(Diag.probeAt)))

        // (b)
        val w = Diag.fixes.window(now)
        s += StatusLine(if (running) Diag.fixes.lastAt > 0 && now - Diag.fixes.lastAt < 20_000 else null, ctx.getString(R.string.path_fixes),
            ctx.getString(R.string.path_fixes_value, w.displayOn, w.displayOff, ago(Diag.fixes.lastAt)))
        s += StatusLine(if (Diag.fixes.total > 0) Diag.fixes.gapsDisplayOff == 0 else null, ctx.getString(R.string.path_gaps_off),
            ctx.getString(R.string.diag_gaps_value, Diag.fixes.gapsDisplayOff, formatDuration(Diag.fixes.longestGapDisplayOffMs)))

        // (c)
        val sessionLost = (Diag.pageStartSession > 0 || Diag.sessionSetAt > 0) && RideState.active && RideState.session <= 0
        s += StatusLine(if (sessionLost) false else null, ctx.getString(R.string.path_session),
            if (RideState.session > 0) ctx.getString(R.string.path_session_value, RideState.session.toString(), ago(Diag.sessionSetAt))
            else ctx.getString(R.string.path_session_none, Diag.fixesWithoutSession))
        s += StatusLine(if (Diag.pointsStored + Diag.pointsDropped > 0) Diag.pointsDropped == 0 && (Diag.lastUploadAt == 0L || Diag.lastUploadCode in 200..299) else null,
            ctx.getString(R.string.path_points), ctx.getString(R.string.path_points_value, Diag.pointsStored, Diag.pointsUploaded, Diag.pointsDropped, Diag.bufferSize))
        return s
    }

    /** "Anmeldung": what keeps the rider signed in. */
    fun login(ctx: Context): List<StatusLine> {
        val now = System.currentTimeMillis()
        val s = mutableListOf<StatusLine>()
        val p = Diag.probe
        s += StatusLine(p?.signedIn, ctx.getString(R.string.login_page),
            when (p?.signedIn) { true -> ctx.getString(R.string.diag_yes); false -> ctx.getString(R.string.diag_no); null -> "—" } + (p?.let { " · " + it.path } ?: ""))
        val remember = Remember.get(ctx) != null
        s += StatusLine(remember, ctx.getString(R.string.login_remember), yn(ctx, remember))
        val base = RideState.baseUrl.ifEmpty { "https://app.eltouro.de" }
        val cookie = try { android.webkit.CookieManager.getInstance().getCookie(base)?.contains("eltouro_sid=") == true } catch (_: Exception) { false }
        s += StatusLine(cookie, ctx.getString(R.string.login_cookie), yn(ctx, cookie))
        val key = DeviceKey.get(ctx)
        s += StatusLine(if (RideState.baseUrl.isNotEmpty()) key != null else null, ctx.getString(R.string.diag_device_key),
            if (key != null) key.take(8) + "…" else ctx.getString(R.string.diag_no))
        val w = de.eltouro.app.widget.WidgetData.state(ctx)
        s += StatusLine(when (w.status) { de.eltouro.app.widget.WidgetData.Status.OK -> true; de.eltouro.app.widget.WidgetData.Status.NEVER -> null; else -> false },
            ctx.getString(R.string.diag_widgets), w.status.name.lowercase() + (if (w.fetchedAt > 0) " · " + ctx.getString(R.string.diag_ago, formatDuration(now - w.fetchedAt)) else ""))
        s += StatusLine(null, ctx.getString(R.string.login_last),
            if (Diag.loginAt > 0) Diag.loginLast + " · " + ctx.getString(R.string.diag_ago, formatDuration(now - Diag.loginAt)) else "—")
        return s
    }

    private fun yn(ctx: Context, b: Boolean) = ctx.getString(if (b) R.string.diag_yes else R.string.diag_no)

    fun snapshot(ctx: Context): List<StatusLine> {
        val now = System.currentTimeMillis()
        val s = mutableListOf<StatusLine>()
        val yes = ctx.getString(R.string.diag_yes)
        val no = ctx.getString(R.string.diag_no)
        fun yn(b: Boolean) = if (b) yes else no
        fun ago(t: Long) = if (t <= 0) "—" else ctx.getString(R.string.diag_ago, formatDuration(now - t))
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager

        // the ride
        val running = RideState.service != null
        s += StatusLine(if (RideState.active) running else null, ctx.getString(R.string.diag_service),
            if (running) ctx.getString(R.string.diag_running_since, formatDuration(now - Diag.serviceSince)) else ctx.getString(R.string.diag_not_running))
        s += StatusLine(null, ctx.getString(R.string.diag_recording), if (RideState.session > 0) "#" + RideState.session else no)
        s += StatusLine(if (running) now - Diag.fixes.lastAt < 15_000 else null, ctx.getString(R.string.diag_last_fix),
            if (Diag.fixes.lastAt > 0) ago(Diag.fixes.lastAt) + " · ±" + Diag.lastAccuracyM.toInt() + " m" else "—")
        s += StatusLine(null, ctx.getString(R.string.diag_fixes_per_min), Diag.fixes.perMinute(now).toString())
        s += StatusLine(if (Diag.fixes.gaps > 0) false else null, ctx.getString(R.string.diag_gaps),
            ctx.getString(R.string.diag_gaps_value, Diag.fixes.gaps, formatDuration(Diag.fixes.longestGapMs)))
        s += StatusLine(if (Diag.bufferSize > 100) false else null, ctx.getString(R.string.diag_buffer), Diag.bufferSize.toString())
        s += StatusLine(if (Diag.lastUploadAt > 0) Diag.lastUploadCode in 200..299 else null, ctx.getString(R.string.diag_last_upload),
            if (Diag.lastUploadAt > 0) ago(Diag.lastUploadAt) + " · " + (if (Diag.lastUploadCode > 0) "HTTP " + Diag.lastUploadCode else Diag.lastUploadError) +
                (if (Diag.failedUploads > 0) " · " + ctx.getString(R.string.diag_failed_n, Diag.failedUploads) else "") else "—")
        s += StatusLine(if (running && Diag.webLastAnswerAt > 0) Diag.webStalledSince == 0L else null, ctx.getString(R.string.diag_webview),
            if (Diag.webStalledSince > 0) ctx.getString(R.string.diag_web_stalled, formatDuration(now - Diag.webStalledSince))
            else if (Diag.webLastAnswerAt > 0) ctx.getString(R.string.diag_web_ok, ago(Diag.webLastAnswerAt)) else "—")
        s += StatusLine(null, ctx.getString(R.string.diag_page_call),
            if (Diag.lastPageCallAt > 0) Diag.lastPageCall + " · " + ago(Diag.lastPageCallAt) else "—")
        s += StatusLine(null, ctx.getString(R.string.diag_restarts), Diag.restarts(ctx).toString())

        // permissions and power
        s += StatusLine(Setup.hasPreciseLocation(ctx), ctx.getString(R.string.diag_perm_precise), yn(Setup.hasPreciseLocation(ctx)))
        s += StatusLine(Setup.hasBackgroundLocation(ctx), ctx.getString(R.string.diag_perm_background), yn(Setup.hasBackgroundLocation(ctx)))
        s += StatusLine(Setup.hasNotifications(ctx), ctx.getString(R.string.diag_perm_notifications), yn(Setup.hasNotifications(ctx)))
        s += StatusLine(Setup.locationEnabled(ctx), ctx.getString(R.string.diag_location_on), yn(Setup.locationEnabled(ctx)))
        s += StatusLine(Setup.ignoresBatteryOptimization(ctx), ctx.getString(R.string.diag_battery_exempt), yn(Setup.ignoresBatteryOptimization(ctx)))
        s += StatusLine(!Setup.backgroundRestricted(ctx), ctx.getString(R.string.diag_bg_restricted), yn(Setup.backgroundRestricted(ctx)))
        if (pm != null) {
            s += StatusLine(if (pm.isPowerSaveMode) false else null, ctx.getString(R.string.diag_power_save), yn(pm.isPowerSaveMode))
            s += StatusLine(null, ctx.getString(R.string.diag_doze), yn(pm.isDeviceIdleMode))
            s += StatusLine(null, ctx.getString(R.string.diag_screen), if (pm.isInteractive) ctx.getString(R.string.diag_on) else ctx.getString(R.string.diag_off))
        }
        if (Build.VERSION.SDK_INT >= 28) {
            val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            if (usm != null) {
                val b = usm.appStandbyBucket
                s += StatusLine(!Names.bucketIsBad(b), ctx.getString(R.string.diag_bucket), Names.standbyBucket(b))
            }
        }
        s += StatusLine(null, ctx.getString(R.string.diag_wakelock), yn(RideState.service?.wakeLockHeld() == true))
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            val ch = nm?.getNotificationChannel(TrackerService.CHANNEL)
            if (ch != null) s += StatusLine(ch.importance >= NotificationManager.IMPORTANCE_DEFAULT, ctx.getString(R.string.diag_channel), ch.importance.toString())
        }

        // device
        s += StatusLine(null, ctx.getString(R.string.diag_device), Build.MANUFACTURER + " " + Build.MODEL + " · " + rom())
        s += StatusLine(null, ctx.getString(R.string.diag_android), Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")")
        s += StatusLine(null, ctx.getString(R.string.diag_app), (ctx.applicationContext as? ElTouroApp)?.appVersion() ?: "?")
        s += StatusLine(null, ctx.getString(R.string.diag_server), RideState.baseUrl.ifEmpty { "—" })
        return s
    }

    /** Vendor ROM and version, e.g. "HyperOS OS2.0.12" or "MIUI V14" – read once from the system properties. */
    private val romName: String by lazy {
        val hyper = sysProp("ro.mi.os.version.name")
        val miui = sysProp("ro.miui.ui.version.name")
        val inc = sysProp("ro.build.version.incremental")
        when {
            hyper.isNotEmpty() -> "HyperOS $hyper ($inc)"
            miui.isNotEmpty() -> "MIUI $miui ($inc)"
            else -> Build.DISPLAY
        }
    }

    fun rom(): String = romName

    private fun sysProp(name: String): String = try {
        val p = ProcessBuilder("getprop", name).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().use { it.readText() }.trim()
        p.waitFor(1, TimeUnit.SECONDS)
        out
    } catch (_: Exception) {
        ""
    }

    fun formatDuration(ms: Long): String {
        val sec = maxOf(0L, ms / 1000)
        return when {
            sec < 120 -> "$sec s"
            sec < 7200 -> "${sec / 60} min"
            else -> "${sec / 3600} h ${(sec % 3600) / 60} min"
        }
    }
}
