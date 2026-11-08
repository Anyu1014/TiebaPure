package dev.anyu.tiebapure

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class AutoReplyRule(val id: String = UUID.randomUUID().toString(), val forumId: String, val forum: String,
    val keywords: List<String>, val message: String, val delaySeconds: Int = 30, val enabled: Boolean = true,
    val revision: String = UUID.randomUUID().toString()) {
    fun validate() {
        Policy.signedId(forumId)
        require(forum.isNotBlank() && forum.length <= 100 && keywords.size in 1..30 && keywords.all { it.isNotBlank() && it.length <= 100 }) { "请填写关键词，每行一个，最多30个" }
        require(message.isNotBlank() && message.length <= 10000 && delaySeconds in 0..3600) { "回复限10000字，延迟为0至3600秒" }
    }
    fun json() = JSONObject().put("id", id).put("forumId", forumId).put("forum", forum).put("keywords", JSONArray(keywords)).put("message", message).put("delay", delaySeconds).put("enabled", enabled).put("revision", revision)
    companion object {
        fun parse(j: JSONObject) = AutoReplyRule(j.getString("id"), j.getString("forumId"), j.getString("forum"), j.getJSONArray("keywords").let { a -> (0 until a.length()).map { a.getString(it) } }, j.getString("message"), j.getInt("delay"), j.getBoolean("enabled"), j.getString("revision")).also { it.validate() }
        fun keywords(text: String) = text.split('\n', ',', '，').map(String::trim).filter(String::isNotEmpty).distinct()
    }
}

data class AutoReplyThread(val id: String, val title: String, val authorId: String, val createdAt: Long, val pinned: Boolean = false)
data class AutoReplyCursor(val watermark: Long, val known: Set<String>, val page: Int = 1, val active: Boolean = false,
    val catchUp: Boolean = true, val newest: Long = watermark, val head: Set<String> = emptySet(), val checked: Set<String> = emptySet()) {
    fun json() = JSONObject().put("watermark", watermark).put("known", JSONArray(known.toList())).put("page", page).put("active", active).put("catchUp", catchUp).put("newest", newest).put("head", JSONArray(head.toList())).put("checked", JSONArray(checked.toList()))
    companion object {
        fun parse(j: JSONObject): AutoReplyCursor {
            fun ids(key: String) = j.getJSONArray(key).let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }
            return AutoReplyCursor(j.getLong("watermark"), ids("known"), j.getInt("page"), j.getBoolean("active"), j.getBoolean("catchUp"), j.getLong("newest"), ids("head"), ids("checked"))
        }
    }
}

enum class AutoReplyStatus(val title: String) { REVIEW("待确认"), QUEUED("等待发送"), SENDING("正在发送"), SENT("已发送"), FAILED("发送失败"), UNKNOWN("结果待确认"), SKIPPED("已忽略") }
data class AutoReplyJob(val threadId: String, val title: String, val ruleId: String, val ruleRevision: String,
    val forumId: String, val forum: String, val keywords: List<String>, val message: String, val delaySeconds: Int,
    val discoveredAt: Long, val dueAt: Long, val status: AutoReplyStatus, val note: String = "", val reviewPrompted: Boolean = false) {
    fun json() = JSONObject().put("threadId", threadId).put("title", title).put("ruleId", ruleId).put("ruleRevision", ruleRevision).put("forumId", forumId).put("forum", forum).put("keywords", JSONArray(keywords)).put("message", message).put("delay", delaySeconds).put("discoveredAt", discoveredAt).put("dueAt", dueAt).put("status", status.name).put("note", note).put("reviewPrompted", reviewPrompted)
    companion object {
        fun parse(j: JSONObject) = AutoReplyJob(j.getString("threadId"), j.getString("title"), j.getString("ruleId"), j.getString("ruleRevision"), j.getString("forumId"), j.getString("forum"), j.getJSONArray("keywords").let { a -> (0 until a.length()).map { a.getString(it) } }, j.getString("message"), j.getInt("delay"), j.getLong("discoveredAt"), j.getLong("dueAt"), AutoReplyStatus.valueOf(j.getString("status")), j.optString("note"), j.optBoolean("reviewPrompted"))
    }
}

interface AutoReplyRepository {
    fun enabled(uid: String): Boolean
    fun setEnabled(uid: String, enabled: Boolean)
    fun rules(uid: String): List<AutoReplyRule>
    fun cursor(uid: String, ruleId: String): AutoReplyCursor?
    fun saveCursor(uid: String, ruleId: String, cursor: AutoReplyCursor)
    fun jobs(uid: String): List<AutoReplyJob>
    fun saveJob(uid: String, job: AutoReplyJob)
    fun wasDeleted(uid: String, threadId: String): Boolean
}

interface AutoReplyBackend {
    suspend fun followed(account: Account): List<ForumEntry>
    suspend fun threads(forum: String, page: Int): List<AutoReplyThread>
    suspend fun body(account: Account, thread: AutoReplyThread): String
    suspend fun reply(account: Account, job: AutoReplyJob)
}

/** Durable claims precede writes; a lost response is never automatically retried. */
class AutoReplyEngine(private val repo: AutoReplyRepository, private val api: AutoReplyBackend,
    private val currentAccount: suspend () -> Account?, private val repliesAllowed: () -> Boolean,
    private val now: () -> Long = { System.currentTimeMillis() / 1000 }) {
    private val scanLock = Mutex()
    private val sendLock = Mutex()

    fun deferQueued(uid: String) {
        repo.jobs(uid).filter { it.status == AutoReplyStatus.QUEUED }.forEach {
            repo.saveJob(uid, it.copy(status = AutoReplyStatus.REVIEW, note = "网络检查曾中断，继续回复前需要确认", reviewPrompted = false))
        }
    }

    fun recover(uid: String) {
        repo.jobs(uid).forEach { job ->
            when (job.status) {
                AutoReplyStatus.SENDING -> repo.saveJob(uid, job.copy(status = AutoReplyStatus.UNKNOWN, note = "上次发送中断，请打开原帖确认，不会自动重发"))
                AutoReplyStatus.QUEUED -> repo.saveJob(uid, job.copy(status = AutoReplyStatus.REVIEW, note = "监测曾中断，继续回复前需要确认", reviewPrompted = false))
                else -> Unit
            }
        }
        repo.rules(uid).forEach { rule -> repo.cursor(uid, rule.id)?.takeIf { it.active }?.let { repo.saveCursor(uid, rule.id, it.copy(catchUp = true)) } }
    }

    private suspend fun valid(account: Account, rule: AutoReplyRule? = null): Boolean =
        account.sessionMatches(currentAccount()) && repo.enabled(account.uid) &&
            (rule == null || repo.rules(account.uid).any { it.id == rule.id && it.revision == rule.revision && it.enabled })

    suspend fun scan(account: Account, catchUp: Boolean) = scanLock.withLock {
        if (!valid(account)) return@withLock
        val rules = repo.rules(account.uid).filter { it.enabled }
        if (rules.isEmpty()) return@withLock
        val followed = api.followed(account).map { it.id }.toSet()
        for (rule in rules) {
            if (!valid(account, rule)) break
            if (rule.forumId !in followed) {
                repo.cursor(account.uid, rule.id)?.let { old ->
                    repo.saveCursor(account.uid, rule.id, if (old.active) old.copy(catchUp = true)
                        else old.copy(active = true, catchUp = true, newest = old.watermark, page = 1, head = emptySet(), checked = emptySet()))
                }
                continue
            }
            scanRule(account, rule, catchUp)
        }
    }

    private suspend fun scanRule(account: Account, rule: AutoReplyRule, catchUp: Boolean) {
        var cursor = repo.cursor(account.uid, rule.id)
        if (cursor == null) {
            val rows = api.threads(rule.forum, 1)
            if (!valid(account, rule)) return
            repo.saveCursor(account.uid, rule.id, AutoReplyCursor(rows.maxOfOrNull { it.createdAt } ?: 0, rows.map { it.id }.toSet()))
            return // Initial selection establishes a baseline, never replies to historical posts.
        }
        if (!cursor.active) cursor = cursor.copy(active = true, catchUp = catchUp, newest = cursor.watermark, head = emptySet(), checked = emptySet(), page = 1)
        if (catchUp) cursor = cursor.copy(catchUp = true)
        val startPage = cursor.page
        val pages = if (startPage > 1) listOf(1, startPage, startPage + 1) else listOf(1, 2, 3)
        for (page in pages) {
            val rows = api.threads(rule.forum, page)
            if (!valid(account, rule)) return
            if (page == 1) cursor = cursor!!.copy(head = rows.map { it.id }.toSet())
            var boundary = rows.isEmpty() || rows.all { it.pinned && it.id in cursor!!.known }
            for (thread in rows) {
                val old = thread.id in cursor!!.known || (cursor!!.watermark > 0 && thread.createdAt in 1 until cursor!!.watermark)
                if (old) { if (!thread.pinned) boundary = true; continue }
                if (thread.id in cursor!!.checked) continue
                if (!repo.wasDeleted(account.uid, thread.id) && repo.jobs(account.uid).none { it.threadId == thread.id } && thread.authorId != account.uid) {
                    var hits = rule.keywords.filter { thread.title.contains(it, ignoreCase = true) }
                    if (hits.isEmpty()) {
                        val body = api.body(account, thread)
                        hits = rule.keywords.filter { body.contains(it, ignoreCase = true) }
                    }
                    if (!valid(account, rule)) return
                    if (hits.isNotEmpty()) {
                        check(repo.jobs(account.uid).size < 5000) { "自动回复历史达到5000条，已暂停扫描，请先检查任务记录" }
                        repo.saveJob(account.uid, AutoReplyJob(thread.id, thread.title, rule.id, rule.revision, rule.forumId, rule.forum, hits,
                            rule.message, rule.delaySeconds, now(), now() + rule.delaySeconds,
                            if (cursor!!.catchUp) AutoReplyStatus.REVIEW else AutoReplyStatus.QUEUED))
                    }
                }
                cursor = cursor!!.copy(checked = cursor!!.checked + thread.id, newest = maxOf(cursor!!.newest, thread.createdAt))
                check(cursor!!.checked.size <= 10000) { "补扫更新超过10000条，已暂停，缓存未前移" }
                repo.saveCursor(account.uid, rule.id, cursor!!)
            }
            if (boundary) {
                repo.saveCursor(account.uid, rule.id, AutoReplyCursor(cursor!!.newest, cursor!!.head))
                return
            }
            cursor = cursor!!.copy(page = if (page == 1 && startPage > 1) startPage else page + 1)
            repo.saveCursor(account.uid, rule.id, cursor!!)
        }
        cursor = cursor!!.copy(page = maxOf(1, cursor!!.page - 1))
        repo.saveCursor(account.uid, rule.id, cursor!!)
    }

    fun pendingPrompts(uid: String) = repo.jobs(uid).filter { it.status == AutoReplyStatus.REVIEW && !it.reviewPrompted }.map { it.threadId }.toSet()

    fun acknowledgePrompts(uid: String, ids: Set<String>) {
        repo.jobs(uid).filter { it.status == AutoReplyStatus.REVIEW && !it.reviewPrompted && it.threadId in ids }
            .forEach { repo.saveJob(uid, it.copy(reviewPrompted = true)) }
    }

    fun review(uid: String, id: String, approve: Boolean) {
        val job = repo.jobs(uid).firstOrNull { it.threadId == id && it.status == AutoReplyStatus.REVIEW } ?: return
        val rule = repo.rules(uid).firstOrNull { it.id == job.ruleId && it.enabled && it.revision == job.ruleRevision }
        repo.saveJob(uid, if (approve && repo.enabled(uid) && rule != null) job.copy(status = AutoReplyStatus.QUEUED, dueAt = now() + job.delaySeconds, note = "补扫任务已由用户确认")
            else job.copy(status = AutoReplyStatus.SKIPPED, note = if (approve) "规则已修改或关闭" else "用户忽略"))
    }

    suspend fun sendDue(account: Account) = sendLock.withLock {
        if (!valid(account) || !repliesAllowed()) return@withLock
        val job = repo.jobs(account.uid).filter { it.status == AutoReplyStatus.QUEUED && it.dueAt <= now() }.minByOrNull { it.dueAt } ?: return@withLock
        val rule = repo.rules(account.uid).firstOrNull { it.id == job.ruleId && it.revision == job.ruleRevision && it.enabled }
        if (rule == null) { repo.saveJob(account.uid, job.copy(status = AutoReplyStatus.SKIPPED, note = "规则已修改或关闭")); return@withLock }
        if (api.followed(account).none { it.id == job.forumId }) {
            repo.saveJob(account.uid, job.copy(status = AutoReplyStatus.SKIPPED, note = "已不再关注该吧，本任务已取消")); return@withLock
        }
        if (!valid(account, rule)) return@withLock
        currentCoroutineContext().ensureActive()
        val latest = repo.jobs(account.uid).firstOrNull { it.threadId == job.threadId }
        if (latest?.status != AutoReplyStatus.QUEUED || latest.dueAt != job.dueAt) return@withLock
        repo.saveJob(account.uid, job.copy(status = AutoReplyStatus.SENDING))
        if (repo.wasDeleted(account.uid, job.threadId)) return@withLock
        var confirmed = false
        try {
            api.reply(account, job)
            confirmed = true
            repo.saveJob(account.uid, job.copy(status = AutoReplyStatus.SENT, note = "贴吧已确认发送成功"))
        } catch (error: Exception) {
            val unknown = confirmed || error is OutcomeUnknown || error is CancellationException
            repo.saveJob(account.uid, job.copy(status = if (unknown) AutoReplyStatus.UNKNOWN else AutoReplyStatus.FAILED,
                note = if (unknown) "发送结果无法确认，请打开原帖检查，不会自动重发" else (error.message ?: "发送失败").take(240)))
            repo.setEnabled(account.uid, false)
            if (error is CancellationException) throw error
        }
    }
}
