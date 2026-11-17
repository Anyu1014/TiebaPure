package dev.anyu.tiebapure

import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

import dev.anyu.tiebapure.official.OfficialIM as IM

class PrivateMessageNetworkTest {
    @Test fun nicknameSearchIncludesFuzzyMatchesAndDeduplicatesUsers() = runBlocking {
        val requests = mutableListOf<Request>()
        val api = api("""{"no":0,"data":{"exactMatch":{"id":"42","name":"raw","show_nickname":"昵称"},"fuzzyMatch":[{"id":42,"name":"raw"},{"uid":"43","name":"另一用户"}]}}""", requests)
        assertEquals(listOf("42","43"), api.searchUsers("昵称", null).map { it.id })
        assertEquals("/mo/q/search/user", requests.single().url.encodedPath)
    }
    @Test fun displayIdLookupUsesDedicatedEndpointAndVerifiesTheEchoedId() = runBlocking {
        val fixture = JSONObject(java.io.File("../../protocol-fixtures/private-messages.json").readText()).getJSONObject("id_lookup")
        val response = fixture.getString("response_hex").chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val requests = mutableListOf<Request>(); val api = TiebaApi("fixture")
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requests += chain.request()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(response.toResponseBody("application/octet-stream".toMediaType())).build()
        }.build()
        TiebaApi::class.java.getDeclaredField("client").apply { isAccessible = true }.set(api, client)
        val user = api.userByTiebaId(fixture.getString("id"), null)
        assertEquals(fixture.getString("uid"), user.id)
        assertEquals("/c/u/user/getUserByTiebaUid", requests.single().url.encodedPath)
        assertEquals("309702", requests.single().url.queryParameter("cmd"))
        assertTrue(runCatching { api.userByTiebaId("900000002", null) }.isFailure)
    }
    @Test fun publicDisplayIdResolvesToTheMatchingProfile() = runBlocking {
        val id = System.getenv("TIEBA_PRIVATE_RECIPIENT_ID").orEmpty()
        org.junit.Assume.assumeTrue("Opt-in anonymous read only", id.toLongOrNull()?.let { it > 0 } == true)
        val api = TiebaApi("anonymous-id-lookup-test")
        val user = api.userByTiebaId(id, null)
        assertNotEquals(id, user.id)
        assertEquals(id, api.profile(user.id, null).tiebaId)
    }
    private fun api(response: String, requests: MutableList<Request>): TiebaApi {
        val api = TiebaApi("fixture")
        val client = OkHttpClient.Builder().followRedirects(false).retryOnConnectionFailure(false).addInterceptor { chain ->
            requests += chain.request()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(response.toResponseBody("application/json".toMediaType())).build()
        }.build()
        TiebaApi::class.java.getDeclaredField("client").apply { isAccessible = true }.set(api, client)
        return api
    }
    private val account = Account("84","fixture","fixture-bduss","fixture-stoken","fixture-tbs")
    @Test fun successiveSendsAcceptSuccessWithOmittedOptionalEchoFields() = runBlocking {
        val api = TiebaApi("fixture"); var attempts = 0
        api.officialPull = { _, _ -> throw java.io.IOException("fixture") }
        api.officialSend = { _, _ -> attempts++; IM.CommitResponse.newBuilder().setError(IM.IMError.newBuilder().setCode(0)).setData(IM.CommitResult.newBuilder().setId(attempts.toLong())).build() }
        val peer = PrivateConversation("42", "42")
        assertEquals("1", api.sendPrivateMessage(account, peer, "第一条", "unused"))
        assertEquals("2", api.sendPrivateMessage(account, peer, "第二条", "unused"))
        assertEquals(listOf("第一条", "第二条"), api.cachedPrivateMessages(account, peer).items.map { it.text })
        assertEquals("第二条", api.cachedPrivateConversations(account).items.first().preview)
        api.officialSend = { _, _ -> IM.CommitResponse.newBuilder().setError(IM.IMError.newBuilder().setCode(0)).setData(IM.CommitResult.newBuilder().setId(3).setToUid(43)).build() }
        assertTrue(runCatching { api.sendPrivateMessage(account, peer, "第三条", "unused") }.exceptionOrNull() is PrivateSendUnknown)
    }
    @Test fun successfulReceiptCanOmitTheErrorEnvelope() = runBlocking {
        val api = TiebaApi("fixture"); var attempts = 0
        api.officialPull = { _, _ -> throw java.io.IOException("unexpected pull") }
        api.officialSend = { _, request -> attempts++; IM.CommitResponse.newBuilder()
            .setData(IM.CommitResult.newBuilder().setId(123).setToUid(42).setRecordId(request.data.recordId)).build() }
        assertEquals("123", api.sendPrivateMessage(account, PrivateConversation("42", "42"), "测试消息", "unused"))
        assertEquals(1, attempts)
    }

    @Test fun ambiguousSendDoesNotPullAndAnotherTapSendsAgain() = runBlocking {
        val api = TiebaApi("fixture"); val peer = PrivateConversation("42", "42")
        var sends = 0; var pulls = 0
        api.officialPull = { _, _ -> pulls++; throw java.io.IOException("unexpected pull") }
        api.officialSend = { _, _ ->
            sends++
            if (sends == 1) throw java.io.IOException("missing receipt")
            IM.CommitResponse.newBuilder().setError(IM.IMError.newBuilder().setCode(0)).setData(IM.CommitResult.newBuilder().setId(2)).build()
        }
        assertTrue(runCatching { api.sendPrivateMessage(account, peer, "相同内容", "unused") }.exceptionOrNull() is PrivateSendUnknown)
        assertEquals("2", api.sendPrivateMessage(account, peer, "相同内容", "unused"))
        assertEquals(2, sends); assertEquals(0, pulls)
    }

    @Test fun officialSendUsesActualUidPlainTextTypeAndMatchingReceiptWithoutRetry() = runBlocking {
        val api = TiebaApi("fixture"); var attempts = 0
        api.officialPull = { _, _ -> throw java.io.IOException("fixture") }
        api.officialSend = { _, request ->
            attempts++; assertEquals(42,request.data.toUid); assertEquals(1,request.data.msgType); assertTrue(request.data.hasToUserType()); assertEquals("测试",request.data.content)
            IM.CommitResponse.newBuilder().setError(IM.IMError.newBuilder().setCode(0)).setData(IM.CommitResult.newBuilder().setId(9007199254740993).setToUid(42).setRecordId(request.data.recordId)).build()
        }
        assertEquals("9007199254740993",api.sendPrivateMessage(account,PrivateConversation("42","42"),"测试","unused")); assertEquals(1,attempts)
        for (mode in 0..2) {
            attempts = 0
            api.officialSend = { _, _ -> attempts++; when(mode) { 0 -> throw java.io.IOException(); 1 -> IM.CommitResponse.newBuilder().setError(IM.IMError.newBuilder().setCode(615)).build(); else -> IM.CommitResponse.getDefaultInstance() } }
            val error = runCatching { api.sendPrivateMessage(account,PrivateConversation("42","42"),"测试","unused") }.exceptionOrNull()
            assertTrue(if(mode == 1) error is PrivateMessageRejected else error is PrivateSendUnknown); assertEquals(1,attempts)
        }
    }
}
