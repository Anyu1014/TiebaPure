package dev.anyu.tiebapure

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ForumPresentationTest {
    private val api = TiebaApi("fixture")

    @Test fun forumRowsKeepAuthorIdentityAndUseTheSelectedTimestamp() {
        val row = JSONObject("""{"id":7,"author_id":42,"create_time":1700000000,"last_time_int":1700000600}""")
        val users = mapOf("42" to JSONObject("""{"id":42,"name_show":"作者甲","portrait":"author-portrait"}"""))
        val replied = api.jsonThread(row, "示例", users)
        assertEquals(User("42", "作者甲", "author-portrait"), replied.author)
        assertEquals(1700000600L, replied.timestamp)
        assertEquals(1700000000L, api.jsonThread(row, "示例", users, publishedFirst = true).timestamp)
        assertEquals(replied, threadFromJson(replied.json()))
        row.put("last_time_int", -1)
        assertEquals(1700000000L, api.jsonThread(row, "示例", users).timestamp)
        row.put("create_time", 0)
        assertEquals(0L, api.jsonThread(row, "示例", users).timestamp)
    }

    @Test fun photosFallbackToValidHttpsSourcesAndKeepTheirOrder() {
        val row = JSONObject("""{"id":7,"media":[
            {"type":3,"src_pic":"http://tiebapic.baidu.com/forum/one.jpg","big_pic":"https://tiebapic.baidu.com/forum/large.jpg"},
            {"type":3,"src_pic":"https://evil.invalid/blocked.jpg","big_pic":"https://imgsrc.baidu.com/forum/two.jpg"},
            {"type":3,"origin_pic":"http://tiebapic.baidu.com/forum/three.jpg"},
            {"type":5,"vsrc":"https://tiebapic.baidu.com/video.mp4"},
            {"src_pic":"file:///private/photo.jpg"}
        ]}""")
        val thread = api.jsonThread(row)
        assertEquals(listOf("https://tiebapic.baidu.com/forum/one.jpg", "https://imgsrc.baidu.com/forum/two.jpg", "https://tiebapic.baidu.com/forum/three.jpg"), thread.previews)
        assertEquals(thread, threadFromJson(thread.json()))
        assertTrue(api.jsonThread(JSONObject("""{"id":8}""")).previews.isEmpty())
    }

    @Test fun previewCountKeepsTheExistingHomeFeedLimit() {
        val row = JSONObject().put("id", 7).put("media", org.json.JSONArray().apply {
            repeat(12) { put(JSONObject().put("src_pic", "https://tiebapic.baidu.com/forum/$it.jpg")) }
        })
        assertEquals(9, api.jsonThread(row).previews.size)
    }

    @Test fun pinnedThreadsFoldWithoutRemovingRegularPostsAndExpandAheadOfThem() {
        val first = Thread("1", "regular one")
        val pinned = api.jsonThread(JSONObject("""{"id":2,"title":"pinned","is_top":1}"""))
        val last = Thread("3", "regular two")
        assertTrue(pinned.isTop)
        assertEquals(pinned, threadFromJson(pinned.json()))
        assertFalse(threadFromJson(JSONObject("""{"id":"old","title":"legacy","author":{}}""")).isTop)
        val collapsed = forumPinnedPresentation(listOf(first, pinned, last), false)
        assertEquals(listOf(pinned), collapsed.pinnedThreads)
        assertEquals(listOf(first, last), collapsed.visibleThreads)
        assertEquals(listOf(pinned, first, last), forumPinnedPresentation(listOf(first, pinned, last), true).visibleThreads)
        assertTrue(forumPinnedPresentation(listOf(first, last), false).pinnedThreads.isEmpty())
        assertEquals(listOf(first, last), forumPinnedPresentation(listOf(first, last), false).visibleThreads)
    }
}
