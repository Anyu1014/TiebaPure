package dev.anyu.tiebapure

import org.junit.Assert.*
import org.junit.Test

class ComposerNavigationTest {
    private val forum = Route("forum", "测试")
    private val thread = Route("thread", "42", "原帖", "200")
    private val subposts = Route("subposts", "42", "测试", "200:1")

    @Test fun replyingFromSubpostsReturnsToItsExistingThreadAndDropsComposerLayers() {
        val stack = mutableListOf(forum, thread, subposts)
        assertEquals(thread, stack.completeComposer("42", true))
        assertEquals(listOf(forum), stack)
    }
    @Test fun replyFromAStandaloneSubpostKeepsThatScreenAsTheFallback() {
        val stack = mutableListOf(forum, subposts)
        assertEquals(subposts, stack.completeComposer("42", true))
        assertEquals(listOf(forum), stack)
    }
    @Test fun aNewThreadReplacesTheComposerInsteadOfLeavingItInBackHistory() {
        val stack = mutableListOf(forum)
        assertEquals(Route("thread", "99"), stack.completeComposer("99", false))
        assertEquals(listOf(forum), stack)
    }
    @Test fun aDetachedReplyDoesNotPopAnUnrelatedThread() {
        val other = Route("thread", "99")
        val stack = mutableListOf(forum, other)
        assertEquals(Route("thread", "42"), stack.completeComposer("42", true))
        assertEquals(listOf(forum, other), stack)
    }
}
