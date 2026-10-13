package de.eltouro.app.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ServerSocket
import kotlin.concurrent.thread

class CookieHopTest {
    /** A one-shot HTTP server on a free port: answers [response] and remembers the request headers. */
    private class OneShot(private val response: String) {
        val socket = ServerSocket(0)
        val port get() = socket.localPort
        @Volatile var requestHeaders = ""
        private val t = thread {
            socket.accept().use { s ->
                val r = s.getInputStream().bufferedReader()
                val sb = StringBuilder()
                while (true) { val l = r.readLine() ?: break; if (l.isEmpty()) break; sb.append(l).append('\n') }
                requestHeaders = sb.toString()
                s.getOutputStream().write(response.toByteArray()); s.getOutputStream().flush()
            }
            socket.close()
        }
        fun join() = t.join(5000)
    }

    @Test
    fun takesTheCookieOfTheRedirectAndDoesNotFollowIt() {
        val srv = OneShot("HTTP/1.1 303 See Other\r\nSet-Cookie: eltouro_sid=SIGNEDIN; expires=Wed, 01 Jan 2031 00:00:00 GMT; path=/; HttpOnly\r\n" +
            "Set-Cookie: lang=de; path=/\r\nLocation: /tour/5?x=1\r\nContent-Length: 0\r\nConnection: close\r\n\r\n")
        val stored = mutableListOf<Pair<String, String>>()
        val url = "http://127.0.0.1:${srv.port}/auth/app?t=abc&next=%2F"
        val r = CookieHop({ "eltouro_sid=ANON" }, { u, c -> stored += u to c }, "TestAgent/1").open(url)
        srv.join()
        assertEquals(303, r.code)
        assertEquals("http://127.0.0.1:${srv.port}/tour/5?x=1", r.location)
        assertEquals(2, r.cookies)
        assertTrue(stored.any { it.first == url && it.second.startsWith("eltouro_sid=SIGNEDIN") })
        assertTrue("sends the current cookie", srv.requestHeaders.contains("Cookie: eltouro_sid=ANON"))
        assertTrue(srv.requestHeaders.contains("User-Agent: TestAgent/1"))
    }

    @Test
    fun noRedirectNoLocation() {
        val srv = OneShot("HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nok")
        val r = CookieHop({ null }, { _, _ -> }).open("http://127.0.0.1:${srv.port}/verify?t=x")
        srv.join()
        assertEquals(200, r.code)
        assertNull(r.location)
        assertEquals(0, r.cookies)
        assertFalse("no cookie header without cookies", srv.requestHeaders.contains("Cookie:"))
    }

    @Test
    fun noConnection() {
        val port = ServerSocket(0).use { it.localPort }   // free, nobody listens
        val r = CookieHop({ null }, { _, _ -> }).open("http://127.0.0.1:$port/auth/app?r=x")
        assertEquals(-1, r.code)
        assertTrue(r.error.isNotEmpty())
    }

    @Test
    fun signInPaths() {
        assertTrue(CookieHop.isSignInPath("/auth/app"))
        assertTrue(CookieHop.isSignInPath("/auth/app/"))
        assertTrue(CookieHop.isSignInPath("/verify"))
        assertFalse(CookieHop.isSignInPath("/auth/app-return"))
        assertFalse(CookieHop.isSignInPath("/login"))
        assertFalse(CookieHop.isSignInPath(null))
    }
}
