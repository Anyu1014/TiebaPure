package dev.anyu.tiebapure

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AutoReplyEngineTest {
    private class MemoryRepo : AutoReplyRepository {
        var on = true
        val rule = AutoReplyRule(id = "rule", forumId = "7", forum = "测试", keywords = listOf("收物"), message = "有货#(呵呵)", delaySeconds = 30, revision = "rev")
        var configured = listOf(rule)
        val cursors = mutableMapOf<String, AutoReplyCursor>()
        val records = linkedMapOf<String, AutoReplyJob>()
        val deleted = mutableSetOf<String>()
        var failClaim = false
        override fun enabled(uid: String) = on
        override fun setEnabled(uid: String, enabled: Boolean) { on = enabled }
        override fun rules(uid: String) = configured
        override fun cursor(uid: String, ruleId: String) = cursors[ruleId]
        override fun saveCursor(uid: String, ruleId: String, cursor: AutoReplyCursor) { cursors[ruleId] = cursor }
        override fun jobs(uid: String) = records.values.toList()
        override fun wasDeleted(uid: String, threadId: String) = threadId in deleted
        override fun saveJob(uid: String, job: AutoReplyJob) { if (job.threadId in deleted) return; if (failClaim && job.status == AutoReplyStatus.SENDING) error("disk full"); records[job.threadId] = job }
        fun deleteJobs(ids: Set<String>) { deleted += ids.intersect(records.keys); ids.forEach { records.remove(it) } }
    }
    private class Fixture {
        val repo = MemoryRepo()
        val account = Account("99", "self", "fixture-bduss", "fixture-stoken", "fixture-tbs")
        var current: Account? = account
        var clock = 200L
        var followed = listOf(ForumEntry("7", "测试"))
        var pages = mapOf(1 to listOf(AutoReplyThread("10", "旧帖", "1", 100)))
        var body = "没有关键词"
        var bodyReads = 0
        var sends = 0
        var beforeFollowed: (() -> Unit)? = null
        var duringReply: (() -> Unit)? = null
        var sendError: Exception? = null
        val backend = object : AutoReplyBackend {
            override suspend fun followed(account: Account): List<ForumEntry> { beforeFollowed?.invoke(); return followed }
            override suspend fun threads(forum: String, page: Int) = pages[page].orEmpty()
            override suspend fun body(account: Account, thread: AutoReplyThread): String { bodyReads++; return body }
            override suspend fun reply(account: Account, job: AutoReplyJob) {
                assertEquals(AutoReplyStatus.SENDING, repo.records[job.threadId]?.status)
                sends++; duringReply?.invoke(); sendError?.let { throw it }
            }
        }
        val engine = AutoReplyEngine(repo, backend, { current }, { true }, { clock })
        suspend fun newPost(catchUp: Boolean = false, title: String = "收物", author: String = "1") {
            engine.scan(account, true)
            pages = mapOf(1 to listOf(AutoReplyThread("11", title, author, 101), AutoReplyThread("10", "旧帖", "1", 100)))
            engine.scan(account, catchUp)
        }
    }
    @Test fun firstSelectionOnlySeedsTheBaseline() = runBlocking {
        val f = Fixture(); f.pages = mapOf(1 to listOf(AutoReplyThread("10", "收物", "1", 100)))
        f.engine.scan(f.account, true)
        assertTrue(f.repo.records.isEmpty()); assertEquals(0, f.bodyReads)
        assertEquals(100L, f.repo.cursors["rule"]!!.watermark)
    }
    @Test fun selectedHistoryDeletionCancelsThePendingTaskAndPreventsRescanning() = runBlocking {
        val f = Fixture(); f.newPost(); val job = f.repo.records["11"]!!
        f.repo.records["12"] = job.copy(threadId = "12", status = AutoReplyStatus.REVIEW)
        f.clock = 300; f.beforeFollowed = { f.repo.deleteJobs(setOf("11")) }
        f.engine.sendDue(f.account)
        assertEquals(0, f.sends); assertFalse(f.repo.records.containsKey("11")); assertTrue(f.repo.records.containsKey("12"))
        f.beforeFollowed = null; f.repo.cursors["rule"] = AutoReplyCursor(100, setOf("10"))
        f.engine.scan(f.account, false)
        assertFalse(f.repo.records.containsKey("11")); assertEquals(0, f.bodyReads)
        f.repo.deleteJobs(f.repo.records.keys.toSet()); assertTrue(f.repo.records.isEmpty()); assertTrue(f.engine.pendingPrompts("99").isEmpty())
    }
    @Test fun aLateReceiptCannotRestoreDeletedHistory() = runBlocking {
        val f = Fixture(); f.newPost(); f.clock = 300
        f.duringReply = { f.repo.deleteJobs(setOf("11")) }
        f.engine.sendDue(f.account)
        assertEquals(1, f.sends); assertTrue(f.repo.records.isEmpty()); assertTrue(f.repo.wasDeleted("99", "11"))
        f.engine.sendDue(f.account); assertEquals(1, f.sends)
    }
    @Test fun onlineHitWaitsForDelayAndSendsExactlyOnce() = runBlocking {
        val f = Fixture(); f.newPost()
        assertEquals(AutoReplyStatus.QUEUED, f.repo.records["11"]!!.status)
        assertEquals(0, f.bodyReads)
        f.engine.sendDue(f.account); assertEquals(0, f.sends)
        f.clock = 230; f.engine.sendDue(f.account); f.engine.sendDue(f.account)
        assertEquals(1, f.sends); assertEquals(AutoReplyStatus.SENT, f.repo.records["11"]!!.status)
        f.engine.scan(f.account, false); assertEquals(1, f.repo.records.size)
    }
    @Test fun offlineHitNeedsExplicitConfirmationAndANewDelay() = runBlocking {
        val f = Fixture(); f.body = "这里有收物关键词"; f.newPost(true, "新标题")
        assertEquals(1, f.bodyReads); assertEquals(AutoReplyStatus.REVIEW, f.repo.records["11"]!!.status)
        f.clock = 500; f.engine.sendDue(f.account); assertEquals(0, f.sends)
        f.engine.review("99", "11", true); f.engine.sendDue(f.account); assertEquals(0, f.sends)
        f.clock = 530; f.engine.sendDue(f.account); assertEquals(1, f.sends)
    }
    @Test fun unmatchedAndOwnPostsNeverCreateATask() = runBlocking {
        val f = Fixture(); f.newPost(true, "普通标题"); assertTrue(f.repo.records.isEmpty())
        val own = Fixture(); own.newPost(false, "收物", "99"); assertTrue(own.repo.records.isEmpty()); assertEquals(0, own.bodyReads)
    }
    @Test fun ignoringACatchUpTaskNeverSendsIt() = runBlocking {
        val f = Fixture(); f.newPost(true); f.engine.review("99", "11", false); f.clock = 999
        f.engine.sendDue(f.account); assertEquals(0, f.sends); assertEquals(AutoReplyStatus.SKIPPED, f.repo.records["11"]!!.status)
    }
    @Test fun backgroundReviewPromptIsDurableAndDoesNotApproveSending() = runBlocking {
        val f = Fixture(); f.newPost(true)
        assertEquals(setOf("11"), f.engine.pendingPrompts("99"))
        f.engine.acknowledgePrompts("99", setOf("11"))
        f.repo.records["11"] = AutoReplyJob.parse(f.repo.records["11"]!!.json())
        f.engine.recover("99")
        assertTrue(f.engine.pendingPrompts("99").isEmpty())
        f.clock = 999; f.engine.sendDue(f.account); assertEquals(0, f.sends)
        f.engine.review("99", "11", true); f.engine.recover("99")
        assertEquals(setOf("11"), f.engine.pendingPrompts("99"))
    }
    @Test fun interruptedAndAmbiguousWritesNeverRetry() = runBlocking {
        val f = Fixture(); f.newPost(); f.clock = 300; f.sendError = OutcomeUnknown(); f.engine.sendDue(f.account)
        assertFalse(f.repo.on); assertEquals(AutoReplyStatus.UNKNOWN, f.repo.records["11"]!!.status)
        f.repo.on = true; f.engine.recover("99"); f.engine.sendDue(f.account); assertEquals(1, f.sends)
    }
    @Test fun recoveryConvertsQueuesToReviewAndClaimsToUnknown() = runBlocking {
        val f = Fixture(); f.newPost(); val job = f.repo.records["11"]!!
        f.repo.records["12"] = job.copy(threadId = "12", status = AutoReplyStatus.SENDING)
        f.engine.recover("99")
        assertEquals(AutoReplyStatus.REVIEW, f.repo.records["11"]!!.status)
        assertEquals(AutoReplyStatus.UNKNOWN, f.repo.records["12"]!!.status)
    }
    @Test fun changedSessionOrRuleCannotSendOldTasks() = runBlocking {
        val f = Fixture(); f.newPost(); f.clock = 300; f.beforeFollowed = { f.current = f.account.copy(stoken = "different") }
        f.engine.sendDue(f.account); assertEquals(0, f.sends)
        val changed = Fixture(); changed.newPost(); changed.clock = 300; changed.repo.configured = listOf(changed.repo.rule.copy(revision = "new"))
        changed.engine.sendDue(changed.account); assertEquals(AutoReplyStatus.SKIPPED, changed.repo.records["11"]!!.status)
    }
    @Test fun aJobMovedBackToReviewDuringPreflightCannotBeDispatched() = runBlocking {
        val f = Fixture(); f.newPost(); f.clock = 300; f.beforeFollowed = { f.engine.recover("99") }
        f.engine.sendDue(f.account); assertEquals(0, f.sends); assertEquals(AutoReplyStatus.REVIEW, f.repo.records["11"]!!.status)
    }
    @Test fun persistenceMustSucceedBeforeDispatch() = runBlocking {
        val f = Fixture(); f.newPost(); f.clock = 300; f.repo.failClaim = true
        assertTrue(runCatching { f.engine.sendDue(f.account) }.isFailure); assertEquals(0, f.sends)
    }
    @Test fun paginatedCatchUpResumesWithoutAdvancingOrDuplicating() = runBlocking {
        val f = Fixture(); f.engine.scan(f.account, true)
        f.pages = (1..4).associateWith { page -> if (page < 4) (1..20).map { n -> AutoReplyThread("${1000 - page * 20 - n}", "收物", "1", 200) } else listOf(AutoReplyThread("10", "旧帖", "1", 100)) }
        f.engine.scan(f.account, true)
        assertEquals(3, f.repo.cursors["rule"]!!.page); assertEquals(60, f.repo.records.size)
        assertEquals(100L, f.repo.cursors["rule"]!!.watermark)
        f.engine.scan(f.account, false)
        assertEquals(200L, f.repo.cursors["rule"]!!.watermark); assertEquals(60, f.repo.records.size)
        assertTrue(f.repo.records.values.all { it.status == AutoReplyStatus.REVIEW })
    }
    @Test fun ruleValidationAndJournalRoundTripsKeepLiteralMessages() {
        val f = Fixture(); val rule = f.repo.rule
        assertEquals(rule, AutoReplyRule.parse(rule.json()))
        assertEquals(listOf("收物", ".*"), AutoReplyRule.keywords(" 收物\n收物，.* "))
        assertTrue(runCatching { rule.copy(keywords = emptyList()).validate() }.isFailure)
        assertTrue(runCatching { rule.copy(delaySeconds = -1).validate() }.isFailure)
    }
    @Test fun shortPagesDoNotTerminateTheUnscannedGap() = runBlocking {
        val f = Fixture(); f.engine.scan(f.account, true)
        f.pages = mapOf(1 to listOf(AutoReplyThread("11", "收物", "1", 102)), 2 to listOf(AutoReplyThread("12", "收物", "1", 101)), 3 to listOf(AutoReplyThread("10", "旧帖", "1", 100)))
        f.engine.scan(f.account, true)
        assertEquals(setOf("11", "12"), f.repo.records.keys); assertEquals(102L, f.repo.cursors["rule"]!!.watermark)
    }
    @Test fun unfollowedQueuesCancelAndAFollowingGapRequiresReview() = runBlocking {
        val f = Fixture(); f.newPost(); f.clock = 300; f.followed = emptyList()
        f.engine.sendDue(f.account); assertEquals(0, f.sends); assertEquals(AutoReplyStatus.SKIPPED, f.repo.records["11"]!!.status)
        f.engine.scan(f.account, false)
        f.followed = listOf(ForumEntry("7", "测试"))
        f.pages = mapOf(1 to listOf(AutoReplyThread("12", "收物", "1", 102), AutoReplyThread("11", "收物", "1", 101)))
        f.engine.scan(f.account, false)
        assertEquals(AutoReplyStatus.REVIEW, f.repo.records["12"]!!.status)
    }
    @Test fun oldPinnedOnlyPagesDoNotTrapTheScanner() = runBlocking {
        val f = Fixture(); val pinned = AutoReplyThread("10", "旧置顶", "1", 100, true)
        f.pages = (1..9).associateWith { listOf(pinned) }; f.engine.scan(f.account, true); f.engine.scan(f.account, false)
        assertFalse(f.repo.cursors["rule"]!!.active); assertTrue(f.repo.records.isEmpty())
    }
}
