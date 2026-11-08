package dev.anyu.tiebapure

import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import android.view.MotionEvent
import androidx.compose.runtime.MutableState
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import tieba.PbContentOuterClass.PbContent
import tieba.PostOuterClass.Post as WirePost
import tieba.UserOuterClass.User as WireUser
import tieba.ThreadInfoOuterClass.ThreadInfo
import tieba.SimpleForumOuterClass.SimpleForum
import tieba.SubPostListOuterClass.SubPostList
import tieba.pbPage.PbPageResponseOuterClass.*
import tieba.pbFloor.PbFloorResponseOuterClass.*
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.cancelChildren

@RunWith(AndroidJUnit4::class)
class ReplyFlowInstrumentedTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private fun nodes(n: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> = if (n == null) emptyList() else listOf(n) + (0 until n.childCount).flatMap { nodes(n.getChild(it)) }
    private fun visible() = nodes(inst.uiAutomation.rootInActiveWindow)
    private fun eventually(check: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 15000
        while (!check() && SystemClock.uptimeMillis() < end) SystemClock.sleep(100)
        assertTrue("Reply flow did not reach its expected state", check())
    }
    private fun click(find: (AccessibilityNodeInfo) -> Boolean) {
        eventually { visible().any(find) }
        inst.waitForIdleSync()
        val n = visible().first(find)
        val bounds = Rect().also(n::getBoundsInScreen)
        assertTrue(n.isEnabled && !bounds.isEmpty)
        val now = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(now, SystemClock.uptimeMillis(), action, bounds.exactCenterX(), bounds.exactCenterY(), 0)
            assertTrue(inst.uiAutomation.injectInputEvent(event, true))
            event.recycle()
        }
        inst.waitForIdleSync()
    }

    @Test fun delayedSuccessfulReplyDoesNotCloseANewVisitToTheSameComposer() {
        val activity = inst.startActivitySync(Intent().setClassName(inst.targetContext, MainActivity::class.java.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        lateinit var vm: AppViewModel
        inst.runOnMainSync { vm = ViewModelProvider(activity)[AppViewModel::class.java] }
        assumeTrue("Run only in the anonymous project lab", vm.account == null)
        @Suppress("UNCHECKED_CAST")
        val account = AppViewModel::class.java.getDeclaredField("account\$delegate").apply { isAccessible = true }.get(vm) as MutableState<Account?>
        val clientField = TiebaApi::class.java.getDeclaredField("client").apply { isAccessible = true }
        val oldClient = clientField.get(vm.api)
        val oldPreference = vm.store.prefs.all["allow-replies"]
        val oldHistory = vm.store.get("history", "42")
        val oldPosition = vm.store.get("position", "42")
        val draftKey = "84:delayed-reply-fixture"
        val oldDraft = vm.store.get("draft", draftKey)
        val started = java.util.concurrent.CountDownLatch(1)
        val finish = java.util.concurrent.CountDownLatch(1)
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val payload: ByteArray
            val type: String
            when (request.url.encodedPath) {
                "/c/f/pb/page" -> {
                    payload = PbPageResponse.newBuilder().setData(PbPageResponseData.newBuilder()
                        .setThread(ThreadInfo.newBuilder().setId(42).setTitle("延迟回复夹具"))
                        .setForum(SimpleForum.newBuilder().setId(1).setName("测试评论"))
                        .addPostList(WirePost.newBuilder().setId(100).setFloor(1).addContent(PbContent.newBuilder().setText("首楼"))))
                        .build().toByteArray()
                    type = "application/x-protobuf"
                }
                "/f/commit/share/fnameShareApi" -> { payload = """{"error_code":0,"data":{"fid":"1"}}""".toByteArray(); type = "application/json" }
                "/c/s/login" -> { payload = """{"error_code":0,"anti":{"tbs":"fixture-token"}}""".toByteArray(); type = "application/json" }
                "/mo/q/apubpost" -> {
                    started.countDown()
                    check(finish.await(10, java.util.concurrent.TimeUnit.SECONDS))
                    payload = """{"no":0,"data":{"tid":"42","pid":"300"}}""".toByteArray(); type = "application/json"
                }
                else -> throw IOException("Unexpected fixture route")
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(payload.toResponseBody(type.toMediaType())).build()
        }.build()
        try {
            clientField.set(vm.api, client)
            inst.runOnMainSync {
                account.value = Account("84", "夹具账号", "fixture-bduss", "fixture-stoken", "fixture-tbs")
                vm.setContentPreferences(replies = true)
                vm.navigate(Route("thread", "42"), root = true)
            }
            eventually { !vm.loading && vm.data.posts.isNotEmpty() }
            inst.runOnMainSync {
                vm.compose("测试评论", "42")
                vm.submitDraft("", "只在拦截器中提交的回复", emptyList(), draftKey)
            }
            assertTrue(started.await(5, java.util.concurrent.TimeUnit.SECONDS))
            inst.runOnMainSync {
                assertTrue(vm.back()); vm.compose("测试评论", "42")
                vm.store.put("draft", draftKey, JSONObject().put("text", "新一轮编辑"))
            }
            finish.countDown()
            eventually { !vm.submitting }
            inst.runOnMainSync {
                assertEquals("An old receipt must leave the new editor open", "compose", vm.route.kind)
                assertEquals("新一轮编辑", vm.store.get("draft", draftKey)?.optString("text"))
                assertTrue(vm.back())
                assertEquals("thread", vm.route.kind)
                assertFalse("Successful completion must not leave an old composer in history", vm.back())
            }
        } finally {
            finish.countDown()
            inst.runOnMainSync {
                vm.viewModelScope.coroutineContext.cancelChildren()
                account.value = null
                clientField.set(vm.api, oldClient)
            }
            vm.store.prefs.edit().apply { if (oldPreference is Boolean) putBoolean("allow-replies", oldPreference) else remove("allow-replies") }.commit()
            if (oldHistory == null) vm.store.remove("history", "42") else vm.store.put("history", "42", oldHistory)
            if (oldPosition == null) vm.store.remove("position", "42") else vm.store.put("position", "42", oldPosition)
            if (oldDraft == null) vm.store.remove("draft", draftKey) else vm.store.put("draft", draftKey, oldDraft)
        }
    }

    @Test fun mainCommentAndSubpostUseTheRealComposerAndSubmissionPath() {
        val activity = inst.startActivitySync(Intent().setClassName(inst.targetContext, MainActivity::class.java.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        lateinit var vm: AppViewModel
        inst.runOnMainSync { vm = ViewModelProvider(activity)[AppViewModel::class.java] }
        assumeTrue("Run only in the anonymous project lab", vm.account == null)
        @Suppress("UNCHECKED_CAST")
        val account = AppViewModel::class.java.getDeclaredField("account\$delegate").apply { isAccessible = true }.get(vm) as MutableState<Account?>
        val clientField = TiebaApi::class.java.getDeclaredField("client").apply { isAccessible = true }
        val oldClient = clientField.get(vm.api)
        val oldPreference = vm.store.prefs.all["allow-replies"]
        val oldHistory = vm.store.get("history", "42")
        val oldPosition = vm.store.get("position", "42")
        val oldDrafts = vm.store.list("draft").toMap()
        val writes = CopyOnWriteArrayList<Map<String, String>>()
        val deletions = CopyOnWriteArrayList<Map<String, String>>()
        val deleted = CopyOnWriteArraySet<String>()
        val longThread = AtomicBoolean(true)
        fun author(id: Long) = WireUser.newBuilder().setId(id).setNameShow(if (id == 42L) "模拟楼主" else "模拟评论者")
        fun post(id: Long, floor: Int, text: String) = WirePost.newBuilder().setId(id).setFloor(floor).setAuthor(author(if (floor == 1) 42 else 84)).addContent(PbContent.newBuilder().setText(text)).build()
        val fixtureClient = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val bytes: ByteArray
            val type: String
            when (request.url.encodedPath) {
                "/f/commit/share/fnameShareApi" -> { bytes = """{"error_code":0,"data":{"fid":"1"}}""".toByteArray(); type = "application/json" }
                "/c/s/login" -> { bytes = """{"error_code":0,"anti":{"tbs":"fixture-token"}}""".toByteArray(); type = "application/json" }
                "/mo/q/apubpost" -> {
                    val body = request.body as FormBody
                    writes.add((0 until body.size).associate { body.name(it) to body.value(it) })
                    bytes = """{"no":0,"data":{"tid":"42","pid":"300"}}""".toByteArray(); type = "application/json"
                }
                "/c/c/bawu/delpost" -> {
                    val body = request.body as FormBody
                    val fields = (0 until body.size).associate { body.name(it) to body.value(it) }
                    require(fields["pid"] in setOf("300", "500"))
                    deletions.add(fields); deleted.add(fields.getValue("pid"))
                    bytes = """{"error_code":0}""".toByteArray(); type = "application/json"
                }
                "/c/f/pb/page" -> {
                    val data = PbPageResponseData.newBuilder().setThread(ThreadInfo.newBuilder().setId(42).setTitle("评论功能模拟验证").setAuthor(author(42)))
                        .setForum(SimpleForum.newBuilder().setId(1).setName("测试评论"))
                        .addPostList(post(100, 1, "首楼正文")).addPostList(post(200, 2, "评论正文"))
                    if (longThread.get()) for (i in 1..20) data.addPostList(post(1000L + i, i + 2, "用于验证滚动时回复条固定在屏幕底部的模拟评论 $i"))
                    writes.lastOrNull()?.takeIf { "300" !in deleted }?.let { data.addPostList(post(300, 3, it.getValue("co")).toBuilder().setAuthor(author(42))) }
                    bytes = PbPageResponse.newBuilder().setData(data).build().toByteArray(); type = "application/x-protobuf"
                }
                "/c/f/pb/floor" -> {
                    val sub = SubPostList.newBuilder().setId(500).setAuthor(author(42)).addContent(PbContent.newBuilder().setText("楼中楼正文"))
                    val data = PbFloorResponseData.newBuilder()
                    if ("500" !in deleted) data.addSubpostList(sub)
                    bytes = PbFloorResponse.newBuilder().setData(data).build().toByteArray(); type = "application/x-protobuf"
                }
                else -> throw IOException("Unexpected fixture route")
            }
            // Every request is intercepted; fixture credentials and posts never reach the network.
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(bytes.toResponseBody(type.toMediaType())).build()
        }.build()
        try {
            clientField.set(vm.api, fixtureClient)
            inst.runOnMainSync {
                assertTrue("A fresh install must enable replies", vm.store.prefs.contains("allow-replies") || vm.repliesEnabled)
                vm.setContentPreferences(replies = true)
                account.value = Account("42", "模拟账号", "fixture-bduss", "fixture-stoken", "fixture-tbs")
            }
            for ((index, id) in listOf("100", "200", "500").withIndex()) {
                inst.runOnMainSync {
                    vm.navigate(if (id == "500") Route("subposts", "42", "测试评论", "200:1") else Route("thread", "42"), root = true)
                }
                eventually { !vm.loading && vm.data.posts.isNotEmpty() }
                if (index == 0) {
                    fun replyBounds(): Rect {
                        var n = visible().first { it.text?.toString() == "回复帖子" }
                        while (!n.isClickable && n.parent != null) n = n.parent
                        return Rect().also(n::getBoundsInScreen)
                    }
                    eventually { visible().any { it.text?.toString() == "回复帖子" } }
                    val before = replyBounds()
                    val density = inst.targetContext.resources.displayMetrics.density
                    val navigation = ViewCompat.getRootWindowInsets(activity.window.decorView)?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0
                    val gap = inst.targetContext.resources.displayMetrics.heightPixels - before.bottom - navigation
                    assertTrue("Reply bar must sit at viewport bottom", gap in (8 * density).toInt()..(20 * density).toInt())
                    assertTrue(visible().first { it.isScrollable }.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD))
                    inst.waitForIdleSync()
                    assertEquals("Scrolling comments must not move the reply bar", before, replyBounds())
                    click { it.text?.toString() == "回复帖子" }
                    eventually { vm.route.kind == "compose" }
                    assertFalse("Bottom bar replies to the thread", JSONObject(vm.route.extra).has("pid"))
                    longThread.set(false)
                    inst.runOnMainSync {
                        vm.store.put("position", "42", JSONObject().put("page", 1).put("index", 0).put("offset", 0))
                        vm.back()
                        vm.navigate(Route("thread", "42"), root = true)
                    }
                    eventually { !vm.loading && vm.data.posts.isNotEmpty() }
                }
                lateinit var originalData: ScreenData
                lateinit var originalScroll: androidx.compose.foundation.lazy.LazyListState
                var originalPage = 0
                var originalIndex = 0
                var originalOffset = 0
                inst.runOnMainSync {
                    originalData = vm.data; originalPage = vm.page
                    originalScroll = vm.readerListState(0, 0)
                    originalIndex = originalScroll.firstVisibleItemIndex; originalOffset = originalScroll.firstVisibleItemScrollOffset
                }
                click { it.viewIdResourceName == "post-comment-$id" }
                eventually { vm.route.kind == "compose" }
                val target = JSONObject(vm.route.extra)
                assertEquals("42", target.getString("tid"))
                assertEquals(if (id == "500") "200" else id, target.getString("pid"))
                if (id == "500") assertEquals("500", target.getString("subpost"))
                val body = "模拟发送验证${index + 1}" + if (index == 0) "#(酷)🙂" else ""
                eventually { visible().any { it.className == "android.widget.EditText" } }
                val editor = visible().first { it.className == "android.widget.EditText" }
                assertTrue(editor.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, body) }))
                click { it.text?.toString() == "发送" }
                click { it.text?.toString() == "确认发送" }
                eventually { !vm.submitting && vm.route.kind == if (id == "500") "subposts" else "thread" }
                inst.runOnMainSync {
                    assertSame("Successful replies retain the original loaded reader", originalData, vm.data)
                    assertEquals(originalPage, vm.page)
                    assertSame("Return keeps the exact scroll state before debounce", originalScroll, vm.readerListState(0, 0))
                    assertEquals(originalIndex, originalScroll.firstVisibleItemIndex)
                    assertEquals(originalOffset, originalScroll.firstVisibleItemScrollOffset)
                    vm.navigate(Route("thread", "42"), root = true)
                }
                eventually { !vm.loading && vm.data.posts.any { p -> TiebaEmoticon.summary(p.blocks).contains(body) } }
                assertEquals(index + 1, writes.size)
                assertEquals(target.getString("pid"), writes.last()["pid"])
                assertEquals(if (id == "500") "500" else null, writes.last()["lzl_id"])
                assertTrue(writes.last().getValue("co").contains(body))
            }
            assertFalse("Other people's comments must not expose delete", visible().any { it.viewIdResourceName == "delete-comment-200" })
            // Delete the newly submitted ordinary comment, including the cancel branch.
            click { it.viewIdResourceName == "delete-comment-300" }
            click { it.text?.toString() == "取消" }
            assertTrue(deletions.isEmpty())
            click { it.viewIdResourceName == "delete-comment-300" }
            click { it.text?.toString() == "确认删除" }
            eventually { vm.data.posts.none { it.id == "300" } }
            inst.runOnMainSync { vm.navigate(Route("subposts", "42", "测试评论", "200:1"), root = true) }
            eventually { !vm.loading && vm.data.posts.any { it.id == "500" } }
            click { it.viewIdResourceName == "delete-comment-500" }
            click { it.text?.toString() == "确认删除" }
            eventually { vm.data.posts.none { it.id == "500" } }
            assertEquals(listOf("300", "500"), deletions.map { it["pid"] })
            assertTrue(deletions.all { it["delete_my_post"] == "1" && it["delete_my_thread"] == null && it["isfloor"] == "0" })
            assertTrue(runCatching { runBlocking { vm.api.deleteOwnComment(vm.account!!, "42", "1", "测试评论", Post("200", 2, User("84"), emptyList()), false) } }.isFailure)
            assertEquals(2, deletions.size)
            inst.runOnMainSync {
                vm.setContentPreferences(replies = false)
                vm.compose("测试评论", "42", "100")
                assertTrue(vm.error.contains("允许回帖"))
                vm.setContentPreferences(replies = true)
                account.value = null
                vm.compose("测试评论", "42", "100")
                assertTrue(vm.error.contains("登录"))
            }
        } finally {
            inst.runOnMainSync {
                account.value = null
                vm.setContentPreferences(replies = oldPreference as? Boolean ?: true)
                vm.navigate(Route("me"), root = true)
                vm.viewModelScope.coroutineContext.cancelChildren()
            }
            inst.waitForIdleSync()
            clientField.set(vm.api, oldClient)
            vm.store.prefs.edit().apply { if (oldPreference == null) remove("allow-replies") else putBoolean("allow-replies", oldPreference as Boolean) }.commit()
            for ((kind, old) in listOf("history" to oldHistory, "position" to oldPosition)) { if (old == null) vm.store.remove(kind, "42") else vm.store.put(kind, "42", old) }
            vm.store.list("draft").filter { it.first !in oldDrafts && it.second.optString("account") == "42" }.forEach { vm.store.remove("draft", it.first) }
            oldDrafts.forEach { (key, value) -> vm.store.put("draft", key, value) }
            inst.runOnMainSync { activity.recreate() }
        }
    }
}
