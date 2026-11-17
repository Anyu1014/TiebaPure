package dev.anyu.tiebapure

import android.content.Intent
import android.os.SystemClock
import android.view.MotionEvent
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class HomeToolbarScrollInstrumentedTest {
    @Test fun titleAndLeftBlankScrollToTopWithoutInterceptingSearch() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val activity = inst.startActivitySync(Intent(inst.targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        lateinit var state: LazyListState
        lateinit var scope: CoroutineScope
        val searches = AtomicInteger()
        val ready = java.util.concurrent.atomic.AtomicBoolean()
        try {
            inst.runOnMainSync {
                activity.setContent {
                    MaterialTheme {
                        state = rememberLazyListState(initialFirstVisibleItemIndex = 25)
                        scope = rememberCoroutineScope()
                        ready.set(true)
                        FrostedAppShell(title = "首页", root = "home", showTabs = false, showBack = false, showSearch = true, hasActions = false,
                            onBack = {}, onSearch = { searches.incrementAndGet() }, onTab = {}, actions = {}, snackbar = {},
                            onHomeTop = { scope.launch { state.animateScrollToItem(0) } }) { padding ->
                            LazyColumn(state = state, contentPadding = padding) { items(60) { Text("项目$it", Modifier.height(80.dp)) } }
                        }
                    }
                }
            }
            fun waitFor(condition: () -> Boolean) {
                val deadline = SystemClock.uptimeMillis() + 10000
                while (SystemClock.uptimeMillis() < deadline) {
                    inst.waitForIdleSync()
                    if (condition()) return
                    SystemClock.sleep(50)
                }
                error("Timed out waiting for scroll or search")
            }
            fun tap(xFraction: Float) {
                val metrics = activity.resources.displayMetrics
                val statusId = activity.resources.getIdentifier("status_bar_height", "dimen", "android")
                val status = if (statusId != 0) activity.resources.getDimensionPixelSize(statusId) else 0
                val x = metrics.widthPixels * xFraction
                val y = status + 26 * metrics.density
                val t = SystemClock.uptimeMillis()
                inst.sendPointerSync(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0))
                inst.sendPointerSync(MotionEvent.obtain(t, t + 20, MotionEvent.ACTION_UP, x, y, 0))
            }
            waitFor { ready.get() }
            SystemClock.sleep(1000)
            waitFor { state.firstVisibleItemIndex == 25 }
            tap(.5f)
            waitFor { state.firstVisibleItemIndex == 0 }
            inst.runOnMainSync { scope.launch { state.scrollToItem(25) } }
            waitFor { state.firstVisibleItemIndex == 25 }
            tap(.08f)
            waitFor { state.firstVisibleItemIndex == 0 }
            inst.runOnMainSync { scope.launch { state.scrollToItem(25) } }
            waitFor { state.firstVisibleItemIndex == 25 }
            tap(.95f)
            waitFor { searches.get() == 1 }
            assertEquals(25, state.firstVisibleItemIndex)
        } finally {
            inst.runOnMainSync { activity.finish() }
        }
    }
}
