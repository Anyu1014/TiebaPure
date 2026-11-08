package dev.anyu.tiebapure

import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class ForumCategoryRegressionTest {
    @Test fun categoryRequestsReturnSeparateListsAndKeepTheCategoryWhilePaging() = runBlocking {
        val fields = mutableListOf<Map<String, String>>()
        val api = TiebaApi("fixture")
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val body = chain.request().body as FormBody
            val request = (0 until body.size).associate { body.name(it) to body.value(it) }
            fields += request
            val category = request.getValue("sort_type")
            if (category == "-1") {
                assertEquals("1", request["is_good"])
                assertEquals("0", request["cid"])
            } else {
                assertFalse(request.containsKey("is_good"))
                assertFalse(request.containsKey("cid"))
            }
            val row = JSONObject().put("id", when (category) { "0" -> "100"; "1" -> "200"; else -> "300" } + request.getValue("pn"))
                .put("title", "category-$category-page-${request.getValue("pn")}")
                .put("create_time", 1793100000L).put("is_top", 1).put("author", JSONObject().put("id", "88"))
                .put("media", JSONArray("""[{"type":3,"src_pic":"http://tiebapic.baidu.com/forum/preview.jpg","origin_pic":"http://tiebapic.baidu.com/forum/original.jpg"}]"""))
            val response = JSONObject().put("error_code", 0).put("forum", JSONObject().put("id", "7").put("name", "测试"))
                .put("thread_list", JSONArray().put(row))
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(response.toString().toResponseBody("application/json".toMediaType())).build()
        }.build()
        TiebaApi::class.java.getDeclaredField("client").apply { isAccessible = true }.set(api, client)
        val reply = api.forum("测试", 1).single()
        assertEquals("1001", reply.id)
        assertEquals(listOf("https://tiebapic.baidu.com/forum/preview.jpg"), reply.previews)
        assertEquals("2001", api.forum("测试", 1, 1).single().id)
        assertEquals("3001", api.forum("测试", 1, 0, true).single().id)
        assertEquals("3002", api.forum("测试", 2, 0, true).single().id)
        assertEquals("1001", api.forum("测试", 1).single().id)
        assertEquals(listOf("0", "1", "-1", "-1", "0"), fields.map { it["sort_type"] })
        val automation = api.automationThreads("测试", 2).single()
        assertEquals("2002", automation.id)
        assertEquals(1793100000L, automation.createdAt)
        assertEquals("88", automation.authorId)
        assertTrue(automation.pinned)
        assertEquals("1", fields.last()["sort_type"])
    }

    @Test fun publicForumCategoriesActuallyReturnDifferentThreads() = runBlocking {
        assumeTrue("Anonymous public read only", System.getenv("RUN_FORUM_PUBLIC_READ") == "1")
        val api = TiebaApi("0123456789abcdef0123456789abcdef")
        val reply = api.forum("奥比交易", 1).map { it.id }
        val publish = api.forum("奥比交易", 1, 1).map { it.id }
        val featured = api.forum("奥比交易", 1, 0, true).map { it.id }
        assertTrue(reply.isNotEmpty() && publish.isNotEmpty() && featured.isNotEmpty())
        assertNotEquals("Latest reply and publication must not reuse the same list", reply, publish)
        assertNotEquals("Featured must not reuse the latest list", reply, featured)
        assertNotEquals(publish, featured)
    }

    @Test fun publicForumPhotosHaveLoadablePreviews() = runBlocking {
        assumeTrue("Anonymous public read only", System.getenv("RUN_FORUM_PUBLIC_READ") == "1")
        val rows = TiebaApi("0123456789abcdef0123456789abcdef").forum("奥比交易", 1)
        val photo = rows.first { !it.isTop && it.previews.isNotEmpty() }.previews.first()
        OkHttpClient.Builder().followRedirects(false).build().newCall(Request.Builder().url(photo).build()).execute().use { response ->
            assertTrue(response.isSuccessful)
            assertTrue(response.header("Content-Type").orEmpty().startsWith("image/"))
            assertTrue(response.body!!.byteStream().read(ByteArray(32)) > 0)
        }
    }
}
