package de.eltouro.app

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import de.eltouro.app.diag.Diag
import de.eltouro.app.diag.Vendor

/** What a ride with the display off needs, and the shortcuts into the system settings to get there. */
object Setup {
    val vendor: Vendor by lazy { Vendor.detect(Build.MANUFACTURER, Build.BRAND) }

    private fun granted(ctx: Context, p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

    fun hasPreciseLocation(ctx: Context) = granted(ctx, Manifest.permission.ACCESS_FINE_LOCATION)
    fun hasAnyLocation(ctx: Context) = hasPreciseLocation(ctx) || granted(ctx, Manifest.permission.ACCESS_COARSE_LOCATION)

    /** "Allow all the time" – needed so the system may restart the service after it was killed with the screen off */
    fun hasBackgroundLocation(ctx: Context) =
        if (Build.VERSION.SDK_INT >= 29) granted(ctx, Manifest.permission.ACCESS_BACKGROUND_LOCATION) else hasAnyLocation(ctx)

    fun hasNotifications(ctx: Context) =
        if (Build.VERSION.SDK_INT >= 33) granted(ctx, Manifest.permission.POST_NOTIFICATIONS)
        else NotificationManagerCompat.from(ctx).areNotificationsEnabled()

    fun ignoresBatteryOptimization(ctx: Context): Boolean {
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return pm.isIgnoringBatteryOptimizations(ctx.packageName)
    }

    /** "Restrict background activity" – on Xiaomi this is what the battery saver setting "Restrict" switches on */
    fun backgroundRestricted(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < 28) return false
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
        return am.isBackgroundRestricted
    }

    fun locationEnabled(ctx: Context): Boolean {
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return if (Build.VERSION.SDK_INT >= 28) lm.isLocationEnabled else lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }

    fun complete(ctx: Context) = hasPreciseLocation(ctx) && hasBackgroundLocation(ctx) && hasNotifications(ctx) &&
        ignoresBatteryOptimization(ctx) && !backgroundRestricted(ctx)

    /** System dialog "Let the app always run in background?" – falls back to the list of all apps */
    fun requestIgnoreBatteryOptimization(activity: Activity) {
        Diag.log("setup: battery optimization dialog")
        if (start(activity, Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + activity.packageName)))) return
        if (start(activity, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))) return
        openAppDetails(activity)
    }

    fun openAppDetails(ctx: Context) {
        start(ctx, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName)))
    }

    /**
     * The maker's own "autostart" / app launch manager. These screens are not public API: the component names come
     * from the vendors' system apps and change between versions, so every one is only tried, with the app details
     * page as the fallback.
     */
    fun openAutostart(ctx: Context) {
        val candidates = when (vendor) {
            Vendor.XIAOMI -> listOf(
                ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"))
            Vendor.HUAWEI -> listOf(
                ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
                ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"))
            Vendor.ONEPLUS -> listOf(
                ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
                ComponentName("com.oplus.safecenter", "com.oplus.safecenter.permission.startup.StartupAppListActivity"))
            Vendor.SAMSUNG -> listOf(
                ComponentName("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity"),
                ComponentName("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.BatteryActivity"))
            Vendor.OTHER -> emptyList()
        }
        for (c in candidates) {
            if (start(ctx, Intent().setComponent(c))) {
                Diag.log("setup: opened ${c.flattenToShortString()}")
                return
            }
        }
        Diag.log("setup: no vendor screen for $vendor, app details instead")
        openAppDetails(ctx)
    }

    /** Xiaomi's per-app battery saver ("No restrictions"); elsewhere the app details page has the battery entry */
    fun openBatteryForApp(ctx: Context) {
        if (vendor == Vendor.XIAOMI) {
            val i = Intent().setComponent(ComponentName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"))
                .putExtra("package_name", ctx.packageName)
                .putExtra("package_label", ctx.getString(R.string.app_name))
            if (start(ctx, i)) return
        }
        openAppDetails(ctx)
    }

    private fun start(ctx: Context, i: Intent): Boolean = try {
        if (ctx !is Activity) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
        true
    } catch (_: Exception) {   // ActivityNotFoundException, SecurityException (screen not exported on this version)
        false
    }

    /**
     * Shown once a day at most when a ride starts and something is missing for display-off rides.
     * The ride is never held up by it.
     */
    fun maybeShowHint(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return   // a dialog on a dying activity crashes (BadTokenException)
        if (complete(activity)) return
        val p = activity.getSharedPreferences("eltouro_diag", Context.MODE_PRIVATE)
        val last = p.getLong("hintShownAt", 0L)
        if (System.currentTimeMillis() - last < 24 * 3600_000L) return
        p.edit().putLong("hintShownAt", System.currentTimeMillis()).apply()
        Diag.log("setup: hint shown (background=${hasBackgroundLocation(activity)} battery=${ignoresBatteryOptimization(activity)} notifications=${hasNotifications(activity)} restricted=${backgroundRestricted(activity)})")
        AlertDialog.Builder(activity)
            .setTitle(R.string.setup_hint_title)
            .setMessage(R.string.setup_hint_text)
            .setPositiveButton(R.string.setup_hint_open) { _, _ ->
                activity.startActivity(Intent(activity, DiagnoseActivity::class.java))
            }
            .setNegativeButton(R.string.setup_hint_later, null)
            .show()
    }
}
