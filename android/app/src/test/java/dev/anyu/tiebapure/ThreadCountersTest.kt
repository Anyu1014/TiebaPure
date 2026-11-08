package dev.anyu.tiebapure

import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import tieba.AgreeOuterClass.Agree
import tieba.ThreadInfoOuterClass.ThreadInfo
import tieba.PostOuterClass.Post as WirePost
import tieba.pbPage.PbPageResponseOuterClass.PbPageResponseData
import tiebapure.profile.UserProfile

class ThreadCountersTest {
    @Test fun forumSearchKeepsEachResultLikeCount() = runBlocking {
        val response = """{"error_code":0,"data":{"post_list":[
            {"tid":"42","pid":"100","like_num":12,"post_info":{}},
            {"tid":"42","pid":"101","like_num":"0","post_info":{}},
            {"tid":"43","thread_info":{"tid":"43","like_num":"5"}},
            {"tid":"44","pid":"102","post_info":{}}
        ]}}"""
        val api = TiebaApi("fixture")
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            assertEquals("2", chain.request().url.queryParameter("ct"))
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(response.toResponseBody("application/json".toMediaType())).build()
        }.build()
        TiebaApi::class.java.getDeclaredField("client").apply { isAccessible = true }.set(api, client)
        val results = api.search("关键字", 1, "测试吧")
        assertEquals(listOf(12L, 0L, 5L, null), results.map { it.likes })
        val updated = ScreenData(threads = results).withThreadCounters(Thread("42", "", likes = 99))
        assertEquals(listOf(12L, 0L, 5L, null), updated.threads.map { it.likes })
    }

    @Test fun forumJSONReadsNestedAndLegacyLikeCounts() {
        val api = TiebaApi("fixture")
        assertEquals(123L, api.jsonThread(JSONObject("""{"id":"42","reply_num":"17","agree":{"agree_num":"123"}}""")).likes)
        assertEquals(17, api.jsonThread(JSONObject("""{"id":"42","reply_num":"17"}""")).replies)
        assertEquals(9L, api.jsonThread(JSONObject("""{"id":"42","agree_num":9}""")).likes)
        assertEquals(0L, api.jsonThread(JSONObject("""{"id":"42","agree":{"agree_num":"0"}}""")).likes)
    }

    @Test fun explicitNestedZeroIsAuthoritativeOverLegacyThreadCount() {
        val wire = ThreadInfo.newBuilder().setId(42).setAgreeNum(17)
            .setAgree(Agree.newBuilder().setHasAgree(1)).build()
        assertEquals(0L, TiebaApi("fixture").threadPage(PbPageResponseData.newBuilder().setThread(wire).build()).thread.likes)
    }

    @Test fun mainFloorUsesThreadLikesWhileRepliesKeepTheirOwnCounter() {
        val wire = PbPageResponseData.newBuilder()
            .setThread(ThreadInfo.newBuilder().setId(42).setReplyNum(103).setAgree(Agree.newBuilder().setAgreeNum(394)))
            .addPostList(WirePost.newBuilder().setId(1).setFloor(1).setAgree(Agree.newBuilder().setAgreeNum(336)))
            .addPostList(WirePost.newBuilder().setId(2).setFloor(2).setAgree(Agree.newBuilder().setAgreeNum(9)))
            .build()
        val page = TiebaApi("fixture").threadPage(wire)
        assertEquals(103, page.thread.replies)
        assertEquals(listOf(394L, 9L), page.posts.map { it.likes })
    }

    @Test fun detailCountersUpdateEveryMatchingPreviewWithoutReplacingContent() {
        val old = Thread("42", "keep title", replies = 102, likes = 336)
        val unrelated = Thread("43", "other", likes = 7)
        val result = ScreenData(threads = listOf(old, unrelated)).withThreadCounters(Thread("42", "different title", replies = 103, likes = 394))
        assertEquals(listOf(old.copy(replies = 103, likes = 394), unrelated), result.threads)
    }

    @Test fun changingCommentOrderReplacesOnlyRepliesAndRetainsMainBody() {
        val main = Post("1", 1, User(), listOf(Block(0, "original body")))
        val summary = Thread("42", "original title", replies = 2, likes = 3)
        val oldPage = ThreadPage(summary, "7", listOf(main, Post("2", 2, User(), emptyList())), 1, 3)
        val fresh = oldPage.copy(thread = summary.copy(title = "new title", replies = 4, likes = 5), posts = listOf(main.copy(blocks = listOf(Block(0, "new body"))), Post("8", 8, User(), emptyList())))
        val result = ScreenData(thread = fresh, posts = fresh.posts, hasMore = true).replacingComments(ScreenData(thread = oldPage, posts = oldPage.posts))
        assertEquals(main.blocks, result.posts.first().blocks)
        assertEquals(listOf("1", "8"), result.posts.map { it.id })
        assertEquals("original title", result.thread?.thread?.title)
        assertEquals(4, result.thread?.thread?.replies)
        assertTrue(result.hasMore)
    }

    @Test fun userTopicsReadOfficialPostInfoListAgreeField40() = runBlocking {
        // Official PostInfoList: thread_id=2, reply_num=17, Agree agree=40.
        val fixture = JSONObject(java.io.File("../../protocol-fixtures/thread-counters.json").readText()).getJSONObject("user_topic")
        val item = UserProfile.UserThreadItem.parseFrom(fixture.getString("item_hex").chunked(2).map { it.toInt(16).toByte() }.toByteArray())
        val wire = UserProfile.UserThreadsResponse.newBuilder().setData(
            UserProfile.UserThreadsResponseData.newBuilder().addPostList(item)
        ).build()
        val api = TiebaApi("fixture")
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(wire.toByteArray().toResponseBody("application/x-protobuf".toMediaType())).build()
        }.build()
        TiebaApi::class.java.getDeclaredField("client").apply { isAccessible = true }.set(api, client)
        val topic = api.userThreads("7", null, 1).single()
        assertEquals(fixture.getString("thread_id"), topic.id)
        assertEquals(fixture.getInt("replies"), topic.replies)
        assertEquals(fixture.getLong("likes"), topic.likes)
    }
}
