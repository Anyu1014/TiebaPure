package dev.anyu.tiebapure

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SubmissionPolicyTest {
    @Test fun imageThreadsEscapeTextAndRejectInjectedUploadIds() {
        assertEquals("<原文>", SubmissionPolicy.threadContent("<原文>", emptyList()))
        assertEquals("&lt;标题&gt;&amp;<br>正文<br><img class=\"BDE_Image\" src=\"https://tiebapic.baidu.com/forum/pic/item/fixture_pic_1.jpg\">", SubmissionPolicy.threadContent("<标题>&\n正文", listOf("fixture_pic_1")))
        assertEquals("<img class=\"BDE_Image\" src=\"https://tiebapic.baidu.com/forum/pic/item/fixture_pic_1.jpg\">", SubmissionPolicy.threadContent("", listOf("fixture_pic_1")))
        for (id in listOf("", "../evil", "x\" onerror=alert(1)")) assertTrue(runCatching { SubmissionPolicy.threadContent("", listOf(id)) }.isFailure)
        assertTrue(runCatching { SubmissionPolicy.threadContent("", List(10) { "image" }) }.isFailure)
    }
    @Test fun strictStatusDoesNotTreatBooleansOrFloatsAsSuccess() {
        for (value in listOf(true, 1.0, "1.0", "yes")) assertNull(SubmissionPolicy.strictInt(value))
        assertEquals(1, SubmissionPolicy.strictInt("1"))
    }
    @Test fun requiresExplicitConsistentReceipt() {
        assertEquals("42", SubmissionPolicy.receipt(JSONObject("{\"result\":1,\"data\":{\"tid\":\"42\"}}"), null))
        assertEquals("42", SubmissionPolicy.receipt(JSONObject("{\"no\":0}"), "42"))
        for (json in listOf("{}", "{\"result\":true}", "{\"result\":1,\"error_code\":2,\"tid\":42}", "{\"result\":1,\"tid\":42,\"data\":{\"tid\":43}}")) assertTrue(runCatching { SubmissionPolicy.receipt(JSONObject(json), null) }.exceptionOrNull() is OutcomeUnknown)
    }
    @Test fun challengeWinsOverNominalSuccess() {
        val e = runCatching { SubmissionPolicy.receipt(JSONObject("{\"error_code\":0,\"data\":{\"anti\":{\"need_vcode\":1}}}"), "42") }.exceptionOrNull()
        assertTrue(e is ChallengeRequired)
    }
    @Test fun explicitRefusalAndProfileStatusAreDistinctFromUnknownOutcome() {
        val rejected = runCatching { SubmissionPolicy.receipt(JSONObject("{\"no\":401,\"error\":\"内容不能为空\",\"data\":{\"tid\":0,\"pid\":0}}"), "42") }.exceptionOrNull()
        assertTrue(rejected is SubmissionRejected)
        assertEquals("内容不能为空", rejected?.message)
        assertTrue(runCatching { SubmissionPolicy.receipt(JSONObject("{\"no\":40,\"error\":\"请完成安全验证\",\"data\":{\"tid\":0,\"pid\":0}}"), "42") }.exceptionOrNull() is ChallengeRequired)
        assertTrue(runCatching { SubmissionPolicy.receipt(JSONObject("{\"result\":0,\"error_msg\":\"拒绝\"}"), null) }.exceptionOrNull() is SubmissionRejected)
        SubmissionPolicy.modifiedProfile(JSONObject("{\"error_code\":0}"))
        assertTrue(runCatching { SubmissionPolicy.modifiedProfile(JSONObject("{\"result\":0,\"data\":{\"errno\":1}}")) }.exceptionOrNull() is OutcomeUnknown)
    }
    @Test fun clientOperationsRejectConflictsAndOnlyExpireConfirmedSessions() {
        SubmissionPolicy.clientStatus(JSONObject("{\"error_code\":0}"))
        assertTrue(runCatching { SubmissionPolicy.clientStatus(JSONObject("{\"error_code\":0,\"err_code\":2}")) }.isFailure)
        assertTrue(runCatching { SubmissionPolicy.clientStatus(JSONObject("{\"error_code\":110001}")) }.exceptionOrNull() is SessionExpired)
        assertFalse(runCatching { SubmissionPolicy.clientStatus(JSONObject("{\"error_code\":4,\"error_msg\":\"参数错误\"}")) }.exceptionOrNull() is SessionExpired)
    }
}
