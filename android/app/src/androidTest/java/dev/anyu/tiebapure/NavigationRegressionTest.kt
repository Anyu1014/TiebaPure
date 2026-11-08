package dev.anyu.tiebapure

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class NavigationRegressionTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private fun eventually(check: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 30_000
        while (!check() && SystemClock.uptimeMillis() < end) SystemClock.sleep(100)
        assertTrue("Expected navigation state did not appear", check())
    }
    private fun start(): AppViewModel {
        val activity = inst.startActivitySync(Intent().setClassName(inst.targetContext, MainActivity::class.java.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        lateinit var vm: AppViewModel
        inst.runOnMainSync { vm = ViewModelProvider(activity)[AppViewModel::class.java] }
        assumeTrue("Use the anonymous lab emulator only", vm.account == null)
        return vm
    }
    private fun nodes(node: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> = if (node == null) emptyList() else listOf(node) + (0 until node.childCount).flatMap { nodes(node.getChild(it)) }
    private fun tap(label: String) {
        eventually { nodes(inst.uiAutomation.rootInActiveWindow).any { it.text?.toString() == label || it.contentDescription?.toString() == label } }
        var node = nodes(inst.uiAutomation.rootInActiveWindow).first { it.text?.toString() == label || it.contentDescription?.toString() == label }
        while (!node.isClickable && node.parent != null) node = node.parent
        assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        inst.waitForIdleSync()
    }
    @Test fun forumCategoriesCanLeaveFeaturedAndDoNotAddHistory() {
        val vm = start()
        inst.runOnMainSync { vm.navigate(Route("forums"), root = true); vm.navigate(Route("forum", "旅行")) }
        eventually { !vm.loading && vm.data.threads.isNotEmpty() }
        val replies = vm.data.threads.map { it.id }
        eventually { nodes(inst.uiAutomation.rootInActiveWindow).any { it.viewIdResourceName == "forum-title" && it.text?.toString() == "旅行吧" } }
        val header = nodes(inst.uiAutomation.rootInActiveWindow)
        assertTrue(header.any { it.viewIdResourceName == "forum-title" })
        assertFalse("Forum name is plain text, without the post-page pill", header.any { it.viewIdResourceName == "toolbar-forum-pill" })
        assertTrue(header.any { it.viewIdResourceName == "forum-category-latest-menu" })
        assertTrue(header.any { it.viewIdResourceName == "forum-follow-button" })
        assertTrue(header.any { it.viewIdResourceName == "forum-search-button" })
        assertTrue(header.any { it.viewIdResourceName == "forum-more-menu" })
        if (vm.newThreadsEnabled) assertTrue(header.any { it.viewIdResourceName == "forum-new-thread-button" })
        tap("精华")
        eventually { vm.route.extra == "good" && !vm.loading && vm.data.threads.isNotEmpty() }
        val featured = vm.data.threads.map { it.id }
        assertNotEquals("Featured must actually replace the reply list", replies, featured)
        tap("最新")
        tap("回复时间排序")
        eventually { !vm.loading && vm.data.threads.isNotEmpty() }
        assertEquals("Featured condition must be cleared", "", vm.route.extra)
        assertEquals(0, vm.sort)
        assertNotEquals(featured, vm.data.threads.map { it.id })
        tap("精华")
        tap("最新")
        tap("发帖时间排序")
        eventually { !vm.loading && vm.data.threads.isNotEmpty() }
        assertEquals("publish", vm.route.extra)
        assertEquals(1, vm.sort)
        val published = vm.data.threads.map { it.id }
        assertNotEquals(featured, published)
        inst.runOnMainSync { vm.sort = 2; vm.load(1) }
        eventually { !vm.loading && vm.data.threads.isNotEmpty() }
        assertEquals("Comment sorting must not change the forum category request", published, vm.data.threads.map { it.id })
        inst.runOnMainSync { assertTrue(vm.back()) }
        assertEquals("forums", vm.route.kind)
    }
    @Test fun backFromThreadRestoresHomeWithoutRefreshing() {
        val vm = start()
        inst.runOnMainSync { vm.navigate(Route("home"), root = true) }
        eventually { !vm.loading && vm.data.threads.isNotEmpty() }
        val data = vm.data
        val page = vm.page
        inst.runOnMainSync {
            vm.navigate(Route("thread", data.threads.first().id))
            assertTrue(vm.back())
            assertSame("Back must use the retained home feed", data, vm.data)
            assertEquals(page, vm.page)
            assertFalse(vm.loading)
        }
    }
    @Test fun backFromThreadRestoresTheForumCategoryAndLoadedPagesWithoutRefreshing() {
        val vm = start()
        inst.runOnMainSync { vm.navigate(Route("forum", "旅行", extra = "publish"), root = true) }
        eventually { !vm.loading && vm.data.threads.isNotEmpty() }
        inst.runOnMainSync { vm.loadNextPage() }
        eventually { !vm.loading }
        val data = vm.data
        val page = vm.page
        inst.runOnMainSync {
            vm.navigate(Route("thread", data.threads.first().id))
            assertTrue(vm.back())
            assertSame("Back must retain all loaded forum rows", data, vm.data)
            assertEquals("publish", vm.route.extra)
            assertEquals(1, vm.sort)
            assertEquals(page, vm.page)
            assertFalse(vm.loading)
        }
    }
    @Test fun everyPageUsesTheSameCompactToolbarAsTheThreadPage() {
        val activity = inst.startActivitySync(Intent().setClassName(inst.targetContext, MainActivity::class.java.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        lateinit var vm: AppViewModel
        inst.runOnMainSync { vm = ViewModelProvider(activity)[AppViewModel::class.java] }
        assumeTrue("Use the anonymous lab emulator only", vm.account == null)
        val density = inst.targetContext.resources.displayMetrics.density
        fun bounds(tag: String) = Rect().also { rect -> nodes(inst.uiAutomation.rootInActiveWindow).first { it.viewIdResourceName == tag }.getBoundsInScreen(rect) }
        inst.runOnMainSync { vm.navigate(Route("thread", "9107183498"), root = true) }
        eventually { nodes(inst.uiAutomation.rootInActiveWindow).any { it.viewIdResourceName == "toolbar-forum-pill" } }
        inst.waitForIdleSync()
        var referenceHeight = 0
        var stableSince = SystemClock.uptimeMillis()
        eventually {
            val height = bounds("top-frosted-bar").height()
            if (height != referenceHeight) { referenceHeight = height; stableSince = SystemClock.uptimeMillis() }
            SystemClock.uptimeMillis() - stableSince >= 1_000
        }
        val routes = listOf(Route("home") to "首页", Route("forums") to "进吧", Route("settings") to "设置", Route("about") to "关于 TiebaPure", Route("forum", "旅行") to "旅行吧", Route("subposts", "9107183498", "奥比交易", "150670290200:1") to "楼中楼")
        for ((route, title) in routes) {
            val drawn = java.util.concurrent.CountDownLatch(1)
            inst.runOnMainSync {
                vm.navigate(route, root = true)
                activity.window.decorView.postOnAnimation { activity.window.decorView.postOnAnimation { drawn.countDown() } }
            }
            assertTrue("${route.kind} draws after navigation", drawn.await(3, java.util.concurrent.TimeUnit.SECONDS))
            var visible = emptyList<AccessibilityNodeInfo>()
            var metrics = "${route.kind}: waiting for toolbar"
            // Insets and accessibility publish separate frames during navigation.
            try { eventually {
                if (android.os.Build.VERSION.SDK_INT >= 33) inst.uiAutomation.clearCache()
                val frame = nodes(inst.uiAutomation.rootInActiveWindow)
                fun height(node: AccessibilityNodeInfo) = Rect().also(node::getBoundsInScreen).height() / density
                val bar = frame.firstOrNull { it.viewIdResourceName == "top-frosted-bar" }
                val backgrounds = frame.filter { it.viewIdResourceName in setOf("toolbar-back-background", "toolbar-search-background", "toolbar-actions-background") }
                val targets = frame.filter { it.viewIdResourceName in setOf("toolbar-back-glass", "toolbar-search-glass", "toolbar-actions-glass") }
                metrics = "${route.kind}: bar=${bar?.let(::height)} reference=${referenceHeight / density} glass=${backgrounds.map { it.viewIdResourceName to height(it) }} targets=${targets.map { it.viewIdResourceName to height(it) }}"
                val ready = frame.any { it.text?.toString() == title || it.contentDescription?.toString() == title } &&
                    bar != null && kotlin.math.abs(height(bar) - referenceHeight / density) <= 1f &&
                    backgrounds.all { kotlin.math.abs(height(it) - 36f) <= 1f } &&
                    (route.kind == "forums" || targets.isNotEmpty()) && targets.all { height(it) >= 47f }
                if (ready) visible = frame
                ready
            } } catch (failure: AssertionError) { throw AssertionError(metrics, failure) }
            visible.filter { it.viewIdResourceName in setOf("toolbar-back-background", "toolbar-search-background", "toolbar-actions-background") }.forEach { node ->
                val bounds = Rect().also(node::getBoundsInScreen)
                assertEquals("${route.kind} glass background height", 36f, bounds.height() / density, 1f)
            }
            visible.filter { it.viewIdResourceName in setOf("toolbar-back-glass", "toolbar-search-glass", "toolbar-actions-glass") }.forEach { node ->
                val bounds = Rect().also(node::getBoundsInScreen)
                assertTrue("${route.kind} keeps its touch target", bounds.height() / density >= 47f)
            }
        }
    }
    @Test fun frostedToolbarHasBottomClearanceAndTheReaderMenuActions() {
        val vm = start()
        inst.runOnMainSync { vm.navigate(Route("home"), root = true) }
        eventually { !vm.loading && vm.data.threads.isNotEmpty() }
        inst.runOnMainSync { vm.navigate(Route("thread", vm.data.threads.first().id)) }
        eventually { !vm.loading && vm.data.posts.isNotEmpty() }
        inst.waitForIdleSync()
        eventually { nodes(inst.uiAutomation.rootInActiveWindow).any { it.viewIdResourceName == "toolbar-forum-pill" } }
        val main = vm.data.posts.first { it.floor == 1 }
        eventually { nodes(inst.uiAutomation.rootInActiveWindow).any { it.viewIdResourceName == "post-author-${main.id}" } }
        val visible = nodes(inst.uiAutomation.rootInActiveWindow)
        fun bounds(tag: String) = Rect().also { rect -> visible.first { it.viewIdResourceName == tag }.getBoundsInScreen(rect) }
        val bar = bounds("top-frosted-bar")
        val density = inst.targetContext.resources.displayMetrics.density
        val author = bounds("post-author-${main.id}")
        val like = bounds("post-like-${main.id}")
        val comment = bounds("post-comment-${main.id}")
        assertTrue("Comment precedes like on the author row", comment.right <= like.left && comment.centerY() in author.top..author.bottom)
        assertTrue("Comment keeps its 48dp touch target", comment.height() >= (48 * density).toInt())
        val title = Rect().also { rect -> visible.first { it.text?.toString() == vm.data.thread!!.thread.title }.getBoundsInScreen(rect) }
        assertTrue("Like belongs at the right of the identity row", like.right >= author.right - density && like.centerY() in author.top..author.bottom)
        assertTrue("Title follows author information", title.top >= author.bottom)
        assertTrue("Main post shows its author badge", visible.any { it.text?.toString() == "楼主" })
        val badge = Rect().also { rect -> visible.first { it.text?.toString() == "楼主" }.getBoundsInScreen(rect) }
        assertTrue("Author badge stays compact", badge.height() <= 20 * density)
        listOf("toolbar-back-glass", "toolbar-forum-pill", "toolbar-actions-glass").forEach { tag ->
            val button = bounds(tag)
            assertTrue("$tag keeps its complete 48dp touch target", button.height() >= (48 * density).toInt())
            assertTrue("$tag needs bottom clearance", button.bottom <= bar.bottom - (4 * density).toInt())
        }
        val postScreenshot = inst.uiAutomation.takeScreenshot()
        File(inst.targetContext.getExternalFilesDir(null), "post-layout.png").outputStream().use { postScreenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
        postScreenshot.recycle()
        var more = nodes(inst.uiAutomation.rootInActiveWindow).first { it.contentDescription?.toString() == "帖子操作" }
        while (!more.isClickable && more.parent != null) more = more.parent
        assertTrue(more.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        eventually { nodes(inst.uiAutomation.rootInActiveWindow).any { it.text?.toString() == "保存到本地" } }
        val menu = nodes(inst.uiAutomation.rootInActiveWindow).mapNotNull { it.text?.toString() }
        listOf("刷新", "保存到本地", "复制链接", "浏览器打开", "分享").forEach { assertTrue(menu.contains(it)) }
        val screenshot = inst.uiAutomation.takeScreenshot()
        File(inst.targetContext.getExternalFilesDir(null), "frosted-toolbar-menu.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
        screenshot.recycle()
        tap("保存到本地")
        eventually { nodes(inst.uiAutomation.rootInActiveWindow).any { it.text?.toString() == "保存完整媒体" } }
        tap("取消")
    }
}
