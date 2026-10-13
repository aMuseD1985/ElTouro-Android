package de.eltouro.app.diag

import android.app.NotificationManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import de.eltouro.app.ElTouroApp
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
