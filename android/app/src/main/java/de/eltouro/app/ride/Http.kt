package de.eltouro.app.ride

import java.net.HttpURLConnection
import java.net.URL

/** Minimal JSON POST (plain JVM). Never on the main thread. */
object Http {
    /** GET with short timeouts (widgets update inside a broadcast). */
    fun get(url: String, headers: Map<String, String>): UploadResult = try {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = "GET"
        c.connectTimeout = 8_000
        c.readTimeout = 10_000
        c.instanceFollowRedirects = false
        c.setRequestProperty("Accept", "application/json")
        for ((k, v) in headers) c.setRequestProperty(k, v)
        val code = c.responseCode
        val body = try {
            (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }?.take(64_000) ?: ""
        } catch (_: Exception) { "" }
        c.disconnect()
        UploadResult(code, body)
    } catch (e: Exception) {
        UploadResult(-1, "", e.javaClass.simpleName)
    }

    fun postJson(url: String, json: String, headers: Map<String, String>): UploadResult = try {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.connectTimeout = 10_000
        c.readTimeout = 15_000
        c.doOutput = true
        c.instanceFollowRedirects = false   // the access gate redirects instead of answering – that is a failure, not a page
        c.setRequestProperty("Content-Type", "application/json")
        for ((k, v) in headers) c.setRequestProperty(k, v)
        c.outputStream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
        val code = c.responseCode
        val body = try {
            (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }?.take(2000) ?: ""
        } catch (_: Exception) { "" }
        c.disconnect()
        UploadResult(code, body)
    } catch (e: Exception) {
        UploadResult(-1, "", e.javaClass.simpleName)
    }
}
