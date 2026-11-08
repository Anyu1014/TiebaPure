package dev.anyu.tiebapure

import java.time.Instant
import java.time.ZoneId
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class MessageFeedTest {
    @Test fun unreadCountsAndViewedHeadsMatchTheExistingFeedFields() {
        val replies = MessageFeed.decode(JSONObject("""
            {"reply_list":[{"thread_id":"42","post_id":"8","time":"200","unread":"1"},
                           {"thread_id":"42","post_id":"7","time":"100","unread":false}],
             "message":{"replyme":"1","atme":2}}
        """.trimIndent()), at = false)
        assertEquals(1, replies.unreadCount)
        assertEquals(2, replies.otherUnreadCount)
        assertEquals(listOf(true, false), replies.items.map { it.unread })
        assertTrue(replies.hasUnread(null, null))
        assertFalse(replies.hasUnread(replies.head, null))

        val mentions = MessageFeed.decode(JSONObject("""
            {"reply_list":[],"at_list":[{"thread_id":43,"post_id":9,"time":200},
                        {"thread_id":43,"post_id":7,"time":100}]}
        """.trimIndent()), at = true)
        assertFalse(mentions.hasUnread(null, null))
        assertTrue(mentions.hasUnread(null, replies.otherUnreadCount))
        assertTrue(mentions.hasUnread(mentions.items.last().id, null))
        assertFalse(mentions.hasUnread(mentions.head, replies.otherUnreadCount))
    }

    @Test fun privateConversationTimeUsesLocalDayAndMondayBoundary() {
        val zone = ZoneId.of("Asia/Shanghai")
        fun label(date: String, now: String = "2026-11-06T16:05:00Z") =
            privateConversationDateText(Instant.parse(date).epochSecond, Instant.parse(now).epochSecond, zone)
        assertEquals("00:04", label("2026-11-06T16:04:00Z"))
        assertEquals("昨天 23:59", label("2026-11-06T15:59:00Z"))
        assertEquals("星期四", label("2026-11-05T01:04:00Z"))
        assertEquals("星期一", label("2026-11-02T01:04:00Z"))
        assertEquals("11月1日", label("2026-11-01T01:04:00Z"))
        assertEquals("昨天 23:59", label("2026-11-08T15:59:00Z", now = "2026-11-08T16:05:00Z"))
        assertEquals("12月31日", label("2025-12-31T01:04:00Z"))
        assertEquals("", privateConversationDateText(0))
    }
}
