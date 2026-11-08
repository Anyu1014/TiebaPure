package dev.anyu.tiebapure

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer

class PrivateMediaUploadTest {
    @Test fun imageChunksAreSignedAndNeverSentAfterAnUploadRejection() = runBlocking {
        val requests = mutableListOf<Request>()
        var reject = false
        val api = TiebaApi("fixture")
        api.officialPull = { _, _ ->
            val group = dev.anyu.tiebapure.official.OfficialIM.Group.newBuilder().setId(600).setType(6).build()
            OfficialPrivateSnapshot(listOf(group), dev.anyu.tiebapure.official.OfficialIM.PullResponse.newBuilder().setError(dev.anyu.tiebapure.official.OfficialIM.IMError.newBuilder().setCode(0)).build())
        }
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requests += chain.request()
            val payload = if (reject) """{"error_code":123}""" else """{"error_code":0,"picInfo":{"originPic":{"picUrl":"https://tiebapic.baidu.com/a.jpg"}}}"""
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(payload.toResponseBody("application/json".toMediaType())).build()
        }.build()
        TiebaApi::class.java.getDeclaredField("client").apply { isAccessible = true }.set(api, client)
        val account = Account("84", "fixture", "fixture-bduss", "fixture-stoken", "fixture-tbs")
        val content = api.uploadPrivateImage(account, ByteArray(512001), 40, 50)
        assertEquals("https://tiebapic.baidu.com/a.jpg", PrivateMessageContent.imageURL(content))
        assertEquals(2, requests.size)
        for ((index, request) in requests.withIndex()) {
            assertTrue(request.url.isHttps)
            assertEquals("tiebac.baidu.com", request.url.host)
            assertEquals("/c/s/uploadPicture", request.url.encodedPath)
            val body = request.body as MultipartBody
            val fields = body.parts.filter { it.headers?.get("Content-Disposition")?.contains("filename=") != true }.associate { part ->
                val name = Regex("name=\"([^\"]+)\"").find(part.headers!!["Content-Disposition"]!!)!!.groupValues[1]
                name to Buffer().also { part.body.writeTo(it) }.readUtf8()
            }
            assertEquals((index + 1).toString(), fields["chunkNo"])
            assertEquals(index.toString(), fields["isFinish"])
            assertEquals("600", fields["groupId"])
            assertEquals("fixture", fields["cuid"])
            assertEquals("CUID=fixture; ka=open; BDUSS=fixture-bduss; stoken=fixture-stoken", request.header("Cookie"))
            assertEquals("jepgwebp", fields["support_image"])
            assertEquals("8114F02769ED1935D0457DCB1D74C7FF512000", fields["resourceId"])
            assertEquals(Policy.sign(fields - "sign"), fields["sign"])
            val file = body.parts.single { it.headers?.get("Content-Disposition")?.contains("filename=") == true }
            assertTrue(file.headers!!["Content-Disposition"]!!.contains("filename=\"file\""))
            assertNull(file.body.contentType())
            assertEquals(if (index == 0) 512000L else 1L, file.body.contentLength())
        }
        requests.clear(); reject = true
        assertTrue(runCatching { api.uploadPrivateImage(account, ByteArray(512001), 40, 50) }.isFailure)
        assertEquals(1, requests.size)
    }
    @Test fun imageUploadReceiptUsesValidatedOriginalURLAndOfficialBodyShape() {
        val api = TiebaApi("fixture")
        val receipt = JSONObject("""{"error_code":0,"picInfo":{"originPic":{"picUrl":"http://tiebapic.baidu.com/forum/pic/item/fixture.jpg"}}}""")
        val content = api.privateImageContent(receipt, 40, 50)
        assertEquals("https://tiebapic.baidu.com/forum/pic/item/fixture.jpg", PrivateMessageContent.imageURL(content))
        val body = org.json.JSONArray(content).getJSONObject(0)
        assertEquals("3", body.getString("type"))
        assertEquals("40,50", body.getString("bsize"))
        for (url in listOf("https://127.0.0.1/a.jpg", "https://example.com/a.jpg", "file:///tmp/a.jpg")) {
            val bad = JSONObject().put("picInfo", JSONObject().put("originPic", JSONObject().put("picUrl", url)))
            assertTrue(runCatching { api.privateImageContent(bad, 40, 50) }.isFailure)
        }
    }
    @Test fun imageReceiptFallsBackToTheOfficialBigAndSmallVariantsAndKeepsSafeErrorCodes() {
        val api = TiebaApi("fixture")
        val receipt = JSONObject("""{"error_code":0,"picInfo":{"bigPic":{"picUrl":"https://tiebapic.baidu.com/big.jpg"},"smallPic":{"picUrl":"https://tiebapic.baidu.com/small.jpg"}}}""")
        val body = org.json.JSONArray(api.privateImageContent(receipt, 40, 50)).getJSONObject(0)
        assertEquals("https://tiebapic.baidu.com/small.jpg", body.getString("src"))
        assertEquals("https://tiebapic.baidu.com/big.jpg", body.getString("big_src"))
        val error = runCatching { api.privateImageContent(JSONObject("""{"error_code":123,"error_msg":"private-server-echo"}"""), 40, 50) }.exceptionOrNull()
        assertTrue(error?.message.orEmpty().contains("123"))
        assertFalse(error?.message.orEmpty().contains("private-server-echo"))
    }
}
