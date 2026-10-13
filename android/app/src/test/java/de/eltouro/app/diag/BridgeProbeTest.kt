package de.eltouro.app.diag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgeProbeTest {
    // exactly what WebView.evaluateJavascript hands back for a string result: the string, JSON-encoded once more
    private fun wrap(json: String) = "\"" + json.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    @Test
    fun parsesTheEvaluateJavascriptResult() {
        val p = BridgeProbe.parse(wrap("""{"p":"/tour/42/go","n":true,"a":true,"c":true,"u":true}"""))!!
        assertEquals("/tour/42/go", p.path)
        assertTrue(p.available)
        assertTrue(p.capacitor)
        assertEquals(true, p.signedIn)
        assertTrue(p.ridePage)
    }

    @Test
    fun browserPathOnTheFreeRidePage() {
        val p = BridgeProbe.parse(wrap("""{"p":"/free","n":true,"a":false,"c":false,"u":true}"""))!!
        assertTrue(p.ridePage)
        assertFalse(p.available)
        assertEquals("/free bridge=no capacitor=no tracker=yes user=yes", p.summary())
    }

    @Test
    fun signedInUnknownWithoutMarker() {
        val p = BridgeProbe.parse(wrap("""{"p":"/login","n":false,"a":false,"c":true,"u":null}"""))!!
        assertNull(p.signedIn)
        assertFalse(p.ridePage)
    }

    @Test
    fun ridePages() {
        fun ride(path: String) = BridgeProbe(path, true, true, true, true).ridePage
        assertTrue(ride("/free"))
        assertTrue(ride("/tour/7/go"))
        assertFalse(ride("/tour/7"))
        assertFalse(ride("/drives"))
        assertFalse(ride("/freebies"))
    }

    @Test
    fun nothingUsable() {
        assertNull(BridgeProbe.parse(null))
        assertNull(BridgeProbe.parse("null"))
        assertNull(BridgeProbe.parse(wrap("""{"x":1}""")))
    }
}
