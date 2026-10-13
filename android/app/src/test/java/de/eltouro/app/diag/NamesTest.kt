package de.eltouro.app.diag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NamesTest {
    @Test
    fun exitReasons() {
        assertEquals("low_memory", Names.exitReason(3))
        assertEquals("user_stopped", Names.exitReason(11))
        assertEquals("other", Names.exitReason(13))
        assertEquals("reason_99", Names.exitReason(99))
    }

    @Test
    fun importance() {
        assertEquals("foreground_service", Names.importance(125))
        assertEquals("cached", Names.importance(400))
        assertEquals("gone", Names.importance(1000))
    }

    @Test
    fun standbyBuckets() {
        assertEquals("active", Names.standbyBucket(10))
        assertEquals("restricted", Names.standbyBucket(45))
        assertTrue(Names.bucketIsBad(40))
        assertFalse(Names.bucketIsBad(20))
    }

    @Test
    fun vendorDetection() {
        assertEquals(Vendor.XIAOMI, Vendor.detect("Xiaomi", "Xiaomi"))
        assertEquals(Vendor.XIAOMI, Vendor.detect("Xiaomi", "POCO"))
        assertEquals(Vendor.XIAOMI, Vendor.detect(null, "Redmi"))
        assertEquals(Vendor.SAMSUNG, Vendor.detect("samsung", "samsung"))
        assertEquals(Vendor.HUAWEI, Vendor.detect("HONOR", "HONOR"))
        assertEquals(Vendor.ONEPLUS, Vendor.detect("OnePlus", "OnePlus"))
        assertEquals(Vendor.ONEPLUS, Vendor.detect("realme", "realme"))
        assertEquals(Vendor.OTHER, Vendor.detect("Google", "google"))
    }
}
