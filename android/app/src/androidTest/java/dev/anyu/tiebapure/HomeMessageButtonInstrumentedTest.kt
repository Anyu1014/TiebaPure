package dev.anyu.tiebapure

import android.content.Intent
import android.os.SystemClock
import android.view.MotionEvent
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class HomeMessageButtonInstrumentedTest {
    @Test fun unreadButtonReplacesOnlyLeftBlankAndDisappearsWhenRead() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val activity = inst.startActivitySync(Intent(inst.targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        val unread = mutableStateOf(false)
        val opened = AtomicInteger()
        val returned = AtomicInteger()
        val searched = AtomicInteger()
        try {
            inst.runOnMainSync {
                activity.setContent {
                    MaterialTheme {
                        FrostedAppShell(title = "首页", root = "home", showTabs = false, showBack = false, showSearch = true, hasActions = false,
                            onBack = {}, onSearch = { searched.incrementAndGet() }, onTab = {}, actions = {}, snackbar = {},
                            onHomeTop = { returned.incrementAndGet() }, onHomeMessages = if (unread.value) { { opened.incrementAndGet() } } else null) { }
                    }
                }
            }
            fun tap(fraction: Float) {
                val metrics = activity.resources.displayMetrics
                val statusId = activity.resources.getIdentifier("status_bar_height", "dimen", "android")
                val status = if (statusId != 0) activity.resources.getDimensionPixelSize(statusId) else 0
                val x = metrics.widthPixels * fraction
                val y = status + 26 * metrics.density
                val t = SystemClock.uptimeMillis()
                inst.sendPointerSync(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0))
                inst.sendPointerSync(MotionEvent.obtain(t, t + 20, MotionEvent.ACTION_UP, x, y, 0))
                inst.waitForIdleSync()
            }
            SystemClock.sleep(1500)
            tap(.065f)
            assertEquals(1, returned.get())
            inst.runOnMainSync { unread.value = true }
            SystemClock.sleep(300)
            tap(.065f)
            assertEquals(1, opened.get())
            assertEquals(1, returned.get())
            tap(.95f)
            assertEquals(1, searched.get())
            inst.runOnMainSync { unread.value = false }
            SystemClock.sleep(300)
            tap(.065f)
            assertEquals(2, returned.get())
            assertEquals(1, opened.get())
        } finally { inst.runOnMainSync { activity.finish() } }
    }
}
