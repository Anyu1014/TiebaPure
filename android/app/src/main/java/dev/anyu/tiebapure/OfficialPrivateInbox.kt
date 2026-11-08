package dev.anyu.tiebapure

import dev.anyu.tiebapure.official.OfficialIM as IM
import java.io.File

internal data class OfficialPrivateSnapshot(val groups: List<IM.Group>, val response: IM.PullResponse)

internal class OfficialPrivateInbox(private val accountId: String, private val file: File? = null) {
    var state: IM.Inbox = file?.let { source ->
        val input = try { android.util.AtomicFile(source).openRead() }
        catch(e: java.io.FileNotFoundException) { if(source.exists()) throw e else return@let null }
        input.use {
            require(source.length() <= 16_777_216) { "私信缓存过大" }
            IM.Inbox.parseFrom(it)
        }
    } ?: IM.Inbox.getDefaultInstance()
        private set
    private fun peer(message: IM.ChatMessage): String? {
        // Official private read events can omit to_uid; their sender identifies the peer.
        if (message.id > 0 && message.type == 22 && message.fromUid > 0 && message.fromUid.toString() != accountId && message.toUid == 0L) return message.fromUid.toString()
        if (message.id <= 0 || message.fromUid <= 0 || message.toUid <= 0 || message.fromUid == message.toUid) return null
        return when(accountId) { message.fromUid.toString() -> message.toUid.toString(); message.toUid.toString() -> message.fromUid.toString(); else -> null }
    }
    fun merge(snapshot: OfficialPrivateSnapshot) {
        val response = snapshot.response
        require(response.hasError()) { "官方私信状态缺失" }
        if (response.error.code != 0) {
            throw PrivateMessageRejected(response.error.code.toLong())
        }
        val groups = state.groupsList.associateBy { it.id }.toMutableMap()
        snapshot.groups.forEach { if(it.id !in groups) groups[it.id] = it.toBuilder().setLastMsgId(0).build() }
        val messages = state.messagesList.filter { peer(it) != null }.associateBy { "${peer(it)}:${it.id}" }.toMutableMap()
        response.data.personal.messagesList.forEach { message ->
            val id = peer(message) ?: return@forEach
            require(message.fromUser.id == 0L || message.fromUser.id == message.fromUid)
            require(message.toUser.id == 0L || message.toUser.id == message.toUid)
            messages["$id:${message.id}"] = message
        }
        response.data.groupsList.forEach { batch ->
            val known = groups[batch.group.id]
            val type = if(batch.group.hasType()) batch.group.type else known?.type
            if(type == 6) batch.messagesList.forEach { message ->
                val id = peer(message) ?: return@forEach
                require(message.fromUser.id == 0L || message.fromUser.id == message.fromUid)
                require(message.toUser.id == 0L || message.toUser.id == message.toUid)
                messages["$id:${message.id}"] = message
            }
            if(known != null || batch.group.hasType()) {
                val group = (known ?: batch.group).toBuilder().mergeFrom(batch.group)
                val cursor = maxOf(known?.lastMsgId ?: 0, batch.messagesList.maxOfOrNull { it.id } ?: 0, if(batch.hasMore == 0) batch.group.lastMsgId else 0)
                groups[batch.group.id] = group.setLastMsgId(cursor).build()
            }
        }
        save(state.toBuilder().clearGroups().addAllGroups(groups.values).clearMessages().addAllMessages(messages.values).build())
    }
    fun sent(peer: PrivateConversation, account: Account, text: String, receipt: IM.CommitResult, type: Int = 1) {
        val message = IM.ChatMessage.newBuilder().setId(receipt.id).setGroupId(receipt.groupId).setType(type).setFromUid(account.uid.toLong()).setToUid(peer.userId.toLong()).setContent(text).setTimestamp((System.currentTimeMillis()/1000).toInt()).setFromUser(IM.User.newBuilder().setId(account.uid.toLong()).setNickname(account.name).setPortrait(account.portrait)).setToUser(IM.User.newBuilder().setId(peer.userId.toLong()).setNickname(peer.name).setPortrait(peer.portrait)).build()
        val messages = state.messagesList.filterNot { this.peer(it) == peer.userId && it.id == message.id } + message
        save(state.toBuilder().clearMessages().addAllMessages(messages).build())
    }
    fun conversations(): PrivateMessagePage<PrivateConversation> {
        val reads = state.readList.groupingBy { it.peerId.toString() }.fold(0L) { latest, cursor -> maxOf(latest, cursor.messageId) }
        val rows = state.messagesList.filter { it.type != 22 }.mapNotNull { message -> peer(message)?.let { it to message } }.groupBy({ it.first }, { it.second }).map { (id, messages) ->
            val last = messages.maxWith(compareBy({ it.timestamp }, { it.id }))
            val user = if(last.fromUid.toString() == id) last.fromUser else last.toUser
            last.id to PrivateConversation(id, id, name = user.displayName.ifBlank { user.nickname }.ifBlank { user.name }.ifBlank { "用户$id" }, portrait = user.portrait, preview = text(last), timestamp = last.timestamp.toLong(), unread = messages.count { it.fromUid.toString() == id && it.id > (reads[id] ?: 0) })
        }.sortedWith(compareByDescending<Pair<Long, PrivateConversation>> { it.second.timestamp }
            .thenByDescending { it.first }.thenByDescending { it.second.userId })
        return PrivateMessagePage(rows.map { it.second }, false, null)
    }
    fun markAllRead() {
        val incoming = state.messagesList.asSequence()
            .filter { it.type != 22 && it.toUid.toString() == accountId && peer(it) != null }
            .groupingBy { it.fromUid }.fold(0L) { latest, message -> maxOf(latest, message.id) }
        advanceReadCursors(incoming)
    }

    private fun advanceReadCursors(updates: Map<Long, Long>) {
        val previous = state.readList.groupingBy { it.peerId }.fold(0L) { latest, cursor -> maxOf(latest, cursor.messageId) }
        val changed = updates.filter { (peer, message) -> message > previous.getOrDefault(peer, 0L) }
        if (changed.isEmpty()) return
        val reads = state.readList.filterNot { it.peerId in changed } + changed.map { (peer, message) ->
            IM.ReadCursor.newBuilder().setPeerId(peer).setMessageId(message).build()
        }
        save(state.toBuilder().clearRead().addAllRead(reads).build())
    }
    fun messages(id: String, before: Long?): PrivateMessagePage<PrivateChatMessage> {
        Policy.signedId(id); require(id != accountId)
        val privateGroups = state.groupsList.filter { it.type == 6 }.map { it.id }.toSet()
        val events = state.messagesList.filter { peer(it) == id && it.fromUid.toString() == id }
        val watermark = events.mapNotNull { PrivateMessageContent.readWatermark(it, privateGroups) }.maxOrNull() ?: 0
        val all = state.messagesList.filter { peer(it) == id && it.type != 22 }.sortedWith(compareBy({ it.timestamp }, { it.id }))
        if(before == null) {
            advanceReadCursors(mapOf(id.toLong() to (all.maxOfOrNull { it.id } ?: 0L)))
        }
        val filtered = all.filter { before == null || it.id < before }; val items = filtered.takeLast(100)
        return PrivateMessagePage(items.map { PrivateMessageContent.message(it, read = it.fromUid.toString() == accountId && it.id <= watermark) }, filtered.size > items.size, items.firstOrNull()?.id)
    }
    private fun save(value: IM.Inbox) {
        val bytes = value.toByteArray(); require(bytes.size <= 16_777_216) { "私信缓存过大，请备份后处理" }
        file?.let {
            it.parentFile?.mkdirs()
            android.util.AtomicFile(it).apply {
                val output = startWrite()
                try { output.write(bytes); finishWrite(output) } catch(e: Exception) { failWrite(output); throw e }
            }
        }
        state = value
    }
    companion object {
        fun text(message: IM.ChatMessage) = when(message.type) { 1 -> message.content; 2 -> "[图片消息]"; 3 -> "[语音消息]"; 22 -> ""; else -> "[暂不支持的消息（类型${message.type}）]" }
    }
}
