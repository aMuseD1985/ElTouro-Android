package de.eltouro.app

import android.content.Context
import android.net.Uri
import android.webkit.CookieManager
import de.eltouro.app.diag.Diag
import de.eltouro.app.ride.Http

/**
 * The app's own key for the server (app_device_lib.php): the background upload uses it instead of the WebView's
 * session, so a killed app with lost cookies keeps recording. Created once from the signed-in WebView (cookie + CSRF),
 * kept per server host, forgotten on logout. Blocking calls – never on the main thread.
 */
object DeviceKey {
    private const val PREFS = "eltouro_device"

    fun get(ctx: Context): String? {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val host = Uri.parse(RideState.baseUrl).host ?: return null
        return if (p.getString("host", null) == host) p.getString("key", null) else null
    }

    /** Makes sure a key exists; registers one when the WebView is signed in. Returns true when a key is there. */
    fun ensure(ctx: Context, appVersion: String): Boolean {
        if (get(ctx) != null) return true
        val base = RideState.baseUrl
        if (base.isEmpty() || RideState.csrf.isEmpty()) return false
        val headers = HashMap<String, String>()
        headers["X-CSRF"] = RideState.csrf
        CookieManager.getInstance().getCookie(base)?.let { headers["Cookie"] = it }
        val json = "{\"action\":\"register\",\"version\":\"" + appVersion.replace("\"", "") + "\"}"
        val r = Http.postJson("$base/api/app/device", json, headers)
        val key = Regex("\"token\"\\s*:\\s*\"(eld_[0-9a-f]{40})\"").find(r.body)?.groupValues?.get(1)
        if (key == null) {
            Diag.log("device key: registration failed – ${if (r.code > 0) "HTTP ${r.code}" else r.error}")
            return false
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("key", key).putString("host", Uri.parse(base).host).apply()
        Diag.log("device key: registered (${key.take(8)}…)")
        return true
    }

    /** Local only – e.g. after the server answered 401 for the key. */
    fun forget(ctx: Context, why: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        Diag.log("device key: forgotten ($why)")
    }

    /** On logout: tell the server, then forget. */
    fun revoke(ctx: Context) {
        val key = get(ctx) ?: return
        Http.postJson(RideState.baseUrl + "/api/app/device", "{\"action\":\"revoke\"}", authHeaders(key))
        forget(ctx, "signed out")
    }

    fun authHeaders(key: String) = mapOf("Authorization" to "Bearer $key", "X-Device-Token" to key)
}
