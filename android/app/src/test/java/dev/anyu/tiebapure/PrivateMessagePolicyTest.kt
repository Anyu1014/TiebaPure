package dev.anyu.tiebapure

import dev.anyu.tiebapure.official.OfficialIM as IM
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PrivateMessagePolicyTest {
    @Test fun newestConversationsAndMarkAllReadPreserveLaterMessages() {
        val inbox = OfficialPrivateInbox("84")
        fun message(id: Long, from: Long, to: Long, time: Int) = IM.ChatMessage.newBuilder()
            .setId(id).setFromUid(from).setToUid(to).setTimestamp(time).setType(1).build()
        fun merge(vararg items: IM.ChatMessage) {
            val response = IM.PullResponse.newBuilder().setError(IM.IMError.newBuilder().setCode(0))
                .setData(IM.PullResult.newBuilder().setPersonal(IM.UserMessages.newBuilder().addAllMessages(items.toList())))
                .build()
            inbox.merge(OfficialPrivateSnapshot(emptyList(), response))
        }
        merge(message(10, 42, 84, 100), message(11, 43, 84, 100), message(15, 44, 84, 90), message(99, 99, 1, 110))
        assertEquals(listOf("43", "42", "44"), inbox.conversations().items.map { it.userId })
        assertEquals(listOf(1, 1, 1), inbox.conversations().items.map { it.unread })
        inbox.markAllRead()
        assertEquals(listOf(0, 0, 0), inbox.conversations().items.map { it.unread })
        merge(message(16, 42, 84, 101))
        assertEquals(listOf("42", "43", "44"), inbox.conversations().items.map { it.userId })
        assertEquals(listOf(1, 0, 0), inbox.conversations().items.map { it.unread })
    }

    @Test fun officialSuffixedVoiceIdentifierReachesPlaybackUrl() {
        val fixture = JSONObject(java.io.File("../../protocol-fixtures/private-messages.json").readText()).getJSONObject("official_content")
        val id = fixture.getString("suffixed_voice_id")
        val message = IM.ChatMessage.newBuilder().setType(3).setContent(fixture.getString("suffixed_voice_content")).build()
        val row = PrivateMessageContent.message(message)
        assertEquals(id, row.voiceMD5)
        assertEquals(4, row.duration)
        assertTrue(PrivateMessageContent.voiceURL(checkNotNull(row.voiceMD5))!!.contains("voice_md5=$id&"))
        assertNull(PrivateMessageContent.voiceURL("$id&a=1"))
    }

    @Test fun peerReadUsesThePrivateStreamAndConversationRatherThanTheEventEnvelope() {
        val inbox = OfficialPrivateInbox("84")
        val group = IM.Group.newBuilder().setId(600).setType(6).build()
        fun outgoing(id: Long, peer: Long) = IM.ChatMessage.newBuilder().setId(id).setFromUid(84).setToUid(peer).setType(1).setContent("消息$id").build()
        fun event(id: Long, from: Long, to: Long, stream: Long) = IM.ChatMessage.newBuilder().setId(id).setFromUid(from).setToUid(to).setType(22)
            .setContent("""{"eventId":"22001","eventParam":{"groupId":$stream,"readMsgId":10}}""").build()
        fun merge(vararg messages: IM.ChatMessage) = inbox.merge(OfficialPrivateSnapshot(listOf(group), IM.PullResponse.newBuilder()
            .setError(IM.IMError.newBuilder().setCode(0)).setData(IM.PullResult.newBuilder().addGroups(IM.GroupMessages.newBuilder().setGroup(group).addAllMessages(messages.toList()))).build()))
        merge(outgoing(10,42), outgoing(11,42), outgoing(9,43), outgoing(8,44), outgoing(7,45), outgoing(6,46))
        // Opening a conversation only changes our local unread cursor.
        assertFalse(inbox.messages("42",null).items.any { it.read })
        merge(event(20,42,84,600), event(21,43,0,600), event(22,84,44,600), event(23,45,84,601), event(24,46,99,600))
        assertEquals(listOf(true,false),inbox.messages("42",null).items.map { it.read })
        assertTrue(inbox.messages("43",null).items.single().read)
        for (peer in listOf("44","45","46")) assertFalse(inbox.messages(peer,null).items.single().read)
        assertEquals(6,inbox.conversations().items.sumOf { inbox.messages(it.userId,null).items.size })
        merge()
        assertTrue(inbox.messages("42",null).items.first().read)
    }
    @Test fun officialReadEventsAreMetadataInsteadOfChatBubbles() {
        val inbox = OfficialPrivateInbox("84")
        val fixture = JSONObject(java.io.File("../../protocol-fixtures/private-messages.json").readText()).getJSONObject("official_content")
        val outgoing = IM.ChatMessage.parseFrom(hex(fixture.getString("outgoing_hex")))
        val event = IM.ChatMessage.parseFrom(hex(fixture.getString("read_event_hex")))
        val group = IM.Group.newBuilder().setId(600).setType(6).build()
        val response = IM.PullResponse.newBuilder().setError(IM.IMError.newBuilder().setCode(0)).setData(IM.PullResult.newBuilder().addGroups(IM.GroupMessages.newBuilder().setGroup(group).addMessages(outgoing).addMessages(event))).build()
        inbox.merge(OfficialPrivateSnapshot(listOf(group), response))
        assertEquals(listOf("已发送"), inbox.messages("42", null).items.map { it.text })
        assertTrue(inbox.messages("42", null).items.single().read)
        assertEquals(0, inbox.conversations().items.single().unread)
    }
    @Test fun peerReadMetadataSurvivesPaginationAndCannotCrossAccounts() {
        val group = IM.Group.newBuilder().setId(600).setType(6).build()
        val outgoing = (1L..150L).map { IM.ChatMessage.newBuilder().setId(it).setFromUid(84).setToUid(42).setType(1).setContent("消息$it").build() }
        val event = IM.ChatMessage.newBuilder().setId(200).setFromUid(42).setToUid(84).setType(22)
            .setContent("""{"eventId":22001,"eventParam":{"groupId":600,"readMsgId":130}}""").build()
        val response = IM.PullResponse.newBuilder().setError(IM.IMError.newBuilder().setCode(0))
            .setData(IM.PullResult.newBuilder().addGroups(IM.GroupMessages.newBuilder().setGroup(group).addAllMessages(outgoing).addMessages(event))).build()
        val inbox = OfficialPrivateInbox("84")
        inbox.merge(OfficialPrivateSnapshot(listOf(group),response))
        val latest = inbox.messages("42",null)
        assertEquals(100,latest.items.size)
        assertEquals(80,latest.items.count { it.read })
        assertTrue(latest.hasMore)
        val earlier = inbox.messages("42",latest.cursor)
        assertEquals(50,earlier.items.size)
        assertTrue(earlier.items.all { it.read })
        assertFalse(earlier.hasMore)
        val otherAccount = OfficialPrivateInbox("99")
        otherAccount.merge(OfficialPrivateSnapshot(listOf(group),response))
        assertTrue(otherAccount.conversations().items.isEmpty())
        assertTrue(otherAccount.messages("42",null).items.isEmpty())
    }
    @Test fun malformedOrForeignReadEventsDoNotMarkMessagesRead() {
        val event = IM.ChatMessage.newBuilder().setType(22).setGroupId(600)
        for (body in listOf("{}", "not json", """{"eventId":22001,"eventParam":{"groupId":601,"readMsgId":10}}""", """{"eventId":22002,"eventParam":{"groupId":600,"readMsgId":10}}""")) {
            assertNull(PrivateMessageContent.readWatermark(event.setContent(body).build(),setOf(600)))
        }
    }
    @Test fun mediaParsesOfficialArraysAndRejectsUntrustedUrls() {
        val image = """[{"src":"https://tiebapic.baidu.com/a.jpg","big_src":"http://tiebapic.baidu.com/original.jpg","type":"3","bsize":"100,200"}]"""
        assertEquals("https://tiebapic.baidu.com/original.jpg", PrivateMessageContent.imageURL(image))
        assertNull(PrivateMessageContent.imageURL("""[{"src":"https://evil.example/a.jpg"}]"""))
        val voice = """[{"voice_md5":"0123456789abcdef0123456789abcdef","during_time":4,"has_read":0}]"""
        assertEquals("0123456789abcdef0123456789abcdef", PrivateMessageContent.voiceMD5(voice))
        assertEquals(4, PrivateMessageContent.duration(voice))
        assertTrue(runCatching { PrivateMessageContent.validate(image, 2) }.isSuccess)
        assertTrue(runCatching { PrivateMessageContent.validate(voice, 3) }.isSuccess)
        assertTrue(runCatching { PrivateMessageContent.validate("[]", 2) }.isFailure)
    }
    private fun hex(s: String) = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    @Test fun independentOfficialWireAndCryptoFixture() {
        val fixture = JSONObject(java.io.File("../../protocol-fixtures/private-messages.json").readText()).getJSONObject("official_lcs")
        val key = OfficialPrivateTransport.derive(fixture.getString("secret"))
        assertArrayEquals(hex(fixture.getString("key_hex")),key)
        val wire = hex(fixture.getString("commit_hex")); val request = IM.CommitRequest.parseFrom(wire)
        assertEquals(42,request.data.toUid); assertEquals(1,request.data.msgType); assertEquals("测试",request.data.content)
        assertTrue(request.data.hasToUserType()); assertEquals(0,request.data.toUserType)
        assertArrayEquals(hex(fixture.getString("cipher_hex")),OfficialPrivateTransport.crypt(wire,key,true))
        assertArrayEquals(wire,OfficialPrivateTransport.crypt(hex(fixture.getString("cipher_hex")),key,false))
    }
    @Test fun initialSyncNeverUsesTheEmptySuccessZeroSentinel() {
        val group = IM.Group.newBuilder().setId(600).setType(6).build()
        for (cursor in listOf<Long?>(null,0,42)) {
            val old = cursor?.let { listOf(group.toBuilder().setLastMsgId(it).build()) }.orEmpty()
            val request = OfficialPrivateTransport.pullRequest("fixture",listOf(group),old)
            assertEquals(if(cursor == null || cursor == 0L) 1L else cursor,request.data.groupsList.single().lastMsgId)
            assertEquals("0",request.data.repair.followType)
        }
    }
    @Test fun inboxSeparatesPeersDeduplicatesTracksUnreadAndSurvivesEmptySync() {
        val inbox = OfficialPrivateInbox("84")
        fun message(id: Long, from: Long, to: Long) = IM.ChatMessage.newBuilder().setId(id).setFromUid(from).setToUid(to).setType(1).setContent("消息$id").setTimestamp(id.toInt()).setFromUser(IM.User.newBuilder().setId(from).setNickname("用户$from")).build()
        val group = IM.Group.newBuilder().setId(6).setType(6).build()
        val batch = IM.GroupMessages.newBuilder().setGroup(group).addAllMessages(listOf(message(1,42,84),message(2,43,84),message(3,42,99))).build()
        val response = IM.PullResponse.newBuilder().setError(IM.IMError.newBuilder().setCode(0)).setData(IM.PullResult.newBuilder().addGroups(batch)).build()
        repeat(2) { inbox.merge(OfficialPrivateSnapshot(listOf(group),response)) }
        assertEquals(2,inbox.conversations().items.size)
        assertEquals(1,inbox.conversations().items.first { it.userId == "42" }.unread)
        assertEquals(listOf("消息1"),inbox.messages("42",null).items.map { it.text })
        assertEquals(0,inbox.conversations().items.first { it.userId == "42" }.unread)
        assertEquals(listOf("消息2"),inbox.messages("43",null).items.map { it.text })
        inbox.merge(OfficialPrivateSnapshot(listOf(group),IM.PullResponse.newBuilder().setError(IM.IMError.newBuilder().setCode(0)).build()))
        assertEquals(2,inbox.conversations().items.size)
        assertEquals("42",PrivateConversation.recipient(User("42","昵称","头像"),"84").userId)
        assertTrue(runCatching { PrivateConversation.recipient(User("84","本人",""),"84") }.isFailure)
    }
}
