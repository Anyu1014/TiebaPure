package dev.anyu.tiebapure

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Insets
import android.graphics.Rect
import android.os.Build
import android.view.WindowInsets
import androidx.compose.runtime.MutableState
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
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
class PrivateMessagesInstrumentedTest {
    @Test fun officialInboxRecoversAnInterruptedAtomicWrite() {
        val root = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"inbox-fixture-" + java.util.UUID.randomUUID())
        root.mkdirs()
        try {
            val file = File(root,"fixture.pb")
            val message = dev.anyu.tiebapure.official.OfficialIM.ChatMessage.newBuilder().setId(1).setFromUid(42).setToUid(84).setType(1).setContent("fixture").build()
            val outgoing = message.toBuilder().setId(2).setFromUid(84).setToUid(42).setContent("sent").build()
            val event = message.toBuilder().setId(3).setType(22).setContent("""{"eventId":"22001","eventParam":{"groupId":600,"readMsgId":2}}""").build()
            val group = dev.anyu.tiebapure.official.OfficialIM.Group.newBuilder().setId(600).setType(6)
            val state = dev.anyu.tiebapure.official.OfficialIM.Inbox.newBuilder().addGroups(group).addMessages(message).addMessages(outgoing).addMessages(event).build()
            file.writeBytes(state.toByteArray())
            val interrupted = android.util.AtomicFile(file).startWrite()
            interrupted.write(byteArrayOf(0,1,2)); interrupted.close()
            val inbox = OfficialPrivateInbox("84",file)
            assertEquals(listOf("fixture","sent"),inbox.messages("42",null).items.map { it.text })
            assertTrue(inbox.messages("42",null).items.last().read)
            assertFalse(inbox.messages("42",null).items.first().read)
            assertEquals(1,inbox.conversations().items.size)
            assertTrue(OfficialPrivateInbox("84",file).messages("42",null).items.last().read)
            assertTrue(OfficialPrivateInbox("99",file).conversations().items.isEmpty())
        } finally { root.deleteRecursively() }
    }

    @Test fun privateChatReservesTheImeInsetExactlyOnce() {
        if (Build.VERSION.SDK_INT < 30) return
        val inst = InstrumentationRegistry.getInstrumentation()
        val activity = inst.startActivitySync(Intent(inst.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        try {
            lateinit var vm: AppViewModel
            inst.runOnMainSync { vm = ViewModelProvider(activity)[AppViewModel::class.java] }
            assumeTrue("Use an anonymous test installation", vm.account == null)
            val api = vm.api
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200)
                    .message("fixture").body("""{"error_code":0}""".toResponseBody("application/json".toMediaType())).build()
            }.build()
            inst.runOnMainSync {
                TiebaApi::class.java.getDeclaredField("client").apply { isAccessible = true }.set(api, client)
                api.officialPull = { _, _ ->
                    val result = dev.anyu.tiebapure.official.OfficialIM.PullResponse.newBuilder()
                    result.errorBuilder.code = 0
                    OfficialPrivateSnapshot(emptyList(), result.build())
                }
                @Suppress("UNCHECKED_CAST")
                val state = AppViewModel::class.java.getDeclaredField("account" + "$" + "delegate")
                    .apply { isAccessible = true }.get(vm) as MutableState<Account?>
                state.value = Account("84", "fixture", "fixture-bduss", "fixture-stoken", "fixture-tbs")
                vm.navigate(Route("messages", "42", "测试会话", "private"))
            }
            fun findComposer(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
                if (node == null || node.viewIdResourceName == "private-chat-composer") return node
                return (0 until node.childCount).firstNotNullOfOrNull { findComposer(node.getChild(it)) }
            }
            fun composerBounds(): Rect {
                val deadline = SystemClock.uptimeMillis() + 8_000
                do {
                    inst.waitForIdleSync()
                    val found = findComposer(inst.uiAutomation.rootInActiveWindow)
                    if (found != null) return Rect().also(found::getBoundsInScreen)
                    SystemClock.sleep(40)
                } while (SystemClock.uptimeMillis() < deadline)
                error("Private chat composer was not visible")
            }
            val baseline = composerBounds()
            var added = 0
            inst.runOnMainSync {
                val decor = activity.window.decorView
                val current = checkNotNull(decor.rootWindowInsets)
                val nav = current.getInsets(WindowInsets.Type.navigationBars()).bottom
                added = decor.height / 3
                decor.dispatchApplyWindowInsets(WindowInsets.Builder(current)
                    .setInsets(WindowInsets.Type.ime(), Insets.of(0, 0, 0, nav + added))
                    .setVisible(WindowInsets.Type.ime(), true)
                    .build())
            }
            val shifted = composerBounds()
            val slack = 3 * activity.resources.displayMetrics.density
            assertEquals(baseline.height().toFloat(), shifted.height().toFloat(), slack)
            assertEquals(added.toFloat(), (baseline.bottom - shifted.bottom).toFloat(), slack)
        } finally {
            inst.runOnMainSync { activity.finish() }
        }
    }

    @Test fun privateTabHasLoginGateAndTabChangesDoNotAddBackStackEntries() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(Intent().setClassName(instrumentation.targetContext, MainActivity::class.java.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        lateinit var vm: AppViewModel
        instrumentation.runOnMainSync { vm = ViewModelProvider(activity)[AppViewModel::class.java] }
        assumeTrue("Never touch a signed-in preview session", vm.account == null)
        fun nodes(node: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> = if (node == null) emptyList() else listOf(node) + (0 until node.childCount).flatMap { nodes(node.getChild(it)) }
        fun allNodes(): List<AccessibilityNodeInfo> {
            val root = instrumentation.uiAutomation.rootInActiveWindow
            root?.refresh()
            return nodes(root)
        }
        fun waitFor(condition: () -> Boolean) {
            val deadline = SystemClock.uptimeMillis() + 15_000
            while (!condition() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(150)
            assertTrue(condition())
        }
        instrumentation.runOnMainSync { vm.navigate(Route("me"), root = true); vm.navigate(Route("messages")) }
        waitFor { allNodes().any { it.text?.toString() == "私信" } }
        for (label in listOf("回复我的", "@我的", "私信")) assertTrue(allNodes().any { it.text?.toString() == label })
        var target = allNodes().first { it.text?.toString() == "私信" }
        while (!target.isClickable && target.parent != null) target = target.parent
        assertTrue(target.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        waitFor { vm.route.extra == "private" }
        waitFor {
            var chip = allNodes().firstOrNull { it.text?.toString() == "私信" }
            while (chip != null && !chip.isSelected && !chip.isChecked) chip = chip.parent
            chip != null && (chip.isSelected || chip.isChecked)
        }
        waitFor { allNodes().any { it.text?.toString()?.contains("登录后可以查看回复、@我的和私信消息") == true } }
        assertTrue(allNodes().any { it.text?.toString() == "登录百度账号" })
        assertFalse(allNodes().any { it.className?.toString() == "android.webkit.WebView" })
        val screenshot = instrumentation.uiAutomation.takeScreenshot()
        File(instrumentation.targetContext.getExternalFilesDir(null), "private-messages-login-gate.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
        screenshot.recycle()
        instrumentation.runOnMainSync { vm.selectMessageTab("at"); vm.selectMessageTab(""); assertTrue(vm.back()); assertEquals("me", vm.route.kind) }
    }
}
