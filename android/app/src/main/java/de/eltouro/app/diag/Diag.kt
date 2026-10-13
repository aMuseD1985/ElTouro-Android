package de.eltouro.app.diag

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Everything the diagnostics screen shows and the exported log contains. Written from the service, the plugin and
 * the activity; read by DiagnoseActivity. Values are plain volatiles – a slightly stale number on screen is fine.
 */
object Diag {
    private var file: LogFile? = null

    val fixes = FixStats()
    @Volatile var serviceSince = 0L
    @Volatile var lastAccuracyM = 0f
    @Volatile var bufferSize = 0
    @Volatile var lastUploadAt = 0L
    @Volatile var lastUploadCode = 0          // HTTP status, -1 = no connection / exception
    @Volatile var lastUploadError = ""
    @Volatile var lastUploadOkAt = 0L
    @Volatile var failedUploads = 0
    @Volatile var webLastAnswerAt = 0L
    @Volatile var webStalledSince = 0L
    @Volatile var lastPageCall = ""
    @Volatile var lastPageCallAt = 0L
    @Volatile var processStartedAt = 0L

    fun init(ctx: Context) {
        if (file == null) file = LogFile(File(ctx.filesDir, "diag"))
    }

    fun log(message: String) {
        Log.i("ElTouro", message)
        file?.add(message)
    }

    fun tail(lines: Int): List<String> = file?.tail(lines) ?: emptyList()
    fun readAll(): String = file?.readAll() ?: ""
    fun clear() { file?.clear() }

    fun pageCall(what: String) {
        lastPageCall = what
        lastPageCallAt = System.currentTimeMillis()
        log("page: $what")
    }

    /** Restarts of the sticky service by the system – kept across processes. */
    fun countRestart(ctx: Context): Int {
        val p = ctx.getSharedPreferences("eltouro_diag", Context.MODE_PRIVATE)
        val n = p.getInt("restarts", 0) + 1
        p.edit().putInt("restarts", n).apply()
        return n
    }

    fun restarts(ctx: Context): Int = ctx.getSharedPreferences("eltouro_diag", Context.MODE_PRIVATE).getInt("restarts", 0)

    /**
     * Why did the previous processes end? (Android 11+.) This is the most direct evidence of whether the system
     * killed the app during a ride. Each exit is logged once.
     */
    fun logExitReasons(ctx: Context) {
        if (Build.VERSION.SDK_INT < 30) return
        try {
            val am = ctx.getSystemService(ActivityManager::class.java) ?: return
            val p = ctx.getSharedPreferences("eltouro_diag", Context.MODE_PRIVATE)
            val seen = p.getLong("exitSeen", 0L)
            val exits = am.getHistoricalProcessExitReasons(ctx.packageName, 0, 10)
                .filter { it.timestamp > seen }
                .sortedBy { it.timestamp }
            val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT)
            for (e in exits) {
                log("previous process ended at ${fmt.format(Date(e.timestamp))}: ${Names.exitReason(e.reason)}" +
                    " (status ${e.status}, importance ${Names.importance(e.importance)}, pss ${e.pss / 1024} MB)" +
                    (e.description?.let { " – $it" } ?: ""))
            }
            exits.lastOrNull()?.let { p.edit().putLong("exitSeen", it.timestamp).apply() }
        } catch (e: Exception) {
            log("exit reasons unavailable: ${e.javaClass.simpleName}")
        }
    }
}
