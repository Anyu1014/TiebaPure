package dev.anyu.tiebapure

import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test

class PrivateVoiceUploadTest {
    private val account = Account("84", "fixture", "fixture-bduss", "fixture-stoken", "fixture-tbs")
    private fun wav() = ByteArray(31000).apply {
        "RIFF".toByteArray().copyInto(this)
        "WAVE".toByteArray().copyInto(this, 8)
    }
    private fun api(requests: MutableList<Request>, chunkStatus: Int = 0): TiebaApi {
        val api = TiebaApi("fixture")
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requests += chain.request()
            val payload = if (chain.request().url.encodedPath.endsWith("chunkupload")) """{"error":{"errno":$chunkStatus}}"""
                else """{"error":{"errno":0},"info":{"voice_md5":"0123456789abcdef0123456789abcdef_Synthetic_2"}}"""
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(payload.toResponseBody("application/json".toMediaType())).build()
        }.build()
        TiebaApi::class.java.getDeclaredField("client").apply { isAccessible = true }.set(api, client)
        return api
    }
    @Test fun chunksKeepTheWholeFileDigestAndTheFinalReceiptCreatesVoiceContent() = runBlocking {
        val requests = mutableListOf<Request>()
        val bytes = wav()
        val content = api(requests).uploadPrivateVoice(account, bytes, 2)
        assertEquals(3, requests.size)
        assertTrue(requests.all { it.url.isHttps && it.url.host == "tiebac.baidu.com" })
        assertTrue(requests.all {
            it.header("Cookie") == "CUID=fixture; ka=open; BDUSS=fixture-bduss; stoken=fixture-stoken"
        })
        val digest = java.security.MessageDigest.getInstance("MD5").digest(bytes).joinToString("") { "%02x".format(it) }
        for (index in 0..1) {
            val multipart = requests[index].body as MultipartBody
            val binary = multipart.parts.single { it.headers?.get("Content-Disposition")?.contains("filename=") == true }
            assertTrue(binary.headers!!["Content-Disposition"]!!.contains("filename=\"file\""))
            assertNull(binary.body.contentType())
            val fields = multipart.parts.filter { it.headers?.get("Content-Disposition")?.contains("filename=") != true }.associate { part ->
                val name = Regex("name=\"([^\"]+)\"").find(part.headers!!["Content-Disposition"]!!)!!.groupValues[1]
                name to Buffer().also { part.body.writeTo(it) }.readUtf8()
            }
            assertEquals((index * 30720).toString(), fields["offset"])
            assertEquals(if (index == 0) "30720" else "280", fields["length"])
            assertEquals(digest, fields["chunk_md5"])
            assertEquals(digest, fields["voice_md5"])
            assertEquals("fixture", fields["cuid"])
            assertEquals(Policy.sign(fields - "sign"), fields["sign"])
        }
        val finish = requests.last().body as FormBody
        val fields = (0 until finish.size).associate { finish.name(it) to finish.value(it) }
        assertEquals("2", fields["type"])
        assertEquals(digest, fields["voice_md5"])
        assertEquals(Policy.sign(fields - "sign"), fields["sign"])
        assertEquals("0123456789abcdef0123456789abcdef_Synthetic_2", PrivateMessageContent.voiceMD5(content))
        assertEquals(2, PrivateMessageContent.duration(content))
        val envelope = org.json.JSONArray(content).getJSONObject(0)
        assertEquals(10, envelope.getInt("type"))
        assertTrue(envelope.getBoolean("isUploaded"))
    }
    @Test fun aRejectedChunkStopsBeforeFinishAndIsNeverRetried() = runBlocking {
        val requests = mutableListOf<Request>()
        val failure = runCatching { api(requests, 123).uploadPrivateVoice(account, wav(), 2) }.exceptionOrNull()
        assertTrue(failure?.message.orEmpty().contains("123"))
        assertEquals(1, requests.size)
        assertTrue(requests.single().url.encodedPath.endsWith("chunkupload"))
    }
    @Test fun invalidAudioAndDurationNeverReachTheNetwork() = runBlocking {
        val requests = mutableListOf<Request>(); val api = api(requests)
        assertTrue(runCatching { api.uploadPrivateVoice(account, ByteArray(50), 2) }.isFailure)
        assertTrue(runCatching { api.uploadPrivateVoice(account, wav(), 61) }.isFailure)
        assertTrue(requests.isEmpty())
    }
}
