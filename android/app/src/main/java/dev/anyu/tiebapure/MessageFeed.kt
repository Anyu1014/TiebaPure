package dev.anyu.tiebapure

import org.json.JSONObject

internal data class MessageFeedItem(val thread: Thread, val id: String, val unread: Boolean?)

internal data class MessageFeed(
    val items: List<MessageFeedItem>,
    val unreadCount: Int?,
    val otherUnreadCount: Int?,
) {
    val head get() = items.firstOrNull()?.id.orEmpty()

    fun hasUnread(viewed: String?, otherCount: Int?): Boolean {
        if (head.isEmpty() || head == viewed) return false
        if ((unreadCount ?: otherCount ?: 0) > 0) return true
        val newer = items.takeWhile { it.id != viewed }
        return newer.any { it.unread == true } ||
            (viewed != null && unreadCount == null && newer.any { it.unread == null })
    }

    companion object {
        fun decode(json: JSONObject, at: Boolean): MessageFeed {
            val rows = json.optJSONArray(if (at) "at_list" else "reply_list")
                ?: json.optJSONArray(if (at) "reply_list" else "at_list")
                ?: json.optJSONArray("message_list")
            val items = rows?.objects().orEmpty().mapNotNull { row ->
                val threadId = row.text("thread_id", "tid")
                if ((threadId.toLongOrNull() ?: 0) <= 0) return@mapNotNull null
                val authorId = row.optJSONObject("replyer")?.text("id").orEmpty()
                val id = listOf(threadId, row.text("post_id", "quote_pid"), authorId, row.text("time")).joinToString(":")
                val flag = row.text("unread")
                val unread = flag.toBooleanStrictOrNull() ?: flag.toIntOrNull()?.let { it > 0 }
                val thread = Thread(threadId, row.text("title", "thread_title").ifBlank { "帖子回复" }, row.text("fname", "forum_name"), summary = row.text("content", "reply_content"))
                MessageFeedItem(thread, id, unread)
            }
            val counts = json.optJSONObject("message")
            val replies = counts?.text("replyme")?.toIntOrNull()
            val mentions = counts?.text("atme")?.toIntOrNull()
            return if (at) MessageFeed(items, mentions, replies) else MessageFeed(items, replies, mentions)
        }
    }
}
