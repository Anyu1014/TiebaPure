package dev.anyu.tiebapure

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class HomeHotCommentTest {
    @Test fun homeThreadRetainsOnlyServerSelectedReply() = runBlocking {
        val fixture = JSONObject(java.io.File("../../protocol-fixtures/home-hot-comment.json").readText())
        val bytes = fixture.getString("response_hex").chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val api = TiebaApi("fixture")
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(bytes.toResponseBody("application/octet-stream".toMediaType())).build()
        }.build()
        TiebaApi::class.java.getDeclaredField("client").apply { isAccessible = true }.set(api, client)
        val threads = api.home(null, 1)
        val reply = threads[0].hotComment!!
        assertEquals("77", reply.id); assertEquals("84", reply.author.id)
        assertEquals("评论作者", reply.author.name)
        assertEquals("这是官方选出的热评", reply.blocks.joinToString("") { it.text })
        assertEquals(460L, reply.likes)
        assertNull(threads[1].hotComment); assertNull(threads[2].hotComment)
    }
    @Test fun officialFeedPostExposeMapsToItsOwnThread() = runBlocking {
        val fixture = JSONObject(java.io.File("../../protocol-fixtures/home-postexpose.json").readText())
        val bytes = fixture.getString("response_hex").chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val api = TiebaApi("fixture")
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val buffer = okio.Buffer()
            (chain.request().body as okhttp3.MultipartBody).part(0).body.writeTo(buffer)
            val request = tieba.Personalized.PersonalizedRequest.parseFrom(buffer.readByteArray())
            assertEquals(1, request.data.isNewfeed); assertEquals(2, request.data.qType)
            assertEquals(1, request.data.cacheColumnStyle); assertEquals("22.13.1.1", request.data.common.clientVersion)
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(bytes.toResponseBody("application/octet-stream".toMediaType())).build()
        }.build()
        TiebaApi::class.java.getDeclaredField("client").apply { isAccessible = true }.set(api, client)
        val threads = api.home(null, 1)
        val hot = threads[0].hotComment
        assertNotNull("server post-expose should be displayed", hot)
        assertEquals("77", hot!!.id); assertEquals("热评作者", hot.author.name)
        assertEquals("官方组件真实格式评论", hot.blocks.joinToString("") { it.text }); assertEquals(460L, hot.likes)
        assertNull(threads[1].hotComment)
    }
    @Test fun feedNewStyleAndMismatchedThreadRemainIsolated() = runBlocking {
        val fixture = JSONObject(java.io.File("../../protocol-fixtures/home-postexpose.json").readText())
        val raw = fixture.getString("response_hex").chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val original = tieba.Personalized.PersonalizedResponse.parseFrom(raw)
        val layout = original.data.pageData.getFeedList(0)
        val feed = layout.feed
        val component = feed.getComponents(0)
        fun responseWith(value: tieba.Personalized.PersonalizedFeedLayout): ByteArray {
            val page = original.data.pageData.toBuilder().setFeedList(0, layout.toBuilder().setFeed(value)).build()
            return original.toBuilder().setData(original.data.toBuilder().setPageData(page)).build().toByteArray()
        }
        var payload = responseWith(feed.toBuilder().clearComponents().addComponents(tieba.Personalized.PersonalizedComponent.getDefaultInstance()).addComponentsNewStyle(component).build())
        val api = TiebaApi("fixture")
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(payload.toResponseBody("application/octet-stream".toMediaType())).build()
        }.build()
        TiebaApi::class.java.getDeclaredField("client").apply { isAccessible = true }.set(api, client)
        assertEquals("77", api.home(null, 1)[0].hotComment?.id)
        val other = component.feedPostexpose.toBuilder().clearBusinessInfo().addBusinessInfo(tieba.Personalized.PersonalizedFeedKV.newBuilder().setKey("thread_id").setValue("43"))
        payload = responseWith(feed.toBuilder().clearComponents().addComponents(component.toBuilder().setFeedPostexpose(other)).build())
        assertNull(api.home(null, 1)[0].hotComment)
    }
}
