package dev.anyu.tiebapure

import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import tieba.PostOuterClass.Post as WirePost
import tieba.pbPage.PbPageResponseOuterClass.PbPageResponseData
import tiebapure.profile.UserProfile

class ForumReturnAndFloorTest {
    @Test fun threadFloorsStartWithMainAndUseNumericOrder() {
        val wire = PbPageResponseData.newBuilder().addAllPostList(listOf(10, 2, 1, 3).map { floor ->
            WirePost.newBuilder().setId(floor.toLong()).setFloor(floor).build()
        }).build()
        assertEquals(listOf(1, 2, 3, 10), TiebaApi("fixture").threadPage(wire).posts.map { it.floor })
        assertEquals(listOf(1, 10, 3, 2), TiebaApi("fixture").threadPage(wire, 1).posts.map { it.floor })
        assertEquals(listOf(1, 10, 2, 3), TiebaApi("fixture").threadPage(wire, 2).posts.map { it.floor })
    }

    @Test fun overlappingReplyPagesStayOrderedAfterDeduplication() {
        fun post(floor: Int) = Post(floor.toString(), floor, User(), emptyList())
        val old = ScreenData(posts = listOf(post(1), post(10), post(9)))
        val merged = ScreenData(posts = listOf(post(1), post(9), post(8)), hasMore = true).appendTo(old)
        assertEquals(listOf(1, 10, 9, 8), merged.posts.inReplyOrder(1).map { it.floor })
        assertEquals(listOf(1, 8, 9, 10), merged.posts.inReplyOrder(0).map { it.floor })
        assertEquals(listOf(1, 10, 9, 8), merged.posts.inReplyOrder(2).map { it.floor })
        assertTrue(merged.hasMore)
    }

    @Test fun userThreadsRetainTheServerLikeCountIncludingZero() = runBlocking {
        val response = UserProfile.UserThreadsResponse.newBuilder().setData(
            UserProfile.UserThreadsResponseData.newBuilder().addAllPostList(listOf(0, 123).map { count ->
                UserProfile.UserThreadItem.newBuilder().setThreadId((count + 1).toLong()).setAgreeNum(count).build()
            })
        ).build()
        val api = TiebaApi("fixture")
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(response.toByteArray().toResponseBody("application/x-protobuf".toMediaType())).build()
        }.build()
        TiebaApi::class.java.getDeclaredField("client").apply { isAccessible = true }.set(api, client)
        assertEquals(listOf(0L, 123L), api.userThreads("42", null, 1).map { it.likes })
    }
}
