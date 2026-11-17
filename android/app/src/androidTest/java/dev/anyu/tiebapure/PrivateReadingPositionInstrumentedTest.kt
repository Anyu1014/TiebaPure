package dev.anyu.tiebapure

import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.runtime.MutableState
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.anyu.tiebapure.official.OfficialIM as IM
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PrivateReadingPositionInstrumentedTest {
    @Test fun opensAtBottomShowsUnreadBoundaryAndExpiresPrompt() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val activity = inst.startActivitySync(Intent(inst.targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        val uid = (7_000_000_000L + (System.nanoTime() % 1_000_000_000L)).toString()
        val peer = "42"
        val key = "private-reading-position.$uid.$peer"
        lateinit var vm: AppViewModel
        val inboxFile = activity.filesDir.resolve("official-private-messages/$uid.pb")
        assertFalse(inboxFile.exists())
        fun nodes(n: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> = if (n == null) emptyList() else listOf(n) + (0 until n.childCount).flatMap { nodes(n.getChild(it)) }
        fun find(text: String): AccessibilityNodeInfo? = nodes(inst.uiAutomation.rootInActiveWindow).firstOrNull { it.text?.toString() == text || it.contentDescription?.toString() == text }
        fun waitFor(timeout: Long = 12_000, predicate: () -> Boolean) {
            val end = SystemClock.uptimeMillis() + timeout
            while (SystemClock.uptimeMillis() < end) { inst.waitForIdleSync(); if (predicate()) return; SystemClock.sleep(80) }
            error("Timed out; saved=" + runCatching { vm.store.prefs.getString(key, null) }.getOrNull() + " visible=" + nodes(inst.uiAutomation.rootInActiveWindow).mapNotNull { it.text?.toString() }.joinToString("|"))
        }
        try {
            inst.runOnMainSync {
                vm = ViewModelProvider(activity)[AppViewModel::class.java]
                assertNull("Fixture requires anonymous preview", vm.account)
                val client = okhttp3.OkHttpClient.Builder().addInterceptor { chain ->
                    okhttp3.Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1).code(200).message("fixture")
                        .body(okhttp3.ResponseBody.create(null, "{}".toByteArray())).build()
                }.build()
                TiebaApi::class.java.getDeclaredField("client").apply { isAccessible = true }.set(vm.api, client)
                val saved = org.json.JSONObject().put("anchor", "20").put("offset", 0).put("latest", "20").put("atBottom", true)
                vm.store.prefs.edit().putString(key, saved.toString()).commit()
                vm.api.officialPull = { _, _ ->
                    val messages = (1..130).map { i -> IM.ChatMessage.newBuilder().setId(i.toLong()).setFromUid(peer.toLong()).setToUid(uid.toLong()).setType(1).setTimestamp(i).setContent("测试消息$i").build() }
                    val response = IM.PullResponse.newBuilder().setError(IM.IMError.newBuilder().setCode(0)).setData(IM.PullResult.newBuilder().setPersonal(IM.UserMessages.newBuilder().addAllMessages(messages))).build()
                    OfficialPrivateSnapshot(emptyList(), response)
                }
                @Suppress("UNCHECKED_CAST")
                val state = AppViewModel::class.java.getDeclaredField("account" + "$" + "delegate").apply { isAccessible = true }.get(vm) as MutableState<Account?>
                state.value = Account(uid, "fixture", "fixture-bduss", "fixture-stoken", "fixture-tbs")
                vm.navigate(Route("messages", peer, "测试会话", "private"))
            }
            waitFor { find("测试消息130") != null && find("跳转到上次未读") != null }
            assertNull(find("测试消息1"))
            var button = find("跳转到上次未读")!!
            while (!button.isClickable && button.parent != null) button = button.parent
            assertTrue(button.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            waitFor { find("以下是新消息") != null && find("测试消息21") != null }
            assertNull(find("跳转到上次未读"))
            val now = SystemClock.uptimeMillis()
            inst.sendPointerSync(android.view.MotionEvent.obtain(now, now, android.view.MotionEvent.ACTION_DOWN, 500f, 600f, 0))
            for (i in 1..10) inst.sendPointerSync(android.view.MotionEvent.obtain(now, now + i * 40L, android.view.MotionEvent.ACTION_MOVE, 500f, 600f + i * 120f, 0))
            inst.sendPointerSync(android.view.MotionEvent.obtain(now, now + 440, android.view.MotionEvent.ACTION_UP, 500f, 1800f, 0))
            SystemClock.sleep(500)
            inst.runOnMainSync { vm.navigate(Route("me"), root = true) }
            waitFor { vm.route.kind == "me" && find("设置") != null }
            val after = org.json.JSONObject(vm.store.prefs.getString(key, "{}")!!)
            assertFalse("Middle scroll must persist atBottom=false", after.optBoolean("atBottom", true))
            inst.runOnMainSync { vm.navigate(Route("messages", peer, "测试会话", "private")) }
            waitFor { find("回到上次阅读位置") != null }
            var back = find("回到上次阅读位置")!!
            while (!back.isClickable && back.parent != null) back = back.parent
            assertTrue(back.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            val anchor = after.getString("anchor")
            waitFor { find("测试消息$anchor") != null }
            SystemClock.sleep(1_500)
            inst.runOnMainSync { vm.navigate(Route("me"), root = true) }
            waitFor { vm.route.kind == "me" && find("设置") != null }
            val again = org.json.JSONObject(vm.store.prefs.getString(key, "{}")!!)
            assertFalse("Restored middle position must remain non-bottom", again.optBoolean("atBottom", true))
            println("SAVED_ANCHORS before=${after.optString("anchor")} after=${again.optString("anchor")}")
            inst.runOnMainSync { vm.navigate(Route("messages", peer, "测试会话", "private")) }
            waitFor { find("回到上次阅读位置") != null }
            SystemClock.sleep(5_500)
            waitFor(3_000) { find("回到上次阅读位置") == null }
        } finally {
            inst.runOnMainSync {
                runCatching { vm.store.prefs.edit().remove(key).commit() }
                activity.finish()
            }
            inboxFile.delete()
        }
    }
}
