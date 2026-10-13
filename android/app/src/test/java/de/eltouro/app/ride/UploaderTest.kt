package de.eltouro.app.ride

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UploaderTest {
    private fun pt(i: Int) = TrackPoint(1_700_000_000_000L + i * 2000L, 51.4 + i / 1e5, 6.7, 5, if (i % 2 == 0) 18.5 else null)

    private class Fake(val answers: MutableList<UploadResult>) : Transport {
        val calls = mutableListOf<Pair<Long, String>>()
        override fun send(session: Long, pointsJson: String): UploadResult {
            calls += session to pointsJson
            // default: the server stores everything it got
            return if (answers.isNotEmpty()) answers.removeAt(0) else UploadResult(200, "{\"stored\":${pointsJson.split("],[").size}}")
        }
    }

    @Test
    fun uploadsEverythingInBatchesAndEmptiesTheStore() {
        val store = MemoryPointStore()
        repeat(1200) { store.add(7, pt(it)) }
        val t = Fake(mutableListOf())
        val out = Uploader(store, t, {}, batch = 500).flush()
        assertTrue(out.ok)
        assertEquals(1200, out.sent)
        assertEquals(3, t.calls.size)
        assertEquals(0, store.count())
    }

    @Test
    fun keepsPointsWhenOffline() {
        val store = MemoryPointStore()
        repeat(10) { store.add(7, pt(it)) }
        val out = Uploader(store, Fake(mutableListOf(UploadResult(-1, error = "UnknownHostException"))), {}).flush()
        assertFalse(out.ok)
        assertEquals(10, store.count())
        assertEquals(-1, out.last!!.code)
    }

    @Test
    fun keepsPointsOnAuthErrors() {
        for (code in listOf(400, 401, 403, 500, 302)) {
            val store = MemoryPointStore()
            repeat(3) { store.add(7, pt(it)) }
            Uploader(store, Fake(mutableListOf(UploadResult(code))), {}).flush()
            assertEquals("HTTP $code keeps the points", 3, store.count())
        }
    }

    @Test
    fun dropsASessionTheServerNoLongerKnowsAndGoesOn() {
        val store = MemoryPointStore()
        repeat(4) { store.add(5, pt(it)) }
        repeat(6) { store.add(6, pt(it)) }
        val logs = mutableListOf<String>()
        val t = Fake(mutableListOf(UploadResult(404, "{\"error\":\"session\"}")))
        val out = Uploader(store, t, { logs += it }).flush()
        assertTrue(out.ok)
        assertEquals(listOf(5L, 6L), t.calls.map { it.first })
        assertEquals(6, out.sent)
        assertEquals(0, store.count())
        assertTrue(logs.single().contains("4 points dropped"))
    }

    @Test
    fun oneSessionPerRequestOldestFirst() {
        val store = MemoryPointStore()
        store.add(1, pt(0)); store.add(1, pt(1)); store.add(2, pt(2)); store.add(1, pt(3))
        val t = Fake(mutableListOf())
        Uploader(store, t, {}).flush()
        assertEquals(listOf(1L, 2L, 1L), t.calls.map { it.first })
    }

    @Test
    fun logsWhenTheServerStoredNothing() {
        val store = MemoryPointStore()
        store.add(9, pt(0))
        val logs = mutableListOf<String>()
        Uploader(store, Fake(mutableListOf(UploadResult(200, "{\"stored\":0}"))), { logs += it }).flush()
        assertEquals(0, store.count())
        assertTrue(logs.single().contains("stored none"))
    }

    @Test
    fun countsWhatTheServerDidNotStoreAsDropped() {
        val store = MemoryPointStore()
        repeat(10) { store.add(3, pt(it)) }
        val out = Uploader(store, Fake(mutableListOf(UploadResult(200, "{\"stored\":7}"))), {}).flush()
        assertEquals(7, out.sent)
        assertEquals(3, out.dropped)
        assertEquals(0, store.count())
    }

    @Test
    fun droppedSessionCounts() {
        val store = MemoryPointStore()
        repeat(4) { store.add(5, pt(it)) }
        val out = Uploader(store, Fake(mutableListOf(UploadResult(409))), {}).flush()
        assertEquals(4, out.dropped)
    }

    @Test
    fun jsonHasTheRecorderFormat() {
        val store = MemoryPointStore()
        store.add(1, TrackPoint(1000, 51.123456, 6.5, 4, 12.3))
        store.add(1, TrackPoint(3000, 51.2, 6.6, 7, null))
        assertEquals("[[1000,51.123456,6.5,4,12.3],[3000,51.2,6.6,7,null]]", pointsJson(store.oldest(10)))
    }

    @Test
    fun trimDropsTheOldest() {
        val store = MemoryPointStore()
        repeat(10) { store.add(1, pt(it)) }
        assertEquals(4, store.trim(6))
        assertEquals(pt(4), store.oldest(1).single().point)
    }

    @Test
    fun storedCountParsing() {
        assertEquals(30, Uploader.storedCount("{\"stored\": 30}"))
        assertEquals(null, Uploader.storedCount("{}"))
    }
}
