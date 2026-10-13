package de.eltouro.app.diag

/**
 * Counts position fixes as they arrive in the service (arrival time, not GPS time: a late or batched delivery is
 * exactly what we want to see). A pause of more than [gapMs] between two arrivals counts as a gap.
 */
class FixStats(private val gapMs: Long = 10_000) {
    var total = 0L
        private set
    var lastAt = 0L
        private set
    var gaps = 0
        private set
    var longestGapMs = 0L
        private set

    private val recent = ArrayDeque<Long>()
    private var minuteCount = 0
    private var minuteAccSum = 0.0

    /** Records a fix. Returns the length of the gap this fix ends, or 0 when there was none. */
    @Synchronized
    fun onFix(atMs: Long, accuracyM: Float): Long {
        val gap = if (lastAt > 0 && atMs - lastAt > gapMs) atMs - lastAt else 0L
        if (gap > 0) {
            gaps++
            if (gap > longestGapMs) longestGapMs = gap
        }
        total++
        lastAt = atMs
        recent.addLast(atMs)
        trim(atMs)
        minuteCount++
        minuteAccSum += accuracyM
        return gap
    }

    /** Fixes that arrived during the 60 s before [nowMs]. */
    @Synchronized
    fun perMinute(nowMs: Long): Int {
        trim(nowMs)
        return recent.size
    }

    /** Fixes and mean accuracy since the previous call – for the once-a-minute log line. */
    @Synchronized
    fun takeMinute(): Minute {
        val m = Minute(minuteCount, if (minuteCount > 0) minuteAccSum / minuteCount else null)
        minuteCount = 0
        minuteAccSum = 0.0
        return m
    }

    private fun trim(nowMs: Long) {
        while (recent.isNotEmpty() && nowMs - recent.first() > 60_000) recent.removeFirst()
    }

    data class Minute(val fixes: Int, val meanAccuracyM: Double?)
}
