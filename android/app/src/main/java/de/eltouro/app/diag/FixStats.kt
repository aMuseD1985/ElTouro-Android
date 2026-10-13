package de.eltouro.app.diag

/**
 * Counts position fixes as they arrive in the service (arrival time, not GPS time: a late or batched delivery is
 * exactly what we want to see). A pause of more than [gapMs] between two arrivals counts as a gap; it is a
 * display-off gap when the display was off at any moment since the previous fix. The last [windowMs] are kept
 * per display state – that is what tells "GPS stops with the display off" apart from everything else.
 */
class FixStats(private val gapMs: Long = 10_000, private val windowMs: Long = 300_000) {
    var total = 0L
        private set
    var lastAt = 0L
        private set
    var gaps = 0
        private set
    var longestGapMs = 0L
        private set
    var gapsDisplayOff = 0
        private set
    var longestGapDisplayOffMs = 0L
        private set

    private data class Arrival(val at: Long, val displayOn: Boolean)
    private val recent = ArrayDeque<Arrival>()
    private var minuteCount = 0
    private var minuteAccSum = 0.0

    /**
     * Records a fix. [displayOn] = the display was on the whole time since the previous fix.
     * Returns the length of the gap this fix ends, or 0 when there was none.
     */
    @Synchronized
    fun onFix(atMs: Long, accuracyM: Float, displayOn: Boolean = true): Long {
        val gap = if (total > 0 && atMs - lastAt > gapMs) atMs - lastAt else 0L
        if (gap > 0) {
            gaps++
            if (gap > longestGapMs) longestGapMs = gap
            if (!displayOn) {
                gapsDisplayOff++
                if (gap > longestGapDisplayOffMs) longestGapDisplayOffMs = gap
            }
        }
        total++
        lastAt = atMs
        recent.addLast(Arrival(atMs, displayOn))
        trim(atMs)
        minuteCount++
        minuteAccSum += accuracyM
        return gap
    }

    /** Fixes that arrived during the 60 s before [nowMs]. */
    @Synchronized
    fun perMinute(nowMs: Long): Int {
        trim(nowMs)
        return recent.count { nowMs - it.at <= 60_000 }
    }

    /** Fixes of the last [windowMs] (5 min), split by display state. */
    @Synchronized
    fun window(nowMs: Long): Window {
        trim(nowMs)
        val on = recent.count { it.displayOn }
        return Window(on, recent.size - on)
    }

    /** Fixes and mean accuracy since the previous call – for the once-a-minute log line. */
    @Synchronized
    fun takeMinute(): Minute {
        val m = Minute(minuteCount, if (minuteCount > 0) minuteAccSum / minuteCount else null)
        minuteCount = 0
        minuteAccSum = 0.0
        return m
    }

    /** A new ride: counters start from zero. */
    @Synchronized
    fun reset() {
        total = 0; lastAt = 0; gaps = 0; longestGapMs = 0; gapsDisplayOff = 0; longestGapDisplayOffMs = 0
        recent.clear(); minuteCount = 0; minuteAccSum = 0.0
    }

    private fun trim(nowMs: Long) {
        while (recent.isNotEmpty() && nowMs - recent.first().at > windowMs) recent.removeFirst()
    }

    data class Minute(val fixes: Int, val meanAccuracyM: Double?)
    data class Window(val displayOn: Int, val displayOff: Int)
}
