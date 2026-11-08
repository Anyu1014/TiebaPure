package dev.anyu.tiebapure

import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.material3.*
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class AvatarRenderingInstrumentedTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private lateinit var activity: MainActivity
    @Test fun forumRowsOpenAuthorsWhileHomeRowsKeepTheirForumIdentity() {
        activity = inst.startActivitySync(Intent().setClassName(inst.targetContext, MainActivity::class.java.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        var usersOpened = 0; var forumsOpened = 0
        val thread = Thread("fixture", "合成帖子", "示例", User("42", "作者甲"), timestamp = System.currentTimeMillis() / 1000,
            forumAvatar = "https://himg.bdimg.com/forum-header-fixture-not-found.png")
        inst.runOnMainSync {
            val vm = ViewModelProvider(activity)[AppViewModel::class.java]
            activity.setContent { MaterialTheme { Column(Modifier.fillMaxSize().padding(16.dp)) {
                ThreadRow(vm, thread, 17f, false, forumCategory = "", onForum = { forumsOpened++ }, onUser = { usersOpened++ }) {}
                ThreadRow(vm, thread, 17f, false, homeFeed = true, onForum = { forumsOpened++ }, onUser = { usersOpened++ }) {}
            } } }
        }
        fun visible() = nodes(inst.uiAutomation.rootInActiveWindow)
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (visible().none { it.text?.toString() == "作者甲" } && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(100)
        assertTrue(visible().any { it.text?.toString() == "作者甲" })
        assertTrue(visible().any { it.text?.toString()?.contains("刚刚回复") == true })
        assertTrue(visible().any { it.text?.toString() == "示例吧" })
        fun clickText(text: String) {
            var node = visible().first { it.text?.toString() == text }
            while (!node.isClickable && node.parent != null) node = node.parent
            assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            inst.waitForIdleSync()
        }
        clickText("作者甲"); assertEquals(1, usersOpened); assertEquals(0, forumsOpened)
        clickText("作"); assertEquals(2, usersOpened); assertEquals(0, forumsOpened)
        clickText("示例吧"); assertEquals(2, usersOpened); assertEquals(1, forumsOpened)
    }
    private fun nodes(node: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> = if (node == null) emptyList() else listOf(node) + (0 until node.childCount).flatMap { nodes(node.getChild(it)) }
    @After fun restoreApp() {
        if (::activity.isInitialized) { inst.runOnMainSync { activity.recreate() }; inst.waitForIdleSync() }
    }
    @OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
    @Test fun myPageRepairsAnEmptyCachedNicknameAndDisplaysTheAvatar() {
        val portrait = InstrumentationRegistry.getArguments().getString("publicPortrait")
        assumeTrue("Pass an anonymous public portrait token", !portrait.isNullOrBlank())
        activity = inst.startActivitySync(Intent().setClassName(inst.targetContext, MainActivity::class.java.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        lateinit var vm: AppViewModel
        inst.runOnMainSync { vm = ViewModelProvider(activity)[AppViewModel::class.java] }
        assumeTrue("Do not replace a real signed-in account", vm.account == null)
        inst.runOnMainSync { vm.navigate(Route("me"), root = true) }
        val loaded = SystemClock.uptimeMillis() + 10_000
        while (vm.loading && SystemClock.uptimeMillis() < loaded) SystemClock.sleep(100)
        assertFalse(vm.loading)
        // Fixture-only state injection never saves credentials or invokes login.
        @Suppress("UNCHECKED_CAST")
        val accountState = AppViewModel::class.java.getDeclaredField("account\$delegate").apply { isAccessible = true }.get(vm) as MutableState<Account?>
        val old = Account.parse(Account("42", " ", "fixture-bduss", "fixture-stoken", "fixture-tbs").json())
        try {
            inst.runOnMainSync {
                accountState.value = old
                activity.setContent { MaterialTheme(colorScheme = lightColorScheme(primary = IOSLayout.blue, surface = Color.White, onSurface = Color.Black, onSurfaceVariant = Color(0xff8e8e93))) { Column(Modifier.fillMaxSize().background(IOSLayout.background).systemBarsPadding().semantics { testTagsAsResourceId = true }) {
                    Text("模拟验证 · 非真实账号", Modifier.padding(16.dp), color = IOSLayout.blue)
                    IOSMeScreen(vm, PaddingValues(bottom = 16.dp)) {}
                } } }
            }
            fun waitFor(check: () -> Boolean) {
                val end = SystemClock.uptimeMillis() + 30_000
                while (!check() && SystemClock.uptimeMillis() < end) SystemClock.sleep(100)
                assertTrue(check())
            }
            waitFor { nodes(inst.uiAutomation.rootInActiveWindow).any { it.text?.toString() == "贴吧用户" } }
            val repaired = old.withIdentity(User("42", "Anyu · 模拟验证", portrait!!))
            assertTrue(old.sessionMatches(repaired))
            inst.runOnMainSync { accountState.value = repaired }
            waitFor {
                val visible = nodes(inst.uiAutomation.rootInActiveWindow)
                visible.any { it.text?.toString() == repaired.name } && visible.any { it.text?.toString() == "UID 42" } && visible.any { it.viewIdResourceName == "avatar-loaded" }
            }
            assertFalse(nodes(inst.uiAutomation.rootInActiveWindow).any { it.text?.toString() == "贴吧用户" })
            val screenshot = inst.uiAutomation.takeScreenshot()
            File(inst.targetContext.getExternalFilesDir(null), "my-account-simulated.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
            screenshot.recycle()
        } finally { inst.runOnMainSync { accountState.value = null } }
    }
    @OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
    @Test fun bodyImageUsesFullWidthAndItsPortraitAspectRatio() {
        activity = inst.startActivitySync(Intent().setClassName(inst.targetContext, MainActivity::class.java.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        val directory = File(inst.targetContext.filesDir, "offline/${UUID.randomUUID()}").apply { mkdirs() }
        val file = File(directory, "0".repeat(64) + ".bin")
        try {
            val bitmap = Bitmap.createBitmap(200, 300, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.CYAN) }
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
            inst.runOnMainSync {
                val vm = ViewModelProvider(activity)[AppViewModel::class.java]
                activity.setContent { MaterialTheme { Column(Modifier.fillMaxSize().padding(16.dp)) {
                    ContentBlockView(Block(3, url = "https://himg.bdimg.com/fixture.png", localFile = "${directory.name}/${file.name}"), vm)
                } } }
            }
            val deadline = SystemClock.uptimeMillis() + 10_000
            fun imageBounds(): android.graphics.Rect? = nodes(inst.uiAutomation.rootInActiveWindow).firstOrNull { it.contentDescription?.toString() == "帖子图片，点击放大" }?.let { node -> android.graphics.Rect().also(node::getBoundsInScreen) }
            while (imageBounds()?.let { it.height() > it.width() * 1.4f } != true && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(100)
            val bounds = imageBounds() ?: error("Body image is missing")
            assertTrue(bounds.width() > inst.targetContext.resources.displayMetrics.widthPixels * .8f)
            assertTrue("Portrait image must not be capped at 360dp", bounds.height() / bounds.width().toFloat() in 1.4f..1.6f)
        } finally { file.delete(); directory.delete() }
    }
    @OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
    @Test fun realPortraitLoadsAndFailedRequestKeepsAFallback() {
        val portrait = InstrumentationRegistry.getArguments().getString("publicPortrait")
        assumeTrue("Pass an anonymous public portrait token", !portrait.isNullOrBlank())
        activity = inst.startActivitySync(Intent().setClassName(inst.targetContext, MainActivity::class.java.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        inst.runOnMainSync {
            activity.setContent {
                MaterialTheme {
                    Column(Modifier.fillMaxSize().padding(32.dp).semantics { testTagsAsResourceId = true }, verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        Text("公开头像加载检查")
                        IOSAvatar("头像校验", portrait!!, diameter = 64.dp)
                        Text("加载失败时的占位检查")
                        IOSAvatar("备用", "https://himg.bdimg.com/avatar-regression-not-found.png", diameter = 64.dp)
                    }
                }
            }
        }
        val end = SystemClock.uptimeMillis() + 30_000
        while (nodes(inst.uiAutomation.rootInActiveWindow).none { it.viewIdResourceName == "avatar-loaded" } && SystemClock.uptimeMillis() < end) SystemClock.sleep(150)
        val visible = nodes(inst.uiAutomation.rootInActiveWindow)
        assertTrue("CDN portrait must reach Coil's success state", visible.any { it.viewIdResourceName == "avatar-loaded" })
        assertTrue("Failed image must retain a visible initial", visible.any { it.text?.toString() == "备" })
        assertFalse(visible.any { it.text?.toString() == "头" })
        val screenshot = inst.uiAutomation.takeScreenshot()
        File(inst.targetContext.getExternalFilesDir(null), "avatar-fixed.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
        screenshot.recycle()
    }
}
