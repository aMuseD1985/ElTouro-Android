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

    @Test
    fun separatesDisplayOnAndOff() {
        val s = FixStats()
        for (i in 0 until 30) s.onFix(i * 1000L, 5f, displayOn = true)
        for (i in 30 until 50) s.onFix(i * 1000L, 5f, displayOn = false)
        val w = s.window(49_000)
        assertEquals(30, w.displayOn)
        assertEquals(20, w.displayOff)
    }

    @Test
    fun windowKeepsFiveMinutes() {
        val s = FixStats()
        s.onFix(0, 5f)
        s.onFix(200_000, 5f, displayOn = false)
        assertEquals(FixStats.Window(1, 1), s.window(250_000))
        assertEquals("the first fix is older than 5 min now", FixStats.Window(0, 1), s.window(310_000))
    }

    @Test
    fun gapWithDisplayOffIsCountedSeparately() {
        val s = FixStats()
        s.onFix(0, 5f)
        s.onFix(30_000, 5f, displayOn = true)     // gap with the display on
        s.onFix(130_000, 5f, displayOn = false)   // 100 s gap, display was off
        assertEquals(2, s.gaps)
        assertEquals(1, s.gapsDisplayOff)
        assertEquals(100_000L, s.longestGapDisplayOffMs)
    }

    @Test
    fun resetStartsFromZero() {
        val s = FixStats()
        s.onFix(0, 5f); s.onFix(60_000, 5f, displayOn = false)
        s.reset()
        assertEquals(0L, s.total)
        assertEquals(0, s.gapsDisplayOff)
        assertEquals("no gap after a reset", 0L, s.onFix(500_000, 5f))
    }
}
