package dev.anyu.tiebapure

import android.os.SystemClock
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BottomBarTouchObserverTest {
    @Test fun nativeTouchSamplesUseTheSameDistanceAtEveryDensityWithoutAScrollingChild() {
        for (density in listOf(1f, 2f, 3f)) {
            val motion = BottomBarMotion()
            val changes = mutableListOf<Boolean>()
            val observer = BottomBarTouchObserver(BottomBarPanInput(motion), density) { changes += it }
            val start = SystemClock.uptimeMillis()
            fun send(action: Int, time: Long, x: Float, y: Float) {
                val event = MotionEvent.obtain(start, start + time, action, x * density, y * density, 0)
                try { observer.observe(event) } finally { event.recycle() }
            }
            try {
                send(MotionEvent.ACTION_DOWN, 0, 100f, 200f)
                send(MotionEvent.ACTION_MOVE, 16, 100f, 188f)
                assertFalse(motion.collapsed)
                send(MotionEvent.ACTION_MOVE, 32, 100f, 176f)
                assertTrue(motion.collapsed)
                send(MotionEvent.ACTION_UP, 48, 100f, 176f)
                send(MotionEvent.ACTION_DOWN, 100, 100f, 200f)
                send(MotionEvent.ACTION_MOVE, 104, 100f, 206f)
                send(MotionEvent.ACTION_MOVE, 108, 100f, 212f)
                assertFalse(motion.collapsed)
                send(MotionEvent.ACTION_UP, 116, 100f, 212f)
                assertEquals(listOf(true, false), changes)
                send(MotionEvent.ACTION_DOWN, 200, 100f, 200f)
                send(MotionEvent.ACTION_MOVE, 216, 200f, 170f)
                assertFalse(motion.collapsed)
                send(MotionEvent.ACTION_CANCEL, 220, 200f, 170f)
                send(MotionEvent.ACTION_MOVE, 230, 200f, 100f)
                assertFalse(motion.collapsed)
            } finally { observer.close() }
        }
    }
}
