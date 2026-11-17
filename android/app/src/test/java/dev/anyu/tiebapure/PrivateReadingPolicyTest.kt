package dev.anyu.tiebapure

import org.junit.Assert.*
import org.junit.Test

class PrivateReadingPolicyTest {
    @Test fun priorBottomSeparatesOnlyActuallyNewMessages() {
        val saved = PrivateReadingPosition("2", 0, "2", true)
        assertEquals(2, PrivateReadingPolicy.boundary(saved, listOf("1", "2", "3")))
        assertEquals("3", PrivateReadingPolicy.target(saved, listOf("1", "2", "3")))
        assertNull(PrivateReadingPolicy.target(saved, listOf("1", "2")))
        assertNull(PrivateReadingPolicy.boundary(saved, listOf("3", "4")))
        assertTrue(PrivateReadingPolicy.needsHistory(saved, listOf("3", "4"), true))
    }
    @Test fun priorMiddleUsesAnchorAndLoadsOlderPagesWhenNeeded() {
        val saved = PrivateReadingPosition("1", 12, "3", false)
        assertEquals("1", PrivateReadingPolicy.target(saved, listOf("1", "2", "3", "4")))
        assertNull(PrivateReadingPolicy.boundary(saved, listOf("1", "2", "3", "4")))
        assertTrue(PrivateReadingPolicy.needsHistory(saved, listOf("3", "4"), true))
        assertFalse(PrivateReadingPolicy.needsHistory(saved, listOf("3", "4"), false))
        assertNull(PrivateReadingPolicy.target(null, listOf("1")))
    }
}
