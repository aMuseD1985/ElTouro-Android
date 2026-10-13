package de.eltouro.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebView
import com.getcapacitor.BridgeActivity
import com.getcapacitor.WebViewListener
import de.eltouro.app.diag.BridgeProbe
import de.eltouro.app.diag.Diag
import de.eltouro.app.diag.UsedLinks

class MainActivity : BridgeActivity() {
    private val heartbeat = WebViewHeartbeat()
    private val h = Handler(Looper.getMainLooper())
    private var creating = false        // inside super.onCreate: BridgeActivity.load() hands the launch intent to onNewIntent
    private var recreated = false       // configuration change or restore after the process was killed
    private var linkHandled = false
    private var lastProbe = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        recreated = savedInstanceState != null
        creating = true
        registerPlugin(TrackerPlugin::class.java)
        super.onCreate(savedInstanceState)   // → load() → onNewIntent(getIntent()) → handleLink (cold start)
        creating = false
        CookieManager.getInstance().setAcceptCookie(true)
        getBridge()?.addWebViewListener(pageListener)
        if (!linkHandled) restoreSignIn()
        heartbeat.start()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (handleLink(intent, coldStart = creating)) linkHandled = true
    }

    override fun onResume() {
        super.onResume()
        if (RideState.service != null) Diag.log("activity: resumed")
    }

    // The WebView keeps cookies in memory first – write them to disk so the sign-in survives closing the app
    override fun onPause() {
        CookieManager.getInstance().flush()
        if (RideState.service != null) Diag.log("activity: paused")
        super.onPause()
    }

    override fun onStop() {
        CookieManager.getInstance().flush()
        if (RideState.service != null) Diag.log("activity: stopped")
        super.onStop()
    }

    override fun onDestroy() {
        heartbeat.stop()
        h.removeCallbacksAndMessages(null)
        if (RideState.service != null) Diag.log("activity: destroyed")
        super.onDestroy()
    }

    /**
     * 1. de.eltouro.app://login?t=…&next=… : back from "Sign in with Google" in the system browser – sign the WebView in with
     *    the one-time token. Each token is redeemed exactly once: Android hands the launch intent over again when the app is
     *    restored from the recents or the activity is recreated, and a second redemption ends on /login with an error.
     * 2. a tapped ElTouro link (flyer QR code, mail, chat): opens inside the app – only our own hosts.
     * Logged without token or query string.
     */
    private fun handleLink(intent: Intent?, coldStart: Boolean): Boolean {
        if (intent == null || Intent.ACTION_VIEW != intent.action) return false
        val data: Uri = intent.data ?: return false
        val fromHistory = (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0
        val how = when {
            !coldStart -> "app running"
            recreated -> "activity recreated"
            else -> "cold start"
        } + (if (fromHistory) ", from recents" else "")
        val shown = data.scheme + "://" + data.host + (data.path ?: "")
        if (coldStart && (recreated || fromHistory)) {
            Diag.log("link: $shown ignored ($how) – the old launch link is not opened again")
            return false
        }
        val bridge = getBridge() ?: return false
        val url: String = if ("de.eltouro.app" == data.scheme && "login" == data.host) {
            val t = data.getQueryParameter("t")
            if (t == null || !t.matches(Regex("[0-9a-f]{64}"))) {
                Diag.log("link: $shown without a valid token ($how)")
                return false
            }
            val prefs = getSharedPreferences("eltouro_links", Context.MODE_PRIVATE)
            val used = UsedLinks(prefs.getString("used", null))
            if (used.seen(t)) {
                Diag.log("link: login link already redeemed – not again ($how)")
                return false
            }
            used.add(t)
            prefs.edit().putString("used", used.serialize()).commit()   // commit: must be on disk before the page loads
            val base = bridge.config.serverUrl ?: return false
            Diag.login("Google hand-over ($how) → /auth/app")
            base.replace(Regex("/+$"), "") + "/auth/app?t=" + t + "&next=" + Uri.encode(data.getQueryParameter("next") ?: "/")
        } else if ("https" == data.scheme && data.host?.let { it in OWN_HOSTS } == true) {
            Diag.log("link: $shown ($how)")
            data.toString()
        } else {
            return false
        }
        load(url, coldStart)
        return true
    }

    /**
     * Cold start without a link: if the WebView has no session cookie any more but the "stay signed in" token is there,
     * sign in with it right away (/auth/app?r=…) – before the first page, instead of waiting for the page to do it.
     */
    private fun restoreSignIn() {
        val base = getBridge()?.config?.serverUrl?.replace(Regex("/+$"), "") ?: return
        val cookie = CookieManager.getInstance().getCookie(base)
        val hasSession = cookie?.contains("eltouro_sid=") == true
        val remember = Remember.get(this)
        Diag.log("start: ${if (recreated) "activity recreated" else "cold start"} · session cookie=${if (hasSession) "yes" else "no"}" +
            " · stay signed in=${if (remember != null) "yes" else "no"}")
        if (!hasSession && remember != null) {
            Diag.login("no session cookie – signing in with the stay-signed-in token")
            load("$base/auth/app?r=$remember&next=%2F", true)
        }
    }

    /** On a cold start the bridge has already started loading the start page: stop it, so its anonymous session cookie cannot overwrite the sign-in. */
    private fun load(url: String, coldStart: Boolean) {
        val wv = getBridge()?.webView ?: return
        wv.post {
            if (coldStart) wv.stopLoading()
            wv.loadUrl(url)
        }
    }

    private val pageListener = object : WebViewListener() {
        override fun onPageLoaded(webView: WebView) {
            // ask the page twice: assets/native_tracker.js and the bridge can come a little after "loaded"
            h.postDelayed({ probe(webView) }, 1500)
            h.postDelayed({ probe(webView) }, 6000)
        }

        override fun onRenderProcessGone(webView: WebView, detail: RenderProcessGoneDetail): Boolean {
            Diag.log("webview: renderer process gone" + (if (Build.VERSION.SDK_INT >= 26) " (crash=${detail.didCrash()})" else ""))
            return false
        }
    }

    /** What the page reports about itself (path, bridge, signed in) – logged when it changes, shown in Diagnose. */
    private fun probe(webView: WebView) {
        if (isDestroyed) return
        webView.evaluateJavascript(BridgeProbe.SCRIPT) { raw -> takeProbe(raw) }
    }

    private fun takeProbe(raw: String?) {
        val p = BridgeProbe.parse(raw) ?: return
        Diag.probe = p
        Diag.probeAt = System.currentTimeMillis()
        val s = p.summary()
        if (s != lastProbe) {
            lastProbe = s
            Diag.log("page: $s")
            if (p.ridePage && !p.available) Diag.log("page: ride page WITHOUT app bridge – it records in the browser, which pauses with the display off")
            if (p.path.startsWith("/login")) Diag.login("page shows /login (signed out)")
        }
    }

    /**
     * Asks the page every 10 s while a ride runs (the same question as [probe]). If the answer does not come, Android has
     * paused the WebView – that is when the web app's announcements stop. Only changes are logged.
     */
    private inner class WebViewHeartbeat {
        private val hb = Handler(Looper.getMainLooper())
        private var sentAt = 0L
        private var pending = false
        private var stalledLogged = false

        private val tick = object : Runnable {
            override fun run() {
                check()
                hb.postDelayed(this, 10_000)
            }
        }

        fun start() {
            hb.removeCallbacks(tick)
            hb.postDelayed(tick, 10_000)
        }

        fun stop() = hb.removeCallbacks(tick)

        private fun check() {
            if (RideState.service == null) {
                pending = false
                stalledLogged = false
                Diag.webStalledSince = 0L
                return
            }
            val now = SystemClock.elapsedRealtime()
            if (pending) {
                if (!stalledLogged && now - sentAt > 15_000) {
                    stalledLogged = true
                    Diag.webStalledSince = System.currentTimeMillis() - (now - sentAt)
                    Diag.log("webview: JavaScript not answering for ${(now - sentAt) / 1000} s")
                }
                return
            }
            val wv = getBridge()?.webView ?: return
            pending = true
            sentAt = now
            wv.evaluateJavascript(BridgeProbe.SCRIPT) { raw ->
                val waited = SystemClock.elapsedRealtime() - sentAt
                pending = false
                Diag.webLastAnswerAt = System.currentTimeMillis()
                if (stalledLogged) Diag.log("webview: JavaScript answering again after ${waited / 1000} s")
                stalledLogged = false
                Diag.webStalledSince = 0L
                takeProbe(raw)
            }
        }
    }

    companion object {
        private val OWN_HOSTS = setOf("app.eltouro.de", "test.eltouro.de", "beta.eltouro.de")
    }
}
