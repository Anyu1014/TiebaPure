package dev.anyu.tiebapure

import org.junit.Assert.*
import org.junit.Test

class InlineReplyDraftTest {
    @Test fun identitySurvivesDisplayChangesAndSeparatesEveryReplyTarget() {
        val main = InlineReplyTarget("test", "100")
        val post = main.copy(parent = "200", floor = 12, replyTo = "old name")
        val subpost = post.copy(subpost = "300")
        assertEquals(post.draftKey, post.copy(forum = "updated", floor = 13, replyTo = "new name").draftKey)
        assertEquals(4, setOf(main.draftKey, post.draftKey, subpost.draftKey, main.copy(thread = "101").draftKey).size)
        assertNotEquals(subpost.draftKey, subpost.copy(subpost = "301").draftKey)
    }
}
