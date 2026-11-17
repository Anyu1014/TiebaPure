package dev.anyu.tiebapure

import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class HomeImagePreviewInstrumentedTest {
    @Test fun homeImageOpensPreviewAndOtherCardContentOpensThread() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val activity = inst.startActivitySync(Intent(inst.targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        val vm = ViewModelProvider(activity)[AppViewModel::class.java]
        val original = vm.originalImageRatio
        val opens = AtomicInteger()
        fun nodes(node: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> = if (node == null) emptyList() else listOf(node) + (0 until node.childCount).flatMap { nodes(node.getChild(it)) }
        fun awaitNode(description: String): AccessibilityNodeInfo {
            val deadline = SystemClock.uptimeMillis() + 10_000
            do {
                inst.waitForIdleSync()
                nodes(inst.uiAutomation.rootInActiveWindow).firstOrNull { it.contentDescription?.toString() == description || it.text?.toString() == description }?.let { return it }
                SystemClock.sleep(50)
            } while (SystemClock.uptimeMillis() < deadline)
            error("Missing node: $description")
        }
        fun clickNode(description: String) {
            val deadline = SystemClock.uptimeMillis() + 10_000
            do {
                var node: AccessibilityNodeInfo? = awaitNode(description)
                while (node != null && !node.isClickable) node = node.parent
                if (node?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) return
                SystemClock.sleep(100)
            } while (SystemClock.uptimeMillis() < deadline)
            error("Could not click: $description")
        }
        val hot = Post("77", 2, User("84", "热评作者"), listOf(Block(0, "热评测试内容")), likes = 460)
        val image = "https://himg.bdimg.com/sys/portrait/item/fixture"
        try {
            for ((enabled, urls) in listOf(true to listOf(image), false to listOf(image), true to listOf(image, image + "2"))) {
                inst.runOnMainSync {
                    vm.selectOriginalImageRatio(enabled)
                    activity.setContent {
                        MaterialTheme {
                            androidx.compose.foundation.layout.Column(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) {
                                ThreadRow(vm, Thread("42", "测试帖子", previews = urls, hotComment = hot), 17f, true, homeFeed = true) { opens.incrementAndGet() }
                            }
                        }
                    }
                }
                assertNotNull(awaitNode("热评测试内容"))
                assertNotNull(awaitNode("热评"))
                clickNode("帖子预览图")
                assertNotNull(awaitNode("放大图片"))
                assertEquals(0, opens.get())
                inst.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
                clickNode("测试帖子")
                assertEquals(1, opens.getAndSet(0))
            }
        } finally {
            inst.runOnMainSync { vm.selectOriginalImageRatio(original); activity.finish() }
        }
    }
}
