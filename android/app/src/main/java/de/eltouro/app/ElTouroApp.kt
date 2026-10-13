package de.eltouro.app

import android.app.Application
import android.os.Build
import android.os.Process
import androidx.core.content.pm.PackageInfoCompat
import de.eltouro.app.diag.Diag

class ElTouroApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Diag.init(this)
        Diag.processStartedAt = System.currentTimeMillis()
        RideState.load(this)
        Diag.log("process start pid=${Process.myPid()} app=${appVersion()} android=${Build.VERSION.RELEASE} (sdk ${Build.VERSION.SDK_INT})" +
            " ride active=${RideState.active} session=${RideState.session}")
        Diag.logExitReasons(this)
    }

    fun appVersion(): String = try {
        val info = packageManager.getPackageInfo(packageName, 0)
        "${info.versionName} (${PackageInfoCompat.getLongVersionCode(info)})"
    } catch (_: Exception) {
        "?"
    }
}
