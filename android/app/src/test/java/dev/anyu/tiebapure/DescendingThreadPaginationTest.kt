package dev.anyu.tiebapure

import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import tieba.PageOuterClass.Page
import tieba.PostOuterClass.Post as WirePost
import tieba.pbPage.PbPageRequestOuterClass.PbPageRequest
import tieba.pbPage.PbPageResponseOuterClass.*

class DescendingThreadPaginationTest {
    @Test fun descendingStartsAtTheLastServerPageAndKeepsTheDiscoveredMainFloor() = runBlocking {
        val pages = mutableListOf<Int>()
        val orders = mutableListOf<Int>()
        val postTargets = mutableListOf<Boolean>()
        val api = TiebaApi("fixture")
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val multipart = chain.request().body as MultipartBody
            val wire = multipart.parts.single { it.headers?.get("Content-Disposition")?.contains("name=\"data\"") == true }.body
            val request = PbPageRequest.parseFrom(Buffer().also { wire.writeTo(it) }.readByteArray()).data
            pages += request.pn; orders += request.r
            postTargets += request.hasPid()
            val floors = when (request.pn) { 3 -> listOf(30,31); 2 -> listOf(15,16); else -> listOf(1,2) }
            val data = PbPageResponseData.newBuilder().setPage(Page.newBuilder().setCurrentPage(request.pn).setTotalPage(3))
                .addAllPostList(floors.map { floor -> WirePost.newBuilder().setId(floor.toLong()).setFloor(floor).build() })
            val response = PbPageResponse.newBuilder().setData(data).build()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(response.toByteArray().toResponseBody("application/x-protobuf".toMediaType())).build()
        }.build()
        TiebaApi::class.java.getDeclaredField("client").apply { isAccessible = true }.set(api, client)
        val result = api.thread("42", 1, null, descending = true)
        assertEquals("Discover page count, then read only the last server page", listOf(1,3), pages)
        assertEquals(listOf(0,0), orders)
        assertEquals(listOf(1,31,30), result.posts.map { it.floor })
        assertEquals(1, result.page)
        assertEquals(3, result.pages)
        val second = api.thread("42", 2, null, descending = true, postId = "2", descendingTotalPages = result.pages)
        assertEquals(listOf(1,3,2), pages)
        assertEquals(listOf(16,15), second.posts.map { it.floor })
        assertEquals(2, second.page)
        val last = api.thread("42", 3, null, descending = true, postId = "2", descendingTotalPages = result.pages)
        assertEquals(listOf(1,3,2,1), pages)
        assertEquals(3, last.page)
        assertFalse(last.page < last.pages)
        assertTrue(postTargets.none { it })
        val old = ScreenData(posts = result.posts)
        val next = ScreenData(posts = second.posts, hasMore = second.page < second.pages).appendTo(old)
        val all = ScreenData(posts = last.posts, hasMore = last.page < last.pages).appendTo(next)
        assertEquals(listOf(1,31,30,16,15,2), all.posts.inReplyOrder(1).map { it.floor })
        assertFalse(all.hasMore)
    }

    @Test fun aSinglePageThreadNeedsOnlyOneDiscoveryRequest() = runBlocking {
        var requests = 0
        val api = TiebaApi("fixture")
        val response = PbPageResponse.newBuilder().setData(PbPageResponseData.newBuilder()
            .setPage(Page.newBuilder().setCurrentPage(1).setTotalPage(1))
            .addPostList(WirePost.newBuilder().setId(1).setFloor(1))
            .addPostList(WirePost.newBuilder().setId(2).setFloor(2))).build()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requests++
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(response.toByteArray().toResponseBody("application/x-protobuf".toMediaType())).build()
        }.build()
        TiebaApi::class.java.getDeclaredField("client").apply { isAccessible = true }.set(api, client)
        val result = api.thread("42", 1, null, descending = true, postId = "2")
        assertEquals(1, requests)
        assertEquals(listOf(1,2), result.posts.map { it.floor })
        assertFalse(result.page < result.pages)
    }
}
