package de.eltouro.app.diag

import java.io.File
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Append-only diagnostics log with two generations (log.txt, log.1.txt): when log.txt passes maxBytes it becomes
 * log.1.txt and a fresh file starts, so the whole log stays below 2 × maxBytes. Plain JVM – no Android classes.
 */
class LogFile(
    private val dir: File,
    private val maxBytes: Long = 400_000,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val current = File(dir, "log.txt")
    private val previous = File(dir, "log.1.txt")
    private val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z", Locale.ROOT)

    @Synchronized
    fun add(message: String) {
        try {
            if (!dir.exists()) dir.mkdirs()
            if (current.length() > maxBytes) {
                previous.delete()
                current.renameTo(previous)
            }
            val line = stamp.format(Date(clock())) + "  " + message.replace('\n', ' ') + "\n"
            current.appendText(line, Charsets.UTF_8)
        } catch (_: Exception) {
            // diagnostics must never take the ride down
        }
    }

    /** Both generations, oldest first. */
    @Synchronized
    fun readAll(): String {
        val sb = StringBuilder()
        if (previous.exists()) sb.append(previous.readText(Charsets.UTF_8))
        if (current.exists()) sb.append(current.readText(Charsets.UTF_8))
        return sb.toString()
    }

    /** The last [lines] lines of the current file (reads at most the last 32 kB). */
    @Synchronized
    fun tail(lines: Int): List<String> {
        if (!current.exists()) return emptyList()
        return try {
            RandomAccessFile(current, "r").use { f ->
                val len = f.length()
                val from = maxOf(0L, len - 32_768)
                val buf = ByteArray((len - from).toInt())
                f.seek(from)
                f.readFully(buf)
                var text = String(buf, Charsets.UTF_8)
                if (from > 0) text = text.substringAfter('\n')
                text.trimEnd('\n').split('\n').filter { it.isNotEmpty() }.takeLast(lines)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun clear() {
        current.delete()
        previous.delete()
    }

    fun sizeBytes(): Long = (if (current.exists()) current.length() else 0L) + (if (previous.exists()) previous.length() else 0L)
}
