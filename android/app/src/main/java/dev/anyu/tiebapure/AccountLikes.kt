package dev.anyu.tiebapure

import org.json.JSONObject
import org.json.JSONArray

data class AccountLikedContent(val preview: Thread, val postId: String? = null, val parentPostId: String? = null, val forumId: String = "", val floor: Int = 0) {
    val id: String get() = "${preview.id}:${postId ?: "0"}"
    val label: String get() = if (parentPostId != null) "楼中楼" else if (postId != null) "回帖" + if (floor > 0) " · ${floor}楼" else "" else "主帖"
    fun route(): Route = if (parentPostId != null) Route("subposts", preview.id, preview.forum, "$parentPostId:$forumId:$postId")
        else Route("thread", preview.id, preview.title, postId ?: "main")
}

data class AccountLikesPage(val items: List<AccountLikedContent>, val hasMore: Boolean)

internal fun TiebaApi.accountLikesPage(response: JSONObject): AccountLikesPage {
    SubmissionPolicy.clientStatus(response)
    val data = response.optJSONObject("data") ?: error("未返回云端点赞列表，请重试")
    val more = when (data.opt("has_more")?.toString()) {
        "1", "true" -> true
        "0", "false" -> false
        else -> error("云端点赞分页状态缺失，请重试")
    }
    val rows = if (!data.has("thread_list") && !more) JSONArray() else data.optJSONArray("thread_list") ?: error("云端点赞列表格式异常，请重试")
    fun positiveId(value: String): String? = value.takeIf { it.toLongOrNull()?.let { id -> id > 0 } == true }
    val items = rows.objects().mapNotNull { row ->
        val tid = positiveId(row.text("tid", "thread_id", "id")) ?: return@mapNotNull null
        val post = row.optJSONObject("top_agree_post")
        if (row.has("top_agree_post") && !row.isNull("top_agree_post") && post == null) return@mapNotNull null
        val pid = post?.let { positiveId(it.text("id", "post_id")) ?: return@mapNotNull null }
        val parent = post?.text("quote_id")?.takeIf { it.isNotBlank() && it != "0" }?.let { positiveId(it) ?: return@mapNotNull null }
        val forum = row.optJSONObject("forum_info") ?: JSONObject()
        val fid = positiveId(forum.text("id", "forum_id")).orEmpty()
        if (parent != null && fid.isBlank()) return@mapNotNull null
        var preview = jsonThread(row, forum.text("name", "forum_name")).copy(id = tid, forumAvatar = forum.text("avatar"))
        if (post != null) {
            val author = post.optJSONObject("author") ?: JSONObject()
            val images = post.optJSONArray("content")?.objects().orEmpty().filter { it.optInt("type") == 3 }.mapNotNull {
                listOf("origin_src", "cdn_src", "src").firstNotNullOfOrNull { key -> Policy.mediaUrl(it.text(key).replaceFirst("http://", "https://")) }
            }.take(9)
            preview = preview.copy(author = User(author.text("id", "uid"), author.userDisplayName().ifBlank { "贴吧用户" }, author.userPortrait()),
                summary = jsonSummary(post.optJSONArray("content")), previews = images,
                timestamp = post.optLong("time"), likes = post.optJSONObject("agree")?.optLong("agree_num") ?: post.optLong("agree_num"))
        }
        AccountLikedContent(preview, pid, parent, fid, post?.optInt("floor") ?: 0)
    }.distinctBy { it.id }
    return AccountLikesPage(items, more)
}
