package dev.anyu.tiebapure

import org.json.JSONObject
import java.io.IOException

class OutcomeUnknown : IOException("提交结果未知，请刷新帖子确认。草稿已保留，请勿直接重复发送。")
class ChallengeRequired : IOException("贴吧要求完成安全验证，本次未自动重发。请在官方页面处理后确认实际结果。")
class SubmissionRejected(message: String) : IOException(message.take(240))
class SessionExpired : IOException("登录已失效，请重新登录")

object SubmissionPolicy {
    fun threadContent(text: String, imageIds: List<String>): String {
        if (imageIds.isEmpty()) return text
        require(imageIds.size <= 9 && imageIds.all { it.matches(Regex("[A-Za-z0-9_-]{1,2048}")) }) { "图片上传标识无效" }
        val escaped = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\r\n", "\n").replace("\n", "<br>")
        return (listOf(escaped).filter(String::isNotEmpty) + imageIds.map { "<img class=\"BDE_Image\" src=\"https://tiebapic.baidu.com/forum/pic/item/$it.jpg\">" }).joinToString("<br>")
    }
    fun clientStatus(j: JSONObject) {
        val roots = listOfNotNull(j, j.optJSONObject("data"), j.optJSONObject("error"))
        val values = roots.flatMap { obj -> listOf("error_code", "err_code", "errno", "error_no", "no").filter(obj::has).map { strictInt(obj.opt(it)) ?: throw IOException("接口状态格式无效") } }
        require(values.isNotEmpty() && values.distinct().size == 1) { "服务器未返回可确认的操作结果" }
        val code = values.first()
        val message = j.text("error_msg", "error", "errmsg").take(240)
        if (code in setOf(110001, 110002, 110003, 110004) || code == 4 && listOf("未登录", "登录失效", "登录已失效").any(message::contains)) throw SessionExpired()
        check(code == 0) { message.ifBlank { "贴吧接口错误 $code" } }
    }
    fun strictInt(value: Any?): Int? = when (value) {
        is Byte, is Short, is Int, is Long, is java.math.BigInteger -> value.toString().toIntOrNull()
        is String -> value.takeIf { it.matches(Regex("-?[0-9]+")) }?.toIntOrNull()
        else -> null
    }
    private fun objects(root: JSONObject): List<JSONObject> {
        val roots = listOfNotNull(root, root.optJSONObject("data"))
        return roots + roots.flatMap { listOfNotNull(it.optJSONObject("info"), it.optJSONObject("anti")) }
    }
    fun challenge(j: JSONObject): Boolean = objects(j).any { obj ->
        val need = obj.text("need_vcode").lowercase()
        (need.isNotEmpty() && need !in setOf("0", "false", "no", "off", "none")) ||
            listOf("vcode_md5", "pass_token", "vcode_pic_url", "block_content").any { obj.text(it).isNotBlank() } ||
            obj.optJSONObject("anti_stat")?.text("vcode_stat")?.let { it.isNotBlank() && it !in setOf("0", "false") } == true ||
            listOf("error_msg", "err_msg", "message", "error", "errmsg", "toast").any { key -> obj.text(key).let { t -> listOf("验证码", "安全验证", "captcha", "verify").any { t.contains(it, true) } } }
    }
    fun receipt(j: JSONObject, threadId: String?): String {
        if (challenge(j)) throw ChallengeRequired()
        val roots = listOfNotNull(j, j.optJSONObject("data"))
        val results = mutableListOf<Int>()
        val errors = mutableListOf<Int>()
        for (obj in roots) for (key in listOf("result", "error_code", "err_code", "no", "errno", "error_no")) if (obj.has(key)) {
            val value = strictInt(obj.opt(key)) ?: throw OutcomeUnknown()
            if (key == "result") results.add(value) else errors.add(value)
        }
        if (results.isEmpty() && errors.isEmpty()) throw OutcomeUnknown()
        if (results.distinct().size > 1 || errors.distinct().size > 1) throw OutcomeUnknown()
        val resultOk = results.firstOrNull()?.let { it == 1 }
        val errorOk = errors.firstOrNull()?.let { it == 0 }
        if (resultOk != null && errorOk != null && resultOk != errorOk) throw OutcomeUnknown()
        if (resultOk == false || errorOk == false) throw SubmissionRejected(roots.firstNotNullOfOrNull { it.text("error_msg", "err_msg", "errmsg", "error").takeIf(String::isNotBlank) } ?: "贴吧拒绝了本次提交")
        val tids = roots.flatMap { obj -> listOf("tid", "thread_id").filter(obj::has).map { obj.text(it) } }.distinct()
        if (tids.any { it.toLongOrNull()?.let { n -> n > 0 } != true } || tids.size > 1) throw OutcomeUnknown()
        val pids = roots.flatMap { obj -> listOf("pid", "post_id").filter(obj::has).map { obj.text(it) } }.distinct()
        if (pids.any { it.toULongOrNull()?.let { n -> n > 0u } != true } || pids.size > 1) throw OutcomeUnknown()
        if (threadId == null) return tids.singleOrNull() ?: throw OutcomeUnknown()
        if (tids.isNotEmpty() && tids.single() != threadId) throw OutcomeUnknown()
        return threadId
    }
    fun modifiedProfile(j: JSONObject) {
        val roots = listOfNotNull(j, j.optJSONObject("data"), j.optJSONObject("error"))
        val values = roots.flatMap { obj -> listOf("result", "error_code", "err_code", "errno", "error_no", "no").filter(obj::has).map { strictInt(obj.opt(it)) ?: throw OutcomeUnknown() } }
        if (values.isEmpty() || values.distinct().size != 1) throw OutcomeUnknown()
        if (values.first() != 0) throw SubmissionRejected(j.text("error_msg", "errmsg").ifBlank { "资料修改未成功" })
    }
}
