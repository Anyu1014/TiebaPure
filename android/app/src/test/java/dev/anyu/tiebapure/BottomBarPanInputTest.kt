package dev.anyu.tiebapure

import org.junit.Assert.*
import org.junit.Test

class BottomBarPanInputTest {
    @Test fun theSameFingerGestureWorksWithoutAnyContentOffset() {
        for (density in listOf(1.0, 2.0, 3.0)) {
            val motion = BottomBarMotion()
            val input = BottomBarPanInput(motion)
            input.begin(0.0, 100.0)
            assertNull(input.move(0.0, 88.0, 0.0, -900.0))
            assertEquals(true, input.move(0.0, (76.0 * density) / density, 0.0, -900.0))
            input.end(0.0, -900.0)
            input.begin(0.0, 0.0)
            assertNull(input.move(0.0, 100.0, 0.0, 120.0))
            assertNull(input.end(0.0, 120.0))
            assertTrue(motion.collapsed)
            input.begin(0.0, 0.0)
            assertNull(input.move(0.0, 6.0, 0.0, 900.0))
            assertEquals(false, input.move(0.0, 12.0, 0.0, 900.0))
        }
    }

    @Test fun horizontalDragsTapsAndCancelledOrUnstartedGesturesDoNotCollapse() {
        val motion = BottomBarMotion(); val input = BottomBarPanInput(motion)
        assertNull(input.move(0.0, -100.0, 0.0, -900.0))
        input.begin(0.0, 0.0)
        assertNull(input.move(100.0, -30.0, 1000.0, -40.0))
        assertNull(input.end(1000.0, -40.0))
        assertFalse(motion.collapsed)
        input.begin(0.0, 0.0); assertNull(input.end(0.0, 0.0))
        input.begin(0.0, 0.0); input.cancel()
        assertNull(input.move(0.0, -100.0, 0.0, -900.0))
        assertFalse(motion.collapsed)
    }

    @Test fun aFastReleaseCanFinishTheSameMinimumReverseDistance() {
        val motion = BottomBarMotion(); val input = BottomBarPanInput(motion)
        input.begin(0.0, 0.0); input.move(0.0, -24.0, 0.0, -100.0)
        input.begin(0.0, 0.0); input.move(0.0, 12.0, 0.0, 120.0)
        assertEquals(false, input.end(0.0, 900.0))
        assertFalse(motion.collapsed)
    }
}
