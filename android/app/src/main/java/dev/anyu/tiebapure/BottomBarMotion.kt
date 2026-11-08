package dev.anyu.tiebapure

import kotlin.math.abs

class BottomBarTransition(progress: Float) {
    val progress = progress.coerceIn(0f, 1f)
    val expandedOpacity get() = 1f - fade(this.progress / 0.75f)
    val avatarOpacity get() = fade((this.progress - 0.25f) / 0.75f)
    private fun fade(value: Float): Float {
        val t = value.coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
}

enum class BottomBarPosition(val key: String, val title: String, val fraction: Float) {
    LEFT("left", "居左", -1f), CENTER("center", "居中", 0f), RIGHT("right", "居右", 1f);
    companion object { fun stored(value: String?) = entries.firstOrNull { it.key == value } ?: LEFT }
}

class BottomBarMotion {
    var collapsed = false; private set
    private var direction = 0
    private var distance = 0.0
    fun beginGesture() { direction = 0; distance = 0.0 }
    fun expand() { collapsed = false; beginGesture() }
    fun finishGesture(velocity: Double): Boolean? {
        val expands = collapsed && direction == -1 && distance >= 12 && velocity.isFinite() && velocity <= -650
        beginGesture()
        if (!expands) return null
        collapsed = false; return false
    }
    // Positive delta means browsing toward later content, not finger direction.
    fun observe(delta: Double, velocity: Double): Boolean? {
        if (!delta.isFinite() || !velocity.isFinite() || abs(delta) < 0.1) return null
        val nextDirection = if (delta > 0) 1 else -1
        if (direction != nextDirection) { direction = nextDirection; distance = 0.0 }
        distance += abs(delta)
        val target = when { delta > 0 && distance >= 24 -> true; delta < 0 && velocity <= -650 && distance >= 12 -> false; else -> collapsed }
        if (target == collapsed) return null
        collapsed = target; distance = 0.0
        return target
    }
}

class BottomBarPanInput(private val motion: BottomBarMotion) {
    private var previousY: Double? = null
    fun begin(x: Double, y: Double) {
        cancel()
        if (x.isFinite() && y.isFinite()) previousY = y
    }
    fun move(x: Double, y: Double, velocityX: Double, velocityY: Double): Boolean? {
        val oldY = previousY ?: return null
        if (!x.isFinite() || !y.isFinite() || !velocityX.isFinite() || !velocityY.isFinite()) return null
        previousY = y
        if (abs(velocityY) <= abs(velocityX)) { motion.beginGesture(); return null }
        // Finger travel still exists on short pages and at a scroll boundary.
        return motion.observe(oldY - y, -velocityY)
    }
    fun end(velocityX: Double, velocityY: Double): Boolean? {
        if (previousY == null) return null
        previousY = null
        if (!velocityX.isFinite() || !velocityY.isFinite() || abs(velocityY) <= abs(velocityX)) { motion.beginGesture(); return null }
        return motion.finishGesture(-velocityY)
    }
    fun cancel() { previousY = null; motion.beginGesture() }
}
