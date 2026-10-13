package de.eltouro.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.core.content.ContextCompat
import de.eltouro.app.diag.Diag

/**
 * Brings the ride service back if the system killed it and did not restart it. While a ride runs, the service pushes
 * an alarm 10 minutes ahead every minute; only when the service is gone does the alarm actually fire.
 * Android 12+ allows the restart from the background only with battery optimization off (setup in Diagnose).
 * A ride ends only when the rider ends it – after a reboot it continues as well (BootReceiver).
 */
object RideWatchdog {
    private const val DELAY_MS = 10 * 60_000L

    private fun pending(ctx: Context): PendingIntent = PendingIntent.getBroadcast(ctx, 7, Intent(ctx, WatchdogReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    fun arm(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + DELAY_MS, pending(ctx))
    }

    fun cancel(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        am.cancel(pending(ctx))
    }

    /** Starts the service again when a ride is active but the service is not running. */
    fun restartIfNeeded(ctx: Context, why: String) {
        RideState.load(ctx)
        if (!RideState.active) return
        if (RideState.service != null) return
        Diag.log("watchdog: ride active but service gone ($why) – starting it again")
        try {
            ContextCompat.startForegroundService(ctx, Intent(ctx, TrackerService::class.java).putExtra("restart", why))
        } catch (e: Exception) {
            Diag.log("watchdog: start refused – ${e.javaClass.simpleName}: ${e.message}")
        }
        arm(ctx)   // try again later if it did not work
    }
}

class WatchdogReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Diag.init(context)
        RideWatchdog.restartIfNeeded(context, "alarm")
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        Diag.init(context)
        RideWatchdog.restartIfNeeded(context, if (intent.action == Intent.ACTION_BOOT_COMPLETED) "reboot" else "app updated")
    }
}
