package de.eltouro.app.web

import java.net.HttpURLConnection
import java.net.URL

/**
 * One request to a page that signs in and redirects (/auth/app, /verify) – done by the app instead of the WebView.
 *
 * Why: with a remote server.url Capacitor fetches every HTML page itself (WebViewLocalServer.handleProxyRequest) to inject
 * its bridge. That HttpURLConnection follows redirects on its own, sends the OLD cookie to the redirect target and keeps only
 * the Set-Cookie of the LAST response. The sign-in cookie comes with the 303 of /auth/app, so it was lost and the next page
 * gave the WebView a fresh anonymous session – "signed in with Google, back in the app, signed out".
 *
 * Here: no redirect following, every Set-Cookie of this response goes into the WebView's cookie store, the caller then
 * loads [Result.location] in the WebView (which now sends the new cookie). Plain JVM; the cookie store is passed in.
 */
class CookieHop(
    private val cookieFor: (url: String) -> String?,
    private val store: (url: String, setCookie: String) -> Unit,
    private val userAgent: String? = null,
) {
    data class Result(val code: Int, val location: String?, val cookies: Int, val error: String = "")

    fun open(url: String): Result = try {
        val c = URL(url).openConnection() as HttpURLConnection
        c.instanceFollowRedirects = false
        c.connectTimeout = 15_000
        c.readTimeout = 20_000
        c.setRequestProperty("Accept", "text/html")
        if (!userAgent.isNullOrEmpty()) c.setRequestProperty("User-Agent", userAgent)
        cookieFor(url)?.takeIf { it.isNotEmpty() }?.let { c.setRequestProperty("Cookie", it) }
        val code = c.responseCode
        val setCookies = c.headerFields.entries.filter { it.key != null && it.key.equals("Set-Cookie", ignoreCase = true) }.flatMap { it.value }
        for (sc in setCookies) store(url, sc)
        val location = c.getHeaderField("Location")?.let { URL(URL(url), it).toString() }
        try { (if (code >= 400) c.errorStream else c.inputStream)?.close() } catch (_: Exception) { }
        c.disconnect()
        Result(code, location, setCookies.size)
    } catch (e: Exception) {
        Result(-1, null, 0, e.javaClass.simpleName)
    }

    companion object {
        /** GET pages of the web app that sign in and set the session cookie in a redirect – never through Capacitor's proxy. */
        val SIGN_IN_PATHS = setOf("/auth/app", "/verify")

        fun isSignInPath(path: String?): Boolean = path != null && path.trimEnd('/') in SIGN_IN_PATHS
    }
}
