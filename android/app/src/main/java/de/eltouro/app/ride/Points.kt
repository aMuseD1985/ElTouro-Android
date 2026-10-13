package de.eltouro.app.ride

/** One recorded point, already rounded like the web recorder: [unixMillis, lat, lng, accuracy, speedKmh?] */
data class TrackPoint(val t: Long, val lat: Double, val lng: Double, val acc: Int, val kmh: Double?)

data class StoredPoint(val id: Long, val session: Long, val point: TrackPoint)

/**
 * Points waiting for the server. Survives the process (SQLite on the phone), so a dead zone, a killed app or a
 * restart loses nothing. Plain JVM interface; the Android implementation is SqlitePointStore.
 */
interface PointStore {
    fun add(session: Long, p: TrackPoint)
    /** Oldest first, across sessions (points of an earlier ride go out before the current ones). */
    fun oldest(limit: Int): List<StoredPoint>
    /** Removes the points of [session] with id ≤ [upToId] – they reached the server. */
    fun delete(session: Long, upToId: Long)
    /** Removes everything of a session the server no longer accepts. Returns how many. */
    fun dropSession(session: Long): Int
    fun count(): Int
    /** Keeps at most [max] points, dropping the oldest. Returns how many were dropped. */
    fun trim(max: Int): Int
}

class MemoryPointStore : PointStore {
    private val rows = ArrayList<StoredPoint>()
    private var nextId = 1L

    @Synchronized override fun add(session: Long, p: TrackPoint) { rows += StoredPoint(nextId++, session, p) }
    @Synchronized override fun oldest(limit: Int) = rows.take(limit)
    @Synchronized override fun delete(session: Long, upToId: Long) { rows.removeAll { it.session == session && it.id <= upToId } }
    @Synchronized override fun dropSession(session: Long): Int { val n = rows.count { it.session == session }; rows.removeAll { it.session == session }; return n }
    @Synchronized override fun count() = rows.size
    @Synchronized override fun trim(max: Int): Int { val n = maxOf(0, rows.size - max); repeat(n) { rows.removeAt(0) }; return n }
}

/** Same JSON as the web recorder and the old service: [[t, lat, lng, acc, kmh|null], …] */
fun pointsJson(rows: List<StoredPoint>): String {
    val sb = StringBuilder("[")
    rows.forEachIndexed { i, r ->
        if (i > 0) sb.append(',')
        val p = r.point
        sb.append('[').append(p.t).append(',').append(p.lat).append(',').append(p.lng).append(',').append(p.acc).append(',')
            .append(p.kmh?.toString() ?: "null").append(']')
    }
    return sb.append(']').toString()
}
