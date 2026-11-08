package dev.anyu.tiebapure

import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import tieba.PageOuterClass.Page
import tieba.SubPostListOuterClass.SubPostList
import tieba.pbFloor.PbFloorRequestOuterClass.PbFloorRequest
import tieba.pbFloor.PbFloorResponseOuterClass.*

class AccountLikesTest {
    private val fixture get() = JSONObject(java.io.File("../../protocol-fixtures/account-likes.json").readText())
    private val api = TiebaApi("fixture")
    private fun intercept(handler: (Request) -> Response) {
        TiebaApi::class.java.getDeclaredField("client").apply { isAccessible = true }.set(api, OkHttpClient.Builder().addInterceptor { handler(it.request()) }.build())
    }
    private fun response(request: Request, body: JSONObject) = Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
        .body(body.toString().toResponseBody("application/x-javascript".toMediaType())).build()

    @Test fun cloudRecordsRetainEachLikedReplyAndExactTargets() {
        val page = api.accountLikesPage(fixture.getJSONObject("first_page"))
        assertEquals(fixture.getJSONArray("expected_ids").let { a -> (0 until a.length()).map(a::getString) }, page.items.map { it.id })
        assertTrue(page.hasMore)
        assertEquals(Route("thread", "1001", "测试主题", "main"), page.items[0].route())
        assertEquals(Route("thread", "1001", "测试主题", "2002"), page.items[1].route())
        assertEquals("第一条赞过的回复", page.items[1].preview.summary)
        assertEquals(7L, page.items[1].preview.likes)
        assertEquals(Route("subposts", "1001", "测试", "2003:7:2004"), page.items[3].route())
        assertEquals("9007199254740993", page.items.last().preview.id)
    }

    @Test fun paginationKeepsOtherRepliesInTheSameThreadAndFollowsTheServer() {
        val first = api.accountLikesPage(fixture.getJSONObject("first_page"))
        val second = api.accountLikesPage(fixture.getJSONObject("second_page"))
        val merged = ScreenData(likes = second, hasMore = second.hasMore).appendTo(ScreenData(likes = first, hasMore = true))
        assertEquals(first.items.map { it.id } + "1001:2005", merged.likes?.items?.map { it.id })
        assertFalse(merged.hasMore)
        val duplicateOnly = ScreenData(likes = AccountLikesPage(first.items, true), hasMore = true).appendTo(ScreenData(likes = first, hasMore = true))
        assertTrue("A repeated page must not impose a local end on cloud history", duplicateOnly.hasMore)
        val emptyButMore = ScreenData(likes = AccountLikesPage(emptyList(), true), hasMore = true).appendTo(ScreenData(likes = AccountLikesPage(emptyList(), true)))
        assertTrue(emptyButMore.hasMore)
        assertFalse(api.accountLikesPage(fixture.getJSONObject("empty_page")).hasMore)
        assertTrue(api.accountLikesPage(JSONObject("""{"error_code":0,"data":{"has_more":0}}""")).items.isEmpty())
    }

    @Test fun loginErrorsAndMalformedResponsesAreNotReportedAsAnEmptyHistory() {
        for (key in listOf("login_error", "malformed")) assertTrue(key, runCatching { api.accountLikesPage(fixture.getJSONObject(key)) }.isFailure)
        val missingMore = fixture.getJSONObject("empty_page").apply { getJSONObject("data").remove("has_more") }
        assertTrue(runCatching { api.accountLikesPage(missingMore) }.isFailure)
    }

    @Test fun allFiltersRequestSignedAccountPagesFromTheCloud() = runBlocking {
        val account = Account("84", "fixture", "fixture-bduss", "fixture-stoken", "fixture-tbs")
        val tabs = mutableListOf<Int>()
        intercept { request ->
            assertEquals("https", request.url.scheme)
            assertEquals("tieba.baidu.com", request.url.host)
            assertEquals("/c/u/feed/userAgree", request.url.encodedPath)
            val body = request.body as FormBody
            val fields = (0 until body.size).associate { body.name(it) to body.value(it) }
            assertEquals(account.bduss, fields["BDUSS"])
            assertEquals(account.stoken, fields["stoken"])
            assertEquals("20", fields["rn"])
            assertEquals("2", fields["pn"])
            assertEquals(Policy.sign(fields - "sign"), fields["sign"])
            tabs += fields.getValue("tab_id").toInt()
            assertEquals("https://tieba.baidu.com/mo/q/hybrid-main-usercenter/myLike/hybrid?nonavigationbar=1", request.header("Referer"))
            response(request, fixture.getJSONObject("second_page"))
        }
        for (tab in 0..2) assertEquals(2, api.accountLikes(account, 2, tab).items.size)
        assertEquals(listOf(0, 1, 2), tabs)
        assertTrue(runCatching { api.accountLikes(account, 0, 0) }.isFailure)
        assertTrue(runCatching { api.accountLikes(account, 1, 3) }.isFailure)
    }

    @Test fun rejectsHTMLAndRedirectsInsteadOfTurningThemIntoEmptyLists() = runBlocking {
        val account = Account("84", "fixture", "fixture-bduss", "fixture-stoken", "fixture-tbs")
        var calls = 0
        intercept { request ->
            calls++
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(fixture.getJSONObject("first_page").toString().toResponseBody("text/html".toMediaType())).build()
        }
        assertTrue(runCatching { api.accountLikes(account, 1, 0) }.isFailure)
        intercept { request ->
            calls++
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(302).message("Redirect")
                .header("Location", "https://example.com/collect").body("".toResponseBody()).build()
        }
        assertTrue(runCatching { api.accountLikes(account, 1, 0) }.isFailure)
        assertEquals(2, calls)
    }

    @Test fun subpostTargetRequestsItsActualServerPageForFurtherPagination() = runBlocking {
        intercept { request ->
            val data = (request.body as MultipartBody).parts.single { it.headers?.get("Content-Disposition")?.contains("name=\"data\"") == true }.body
            val wire = PbFloorRequest.parseFrom(Buffer().also { data.writeTo(it) }.readByteArray()).data
            assertEquals(1001L, wire.kz)
            assertEquals(2003L, wire.pid)
            assertEquals(2004L, wire.spid)
            assertEquals(0, wire.pn)
            val result = PbFloorResponse.newBuilder().setData(PbFloorResponseData.newBuilder()
                .setPage(Page.newBuilder().setCurrentPage(6).setTotalPage(9))
                .addSubpostList(SubPostList.newBuilder().setId(2004))).build()
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(result.toByteArray().toResponseBody("application/x-protobuf".toMediaType())).build()
        }
        val result = api.subpostsPage("1001", "2003", "7", 1, null, "2004")
        assertEquals(6, result.page)
        assertEquals(listOf("2004"), result.posts.map { it.id })
        assertTrue(result.hasMore)
    }
}
