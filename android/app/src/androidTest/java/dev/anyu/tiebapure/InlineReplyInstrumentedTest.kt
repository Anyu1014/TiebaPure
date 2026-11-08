package dev.anyu.tiebapure

import android.app.Application
import android.content.Intent
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import android.os.SystemClock
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class InlineReplyInstrumentedTest {
    @Suppress("UNCHECKED_CAST")
    private fun <T> state(vm: AppViewModel, name: String): MutableState<T> =
        AppViewModel::class.java.getDeclaredField(name + "$" + "delegate").apply { isAccessible = true }.get(vm) as MutableState<T>

    @OptIn(ExperimentalComposeUiApi::class)
    @Test fun replyHeaderKeepsTheInputRowAboveTheBottomInset() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val activity = inst.startActivitySync(
            Intent(inst.targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ) as MainActivity
        try {
            lateinit var vm: AppViewModel
            inst.runOnMainSync { vm = ViewModelProvider(activity)[AppViewModel::class.java] }
            assumeTrue("Run only in an anonymous project test installation", vm.account == null)
            val thread = InlineReplyTarget("测试吧", "42")
            val target = mutableStateOf(thread)
            inst.runOnMainSync {
                activity.setContentView(ComposeView(activity).apply {
                    setContent {
                        MaterialTheme {
                            Box(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) {
                                Box(Modifier.requiredSize(320.dp, 94.dp)) {
                                    FloatingReplyReader(vm, PaddingValues(bottom = 16.dp), true, target.value) {
                                        Box(it)
                                    }
                                }
                            }
                        }
                    }
                })
            }

            fun findBar(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
                if (node == null || node.viewIdResourceName == "thread-reply-bar") return node
                return (0 until node.childCount).firstNotNullOfOrNull { findBar(node.getChild(it)) }
            }
            fun bounds(): Rect {
                val end = SystemClock.uptimeMillis() + 5_000
                do {
                    inst.waitForIdleSync()
                    val bar = findBar(inst.uiAutomation.rootInActiveWindow)
                    if (bar != null) return Rect().also(bar::getBoundsInScreen)
                    SystemClock.sleep(25)
                } while (SystemClock.uptimeMillis() < end)
                error("Reply input row did not appear")
            }
            val baseline = bounds()
            assertEquals(62f, baseline.height() / activity.resources.displayMetrics.density, 1f)
            val post = thread.copy(parent = "200", floor = 2, replyTo = "测试用户")
            for (reply in listOf(post, post.copy(subpost = "201"))) {
                inst.runOnMainSync { target.value = reply }
                inst.waitForIdleSync()
                assertEquals(baseline, bounds())
            }
        } finally {
            inst.runOnMainSync { activity.finish() }
        }
    }

    @Test fun switchingTargetsAndSendingKeepTheReaderAndOnlyClearTheSentDraft() {
        val inst = InstrumentationRegistry.getInstrumentation()
        lateinit var vm: AppViewModel
        inst.runOnMainSync { vm = AppViewModel(ApplicationProvider.getApplicationContext<Application>()) }
        assumeTrue("Run only in an anonymous project test installation", vm.account == null)
        val clientField = TiebaApi::class.java.getDeclaredField("client").apply { isAccessible = true }
        val original = clientField.get(vm.api)
        val attempts = AtomicInteger()
        val fake = OkHttpClient.Builder().addInterceptor { chain ->
            val json = when {
                chain.request().url.encodedPath.contains("fnameShareApi") -> "{\"error_code\":0,\"data\":{\"fid\":\"10\"}}"
                chain.request().url.encodedPath.endsWith("/c/s/login") -> "{\"error_code\":0,\"anti\":{\"tbs\":\"fixture-token\"}}"
                chain.request().url.encodedPath.endsWith("/apubpost") -> if (attempts.incrementAndGet() == 1) "{\"no\":1}" else "{\"no\":0}"
                else -> error("Unexpected request in inline reply fixture")
            }
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("fixture").body(json.toResponseBody("application/json".toMediaType())).build()
        }.build()
        fun eventually(check: () -> Boolean) {
            val end = SystemClock.uptimeMillis() + 10000
            while (!check() && SystemClock.uptimeMillis() < end) SystemClock.sleep(20)
            assertTrue(check())
        }
        try {
            inst.runOnMainSync {
                (AppViewModel::class.java.getDeclaredField("loadJob").apply { isAccessible = true }.get(vm) as? Job)?.cancel()
                clientField.set(vm.api, fake)
                state<Account?>(vm, "account").value = Account("100", "fixture", "synthetic-bduss", "synthetic-stoken", "fixture")
                state<Route>(vm, "route").value = Route("thread", "42")
                val scroll = vm.readerListState(14, 17)
                vm.compose("fixture", "42", "200", 2)
                val first = checkNotNull(vm.inlineReplyTarget)
                assertEquals("thread", vm.route.kind)
                assertSame(scroll, vm.readerListState(0, 0))
                assertEquals(null, vm.inlineReplyDrafts[first.draftKey])
                vm.inlineReplyDrafts[first.draftKey] = InlineReplyDraft("first")
                vm.compose("fixture", "42", "201", 3)
                val next = checkNotNull(vm.inlineReplyTarget)
                assertNull(vm.inlineReplyDrafts[next.draftKey])
                vm.inlineReplyDrafts[next.draftKey] = InlineReplyDraft("second")
                vm.compose("fixture", "42", "200", 2)
                assertEquals("first", vm.inlineReplyDrafts[first.draftKey]?.text)
                assertTrue(vm.takeInlineReplyFocusRequest()); assertFalse(vm.takeInlineReplyFocusRequest())
                vm.submitInlineReply(first)
            }
            eventually { !vm.submitting }
            inst.runOnMainSync {
                assertEquals("first", vm.inlineReplyDrafts[checkNotNull(vm.inlineReplyTarget).draftKey]?.text)
                vm.submitInlineReply(checkNotNull(vm.inlineReplyTarget))
            }
            eventually { !vm.submitting && attempts.get() == 2 }
            inst.runOnMainSync {
                val first = checkNotNull(vm.inlineReplyTarget)
                assertNull(vm.inlineReplyDrafts[first.draftKey])
                assertEquals("second", vm.inlineReplyDrafts[first.copy(parent = "201", floor = 3).draftKey]?.text)
                assertEquals(Route("thread", "42"), vm.route)
                val scroll = vm.readerListState(0, 0)
                assertEquals(14, scroll.firstVisibleItemIndex); assertEquals(17, scroll.firstVisibleItemScrollOffset)
            }
        } finally { inst.runOnMainSync { vm.viewModelScope.cancel(); clientField.set(vm.api, original) } }
    }
}
