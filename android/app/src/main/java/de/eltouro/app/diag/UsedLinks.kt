package de.eltouro.app.diag

import java.security.MessageDigest

/**
 * One-time login links (de.eltouro.app://login?t=…) already redeemed – kept as SHA-256, the newest [max].
 * Android hands the launch intent over again when the app is restored from the recents or the activity is
 * recreated; redeeming the same token twice fails on the server and lands on /login. Plain JVM.
 */
class UsedLinks(serialized: String?, private val max: Int = 20) {
    private val hashes = (serialized ?: "").split(',').filter { it.length == 64 }.toMutableList()

    fun seen(token: String): Boolean = sha256(token) in hashes

    fun add(token: String) {
        val h = sha256(token)
        hashes.remove(h)
        hashes.add(h)
        while (hashes.size > max) hashes.removeAt(0)
    }

    fun serialize(): String = hashes.joinToString(",")

    companion object {
        fun sha256(s: String): String =
            MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
