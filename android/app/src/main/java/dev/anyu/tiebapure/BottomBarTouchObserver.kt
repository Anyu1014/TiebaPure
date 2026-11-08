package dev.anyu.tiebapure

import android.view.MotionEvent
import android.view.VelocityTracker

internal class BottomBarTouchObserver(private val input: BottomBarPanInput, private val density: Float, private val onCollapse: (Boolean) -> Unit) {
    private var tracker: VelocityTracker? = null
    fun observe(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                close()
                tracker = VelocityTracker.obtain().also { it.addMovement(event) }
                input.begin(event.x.toDouble() / density, event.y.toDouble() / density)
            }
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> {
                if (event.pointerCount != 1) { close(); return }
                val velocity = tracker ?: return
                velocity.addMovement(event); velocity.computeCurrentVelocity(1000)
                val x = velocity.xVelocity.toDouble() / density
                val y = velocity.yVelocity.toDouble() / density
                val value = if (event.actionMasked == MotionEvent.ACTION_UP) input.end(x, y)
                    else input.move(event.x.toDouble() / density, event.y.toDouble() / density, x, y)
                value?.let(onCollapse)
                if (event.actionMasked == MotionEvent.ACTION_UP) close()
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> close()
        }
    }
    fun close() { tracker?.recycle(); tracker = null; input.cancel() }
}
