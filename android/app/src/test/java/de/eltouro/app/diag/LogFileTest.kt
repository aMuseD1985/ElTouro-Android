package de.eltouro.app.diag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class LogFileTest {
    private fun tmp() = Files.createTempDirectory("eltouro-log").toFile()

    @Test
    fun appendsLinesWithTimestamp() {
        val log = LogFile(tmp(), clock = { 0L })
        log.add("first")
        log.add("second\nline")
        val lines = log.tail(10)
        assertEquals(2, lines.size)
        assertTrue(lines[0].endsWith("  first"))
        assertTrue("newlines are flattened", lines[1].endsWith("  second line"))
        assertTrue(lines[0].startsWith("1970-01-01"))
    }

    @Test
    fun rotatesIntoSecondGenerationAndKeepsBoth() {
        val dir = tmp()
        val log = LogFile(dir, maxBytes = 200)
        repeat(40) { log.add("entry number $it") }
        assertTrue(java.io.File(dir, "log.1.txt").exists())
        assertTrue("bounded to about two generations", log.sizeBytes() < 600)
        val all = log.readAll()
        assertTrue(all.contains("entry number 39"))
        assertTrue("oldest entries are dropped", !all.contains("entry number 0\n"))
    }

    @Test
    fun tailReturnsLastLines() {
        val log = LogFile(tmp())
        repeat(100) { log.add("n$it") }
        val t = log.tail(3)
        assertEquals(listOf("n97", "n98", "n99"), t.map { it.substringAfterLast("  ") })
    }

    @Test
    fun clearRemovesEverything() {
        val log = LogFile(tmp())
        log.add("x")
        log.clear()
        assertEquals(0L, log.sizeBytes())
        assertEquals(emptyList<String>(), log.tail(5))
    }
}
