package dev.anyu.tiebapure

import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import android.view.MotionEvent
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PresentationInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private fun eventually(check: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 30_000
        while (!check() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(200)
        assertTrue("Condition did not become true", check())
    }
    private fun readNodes(node: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> {
        if (node == null) return emptyList()
        return listOf(node) + (0 until node.childCount).flatMap { readNodes(node.getChild(it)) }
    }
    private fun text(): String = readNodes(instrumentation.uiAutomation.rootInActiveWindow).joinToString("\n") { "${it.text?.toString().orEmpty()} ${it.contentDescription?.toString().orEmpty()}" }
    private fun pullAtTop() {
        val bounds = android.graphics.Rect().also { instrumentation.uiAutomation.rootInActiveWindow.getBoundsInScreen(it) }
        val x = bounds.centerX().toFloat()
        val start = bounds.height() * .25f
        val distance = bounds.height() * .4f
        val down = SystemClock.uptimeMillis()
        fun event(action: Int, y: Float) {
            val motion = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
            instrumentation.uiAutomation.injectInputEvent(motion, true)
            motion.recycle()
        }
        event(MotionEvent.ACTION_DOWN, start)
        for (step in 1..24) { SystemClock.sleep(16); event(MotionEvent.ACTION_MOVE, start + distance * step / 24) }
        event(MotionEvent.ACTION_UP, start + distance)
    }

    @Test fun aboutAndNativeScrollPagination() {
        val activity = instrumentation.startActivitySync(Intent().setClassName(instrumentation.targetContext, MainActivity::class.java.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        lateinit var vm: AppViewModel
        instrumentation.runOnMainSync { vm = ViewModelProvider(activity)[AppViewModel::class.java] }
        assumeTrue("Anonymous review only", vm.account == null)
        instrumentation.runOnMainSync { vm.navigate(Route("me"), root = true); vm.navigate(Route("about")) }
        eventually { text().contains("Anyu") && text().contains("1.0") }
        assertTrue(text().contains("GPL-3.0-only"))
        assertTrue(text().contains("SwiftProtobuf"))
        assertFalse(text().contains("基于 infinityf4p"))
        assertTrue(text().contains("查看 TiebaPure 源码"))
        val screenshot = instrumentation.uiAutomation.takeScreenshot()
        File(instrumentation.targetContext.getExternalFilesDir(null), "about-1.0.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
        screenshot.recycle()
        instrumentation.runOnMainSync { vm.navigate(Route("license", "LICENSE", "TiebaPure")) }
        eventually { text().contains("GNU GENERAL PUBLIC LICENSE") }
        instrumentation.runOnMainSync { vm.navigate(Route("license", "licenses/SwiftProtobuf-Apache-2.0.txt", "SwiftProtobuf")) }
        eventually { text().contains("Apache License") }

        instrumentation.runOnMainSync { vm.navigate(Route("home"), root = true) }
        eventually { !vm.loading && (vm.data.threads.isNotEmpty() || vm.error.isNotBlank()) }
        assertEquals("", vm.error)
        val homeData = vm.data
        val homePage = vm.page
        instrumentation.runOnMainSync { vm.navigate(Route("me"), root = true) }
        eventually { text().contains("关于 TiebaPure") }
        instrumentation.runOnMainSync { vm.navigate(Route("home"), root = true) }
        assertSame("Tab return must restore home without requesting a refresh", homeData, vm.data)
        assertEquals(homePage, vm.page)
        assertFalse(vm.loading)
        instrumentation.waitForIdleSync()
        pullAtTop()
        eventually { !vm.loading && vm.data !== homeData }
        assertEquals("", vm.error)
        assertEquals(1, vm.page)

        instrumentation.runOnMainSync { vm.navigate(Route("forum", "旅行"), root = true) }
        eventually { !vm.loading && (vm.data.threads.isNotEmpty() || vm.error.isNotBlank()) }
        assertEquals("", vm.error)
        val firstIds = vm.data.threads.map { it.id }
        assertTrue(firstIds.isNotEmpty())
        repeat(20) {
            if (vm.page >= 2 || vm.error.isNotBlank()) return@repeat
            readNodes(instrumentation.uiAutomation.rootInActiveWindow).firstOrNull {
                val bounds = android.graphics.Rect().also(it::getBoundsInScreen)
                it.isScrollable && bounds.height() > bounds.width()
            }
                ?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            SystemClock.sleep(400)
        }
        eventually { vm.page >= 2 || vm.error.isNotBlank() }
        assertEquals("", vm.error)
        assertTrue(vm.data.threads.map { it.id }.containsAll(firstIds))
        assertFalse(text().contains("加载下一页"))
        val thread = vm.data.threads.firstOrNull { it.replies > 3 } ?: vm.data.threads.first()
        instrumentation.runOnMainSync { vm.navigate(Route("thread", thread.id)) }
        eventually { !vm.loading && (vm.data.posts.isNotEmpty() || vm.error.isNotBlank()) }
        assertEquals("", vm.error)
        val reply = vm.data.posts.firstOrNull { it.floor != 1 }
        assertNotNull("Live thread should contain a reply", reply)
        val label = "回复："
        repeat(40) {
            if (text().contains(label)) return@repeat
            readNodes(instrumentation.uiAutomation.rootInActiveWindow).firstOrNull {
                val bounds = android.graphics.Rect().also(it::getBoundsInScreen)
                it.isScrollable && bounds.height() > bounds.width()
            }?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            SystemClock.sleep(200)
        }
        eventually { text().contains(label) }
        readNodes(instrumentation.uiAutomation.rootInActiveWindow).firstOrNull { it.contentDescription?.toString()?.startsWith(label) == true }
            ?.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
        val comments = instrumentation.uiAutomation.takeScreenshot()
        File(instrumentation.targetContext.getExternalFilesDir(null), "comments-1.0.png").outputStream().use { comments.compress(Bitmap.CompressFormat.PNG, 100, it) }
        comments.recycle()
    }
}
