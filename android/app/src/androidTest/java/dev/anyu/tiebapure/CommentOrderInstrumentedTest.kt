package dev.anyu.tiebapure

import android.content.Intent
import android.os.SystemClock
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.cancel
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import tieba.PageOuterClass.Page
import tieba.PbContentOuterClass.PbContent
import tieba.PostOuterClass.Post as WirePost
import tieba.ThreadInfoOuterClass.ThreadInfo
import tieba.pbPage.PbPageResponseOuterClass.*
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class CommentOrderInstrumentedTest {
    @Test fun switchingAllThreeOrdersKeepsTheReaderAndRefreshesOnlyReplies() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val activity = inst.startActivitySync(Intent().setClassName(inst.targetContext, MainActivity::class.java.name)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        lateinit var vm: AppViewModel
        inst.runOnMainSync { vm = ViewModelProvider(activity)[AppViewModel::class.java] }
        assumeTrue("Anonymous project lab only", vm.account == null)
        fun eventually(check: () -> Boolean) {
            val end = SystemClock.uptimeMillis() + 15000
            while (!check() && SystemClock.uptimeMillis() < end) SystemClock.sleep(50)
            assertTrue(check())
        }
        val position = vm.store.get("position", "42")
        val history = vm.store.get("history", "42")
        val requests = AtomicInteger()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val visit = requests.incrementAndGet()
            val data = PbPageResponseData.newBuilder().setThread(ThreadInfo.newBuilder().setId(42).setTitle("fixture"))
                .setPage(Page.newBuilder().setCurrentPage(1).setTotalPage(1))
            for (floor in 1..30) data.addPostList(WirePost.newBuilder().setId(floor.toLong()).setFloor(floor)
                .addContent(PbContent.newBuilder().setText(if (floor == 1) "main visit $visit" else "reply $floor visit $visit")))
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(PbPageResponse.newBuilder().setData(data).build().toByteArray().toResponseBody("application/x-protobuf".toMediaType())).build()
        }.build()
        val field = TiebaApi::class.java.getDeclaredField("client").apply { isAccessible = true }
        val previous = field.get(vm.api)
        try {
            vm.store.put("position", "42", JSONObject().put("sort", 0).put("page", 1))
            inst.runOnMainSync { field.set(vm.api, client); vm.navigate(Route("thread", "42"), root = true) }
            eventually { !vm.loading && vm.data.posts.size == 30 }
            val body = vm.data.posts.first().blocks
            val scroll = vm.readerListState(0, 0)
            inst.runOnMainSync { scroll.requestScrollToItem(2, 19) }
            eventually { scroll.firstVisibleItemIndex == 2 && scroll.firstVisibleItemScrollOffset == 19 }
            for (order in listOf(1, 2, 0)) {
                inst.runOnMainSync {
                    vm.selectReplies(order)
                    assertTrue(vm.commentsRefreshing)
                    assertFalse(vm.refreshing)
                    assertEquals(body, vm.data.posts.first().blocks)
                }
                eventually { !vm.loading }
                inst.waitForIdleSync()
                inst.runOnMainSync {
                    assertSame(scroll, vm.readerListState(0, 0))
                    assertEquals(2, scroll.firstVisibleItemIndex)
                    assertEquals(19, scroll.firstVisibleItemScrollOffset)
                    assertEquals(body, vm.data.posts.first().blocks)
                    assertEquals(order, vm.sort)
                }
            }
        } finally {
            inst.runOnMainSync { vm.viewModelScope.cancel(); activity.finish() }
            inst.waitForIdleSync()
            field.set(vm.api, previous)
            for ((kind, old) in listOf("position" to position, "history" to history)) {
                if (old == null) vm.store.remove(kind, "42") else vm.store.put(kind, "42", old)
            }
        }
    }
}
