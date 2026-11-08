package dev.anyu.tiebapure

import org.junit.Assert.*
import org.junit.Test

class BottomBarMotionTest {
    @Test fun contentHandoffIsContinuousMonotonicAndBounded() {
        val start = BottomBarTransition(-0.1f); val end = BottomBarTransition(1.1f)
        assertEquals(0f, start.progress, 0f); assertEquals(1f, start.expandedOpacity, 0f); assertEquals(0f, start.avatarOpacity, 0f)
        assertEquals(1f, end.progress, 0f); assertEquals(0f, end.expandedOpacity, 0f); assertEquals(1f, end.avatarOpacity, 0f)
        var previous = start
        for (step in 0..1000) {
            val current = BottomBarTransition(step / 1000f)
            assertTrue(current.expandedOpacity <= previous.expandedOpacity && current.avatarOpacity >= previous.avatarOpacity)
            assertTrue("no empty capsule at the handoff", current.expandedOpacity + current.avatarOpacity > 0.5f)
            assertTrue(kotlin.math.abs(current.expandedOpacity - previous.expandedOpacity) < 0.003f)
            assertTrue(kotlin.math.abs(current.avatarOpacity - previous.avatarOpacity) < 0.003f)
            previous = current
        }
    }
    @Test fun browsingDownCollapsesButSlowReverseNeverExpands() {
        val motion = BottomBarMotion()
        repeat(11) { assertNull(motion.observe(2.0, 100.0)) }
        assertEquals(true, motion.observe(2.0, 100.0))
        motion.beginGesture()
        repeat(100) { assertNull(motion.observe(-2.0, -120.0)) }
        assertTrue(motion.collapsed)
    }
    @Test fun fastReverseNeedsDistanceAndManualExpansionResetsTheGesture() {
        val motion = BottomBarMotion(); motion.observe(24.0, 100.0); motion.beginGesture()
        assertNull(motion.observe(-6.0, -800.0))
        assertEquals(false, motion.observe(-6.0, -800.0))
        motion.observe(24.0, 100.0); motion.expand()
        assertFalse(motion.collapsed)
        assertNull(motion.observe(2.0, 100.0))
    }
    @Test fun reversalJitterAndInvalidSamplesDoNotToggleTheBar() {
        val motion = BottomBarMotion()
        repeat(20) { motion.observe(10.0, 900.0); motion.observe(-2.0, -100.0) }
        assertFalse(motion.collapsed)
        assertNull(motion.observe(Double.NaN, 0.0)); assertNull(motion.observe(10.0, Double.POSITIVE_INFINITY))
        motion.observe(24.0, 100.0)
        repeat(20) { motion.observe(-0.05, -1000.0) }
        assertTrue(motion.collapsed)
    }
    @Test fun storedPositionsHaveStableKeysAndSafeDefaults() {
        assertEquals(BottomBarPosition.LEFT, BottomBarPosition.stored(null))
        assertEquals(BottomBarPosition.LEFT, BottomBarPosition.stored("invalid"))
        BottomBarPosition.entries.forEach { assertEquals(it, BottomBarPosition.stored(it.key)) }
        assertEquals(listOf(-1f, 0f, 1f), BottomBarPosition.entries.map { it.fraction })
    }
    @Test fun aShortFastFlickCanExpandButASlowReleaseCannot() {
        val motion = BottomBarMotion(); motion.observe(24.0, 100.0); motion.beginGesture()
        motion.observe(-12.0, 0.0)
        assertEquals(false, motion.finishGesture(-900.0))
        motion.observe(24.0, 100.0); motion.beginGesture(); motion.observe(-30.0, -100.0)
        assertNull(motion.finishGesture(-100.0)); assertTrue(motion.collapsed)
    }
}
