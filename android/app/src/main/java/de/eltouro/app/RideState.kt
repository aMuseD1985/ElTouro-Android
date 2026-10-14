package de.eltouro.app

import android.content.Context

/**
 * State shared by the plugin (called from the web page) and the foreground service. Unlike the old in-memory
 * TrackerState it is written to disk: when the system kills the process during a ride and restarts the sticky
 * service, the recording session is still known and the points keep going to the server.
 */
object RideState {
    @Volatile var baseUrl = ""
    @Volatile var csrf = ""
    @Volatile var session = 0L            // recording session of the server; 0 = no recording, only position events
    @Volatile var active = false          // the rider has started ride mode / free ride and not stopped it
    @Volatile var title = "ElTouro"
    @Volatile var text = ""
    @Volatile var ridePath = ""        // page of the running ride (/tour/<id>/go or /free) – opened again after a restart
    @Volatile var startedAt = 0L

    /** A ride the app should bring the rider back to: active, on a known page, started less than 12 h ago (the server's resume window). */
    fun resumablePath(now: Long = System.currentTimeMillis()): String? =
        if (active && ridePath.startsWith("/") && now - startedAt < 12 * 3600_000L) ridePath else null

    @Volatile var plugin: TrackerPlugin? = null
    @Volatile var service: TrackerService? = null

    private const val PREFS = "eltouro_ride"

    fun load(ctx: Context) {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        baseUrl = p.getString("baseUrl", "") ?: ""
        csrf = p.getString("csrf", "") ?: ""
        session = p.getLong("session", 0L)
        active = p.getBoolean("active", false)
        title = p.getString("title", "ElTouro") ?: "ElTouro"
        text = p.getString("text", "") ?: ""
        ridePath = p.getString("ridePath", "") ?: ""
        startedAt = p.getLong("startedAt", 0L)
    }

    fun save(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("baseUrl", baseUrl)
            .putString("csrf", csrf)
            .putLong("session", session)
            .putBoolean("active", active)
            .putString("title", title)
            .putString("text", text)
            .putString("ridePath", ridePath)
            .putLong("startedAt", startedAt)
            .apply()
    }
}
