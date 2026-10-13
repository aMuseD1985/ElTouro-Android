package de.eltouro.app.ride

/** HTTP answer of one upload: code -1 = no connection (error says why). */
data class UploadResult(val code: Int, val body: String = "", val error: String = "")

fun interface Transport {
    fun send(session: Long, pointsJson: String): UploadResult
}

data class FlushOutcome(val ok: Boolean, val sent: Int, val last: UploadResult?)

/**
 * Empties the [store] towards the server, oldest points first, one session per request, at most [batch] points
 * (the server takes 600). Stops at the first failure and keeps everything that did not arrive.
 *   2xx           → delete what was sent; {"stored":0} for a non-empty batch means the server had already closed the
 *                   recording (2 h without a position) – logged, the points are gone either way
 *   404, 409      → the server does not know/accept that session any more: drop its points, go on with the rest
 *   anything else → keep (401 key/session gone, 400 CSRF, 403 consent, 5xx, no network) and try again later
 */
class Uploader(
    private val store: PointStore,
    private val transport: Transport,
    private val log: (String) -> Unit,
    private val batch: Int = 500,
) {
    fun flush(): FlushOutcome {
        var sent = 0
        var last: UploadResult? = null
        while (true) {
            val rows = store.oldest(batch)
            if (rows.isEmpty()) return FlushOutcome(true, sent, last)
            val session = rows.first().session
            val part = rows.takeWhile { it.session == session }
            val r = transport.send(session, pointsJson(part))
            last = r
            when {
                r.code in 200..299 -> {
                    if (storedCount(r.body) == 0) log("upload: server stored none of ${part.size} points of session $session (recording already closed?)")
                    store.delete(session, part.last().id)
                    sent += part.size
                }
                r.code == 404 || r.code == 409 -> {
                    val n = store.dropSession(session)
                    log("upload: server rejects session $session (HTTP ${r.code}) – $n points dropped")
                }
                else -> return FlushOutcome(false, sent, r)
            }
        }
    }

    companion object {
        fun storedCount(body: String): Int? = Regex("\"stored\"\\s*:\\s*(\\d+)").find(body)?.groupValues?.get(1)?.toIntOrNull()
    }
}
