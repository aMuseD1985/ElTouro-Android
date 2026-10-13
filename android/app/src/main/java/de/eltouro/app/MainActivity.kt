package de.eltouro.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.webkit.CookieManager
import com.getcapacitor.BridgeActivity
import de.eltouro.app.diag.Diag

class MainActivity : BridgeActivity() {
    private val heartbeat = WebViewHeartbeat()

    override fun onCreate(savedInstanceState: Bundle?) {
        registerPlugin(TrackerPlugin::class.java)
        super.onCreate(savedInstanceState)   // BridgeActivity.load() hands the launch intent to onNewIntent → openLink
        CookieManager.getInstance().setAcceptCookie(true)
        heartbeat.start()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openLink(intent)
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
        if (RideState.service != null) Diag.log("activity: destroyed")
        super.onDestroy()
    }

    /**
     * 1. de.eltouro.app://login?t=…&next=… : back from "Sign in with Google" in the system browser – sign the WebView in with the token
     * 2. a tapped ElTouro link (flyer QR code, mail, chat): opens inside the app – only our own hosts
     */
    private fun openLink(intent: Intent?) {
        if (intent == null || Intent.ACTION_VIEW != intent.action) return
        val data: Uri = intent.data ?: return
        val bridge = getBridge() ?: return
        val url: String = if ("de.eltouro.app" == data.scheme && "login" == data.host) {
            val t = data.getQueryParameter("t")
            val next = data.getQueryParameter("next")
            if (t == null || !t.matches(Regex("[0-9a-f]{64}"))) return
            val base = bridge.config.serverUrl ?: return
            base.replace(Regex("/+$"), "") + "/auth/app?t=" + t + "&next=" + Uri.encode(next ?: "/")
        } else if ("https" == data.scheme && data.host?.let { it in OWN_HOSTS } == true) {
            data.toString()
        } else {
            return
        }
        val wv = bridge.webView ?: return
        wv.post { wv.loadUrl(url) }
    }

    /**
     * Pings the page's JavaScript every 10 s while a ride runs. If the answer does not come, Android has paused the
     * WebView – that is when the web app's announcements stop. Only changes are logged.
     */
    private inner class WebViewHeartbeat {
        private val h = Handler(Looper.getMainLooper())
        private var sentAt = 0L
        private var pending = false
        private var stalledLogged = false

        private val tick = object : Runnable {
            override fun run() {
                check()
                h.postDelayed(this, 10_000)
            }
        }

        fun start() {
            h.removeCallbacks(tick)
            h.postDelayed(tick, 10_000)
        }

        fun stop() = h.removeCallbacks(tick)

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
            wv.evaluateJavascript("1") {
                val waited = SystemClock.elapsedRealtime() - sentAt
                pending = false
                Diag.webLastAnswerAt = System.currentTimeMillis()
                if (stalledLogged) Diag.log("webview: JavaScript answering again after ${waited / 1000} s")
                stalledLogged = false
                Diag.webStalledSince = 0L
            }
        }
    }

    companion object {
        private val OWN_HOSTS = setOf("app.eltouro.de", "test.eltouro.de", "beta.eltouro.de")
    }
}
