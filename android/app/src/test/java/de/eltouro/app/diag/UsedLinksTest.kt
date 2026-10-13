package de.eltouro.app.diag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsedLinksTest {
    private val t1 = "a".repeat(64)
    private val t2 = "b".repeat(64)

    @Test
    fun remembersRedeemedTokensAcrossSaves() {
        val u = UsedLinks(null)
        assertFalse(u.seen(t1))
        u.add(t1)
        val again = UsedLinks(u.serialize())
        assertTrue(again.seen(t1))
        assertFalse(again.seen(t2))
    }

    @Test
    fun keepsOnlyHashesAndTheNewest() {
        val u = UsedLinks(null, max = 3)
        repeat(5) { u.add(it.toString().repeat(64)) }
        val parts = u.serialize().split(',')
        assertEquals(3, parts.size)
        assertFalse("never the token itself", u.serialize().contains("4444"))
        assertTrue(u.seen("4".repeat(64)))
        assertFalse(u.seen("0".repeat(64)))
    }

    @Test
    fun sha256IsHex() {
        assertEquals("ca978112ca1bbdcafac231b39a23dc4da786eff8147c4e72b9807785afee48bb", UsedLinks.sha256("a"))
    }
}
