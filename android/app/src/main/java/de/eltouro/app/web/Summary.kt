package de.eltouro.app.web

/**
 * What the home-screen widgets show – GET /api/app/summary (device key), contract in docs/handoff/aufgaben-server-1.md.
 * Every list holds at most three entries; links are only paths of our own server. Unknown fields are ignored, so the server
 * may add more. Plain JVM.
 */
data class Summary(
    val ride: Ride?,
    val nearLabel: String?,
    val rides: List<NearRide>,
    val tours: List<NearTour>,
    val herds: List<Herd>,
    val news: List<News>,
    val unreadTotal: Int,
) {
    data class Ride(val title: String, val url: String, val distanceM: Long)
    data class NearRide(val title: String, val startsAt: String, val distanceKm: Double?, val url: String)
    data class NearTour(val title: String, val lengthKm: Double?, val distanceKm: Double?, val url: String)
    data class Herd(val name: String, val unread: Int, val last: String, val url: String)
    data class News(val text: String, val at: String, val url: String)

    companion object {
        const val MAX = 3

        fun parse(text: String): Summary? = try { from(Json.parse(text)) } catch (_: Exception) { null }

        @Suppress("UNCHECKED_CAST")
        private fun from(root: Any?): Summary? {
            val o = root as? Map<String, Any?> ?: return null
            if ((o["v"] as? Double)?.toInt() != 1) return null
            fun Map<String, Any?>.s(k: String) = (this[k] as? String)?.trim().orEmpty()
            fun Map<String, Any?>.d(k: String) = this[k] as? Double
            fun list(v: Any?) = (v as? List<Any?>).orEmpty().mapNotNull { it as? Map<String, Any?> }
            fun url(m: Map<String, Any?>) = m.s("url").takeIf { isAppPath(it) }

            val ride = (o["ride"] as? Map<String, Any?>)?.let { r -> url(r)?.let { Ride(r.s("title"), it, (r.d("distance_m") ?: 0.0).toLong()) } }
            val near = o["near"] as? Map<String, Any?>
            return Summary(
                ride = ride,
                nearLabel = near?.s("label")?.ifEmpty { null },
                rides = list(near?.get("rides")).mapNotNull { m -> url(m)?.let { NearRide(m.s("title"), m.s("starts_at"), m.d("distance_km"), it) } }.take(MAX),
                tours = list(near?.get("tours")).mapNotNull { m -> url(m)?.let { NearTour(m.s("title"), m.d("length_km"), m.d("distance_km"), it) } }.take(MAX),
                herds = list(o["herds"]).mapNotNull { m -> url(m)?.let { Herd(m.s("name"), (m.d("unread") ?: 0.0).toInt(), m.s("last"), it) } }.take(MAX),
                news = list(o["news"]).mapNotNull { m -> url(m)?.let { News(m.s("text"), m.s("at"), it) } }.take(MAX),
                unreadTotal = ((o["unread_total"] as? Double) ?: 0.0).toInt(),
            )
        }

        /** Only "/…" of our own server – never "//host", a scheme or something long. */
        fun isAppPath(p: String) = p.startsWith("/") && !p.startsWith("//") && !p.contains("://") && p.length < 300
    }
}
