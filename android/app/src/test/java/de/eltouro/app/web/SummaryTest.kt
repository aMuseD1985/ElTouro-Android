package de.eltouro.app.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SummaryTest {
    private val sample = """
      {"v":1,"generated_at":"2026-10-14T12:00:00Z","user":{"name":"Marco"},
       "ride":{"session_id":12,"tour_id":45,"title":"Rhein-Runde","url":"/tour/45/go","started_at":"2026-10-14T11:00:00Z","distance_m":12345},
       "near":{"label":"Duisburg","radius_km":25,
         "rides":[{"title":"Feierabendrunde","starts_at":"2026-10-14T16:30:00Z","distance_km":3.2,"url":"/ride/9"}],
         "tours":[{"title":"Hafen \u0026 Halden","length_km":32.5,"distance_km":1.2,"url":"/tour/45"},
                  {"title":"böse","url":"https://evil.example/x"},{"title":"a","url":"/tour/1"},{"title":"b","url":"/tour/2"},{"title":"c","url":"/tour/3"}]},
       "herds":[{"name":"Rheinbullen","unread":4,"last":"Anna: \"Treffen\" um 18 Uhr?","url":"/crew/rheinbullen/talk"}],
       "news":[{"text":"Neue Ausfahrt in deiner Herde","at":"2026-10-14T10:00:00Z","url":"/ride/9"}],
       "unread_total":7, "future_field":{"x":[1,2,{"y":null}]}}
    """.trimIndent()

    @Test
    fun parsesTheContract() {
        val s = Summary.parse(sample)!!
        assertEquals("Rhein-Runde", s.ride!!.title)
        assertEquals(12345L, s.ride!!.distanceM)
        assertEquals("Duisburg", s.nearLabel)
        assertEquals("Feierabendrunde", s.rides.single().title)
        assertEquals(3.2, s.rides.single().distanceKm!!, 1e-9)
        assertEquals("Hafen & Halden", s.tours.first().title)
        assertEquals("Anna: \"Treffen\" um 18 Uhr?", s.herds.single().last)
        assertEquals(4, s.herds.single().unread)
        assertEquals(7, s.unreadTotal)
    }

    @Test
    fun foreignLinksAreDroppedAndListsCapped() {
        val s = Summary.parse(sample)!!
        assertEquals(3, s.tours.size)
        assertTrue(s.tours.none { it.url.contains("evil") })
    }

    @Test
    fun wrongVersionOrBrokenJson() {
        assertNull(Summary.parse("""{"v":2}"""))
        assertNull(Summary.parse("""{"v":1,"""))
        assertNull(Summary.parse("<html>404</html>"))
    }

    @Test
    fun emptyIsFine() {
        val s = Summary.parse("""{"v":1,"ride":null,"near":{"label":null,"rides":[],"tours":[]},"herds":[],"news":[]}""")!!
        assertNull(s.ride)
        assertNull(s.nearLabel)
        assertTrue(s.rides.isEmpty() && s.herds.isEmpty())
    }

    @Test
    fun jsonReader() {
        assertEquals(listOf(1.0, "a", true, null, mapOf("k" to "ü")), Json.parse("""[1, "a", true, null, {"k":"\u00fc"}]"""))
        assertEquals(-2.5e3, Json.parse(" -2.5e3 ") as Double, 1e-9)
    }
}
