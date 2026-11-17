package dev.anyu.tiebapure

import android.content.Intent
import android.os.Bundle
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

@RunWith(AndroidJUnit4::class)
class PrivateSendReconcileInstrumentedTest {
    @Test fun sameTextCanBeSentAfterMissingReceiptWithoutAnotherPull() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val activity = inst.startActivitySync(Intent(inst.targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        val uid = (8_000_000_000L + System.nanoTime() % 1_000_000_000L).toString()
        val inboxFile = activity.filesDir.resolve("official-private-messages/$uid.pb")
        assertFalse(inboxFile.exists())
        lateinit var vm: AppViewModel
        var attempts = 0
        var pulls = 0
        val messages = mutableListOf<IM.ChatMessage>()
        fun nodes(n: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> = if (n == null) emptyList() else listOf(n) + (0 until n.childCount).flatMap { nodes(n.getChild(it)) }
        fun all() = nodes(inst.uiAutomation.rootInActiveWindow)
        fun find(text: String) = all().firstOrNull { it.text?.toString() == text || it.contentDescription?.toString() == text }
        fun waitFor(check: () -> Boolean) {
            val end = SystemClock.uptimeMillis() + 12_000
            while (SystemClock.uptimeMillis() < end) { inst.waitForIdleSync(); if (check()) return; SystemClock.sleep(80) }
            error("Timed out; attempts=$attempts pulls=$pulls account=" + runCatching { vm.account?.uid == uid }.getOrNull() + " visible=" + all().mapNotNull { it.text?.toString() }.joinToString("|"))
        }
        try {
            inst.runOnMainSync {
                vm = ViewModelProvider(activity)[AppViewModel::class.java]
                assertNull("Fixture requires anonymous preview", vm.account)
                val client = okhttp3.OkHttpClient.Builder().addInterceptor { chain -> okhttp3.Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1).code(200).message("fixture").body(okhttp3.ResponseBody.create(null, "{}".toByteArray())).build() }.build()
                TiebaApi::class.java.getDeclaredField("client").apply { isAccessible = true }.set(vm.api, client)
                vm.api.officialSend = { _, request ->
                    attempts++
                    if (attempts == 3) kotlinx.coroutines.delay(1_500)
                    messages += IM.ChatMessage.newBuilder().setId(attempts.toLong()).setFromUid(uid.toLong()).setToUid(42).setType(1).setRecordId(request.data.recordId).setContent(request.data.content).setTimestamp((System.currentTimeMillis() / 1000).toInt()).build()
                    val response = IM.CommitResponse.newBuilder().setError(IM.IMError.newBuilder().setCode(0))
                    if (attempts > 1) response.setData(IM.CommitResult.newBuilder().setId(attempts.toLong()))
                    response.build()
                }
                vm.api.officialPull = { _, _ -> pulls++; OfficialPrivateSnapshot(emptyList(), IM.PullResponse.newBuilder().setError(IM.IMError.newBuilder().setCode(0)).setData(IM.PullResult.newBuilder().setPersonal(IM.UserMessages.newBuilder().addAllMessages(messages))).build()) }
                @Suppress("UNCHECKED_CAST")
                val state = AppViewModel::class.java.getDeclaredField("account" + "$" + "delegate").apply { isAccessible = true }.get(vm) as MutableState<Account?>
                state.value = Account(uid, "fixture", "fixture-bduss", "fixture-stoken", "fixture-tbs")
                vm.navigate(Route("messages", "42", "测试会话", "private"))
            }
            waitFor { all().any { it.isEditable } && find("发送") != null }
            val originalPulls = pulls
            val text = "相同内容"
            val input = all().first { it.isEditable }
            val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }
            assertTrue(input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args))
            fun pressSend() {
                SystemClock.sleep(300)
                waitFor {
                    var send = find("发送")
                    while (send != null && !send.isClickable) send = send.parent
                    send != null && send.isEnabled && send.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }
            }
            pressSend()
            waitFor { find("未收到发送结果，可继续发送。") != null }
            assertEquals(1, attempts)
            assertEquals(originalPulls, pulls)
            waitFor { all().any { it.isEditable && it.text?.toString() == text } }
            pressSend()
            waitFor { attempts == 2 && all().any { !it.isEditable && it.text?.toString() == text } }
            assertNull(find("已核对，继续发送"))
            assertEquals(originalPulls, pulls)
            assertEquals(2, attempts)
            fun enter(value: String) {
                val field = all().first { it.isEditable }
                val arguments = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value) }
                assertTrue(field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments))
                SystemClock.sleep(150)
            }
            enter("快速消息甲")
            pressSend()
            enter("快速消息乙")
            pressSend()
            waitFor { attempts == 4 && all().any { !it.isEditable && it.text?.toString() == "快速消息乙" } }
            assertEquals(originalPulls, pulls)
        } finally {
            inst.runOnMainSync { runCatching { vm.store.prefs.edit().remove("private-reading-position.$uid.42").commit() }; activity.finish() }
            inboxFile.delete()
        }
    }
}
