package dev.anyu.tiebapure

import android.app.Application
import android.webkit.CookieManager
import android.webkit.WebStorage
import androidx.compose.runtime.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class ScreenData(val threads: List<Thread> = emptyList(), val thread: ThreadPage? = null, val posts: List<Post> = emptyList(), val forums: List<ForumEntry> = emptyList(), val profile: Profile? = null, val users: List<User> = emptyList(), val hasMore: Boolean = false, val likes: AccountLikesPage? = null, val replyPage: Int? = null)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val store = LocalStore(app)
    val api = TiebaApi(
        store.prefs.getString("client-id", null) ?: UUID.randomUUID().toString().replace("-", "").also { store.prefs.edit().putString("client-id", it).apply() },
        app.resources.displayMetrics.widthPixels, app.resources.displayMetrics.heightPixels, app.resources.displayMetrics.density.toDouble(),
        android.os.Build.VERSION.RELEASE, android.os.Build.MODEL, android.os.Build.MANUFACTURER, privateDirectory = app.filesDir.resolve("official-private-messages")
    )
    val offline = OfflineLibrary(app, api, store)
    val forumAvatars = mutableStateMapOf<String, String>()
    private val forumAvatarPermits = Semaphore(3)
    suspend fun ensureForumAvatar(name: String) {
        if (name.isBlank() || forumAvatars.containsKey(name)) return
        try {
            forumAvatarPermits.withPermit {
                if (!forumAvatars.containsKey(name)) {
                    val info = withContext(Dispatchers.IO) { api.forumInfo(name) }
                    val avatar = Policy.avatar(info.avatar) ?: return@withPermit
                    forumAvatars[name] = avatar
                    withContext(Dispatchers.IO) { store.updateForumMetadata(name, info) }
                }
            }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { /* Keep the initial visible; a later visit retries. */ }
    }
    val fontStore = FontStore(app)
    var readerTypeface by mutableStateOf(fontStore.typeface(fontStore.selected())); private set
    var fonts by mutableStateOf(fontStore.list()); private set
    var pendingExport: PreparedExport? = null
    var account by mutableStateOf<Account?>(null); private set
    var route by mutableStateOf(Route()); private set
    var data by mutableStateOf(ScreenData()); private set
    var contextThreads by mutableStateOf<List<Thread>>(emptyList()); private set
    var loading by mutableStateOf(false); private set
    var refreshing by mutableStateOf(false); private set
    var commentsRefreshing by mutableStateOf(false); private set
    // shortcut: Input-bar drafts last for this app session; add separate persistence if restart recovery is requested.
    internal val inlineReplyDrafts = mutableStateMapOf<String, InlineReplyDraft>()
    internal var inlineReplyTarget by mutableStateOf<InlineReplyTarget?>(null); private set
    internal var inlineReplyFocusRequest by mutableIntStateOf(0); private set
    private var inlineReplyFocusHandled = 0
    internal fun takeInlineReplyFocusRequest(): Boolean {
        if (inlineReplyFocusRequest <= inlineReplyFocusHandled) return false
        inlineReplyFocusHandled = inlineReplyFocusRequest; return true
    }
    internal fun resetInlineReplyTarget() { inlineReplyTarget = null; inlineReplyFocusRequest++ }
    var submitting by mutableStateOf(false); private set
    var saving by mutableStateOf(false); private set
    var saveProgress by mutableStateOf(""); private set
    var signing by mutableStateOf(false); private set
    var error by mutableStateOf(""); private set
    var notice by mutableStateOf(""); private set
    var page by mutableIntStateOf(1); private set
    var postPages by mutableStateOf<Map<String, Int>>(emptyMap()); private set
    private var retryPage = 1
    private var retryAppend = false
    private var retryCommentsOnly = false
    fun retryLoad() = load(retryPage, retryAppend, retryCommentsOnly)
    fun loadNextPage() {
        if (!loading && error.isBlank() && data.hasMore) load(page + 1, append = true)
    }
    var sort by mutableIntStateOf(0)
    var defaultReplySort by mutableIntStateOf(store.prefs.getInt("reader-reply-sort", 2).coerceIn(0, 2)); private set
    fun selectDefaultReplySort(value: Int) { require(value in 0..2); store.prefs.edit().putInt("reader-reply-sort", value).apply(); defaultReplySort = value }
    var onlyAuthor by mutableStateOf(false)
    var darkMode by mutableStateOf(store.prefs.getString("theme", "system") ?: "system")
    var fontSize by mutableFloatStateOf(store.prefs.getFloat("font-size", 17f))
    var manualMedia by mutableStateOf(store.prefs.getBoolean("manual-media", false))
    var bottomBarPosition by mutableStateOf(BottomBarPosition.stored(store.prefs.getString("bottom-bar-position", null))); private set
    var bottomBarShowsLabels by mutableStateOf(store.prefs.getBoolean("bottom-bar-labels", true)); private set
    var bottomBarAutoCollapse by mutableStateOf(store.prefs.getBoolean("bottom-bar-auto-collapse", true)); private set
    fun setBottomBarPreferences(position: BottomBarPosition = bottomBarPosition, showsLabels: Boolean = bottomBarShowsLabels, autoCollapse: Boolean = bottomBarAutoCollapse) {
        store.prefs.edit().putString("bottom-bar-position", position.key).putBoolean("bottom-bar-labels", showsLabels).putBoolean("bottom-bar-auto-collapse", autoCollapse).apply()
        bottomBarPosition = position; bottomBarShowsLabels = showsLabels; bottomBarAutoCollapse = autoCollapse
    }
    var newThreadsEnabled by mutableStateOf(store.prefs.getBoolean("allow-new-threads", true)); private set
    var repliesEnabled by mutableStateOf(store.prefs.getBoolean("allow-replies", true)); private set
    var likesEnabled by mutableStateOf(store.prefs.getBoolean("allow-likes", true)); private set
    fun setContentPreferences(newThreads: Boolean = newThreadsEnabled, replies: Boolean = repliesEnabled, likes: Boolean = likesEnabled) {
        store.prefs.edit().putBoolean("allow-new-threads", newThreads).putBoolean("allow-replies", replies).putBoolean("allow-likes", likes).apply()
        newThreadsEnabled = newThreads; repliesEnabled = replies; likesEnabled = likes
    }

    private val stack = mutableListOf<Route>()
    private var loadJob: Job? = null
    private var saveJob: Job? = null
    private val actions = mutableSetOf<Job>()
    private var generation = 0
    private var accountGeneration = 0
    private var loginJob: Job? = null
    private data class HomeSnapshot(val data: ScreenData, val page: Int, val error: String, val retryPage: Int, val retryAppend: Boolean)
    private var homeSnapshot: HomeSnapshot? = null
    private val listSnapshots = linkedMapOf<Route, HomeSnapshot>()
    private data class ReaderScroll(val route: Route, val state: LazyListState)
    private data class ReaderSnapshot(val data: ScreenData, val page: Int, val postPages: Map<String, Int>, val sort: Int, val onlyAuthor: Boolean, val error: String, val retryPage: Int, val retryAppend: Boolean, val retryCommentsOnly: Boolean, val scroll: ReaderScroll?)
    private val latestThreadCounters = linkedMapOf<String, Thread>()
    private val readerSnapshots = linkedMapOf<Route, ReaderSnapshot>()
    private var readerScroll: ReaderScroll? = null

    internal fun readerListState(initialIndex: Int, initialOffset: Int): LazyListState {
        val previous = readerScroll
        if (previous != null && previous.route == route) return previous.state
        val state = if (previous == null) LazyListState(initialIndex, initialOffset) else LazyListState()
        readerScroll = ReaderScroll(route, state)
        return state
    }

    private fun updateThreadCounters(thread: Thread) {
        latestThreadCounters.remove(thread.id)
        latestThreadCounters[thread.id] = thread
        if (latestThreadCounters.size > 256) latestThreadCounters.remove(latestThreadCounters.keys.first())
        data = data.withThreadCounters(thread)
        contextThreads = contextThreads.map { if (it.id == thread.id) it.copy(replies = thread.replies, likes = thread.likes) else it }
        homeSnapshot = homeSnapshot?.let { it.copy(data = it.data.withThreadCounters(thread)) }
        listSnapshots.replaceAll { _, snapshot -> snapshot.copy(data = snapshot.data.withThreadCounters(thread)) }
        readerSnapshots.replaceAll { _, snapshot -> snapshot.copy(data = snapshot.data.withThreadCounters(thread)) }
    }

    fun applyPostLike(threadID: String, postID: String, mainPost: Boolean, liked: Boolean) {
        if (route.id != threadID || route.kind !in setOf("thread", "subposts")) return
        val post = data.posts.firstOrNull { it.id == postID } ?: return
        val count = (if (mainPost) data.thread?.thread?.likes ?: post.likes else post.likes)?.let { (it + if (post.liked == liked) 0 else if (liked) 1 else -1).coerceAtLeast(0) }
        data = data.copy(posts = data.posts.map { if (it.id == postID) it.copy(likes = count, liked = liked) else it })
        if (mainPost) data.thread?.thread?.copy(likes = count)?.let(::updateThreadCounters)
    }

    private fun rememberReader() {
        if (route.kind !in setOf("thread", "subposts")) return
        readerSnapshots.remove(route)
        readerSnapshots[route] = ReaderSnapshot(data, page, postPages, sort, onlyAuthor, error, retryPage, retryAppend, retryCommentsOnly, readerScroll)
        if (readerSnapshots.size > 8) readerSnapshots.remove(readerSnapshots.keys.first())
    }

    init {
        runCatching { store.loadAccount() }.onSuccess { account = it }.onFailure { error = "本机登录资料无法解密，请重新登录" }
        load()
    }
    fun report(message: String) { error = message.take(300) }
    fun inform(message: String) { notice = message }
    fun refreshFonts() { fonts = fontStore.list(); readerTypeface = fontStore.typeface(fontStore.selected()) }
    fun clearNotice() { notice = "" }
    private fun rememberList() {
        if (route.kind !in setOf("forum", "likes") || loading && data.threads.isEmpty() && data.likes?.items.isNullOrEmpty()) return
        listSnapshots.remove(route)
        listSnapshots[route] = HomeSnapshot(data, page, error, retryPage, retryAppend)
        if (listSnapshots.size > 8) listSnapshots.remove(listSnapshots.keys.first())
    }
    fun selectLikesTab(tab: Int) {
        require(tab in 0..2)
        if (route.kind != "likes" || (route.extra.toIntOrNull() ?: 0) == tab) return
        rememberList()
        showRoute(route.copy(extra = tab.toString()))
    }
    fun navigate(next: Route, root: Boolean = false) {
        rememberReader()
        if (route.kind == "home" && (!loading || data.threads.isNotEmpty())) homeSnapshot = HomeSnapshot(data, page, error, retryPage, retryAppend)
        rememberList()
        if (next.kind == "forum" && next.id.trim().length in 1..100) store.put("forum", next.id, (store.get("forum", next.id) ?: JSONObject()).put("name", next.id))
        if (next.kind == "thread" && data.threads.isNotEmpty()) contextThreads = data.threads
        if (root) contextThreads = emptyList()
        if (root) stack.clear() else if (!(route.kind in setOf("search", "searchinput") && next.kind in setOf("search", "searchinput"))) stack.add(route)
        showRoute(next)
    }
    private fun showRoute(next: Route, restoreReader: Boolean = false) {
        route = next
        readerScroll = null
        if (restoreReader) readerSnapshots[next]?.let { cached ->
            loadJob?.cancel(); generation++
            data = cached.data; page = cached.page; postPages = cached.postPages; sort = cached.sort; onlyAuthor = cached.onlyAuthor
            error = cached.error; retryPage = cached.retryPage; retryAppend = cached.retryAppend; retryCommentsOnly = cached.retryCommentsOnly; readerScroll = cached.scroll
            loading = false; refreshing = false; commentsRefreshing = false
            return
        }
        val snapshot = if (next.kind == "home") homeSnapshot else if (next.kind in setOf("forum", "likes")) listSnapshots[next] else null
        snapshot?.let { cached ->
            loadJob?.cancel(); generation++
            data = cached.data; page = cached.page; error = cached.error; loading = false; refreshing = false; commentsRefreshing = false
            sort = if (next.extra == "publish") 1 else 0; onlyAuthor = false; postPages = emptyMap()
            retryPage = cached.retryPage; retryAppend = cached.retryAppend; retryCommentsOnly = false
            return
        }
        val position = if (next.kind == "thread" && next.extra.isBlank()) store.get("position", next.id) else null
        page = position?.optInt("page", 1)?.coerceAtLeast(1) ?: 1
        sort = if (next.kind == "forum" && next.extra == "publish") 1 else if (next.kind == "thread" && next.extra.isBlank()) position?.optInt("sort", 0)?.coerceIn(0, 2) ?: defaultReplySort else 0
        onlyAuthor = position?.optBoolean("only-author", false) ?: false
        data = ScreenData()
        load()
    }
    fun back(): Boolean {
        if (stack.isEmpty()) return false
        showRoute(stack.removeAt(stack.lastIndex), restoreReader = true)
        return true
    }
    fun selectForumCategory(value: Int) {
        require(value in -1..1)
        if (route.kind != "forum") return
        val extra = when (value) { -1 -> "good"; 1 -> "publish"; else -> "" }
        if (route.extra == extra) return
        route = route.copy(extra = extra)
        sort = if (value == 1) 1 else 0
        page = 1; data = ScreenData()
        load(1)
    }
    private var messageBadgeAccount by mutableStateOf<Account?>(null)
    private var unreadMessageTabs by mutableStateOf(emptySet<String>())
    private val companionUnreadCounts = mutableMapOf<String, Int>()

    internal fun hasUnreadMessages(tab: String? = null): Boolean =
        messageBadgeAccount?.sessionMatches(account) == true &&
            if (tab == null) unreadMessageTabs.isNotEmpty() else tab in unreadMessageTabs

    private fun prepareMessageBadges(session: Account) {
        if (!session.sessionMatches(messageBadgeAccount)) {
            unreadMessageTabs = emptySet()
            companionUnreadCounts.clear()
            messageBadgeAccount = session
        }
    }

    private fun setMessageBadge(tab: String, unread: Boolean) {
        unreadMessageTabs = if (unread) unreadMessageTabs + tab else unreadMessageTabs - tab
    }

    private fun viewedMessageKey(session: Account, tab: String) =
        "message-viewed-head.${session.uid}.${if (tab == "at") "at" else "reply"}"

    private fun didViewMessageFeed(session: Account, tab: String, feed: MessageFeed) {
        prepareMessageBadges(session)
        val otherTab = if (tab == "at") "" else "at"
        val count = feed.otherUnreadCount
        if (count != null) companionUnreadCounts[otherTab] = count else companionUnreadCounts.remove(otherTab)
        val key = viewedMessageKey(session, tab)
        if (store.prefs.getString(key, null) != feed.head) store.prefs.edit().putString(key, feed.head).apply()
        setMessageBadge(tab, false)
    }

    internal fun updatePrivateUnread(session: Account, unread: Boolean) {
        if (!session.sessionMatches(account)) return
        prepareMessageBadges(session)
        setMessageBadge("private", unread)
    }

    internal suspend fun refreshMessageBadges(session: Account, viewing: String?) = coroutineScope {
        suspend fun <T> fetch(block: suspend () -> T): T? {
            return try { withContext(Dispatchers.IO) { block() } }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { null }
        }
        val replyFeed = async { if (viewing == "") null else fetch { api.messages(session, false, 1) } }
        val mentionFeed = async { if (viewing == "at") null else fetch { api.messages(session, true, 1) } }
        launch {
            fetch { api.cachedPrivateUnread(session) }?.let { updatePrivateUnread(session, it) }
            if (viewing != "private") fetch { api.privateConversations(session) }?.let { page ->
                updatePrivateUnread(session, page.items.any { it.unread > 0 })
            }
        }
        val reply = replyFeed.await()
        val mention = mentionFeed.await()
        if (session.sessionMatches(account)) {
            prepareMessageBadges(session)
            if (reply != null) {
                val viewed = store.prefs.getString(viewedMessageKey(session, ""), null)
                setMessageBadge("", reply.hasUnread(viewed, mention?.otherUnreadCount ?: companionUnreadCounts[""]))
            }
            if (mention != null) {
                val viewed = store.prefs.getString(viewedMessageKey(session, "at"), null)
                setMessageBadge("at", mention.hasUnread(viewed, reply?.otherUnreadCount ?: companionUnreadCounts["at"]))
            }
        }
    }

    fun selectMessageTab(value: String) {
        require(value in setOf("", "at", "private"))
        if (route.kind != "messages" || route.extra == value) return
        route = route.copy(extra = value)
        data = ScreenData()
        load(1)
    }
    fun selectReplies(order: Int = sort, authorOnly: Boolean = onlyAuthor) {
        require(order in 0..2)
        if (route.kind != "thread" || order == sort && authorOnly == onlyAuthor) return
        sort = order; onlyAuthor = authorOnly
        load(1, commentsOnly = true)
    }

    fun load(requestedPage: Int = page, append: Boolean = false, commentsOnly: Boolean = false) {
        loadJob?.cancel()
        retryPage = requestedPage; retryAppend = append; retryCommentsOnly = commentsOnly
        val current = ++generation
        val r = route; val a = account; val order = sort; val lz = onlyAuthor
        val descendingTotalPages = if (r.kind == "thread" && order == 1 && append) data.thread?.pages else null
        commentsRefreshing = commentsOnly
        refreshing = !commentsOnly && !append && requestedPage == 1 && (data.threads.isNotEmpty() || data.posts.isNotEmpty() || data.likes?.items?.isNotEmpty() == true)
        loading = true; error = ""
        loadJob = viewModelScope.launch {
            try {
                var messageFeed: MessageFeed? = null
                val result = withContext(Dispatchers.IO) {
                    when (r.kind) {
                        "home" -> ScreenData(threads = api.home(a, requestedPage))
                        "forum" -> ScreenData(threads = api.forum(r.id, requestedPage, if (r.extra == "publish") 1 else 0, r.extra == "good"))
                        "search" -> ScreenData(threads = api.search(r.id, requestedPage, r.extra))
                        "thread" -> {
                            val p = api.thread(r.id, requestedPage, a, order == 1, lz, r.extra.takeIf { !commentsOnly && it.toLongOrNull()?.let { id -> id > 0 } == true }, hot = order == 2, descendingTotalPages = descendingTotalPages)
                            store.put("history", r.id, p.thread.json())
                            val position = store.get("position", r.id)?.takeIf { it.optInt("page") == p.page } ?: JSONObject()
                            if (!commentsOnly && r.extra.isNotBlank() && requestedPage <= 1) position.put("index", (p.posts.indexOfFirst { it.id == r.extra } + 1).coerceAtLeast(0)).put("offset", 0)
                            store.put("position", r.id, position.put("page", p.page).put("sort", order).put("only-author", lz))
                            ScreenData(thread = p, posts = p.posts, hasMore = p.page < p.pages)
                        }
                        "subposts" -> {
                            val target = r.extra.split(':')
                            api.subpostsPage(r.id, target[0], target.getOrElse(1) { "" }, requestedPage, a, target.getOrNull(2)).let {
                                ScreenData(posts = it.posts, hasMore = it.hasMore, replyPage = it.page)
                            }
                        }
                        "likes" -> if (a == null) ScreenData() else api.accountLikes(a, requestedPage, r.extra.toIntOrNull() ?: 0).let { ScreenData(likes = it, hasMore = it.hasMore) }
                        "forums" -> {
                            ScreenData(forums = if (a != null) api.followedForums(a) else emptyList())
                        }
                        "history", "saved" -> ScreenData(threads = store.list(r.kind).map { threadFromJson(it.second) })
                        "favorites" -> ScreenData(threads = api.favorites(a ?: error("请先登录"), requestedPage))
                        "messages" -> if (r.extra == "private" || a == null) ScreenData() else {
                            val feed = api.messages(a, r.extra == "at", requestedPage)
                            messageFeed = feed
                            ScreenData(threads = feed.items.map { it.thread })
                        }
                        "me" -> if (a == null) ScreenData() else ScreenData(profile = api.profile(a.uid, a))
                        "profile", "editprofile" -> ScreenData(profile = api.profile(r.id, a))
                        "userthreads" -> ScreenData(threads = api.userThreads(r.id, a, requestedPage))
                        "following", "followers" -> ScreenData(users = api.relationships(r.id, a, r.kind == "followers", requestedPage))
                        else -> ScreenData()
                    }.let { result ->
                        val more = when (r.kind) {
                            "home", "forum", "search", "favorites", "messages", "userthreads" -> result.threads.isNotEmpty()
                            "following", "followers" -> result.users.isNotEmpty()
                            else -> result.hasMore
                        }
                        result.copy(hasMore = more, threads = result.threads.filterNot(store::blocked).distinctBy { it.id to it.matchedPost }, users = result.users.distinctBy { it.id }, posts = result.posts.filterNot { post -> store.blocked(Thread(post.id, post.blocks.joinToString("") { it.text }, result.thread?.thread?.forum ?: r.title, post.author)) }.map { post -> post.copy(children = post.children.filterNot { child -> store.blocked(Thread(child.id, child.blocks.joinToString("") { it.text }, result.thread?.thread?.forum ?: r.title, child.author)) }) }.distinctBy { it.id })
                    }
                }
                if (current == generation) {
                    if (a != null && a.sessionMatches(account) && requestedPage == 1 && !append) {
                        messageFeed?.let { didViewMessageFeed(a, r.extra, it) }
                    }
                    if (r.kind == "me" && a != null && a.sessionMatches(account)) result.profile?.let { account = a.withIdentity(it.user) }
                    result.threads.filter { it.forum.isNotBlank() }.forEach { thread -> Policy.avatar(thread.forumAvatar)?.let { forumAvatars[thread.forum] = it } }
                    if (r.kind == "forum") result.threads.firstOrNull()?.let { thread ->
                        if (thread.forumAvatar.isNotBlank()) store.updateForumMetadata(r.id, ForumEntry("", r.id, thread.forumAvatar))
                    }
                    val incomingPostPages = result.posts.associate { it.id to (result.thread?.page ?: result.replyPage ?: requestedPage) }
                    postPages = if (append) incomingPostPages + postPages else incomingPostPages
                    result.thread?.thread?.let(::updateThreadCounters)
                    val loaded = when {
                        append -> result.appendTo(data)
                        commentsOnly -> result.replacingComments(data)
                        else -> result
                    }.let { it.copy(threads = it.threads.map { thread -> latestThreadCounters[thread.id]?.let(thread::withThreadCounters) ?: thread }) }
                    if (commentsOnly) readerScroll?.state?.let { it.requestScrollToItem(it.firstVisibleItemIndex, it.firstVisibleItemScrollOffset) }
                    data = if (r.kind == "thread") loaded.copy(posts = loaded.posts.inReplyOrder(order)) else loaded
                    page = result.thread?.page ?: result.replyPage ?: requestedPage
                    val target = if (r.kind == "thread") r.extra.takeIf { it.toLongOrNull()?.let { id -> id > 0 } == true } else if (r.kind == "subposts") r.extra.split(':').getOrNull(2) else null
                    if (!append && !commentsOnly && target != null && result.posts.none { it.id == target }) notice = "目标回复暂不可用，已打开所在帖子"
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (current == generation) { error = e.message?.take(240) ?: "加载失败，请重试"; if (e is SessionExpired) logout() } }
            finally { if (current == generation) { loading = false; refreshing = false; commentsRefreshing = false } }
        }
    }
    fun action(onSuccess: () -> Unit = {}, block: suspend (Account) -> String) {
        val a = account ?: run { report("请先登录百度账号"); return }
        val session = accountGeneration
        lateinit var job: Job
        job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                val result = withContext(Dispatchers.IO) { block(a) }
                if (session == accountGeneration && a.sessionMatches(account)) { onSuccess(); notice = result }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (session == accountGeneration && a.sessionMatches(account)) { error = e.message?.take(240) ?: "操作失败"; if (e is SessionExpired) logout() } }
            finally { actions.remove(job) }
        }
        actions.add(job); job.start()
    }
    fun completeLogin(bduss: String, stoken: String, onSuccess: () -> Unit) {
        if (loginJob?.isActive == true) return
        loading = true
        loginJob = viewModelScope.launch {
            try {
                val a = withContext(Dispatchers.IO) { api.login(bduss, stoken) }
                AutoReplyRuntime.get(getApplication<Application>()).stopForAccountChange()
                actions.toList().forEach { it.cancelAndJoin() }; actions.clear(); saveJob?.cancelAndJoin(); loadJob?.cancelAndJoin(); generation++
                withContext(Dispatchers.IO) { store.saveAccount(a) }
                accountGeneration++
                homeSnapshot = null
                listSnapshots.clear(); latestThreadCounters.clear()
                readerSnapshots.clear(); readerScroll = null
                inlineReplyDrafts.clear(); inlineReplyTarget = null
                account = a; AutoReplyRuntime.get(getApplication<Application>()).accountChanged(); notice = "登录成功"; onSuccess(); load()
                signing = false; submitting = false
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { report(e.message ?: "登录验证失败") }
            finally { loading = false }
        }
    }
    fun logout() {
        viewModelScope.launch {
            AutoReplyRuntime.get(getApplication<Application>()).stopForAccountChange()
            actions.toList().forEach { it.cancelAndJoin() }; actions.clear()
            loadJob?.cancelAndJoin(); saveJob?.cancelAndJoin(); generation++
            try {
                withContext(Dispatchers.IO) { store.saveAccount(null) }
                accountGeneration++
                inlineReplyDrafts.clear(); inlineReplyTarget = null
                account = null
                messageBadgeAccount = null; unreadMessageTabs = emptySet(); companionUnreadCounts.clear()
                AutoReplyRuntime.get(getApplication<Application>()).accountChanged()
                submitting = false
                signing = false
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
                WebStorage.getInstance().deleteAllData()
                navigate(Route("me"), root = true)
                homeSnapshot = null
                listSnapshots.clear(); latestThreadCounters.clear()
                readerSnapshots.clear(); readerScroll = null
                notice = "已退出登录"
            } catch (e: Exception) { report("退出清理失败，请重试") }
        }
    }
    fun saveThread(mode: Int = 0) {
        val p = data.thread ?: return
        if (saving) return
        val active = account
        saving = true
        saveJob = viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { offline.capture(p.thread.id, active, mode) { progress -> withContext(Dispatchers.Main) { saveProgress = progress } } }
                notice = when (mode) { 1 -> "正文、楼中楼和图片已保存"; 2 -> "正文、楼中楼及媒体已保存"; else -> "整帖正文和楼中楼已保存，媒体仍需联网" }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { report(e.message ?: "保存失败，已有保存保持不变") }
            finally { saving = false; saveProgress = "" }
        }
    }
    fun cancelSave() { saveJob?.cancel() }
    fun removeEntries(kind: String, ids: Set<String>, onSuccess: () -> Unit) {
        require(kind in setOf("history", "saved", "favorites") && ids.size <= 1000)
        val requestedRoute = route
        if (kind == "favorites") action(onSuccess = { onSuccess(); if (route == requestedRoute) load() }) { a ->
            ids.forEach { api.favorite(a, it, "0", false) }
            "已取消 ${ids.size} 个账号收藏"
        } else viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { ids.forEach { if (kind == "saved") offline.delete(it) else store.remove(kind, it) } }
                onSuccess(); if (route == requestedRoute) load()
                notice = "已删除 ${ids.size} 条本机记录"
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { report(e.message ?: "部分记录未能删除，请刷新确认") }
        }
    }
    fun modifyProfile(name: String, intro: String, sex: Int) {
        var updated: Account? = null
        var profile: Profile? = null
        action(onSuccess = {
            val fresh = checkNotNull(updated)
            store.saveAccount(fresh)
            if (account?.sessionMatches(fresh) != true) { inlineReplyDrafts.clear(); inlineReplyTarget = null }
            account = fresh
            data = data.copy(profile = profile)
        }) { a ->
            api.updateProfile(a, name, intro, sex)
            val verified = api.profile(a.uid, a)
            check(verified.user.name == name.trim()) { "资料已提交，但昵称尚未核实，请刷新确认" }
            updated = a.copy(name = verified.user.name, portrait = verified.user.portrait)
            profile = verified
            "资料修改已确认"
        }
    }
    fun offlineThread(id: String) {
      try {
        val j = store.get("saved", id) ?: return
        val t = threadFromJson(j)
        fun visible(post: Post) = !store.blocked(Thread(post.id, post.blocks.joinToString("") { it.text }, t.forum, post.author))
        val posts = j.getJSONArray("posts").objects().map(::postFromJson).filter(::visible).map { it.copy(children = it.children.filter(::visible)) }
        rememberReader(); readerScroll = null
        stack.add(route); route = Route("offline", id, t.title); data = ScreenData(thread = ThreadPage(t, j.optString("forum-id"), posts, j.optInt("page", 1), j.optInt("pages", 1)), posts = posts)
      } catch (e: Exception) { report(e.message ?: "离线正文损坏，请重新保存") }
    }
    fun setSettings(theme: String = darkMode, size: Float = fontSize, media: Boolean = manualMedia) {
        darkMode = theme; fontSize = size.coerceIn(12f, 26f); manualMedia = media
        store.prefs.edit().putString("theme", theme).putFloat("font-size", fontSize).putBoolean("manual-media", media).apply()
    }
    fun signAll(automatic: Boolean = false) {
        val a = account ?: return
        if (signing) return
        if (automatic && !store.prefs.getBoolean("auto-sign", false)) return
        val date = java.time.LocalDate.now().toString()
        if (automatic && store.prefs.getString("sign-cycle-${a.uid}", "") == date) return
        signing = true
        action {
          try {
            var success = 0; var failed = 0
            for ((id, name) in api.followedForums(it)) {
                currentCoroutineContext().ensureActive()
                val key = "signed-${a.uid}-$date-$id"
                if (store.prefs.getBoolean(key, false)) continue
                try { api.signForum(it, id, name); store.prefs.edit().putBoolean(key, true).apply(); success++ }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { failed++ }
                delay(300)
            }
            if (failed == 0) store.prefs.edit().putString("sign-cycle-${a.uid}", date).apply()
            "签到完成：$success 个成功，$failed 个失败"
          } finally { withContext(NonCancellable + Dispatchers.Main) { signing = false } }
        }
    }

    fun removeDeletedComment(id: String, from: Route) {
        if (route != from) return
        fun remove(posts: List<Post>) = posts.filterNot { it.id == id }.map { p -> p.copy(children = p.children.filterNot { it.id == id }) }
        data = data.copy(posts = remove(data.posts), thread = data.thread?.let { it.copy(posts = remove(it.posts)) })
    }

    fun compose(forum: String, tid: String? = null, pid: String? = null, floor: Int = 1, subpost: String? = null, replyTo: String = "") {
        if (tid == null && !newThreadsEnabled || tid != null && !repliesEnabled) { report("请先在设置中开启" + if (tid == null) "允许发帖" else "允许回帖"); return }
        if (account == null) { report("请先登录百度账号"); return }
        if (tid != null && route.id == tid && route.kind in setOf("thread", "subposts")) {
            inlineReplyTarget = InlineReplyTarget(forum, tid, pid, floor, subpost, replyTo)
            inlineReplyFocusRequest++
            return
        }
        val target = JSONObject().put("floor", floor).put("reply-to", replyTo)
        if (tid != null) target.put("tid", tid)
        if (pid != null) target.put("pid", pid)
        if (subpost != null) target.put("subpost", subpost)
        navigate(Route("compose", forum, if (tid == null) "发布主题" else "回复", target.toString()))
    }
    fun submitDraft(title: String, text: String, imageUris: List<String>, draftKey: String) {
        if (submitting || route.kind != "compose") return
        submitContent(route, title, text, imageUris, draftKey)
    }

    internal fun submitInlineReply(target: InlineReplyTarget) {
        if (submitting || route.kind !in setOf("thread", "subposts") || route.id != target.thread) return
        val draft = inlineReplyDrafts[target.draftKey] ?: return
        val json = JSONObject().put("floor", target.floor).put("tid", target.thread).put("reply-to", target.replyTo)
        target.parent?.let { json.put("pid", it) }; target.subpost?.let { json.put("subpost", it) }
        submitContent(Route("compose", target.forum, "回复", json.toString()), "", draft.text, draft.images, null, target)
    }

    private fun submitContent(r: Route, title: String, text: String, imageUris: List<String>, draftKey: String?, inlineTarget: InlineReplyTarget? = null) {
        val a = account ?: run { report("请先登录"); return }
        val sourceRoute = route
        val submissionNavigationGeneration = generation
        val target = JSONObject(r.extra)
        val tid = target.optString("tid").takeIf(String::isNotBlank)
        if (tid == null && !newThreadsEnabled || tid != null && !repliesEnabled) { report("当前内容发送已在设置中关闭，草稿保持不变"); return }
        if (text.graphemes() > 10000 || (tid == null && (title.isBlank() || title.graphemes() > 64))) { report("主题标题限 64 字，正文限 10000 字"); return }
        if (text.isBlank() && imageUris.isEmpty()) { report("请输入正文或选择图片"); return }
        if (imageUris.size > 9) { report("最多选择 9 张图片"); return }
        submitting = true
        action { active ->
            try {
                val images = imageUris.mapIndexed { index, raw ->
                    val uri = android.net.Uri.parse(raw)
                    require(uri.scheme == "content") { "图片来源不受支持，请重新选择" }
                    val bytes = getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                        val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
                        while (true) { val n = input.read(buffer); if (n < 0) break; require(out.size() + n <= 10 * 1024 * 1024) { "单张图片不能超过 10 MB" }; out.write(buffer, 0, n) }
                        out.toByteArray()
                    } ?: error("无法读取回复图片，请重新选择")
                    val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                    require(options.outWidth in 1..20000 && options.outHeight in 1..20000 && options.outWidth.toLong() * options.outHeight <= 80000000) { "图片尺寸无效或过大" }
                    if (tid == null) api.uploadThreadImage(active, bytes) else api.uploadReplyImage(active, bytes, tid, index)
                }
                val prefixName = target.optString("reply-to").trim()
                val body = if (target.has("subpost") && prefixName.isNotBlank() && prefixName.toByteArray().size <= 512 && prefixName.none { it == ',' || it == ')' || it.code < 32 }) "回复 $prefixName : $text" else text
                val resultTid = api.submit(active, r.id, title, body, tid, target.optString("pid").takeIf(String::isNotBlank), target.optInt("floor", 1), target.optString("subpost").takeIf(String::isNotBlank), images)
                withContext(Dispatchers.Main) {
                    if (active.sessionMatches(account)) {
                        if (inlineTarget != null) {
                            if (inlineReplyDrafts[inlineTarget.draftKey] == InlineReplyDraft(text, imageUris)) inlineReplyDrafts.remove(inlineTarget.draftKey)
                        } else if (route == sourceRoute && generation == submissionNavigationGeneration) {
                            draftKey?.let { store.remove("draft", it) }
                            val restoresReader = tid != null && stack.any { it.id == resultTid && it.kind in setOf("thread", "subposts") }
                            showRoute(stack.completeComposer(resultTid, tid != null), restoreReader = restoresReader)
                        }
                    }
                }
                "提交成功"
            } finally { withContext(NonCancellable + Dispatchers.Main) { submitting = false } }
        }
    }
}

fun String.graphemes(): Int {
    val iterator = android.icu.text.BreakIterator.getCharacterInstance().apply { setText(this@graphemes) }
    var count = 0
    iterator.first()
    while (iterator.next() != android.icu.text.BreakIterator.DONE) count++
    return count
}
