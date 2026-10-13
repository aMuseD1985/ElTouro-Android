package de.eltouro.app.diag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FixStatsTest {
    @Test
    fun countsFixesPerMinute() {
        val s = FixStats()
        for (i in 0 until 90) s.onFix(i * 1000L, 5f)
        assertEquals(90L, s.total)
        assertEquals("only the last 60 s count", 61, s.perMinute(89_000))
    }

    @Test
    fun detectsGapsLongerThanThreshold() {
        val s = FixStats(gapMs = 10_000)
        assertEquals(0L, s.onFix(1_000, 4f))
        assertEquals(0L, s.onFix(11_000, 4f))       // exactly 10 s is not a gap
        assertEquals(45_000L, s.onFix(56_000, 4f))
        assertEquals(0L, s.onFix(57_000, 4f))
        assertEquals(1, s.gaps)
        assertEquals(45_000L, s.longestGapMs)
    }

    @Test
    fun minuteSummaryResets() {
        val s = FixStats()
        s.onFix(0, 2f)
        s.onFix(1000, 6f)
        val m = s.takeMinute()
        assertEquals(2, m.fixes)
        assertEquals(4.0, m.meanAccuracyM!!, 0.001)
        val empty = s.takeMinute()
        assertEquals(0, empty.fixes)
        assertNull(empty.meanAccuracyM)
    }
}
