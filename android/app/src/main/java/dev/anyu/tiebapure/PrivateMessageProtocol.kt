package dev.anyu.tiebapure

internal data class PrivateConversation(val userId: String, val passUK: String, val userType: Int = 0, val name: String = "用户$userId", val portrait: String = "", val preview: String = "", val timestamp: Long = 0, val unread: Int = 0) {
    val id get() = "$userType:$passUK"
    companion object {
        fun recipient(user: User, accountId: String): PrivateConversation {
            Policy.signedId(user.id)
            require(user.id != accountId) { "请选择其他用户" }
            return PrivateConversation(user.id, user.id, name = user.name, portrait = user.portrait)
        }
    }
}
internal data class PrivateChatMessage(val id: String, val senderId: String, val text: String, val timestamp: Long, val type: Int = 1, val mediaURL: String? = null, val voiceMD5: String? = null, val duration: Int = 0, val read: Boolean = false)

internal object PrivateMessageContent {
    private fun body(content: String): org.json.JSONObject? = runCatching {
        if (content.length > 65536) null else org.json.JSONArray(content).optJSONObject(0)
    }.getOrNull()
    fun readWatermark(message: dev.anyu.tiebapure.official.OfficialIM.ChatMessage, privateGroupIds: Set<Long>): Long? {
        if (message.type != 22 || message.content.length > 65536) return null
        return runCatching {
            val json = org.json.JSONObject(message.content)
            val param = json.optJSONObject("eventParam") ?: return null
            val group = param.opt("groupId")?.toString()?.toLongOrNull()
            val watermark = param.opt("readMsgId")?.toString()?.toLongOrNull()
            // 22001 references the account's type-6 private stream, not its message envelope.
            watermark?.takeIf { json.optString("eventId") == "22001" && group != null && group > 0 && group in privateGroupIds && it > 0 }
        }.getOrNull()
    }
    fun imageURL(content: String): String? = body(content)?.let { json ->
        listOf("big_src", "src").firstNotNullOfOrNull { Policy.mediaUrl(json.optString(it).replaceFirst("http://", "https://")) }
    }
    private val voiceIdPattern = Regex("[a-fA-F0-9]{32}(?:_[a-zA-Z0-9_-]{1,95})?")
    fun voiceID(value: String): String? =
        value.takeIf(voiceIdPattern::matches)?.let { if ('_' in it) it else it.lowercase() }
    fun voiceURL(value: String): String? = voiceID(value)?.let {
        "https://tiebac.baidu.com/c/p/voice?voice_md5=$it&play_from=pb_voice_play"
    }
    fun voiceMD5(content: String): String? = body(content)?.optString("voice_md5")?.let(::voiceID)
    fun duration(content: String): Int = body(content)?.optInt("during_time", 0)?.coerceIn(0, 600) ?: 0
    fun validate(content: String, type: Int) {
        require(content.isNotBlank() && content.length <= if (type == 1) 5000 else 65536) { "私信内容无效或过长" }
        require(type == 1 || type == 2 && imageURL(content) != null || type == 3 && voiceMD5(content) != null && (body(content)?.optInt("during_time", 0) ?: 0) in 1..600) { "私信附件无效，请重新选择" }
    }
    fun message(message: dev.anyu.tiebapure.official.OfficialIM.ChatMessage, read: Boolean = false): PrivateChatMessage = PrivateChatMessage(
        message.id.toString(), message.fromUid.toString(), OfficialPrivateInbox.text(message), message.timestamp.toLong(), message.type,
        if (message.type == 2) imageURL(message.content) else null,
        if (message.type == 3) voiceMD5(message.content) else null,
        if (message.type == 3) duration(message.content) else 0, read
    )
}
internal data class PrivateMessagePage<T>(val items: List<T>, val hasMore: Boolean, val cursor: Long?)
internal class PrivateMessageRejected(val code: Long, val detail: String = "") : Exception("贴吧私信请求未通过（$code）。" + detail.ifBlank { "请稍后重试。" })
internal class PrivateSendUnknown : Exception("发送结果无法确认，请先刷新消息核对，避免重复发送。")

