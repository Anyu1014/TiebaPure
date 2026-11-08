package dev.anyu.tiebapure

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import org.json.JSONObject
import androidx.compose.runtime.*
import kotlinx.coroutines.*

class AndroidAutoReplyRepository(private val store: LocalStore, private val changed: () -> Unit) : AutoReplyRepository {
    private val ruleCache = mutableMapOf<String, List<AutoReplyRule>>()
    private val jobCache = mutableMapOf<String, List<AutoReplyJob>>()
    private val deletedCache = mutableMapOf<String, Set<String>>()
    private fun key(uid: String, type: String, id: String = "") = "$uid:$type:$id"
    override fun enabled(uid: String) = store.prefs.getBoolean("auto-reply-enabled-$uid", false)
    override fun setEnabled(uid: String, enabled: Boolean) { check(store.prefs.edit().putBoolean("auto-reply-enabled-$uid", enabled).commit()); changed() }
    fun background(uid: String) = store.prefs.getBoolean("auto-reply-background-$uid", false)
    fun setBackground(uid: String, value: Boolean) { check(store.prefs.edit().putBoolean("auto-reply-background-$uid", value).commit()); changed() }
    @Synchronized override fun rules(uid: String) = ruleCache.getOrPut(uid) { store.list("automation").filter { it.first.startsWith(key(uid, "rule")) }.map { AutoReplyRule.parse(it.second) } }
    @Synchronized fun saveRule(uid: String, rule: AutoReplyRule) {
        rule.validate()
        require(rules(uid).count { it.id != rule.id } < 20) { "最多监测20个贴吧" }
        require(rules(uid).none { it.id != rule.id && it.forumId == rule.forumId }) { "该贴吧已有规则，请编辑原规则" }
        cancelRule(uid, rule.id)
        if (rules(uid).any { it.id == rule.id && it.forumId != rule.forumId }) store.remove("automation", key(uid, "cursor", rule.id))
        store.put("automation", key(uid, "rule", rule.id), rule.json())
        ruleCache[uid] = rules(uid).filterNot { it.id == rule.id } + rule; changed()
    }
    @Synchronized fun removeRule(uid: String, id: String) { cancelRule(uid, id); store.remove("automation", key(uid, "rule", id)); store.remove("automation", key(uid, "cursor", id)); ruleCache[uid] = rules(uid).filterNot { it.id == id }; changed() }
    private fun cancelRule(uid: String, id: String) {
        jobs(uid).filter { it.ruleId == id && it.status in setOf(AutoReplyStatus.QUEUED, AutoReplyStatus.REVIEW) }
            .forEach { saveJob(uid, it.copy(status = AutoReplyStatus.SKIPPED, note = "规则已修改或删除")) }
    }
    override fun cursor(uid: String, ruleId: String) = store.get("automation", key(uid, "cursor", ruleId))?.let(AutoReplyCursor::parse)
    override fun saveCursor(uid: String, ruleId: String, cursor: AutoReplyCursor) { store.put("automation", key(uid, "cursor", ruleId), cursor.json()) }
    @Synchronized override fun jobs(uid: String) = jobCache.getOrPut(uid) { store.list("automation").filter { it.first.startsWith(key(uid, "job")) }.map { AutoReplyJob.parse(it.second) }.sortedByDescending { it.discoveredAt } }
    private fun deleted(uid: String) = deletedCache.getOrPut(uid) { store.list("automation").filter { it.first.startsWith(key(uid, "deleted")) }.map { it.second.getString("threadId") }.toSet() }
    @Synchronized override fun wasDeleted(uid: String, threadId: String) = threadId in deleted(uid)
    @Synchronized override fun saveJob(uid: String, job: AutoReplyJob) {
        if (wasDeleted(uid, job.threadId)) return // A late receipt must not restore deleted history.
        store.put("automation", key(uid, "job", job.threadId), job.json()); jobCache[uid] = (jobs(uid).filterNot { it.threadId == job.threadId } + job).sortedByDescending { it.discoveredAt }; changed()
    }
    @Synchronized fun deleteJobs(uid: String, ids: Set<String>) {
        val existing = jobs(uid)
        val removed = existing.filter { it.threadId in ids }.map { it.threadId }.toSet()
        if (removed.isEmpty()) return
        val previousDeleted = deleted(uid)
        val db = store.writableDatabase
        db.beginTransaction()
        try {
            for (id in removed) {
                val marker = ContentValues().apply {
                    put("kind", "automation"); put("id", key(uid, "deleted", id))
                    put("data", JSONObject().put("threadId", id).toString()); put("updated", System.currentTimeMillis())
                }
                check(db.insertWithOnConflict("items", null, marker, SQLiteDatabase.CONFLICT_REPLACE) != -1L) { "删除历史记录失败" }
                db.delete("items", "kind=? AND id=?", arrayOf("automation", key(uid, "job", id)))
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        deletedCache[uid] = previousDeleted + removed
        jobCache[uid] = existing.filterNot { it.threadId in removed }; changed()
    }
}

class AutoReplyRuntime private constructor(context: Context) {
    private val app = context.applicationContext as Application
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val store = LocalStore(app)
    var revision by mutableIntStateOf(0); private set
    var status by mutableStateOf("自动回复未开启"); private set
    var foreground by mutableStateOf(false); private set
    var reviewEpoch by mutableIntStateOf(0); private set
    var isScanning by mutableStateOf(false); private set
    var readyForPrompts by mutableStateOf(false); private set
    var promptJobIds by mutableStateOf(emptySet<String>()); private set
    var serviceActive by mutableStateOf(false); private set
    val repository = AndroidAutoReplyRepository(store) { scope.launch { revision++ } }
    private val api = TiebaApi(store.prefs.getString("client-id", "automation-client")!!)
    private var controller: Job? = null
    private var session: Account? = null
    private var sessionTransition = false
    private var lastScan = 0L
    val engine = AutoReplyEngine(repository, object : AutoReplyBackend {
        override suspend fun followed(account: Account) = api.followedForums(account)
        override suspend fun threads(forum: String, page: Int) = api.automationThreads(forum, page)
        override suspend fun body(account: Account, thread: AutoReplyThread): String {
            val first = api.thread(thread.id, 1, account).posts.firstOrNull { it.floor == 1 } ?: error("原帖首楼暂不可读取")
            return first.blocks.joinToString("\n") { it.text }
        }
        override suspend fun reply(account: Account, job: AutoReplyJob) {
            check(account.sessionMatches(store.loadAccount())) { "账号已变化，自动回复已取消" }
            api.submit(account, job.forum, "", job.message, job.threadId, null, 1, null, emptyList())
        }
    }, { store.loadAccount() }, { store.prefs.getBoolean("allow-replies", true) })

    fun onForeground(value: Boolean) {
        foreground = value
        if (value) ensureBackgroundService()
        update(force = value && controller?.isActive == true && System.currentTimeMillis() - lastScan > 120000)
    }
    fun serviceChanged(value: Boolean) { serviceActive = value; update() }
    fun message(value: String) { status = value.take(240) }
    fun enabled(uid: String) = repository.enabled(uid)
    fun configureEnabled(uid: String, value: Boolean) {
        repository.setEnabled(uid, value)
        if (!value) app.stopService(Intent(app, AutoReplyService::class.java)) else ensureBackgroundService()
        update(force = true)
    }
    fun configureBackground(uid: String, value: Boolean) {
        repository.setBackground(uid, value)
        if (value) ensureBackgroundService() else app.stopService(Intent(app, AutoReplyService::class.java))
        update()
    }
    fun changed() { revision++; update(force = true) }
    fun accountChanged() { sessionTransition = false; update(force = true) }
    suspend fun stopForAccountChange() {
        sessionTransition = true
        app.stopService(Intent(app, AutoReplyService::class.java))
        controller?.cancelAndJoin(); controller = null; session = null
    }
    private fun ensureBackgroundService() {
        val account = runCatching { store.loadAccount() }.getOrNull() ?: return
        if (!foreground || !enabled(account.uid) || !repository.background(account.uid) || serviceActive) return
        if (android.os.Build.VERSION.SDK_INT >= 33 && androidx.core.content.ContextCompat.checkSelfPermission(app, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            status = "后台保活需通知权限；前台监测仍可使用"; return
        }
        try { androidx.core.content.ContextCompat.startForegroundService(app, Intent(app, AutoReplyService::class.java)) }
        catch (error: Exception) { status = "后台保活未能启动：${error.message?.take(100)}" }
    }
    private fun update(force: Boolean = false) {
        val account = runCatching { store.loadAccount() }.getOrNull()
        val allowed = !sessionTransition && account != null && enabled(account.uid) && (foreground || serviceActive)
        if (allowed && !force && controller?.isActive == true && account!!.sessionMatches(session)) return
        val previous = controller
        previous?.cancel()
        readyForPrompts = false; promptJobIds = emptySet()
        session = account
        controller = scope.launch {
            previous?.join()
            if (!allowed) { status = if (account == null) "请先登录" else "自动回复已暂停"; return@launch }
            reviewEpoch++
            try {
                withContext(Dispatchers.IO) { engine.recover(account.uid) }
                status = "等待界面加载，稍后检查新增帖子"
                delay(8000)
                coroutineScope {
                    launch {
                        var catchUp = true
                        while (isActive && enabled(account.uid)) {
                            try {
                                isScanning = true
                                withContext(Dispatchers.IO) { engine.scan(account, catchUp) }
                                lastScan = System.currentTimeMillis()
                                catchUp = false
                                status = "监测中 · 每60秒检查一次"
                            } catch (error: CancellationException) { throw error }
                            catch (error: Exception) { if (!catchUp) reviewEpoch++; catchUp = true; withContext(Dispatchers.IO) { engine.deferQueued(account.uid) }; status = "检查暂未完成：${error.message?.take(160)}" }
                            finally { isScanning = false }
                            promptJobIds = withContext(Dispatchers.IO) { engine.pendingPrompts(account.uid) }
                            readyForPrompts = true
                            delay(60000)
                        }
                    }
                    launch {
                        while (isActive && enabled(account.uid)) {
                            try { withContext(Dispatchers.IO) { engine.sendDue(account) }; if (!enabled(account.uid)) status = "自动回复已暂停，请检查历史任务" }
                            catch (error: CancellationException) { throw error }
                            catch (error: Exception) {
                                promptJobIds = withContext(Dispatchers.IO) { engine.deferQueued(account.uid); engine.pendingPrompts(account.uid) }
                                reviewEpoch++; readyForPrompts = true
                                status = "发送前检查失败，任务转为待确认：${error.message?.take(160)}"
                            }
                            delay(1000)
                        }
                        app.stopService(Intent(app, AutoReplyService::class.java))
                    }
                }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { runCatching { repository.setEnabled(account.uid, false) }; app.stopService(Intent(app, AutoReplyService::class.java)); status = "自动回复已暂停：${error.message?.take(160)}" }
        }
    }
    companion object {
        @Volatile private var instance: AutoReplyRuntime? = null
        fun get(context: Context) = instance ?: synchronized(this) { instance ?: AutoReplyRuntime(context).also { instance = it } }
    }
}
