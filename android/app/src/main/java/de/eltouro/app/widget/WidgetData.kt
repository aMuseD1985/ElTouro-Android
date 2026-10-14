package de.eltouro.app.widget

import android.content.Context
import de.eltouro.app.DeviceKey
import de.eltouro.app.RideState
import de.eltouro.app.diag.Diag
import de.eltouro.app.ride.Http
import de.eltouro.app.web.Summary
import java.util.Locale

/**
 * Data of the home-screen widgets: GET /api/app/summary with the device key (no WebView, no session), cached on the phone.
 * "Near me" uses the last coarse position of a ride (rounded to 2 decimals ≈ 1 km, sent only to our server, never stored there)
 * – without one the server takes the area from the rider's notification settings.
 */
object WidgetData {
    private const val PREFS = "eltouro_widgets"

    enum class Status { OK, NO_KEY, NOT_AVAILABLE, OFFLINE, ERROR, NEVER }

    data class State(val summary: Summary?, val fetchedAt: Long, val status: Status)

    fun state(ctx: Context): State {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val status = try { Status.valueOf(p.getString("status", "NEVER") ?: "NEVER") } catch (_: Exception) { Status.NEVER }
        return State(p.getString("json", null)?.let { Summary.parse(it) }, p.getLong("at", 0L), status)
    }

    fun ageMs(ctx: Context): Long = System.currentTimeMillis() - ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong("tried", 0L)

    fun saveWhere(ctx: Context, lat: Double, lng: Double) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("lat", String.format(Locale.ROOT, "%.2f", lat))
            .putString("lng", String.format(Locale.ROOT, "%.2f", lng))
            .putLong("whereAt", System.currentTimeMillis()).apply()
    }

    /** Blocking – never on the main thread. */
    fun refresh(ctx: Context) {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        p.edit().putLong("tried", System.currentTimeMillis()).apply()
        val base = RideState.baseUrl
        val key = DeviceKey.get(ctx)
        if (base.isEmpty() || key == null) {
            p.edit().putString("status", Status.NO_KEY.name).apply()
            return
        }
        var url = "$base/api/app/summary?v=1"
        val lat = p.getString("lat", null)
        val lng = p.getString("lng", null)
        if (lat != null && lng != null && System.currentTimeMillis() - p.getLong("whereAt", 0L) < 7 * 24 * 3600_000L) url += "&lat=$lat&lng=$lng"
        val r = Http.get(url, DeviceKey.authHeaders(key))
        val status = when {
            r.code in 200..299 && Summary.parse(r.body) != null -> {
                p.edit().putString("json", r.body).putLong("at", System.currentTimeMillis()).apply()
                Status.OK
            }
            r.code == 401 -> { DeviceKey.forget(ctx, "widgets: server answered 401"); Status.NO_KEY }
            r.code == 404 -> Status.NOT_AVAILABLE      // the server does not have /api/app/summary yet
            r.code == -1 -> Status.OFFLINE
            else -> Status.ERROR
        }
        val before = p.getString("status", "NEVER")
        p.edit().putString("status", status.name).apply()
        if (before != status.name) Diag.log("widgets: summary ${status.name.lowercase()}" + (if (r.code > 0) " (HTTP ${r.code})" else ""))
    }
}
