package dev.anyu.tiebapure

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.lifecycleScope
import coil.compose.AsyncImage
import coil.ImageLoader
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.collect
import org.json.JSONObject

private val mediaClient = okhttp3.OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
    .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS).readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
    .addInterceptor { chain ->
        require(Policy.mediaUrl(chain.request().url.toString()) != null) { "不受信任的媒体地址" }
        chain.proceed(chain.request())
    }.build()

class MainActivity : ComponentActivity() {
    private val model: AppViewModel by viewModels()
    private val export = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) runCatching { val bytes = model.store.backup(); contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("无法打开文件"); model.inform("备份已导出") }.onFailure { runCatching { contentResolver.delete(uri, null, null) }; model.report("备份导出失败：${it.message}") }
    }
    private val restore = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            val bytes = contentResolver.openInputStream(uri)?.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) { val count = input.read(buffer); if (count < 0) break; require(output.size() + count <= 8 * 1024 * 1024) { "备份超过 8 MB" }; output.write(buffer, 0, count) }
                output.toByteArray()
            } ?: error("无法打开文件")
            model.store.restore(bytes); model.load()
        }.onFailure { model.report("备份导入失败：${it.message}") }
    }
    private val importFont = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) lifecycleScope.launch {
            try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val name = contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else "导入字体" } ?: "导入字体"
                    model.fontStore.importFont(contentResolver.openInputStream(uri) ?: error("无法打开字体"), name)
                }
                model.refreshFonts(); model.inform("字体已导入")
            } catch (e: Exception) { model.report(e.message ?: "字体导入失败") }
        }
    }
    private val saveMediaDocument = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val prepared = model.pendingExport
        model.pendingExport = null
        if (uri != null && prepared != null) lifecycleScope.launch {
            try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { contentResolver.openOutputStream(uri)?.use { out -> prepared.file.inputStream().use { it.copyTo(out) } } ?: error("无法写入所选文件") }
                model.inform("媒体已保存")
            } catch (e: Exception) { runCatching { contentResolver.delete(uri, null, null) }; model.report("保存失败，请重试") }
            finally { prepared.file.delete() }
        } else prepared?.file?.delete()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        coil.Coil.setImageLoader(ImageLoader.Builder(this).okHttpClient(mediaClient).build())
        handleIntent(intent)
        setContent {
            val vm = model
            val dark = when (vm.darkMode) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
            SideEffect {
                androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            MaterialTheme(shapes = Shapes(extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(14.dp), small = androidx.compose.foundation.shape.RoundedCornerShape(16.dp), medium = androidx.compose.foundation.shape.RoundedCornerShape(24.dp), large = androidx.compose.foundation.shape.RoundedCornerShape(26.dp), extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28.dp)), colorScheme = if (dark) darkColorScheme(primary = Color(0xff0a84ff), secondary = Color(0xff0a84ff), secondaryContainer = Color(0xff163565), background = Color.Black, surface = Color(0xff1c1c1e), surfaceContainer = Color(0xff1c1c1e), onSurface = Color.White, onSurfaceVariant = Color(0xff8e8e93)) else lightColorScheme(primary = IOSLayout.blue, secondary = IOSLayout.blue, secondaryContainer = Color(0xffe2edff), surface = Color.White, surfaceContainer = Color.White, background = IOSLayout.background, onSurface = Color.Black, onSurfaceVariant = Color(0xff8e8e93))) {
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                AppScreen(vm, { export.launch("TiebaPure-backup.json") }, { restore.launch(arrayOf("application/json", "application/octet-stream")) })
                }
            }
        }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); handleIntent(intent) }
    override fun onResume() { super.onResume(); model.signAll(automatic = true); AutoReplyRuntime.get(this).onForeground(true) }
    override fun onStop() { if (!isChangingConfigurations) AutoReplyRuntime.get(this).onForeground(false); super.onStop() }
    fun chooseFont() { importFont.launch(arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "application/octet-stream")) }
    fun saveMedia(block: Block) {
        lifecycleScope.launch {
            var prepared: PreparedExport? = null
            try {
                model.inform("正在准备媒体文件…")
                prepared = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { model.offline.prepareExport(block) }
                val item = prepared
                if (android.os.Build.VERSION.SDK_INT < 29) { model.pendingExport = item; saveMediaDocument.launch(item.name); prepared = null; return@launch }
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val collection = when (item.folder) { "Pictures" -> android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI; "Movies" -> android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI; else -> android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI }
                    val values = android.content.ContentValues().apply { put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, item.name); put(android.provider.MediaStore.MediaColumns.MIME_TYPE, item.mime); put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, item.folder + "/TiebaPure"); put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1) }
                    val uri = contentResolver.insert(collection, values) ?: error("无法创建媒体文件")
                    try {
                        contentResolver.openOutputStream(uri)?.use { out -> item.file.inputStream().use { it.copyTo(out) } } ?: error("无法写入媒体库")
                        contentResolver.update(uri, android.content.ContentValues().apply { put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
                    } catch (e: Exception) { contentResolver.delete(uri, null, null); throw e }
                }
                model.inform("已保存到 ${item.folder}/TiebaPure")
            } catch (e: Exception) { model.report(e.message ?: "媒体保存失败") }
            finally { prepared?.file?.delete() }
        }
    }
    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra("open-automation", false) == true) { model.navigate(Route("automation")); return }
        val raw = intent?.dataString ?: intent?.getStringExtra(Intent.EXTRA_TEXT)
        if (raw != null) {
            val url = Regex("(?:https://|tiebapure://)[^\\s]+").find(raw)?.value ?: raw
            Policy.route(url)?.let { model.navigate(it) } ?: model.report("这个链接不能作为贴吧帖子或贴吧打开")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun AppScreen(vm: AppViewModel, export: () -> Unit, restore: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var blockDialog by remember { mutableStateOf(false) }
    var blockText by remember { mutableStateOf("") }
    var blockType by remember { mutableStateOf("keyword") }
    var confirmLogout by remember { mutableStateOf(false) }
    var confirmDelete by remember(vm.route) { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val r = vm.route
    var listFilter by remember(r) { mutableStateOf("") }
    var readingFilter by remember(r) { mutableIntStateOf(0) }
    var managing by remember(r) { mutableStateOf(false) }
    var selected by remember(r) { mutableStateOf(emptySet<String>()) }
    var confirmBatch by remember(r) { mutableStateOf(false) }
    val isLibrary = r.kind in setOf("history", "saved", "favorites")
    val readIds = if (isLibrary) vm.store.list("position").map { it.first.removePrefix("offline:") }.toSet() else emptySet()
    val shownThreads = vm.data.threads.filter { t ->
        (!isLibrary || listFilter.isBlank() || (t.title + t.author.name + t.forum).contains(listFilter.trim(), true)) &&
            (readingFilter == 0 || readingFilter == 1 && t.id !in readIds || readingFilter == 2 && t.id in readIds)
    }
    var showsPinnedThreads by remember(r) { mutableStateOf(false) }
    val forumPresentation = if (r.kind == "forum") forumPinnedPresentation(shownThreads, showsPinnedThreads) else null
    val visibleThreads = forumPresentation?.visibleThreads ?: shownThreads
    val context = LocalContext.current
    val searchFocus = remember { FocusRequester() }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    LaunchedEffect(r.kind) { if (r.kind == "searchinput") searchFocus.requestFocus() }
    LaunchedEffect(vm.notice) { if (vm.notice.isNotBlank()) { snackbar.showSnackbar(vm.notice); vm.clearNotice() } }
    BackHandler(enabled = r.kind !in setOf("home", "forums", "me")) { vm.back() }
    val title = when (r.kind) {
        "home" -> "首页"
        "forums" -> "进吧"
        "me" -> "我的"
        "forum" -> if (r.id.endsWith("吧")) r.id else "${r.id}吧"
        "thread", "offline" -> vm.data.thread?.thread?.title ?: "帖子详情"
        "searchusers" -> "搜索用户"
        "search", "searchinput" -> if (r.extra.isBlank()) "搜索" else "搜索本吧"
        "subposts" -> "楼中楼"
        "history" -> "浏览历史"
        "saved" -> "本地保存"
        "favorites" -> "账号收藏"
        "likes" -> "我的点赞"
        "messages" -> "消息"
        "settings" -> "设置"
        "automation" -> "自动回复"
        "readingSettings" -> "阅读设置"
        "block" -> "屏蔽管理"
        "about" -> "关于 TiebaPure"
        "login" -> "登录百度账号"
        else -> r.title
    }
    var activeRoot by remember { mutableStateOf("home") }
    LaunchedEffect(r.kind) { if (r.kind in setOf("home", "forums", "me")) activeRoot = r.kind }
    val homeFeedState = rememberLazyListState()
    val listFeedStates = remember(vm.account?.uid) { linkedMapOf<Route, LazyListState>() }
    val otherFeedState = key(r) { rememberLazyListState() }
    val savedFeedState = if (r.kind in setOf("forum", "likes")) listFeedStates.getOrPut(r) { LazyListState() }.also {
        if (listFeedStates.size > 8) listFeedStates.remove(listFeedStates.keys.first { it != r })
    } else null
    val feedState = if (r.kind == "home") homeFeedState else savedFeedState ?: otherFeedState
    val scope = rememberCoroutineScope()
    var isCollected by remember(r.id, vm.data.thread?.thread?.collected) { mutableStateOf(vm.data.thread?.thread?.collected == true) }
    val openSearch: () -> Unit = {
        query = ""
        vm.navigate(Route("searchinput", title = if (r.kind == "thread") "搜索本吧" else "搜索", extra = if (r.kind == "thread") vm.data.thread?.thread?.forum.orEmpty() else if (r.kind == "forum") r.id else ""))
    }
    val showRefresh = r.kind !in setOf("home", "forums", "thread", "searchinput", "search", "login", "settings", "about", "me", "block", "messages")
    FrostedAppShell(
        title = title, root = activeRoot,
        tabPosition = vm.bottomBarPosition, showTabLabels = vm.bottomBarShowsLabels, autoCollapseTabs = vm.bottomBarAutoCollapse, account = vm.account, scrollKey = r.kind + ":" + r.id,
        showTabs = r.kind !in setOf("thread", "subposts", "offline", "login", "compose") && !(r.kind == "messages" && r.extra == "private"),
        showBack = r.kind !in setOf("home", "forums", "me"),
        showSearch = r.kind == "home",
        startAlignedTitle = r.kind == "forum",
        titleTrailingInset = if (vm.newThreadsEnabled) 184.dp else 140.dp,
        hasActions = showRefresh || r.kind == "thread",
        titleContent = if (r.kind == "thread") { {
            Row(Modifier.heightIn(min = 48.dp).clickable { vm.data.thread?.thread?.forum?.takeIf(String::isNotBlank)?.let { vm.navigate(Route("forum", it)) } }.padding(vertical = 8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                ForumAvatar(vm, vm.data.thread?.thread?.forum.orEmpty(), vm.data.thread?.thread?.forumAvatar.orEmpty(), diameter = 18.dp)
                Spacer(Modifier.width(4.dp))
                Text(vm.data.thread?.thread?.forum?.let { if (it.endsWith("吧")) it else it + "吧" } ?: "帖子", fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f, fill = false), overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Icon(Icons.Default.KeyboardArrowRight, null, modifier = Modifier.size(12.dp))
            }
        } } else null,
        onBack = { vm.back() },
        onSearch = openSearch,
        onTab = { kind ->
            if (kind == "home" && r.kind == "home") { scope.launch { feedState.animateScrollToItem(0) }; vm.load(1) }
            else vm.navigate(Route(kind), root = true)
        },
        snackbar = { SnackbarHost(snackbar) },
        feedback = {
            if (vm.loading && !vm.commentsRefreshing && r.kind != "home" && !(r.kind == "messages" && r.extra == "private")) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (vm.saving) Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) { Text(vm.saveProgress.ifBlank { "正在准备本地保存…" }, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall); TextButton(onClick = vm::cancelSave) { Text("取消") } }
            if (vm.error.isNotBlank() && r.kind != "home") Surface(color = MaterialTheme.colorScheme.errorContainer) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(vm.error, Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer)
                    TextButton(onClick = vm::retryLoad) { Text("重试") }
                }
            }
        },
        actions = {
            if (r.kind == "forum") ForumToolbarActions(vm, onSearch = openSearch)
            else if (showRefresh) IconButton(onClick = { vm.load(1) }, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.Refresh, "刷新", Modifier.size(18.dp)) }
            if (r.kind == "thread") {
                IconButton(onClick = {
                    val desired = !isCollected
                    vm.action(onSuccess = { isCollected = desired }) { a -> vm.api.favorite(a, r.id, vm.data.posts.firstOrNull()?.id ?: "0", desired); if (desired) "收藏成功" else "已取消收藏" }
                }, modifier = Modifier.size(48.dp)) { IOSCollectionStar(isCollected, diameter = 18.dp) }
                IconButton(onClick = openSearch, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.Search, "搜索本吧", modifier = Modifier.size(18.dp)) }
                ThreadMoreMenu(vm) { confirmDelete = true }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(top = if (r.kind in setOf("home", "forums", "me", "thread", "subposts", "offline", "settings", "readingSettings")) 0.dp else padding.calculateTopPadding())) {
            when (r.kind) {
                "login" -> LoginView(vm) { vm.navigate(Route("me"), root = true) }
                "profile" -> ProfileScreen(vm, padding)
                "editprofile" -> EditProfileScreen(vm, padding)
                "following", "followers" -> RelationshipScreen(vm, padding)
                "compose" -> ComposerScreen(vm)
                "me" -> IOSMeScreen(vm, padding) { if (vm.account == null) vm.navigate(Route("login")) else confirmLogout = true }
                "forums" -> IOSForumHubScreen(vm, padding)
                "settings" -> IOSSettingsScreen(vm, padding) { confirmLogout = true }
                "readingSettings" -> IOSReadingSettingsScreen(vm, padding, export, restore)
                "about" -> AboutScreen(vm, padding)
                "license" -> LicenseScreen(r.id, padding)
                "block" -> {
                    Button(onClick = { blockDialog = true }, Modifier.padding(16.dp)) { Text("添加屏蔽") }
                    var revision by remember { mutableIntStateOf(0) }
                    val blocks = remember(revision, blockDialog) { vm.store.list("block") }
                    LazyColumn(contentPadding = PaddingValues(bottom = padding.calculateBottomPadding())) { items(blocks, key = { it.first }) { (id, j) -> Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Text("${j.optString("type")}：${j.optString("value")}", Modifier.weight(1f)); IconButton(onClick = { vm.store.remove("block", id); revision++ }) { Icon(Icons.Default.Delete, "删除屏蔽") } } } }
                }
                "drafts" -> {
                    var revision by remember { mutableIntStateOf(0) }
                    val rows = remember(vm.account?.uid, revision) { vm.store.list("draft").filter { it.second.optString("account") == vm.account?.uid && it.second.has("target") } }
                    LazyColumn(contentPadding = PaddingValues(bottom = padding.calculateBottomPadding())) {
                        if (rows.isEmpty()) item { EmptyMessage("当前账号没有本机草稿") }
                        items(rows, key = { it.first }) { (id, j) -> Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            NativeEmoticonText(TiebaEmoticon.parts(j.optString("title").ifBlank { j.optString("text").take(60) }), Modifier.weight(1f).clickable { vm.navigate(Route("compose", j.getString("forum"), "编辑草稿", j.getString("target"))) })
                            IconButton(onClick = { vm.store.remove("draft", id); revision++ }) { Icon(Icons.Default.Delete, "删除草稿") }
                        } }
                    }
                }
                "automation" -> AutoReplySettingsScreen(vm, padding)
                "messages" -> MessagesScreen(vm, padding)
                "likes" -> MyLikesScreen(vm, padding, feedState)
                "thread", "subposts", "offline" -> BoxWithConstraints(Modifier.fillMaxSize()) {
                    if (maxWidth >= 840.dp && vm.contextThreads.isNotEmpty()) Row(Modifier.fillMaxSize()) {
                        LazyColumn(Modifier.weight(0.38f).fillMaxHeight()) { items(vm.contextThreads, key = { it.id + ":" + it.matchedPost }) { t -> ThreadRow(vm, t, vm.fontSize, !vm.manualMedia) { vm.navigate(Route("thread", t.id, t.title, t.matchedPost)) } } }
                        VerticalDivider()
                        Box(Modifier.weight(0.62f)) { PostScreen(vm, padding) }
                    } else PostScreen(vm, padding)
                }
                "searchusers" -> UserSearchScreen(vm)
                else -> {
                    if (isLibrary) {
                        OutlinedTextField(value = listFilter, onValueChange = { listFilter = it }, singleLine = true, label = { Text(if (r.kind == "favorites") "筛选已加载收藏" else "搜索本机列表") }, modifier = Modifier.fillMaxWidth().padding(12.dp))
                        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            listOf("全部", "未读", "读过").forEachIndexed { index, title -> FilterChip(selected = readingFilter == index, onClick = { readingFilter = index }, label = { Text(title) }, modifier = Modifier.padding(end = 8.dp)) }
                            TextButton(onClick = { managing = !managing; selected = emptySet() }) { Text(if (managing) "取消管理" else "批量管理") }
                        }
                        if (managing) Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { selected = shownThreads.map { it.id }.toSet() }) { Text("全选当前结果") }
                            Text("已选 ${selected.size}", Modifier.weight(1f))
                            TextButton(onClick = { confirmBatch = true }, enabled = selected.isNotEmpty()) { Text(if (r.kind == "favorites") "取消收藏" else "删除") }
                        }
                    }
                    if (r.kind in setOf("search", "searchinput")) {
                        val submitSearch: () -> Unit = {
                            if (query.isNotBlank()) { keyboard?.hide(); vm.navigate(Route("search", query.trim(), extra = r.extra)) }
                        }
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().background(MaterialTheme.colorScheme.surface, androidx.compose.foundation.shape.RoundedCornerShape(IOSLayout.innerRadius)).padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(8.dp))
                            BasicTextField(query, { if (it.length <= 500) query = it else vm.report("关键词最多 500 字") }, Modifier.weight(1f).focusRequester(searchFocus).heightIn(min = 44.dp).padding(vertical = 11.dp), singleLine = true, textStyle = androidx.compose.ui.text.TextStyle(fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, autoCorrectEnabled = false), keyboardActions = KeyboardActions(onSearch = { submitSearch() }), decorationBox = { inner -> Box { if (query.isEmpty()) Text("搜索贴吧内容", fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant); inner() } })
                            if (query.isNotEmpty()) IconButton(onClick = { query = ""; if (r.kind == "search") vm.navigate(Route("searchinput", extra = r.extra)) }, modifier = Modifier.size(44.dp)) { Icon(Icons.Default.Clear, "清空搜索内容", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(17.dp)) }
                            else Spacer(Modifier.width(16.dp))
                        }
                    }
                    if (r.kind in setOf("search", "searchinput")) TextButton(onClick = { vm.navigate(Route("searchusers", query.ifBlank { r.id })) }) { Text("搜索用户（昵称 / ID）") }
                    if (r.kind == "forum") ForumCategoryPicker(vm)
                    PullRefreshContent(vm, Modifier.weight(1f), if (r.kind == "home") padding.calculateTopPadding() else 0.dp) {
                    LazyColumn(Modifier.fillMaxSize(), state = feedState, contentPadding = PaddingValues(top = if (r.kind == "home") padding.calculateTopPadding() + 6.dp else 0.dp, bottom = padding.calculateBottomPadding())) {
                        if (r.kind == "home" && vm.loading && shownThreads.isEmpty()) item {
                            Box(Modifier.fillParentMaxHeight(0.7f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) { CircularProgressIndicator(color = IOSLayout.blue, modifier = Modifier.size(24.dp)); Text("正在加载帖子", Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                        }
                        if (r.kind == "home" && vm.error.isNotBlank()) item {
                            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(vm.error); TextButton(onClick = vm::retryLoad) { Text("重试") } }
                        }
                        if (shownThreads.isEmpty() && !vm.loading && vm.error.isBlank()) item { EmptyMessage(if (r.kind == "searchinput") "输入关键词开始搜索" else if (listFilter.isBlank()) "暂无内容" else "没有匹配结果") }
                        if (forumPresentation?.pinnedThreads?.isNotEmpty() == true) item(key = "forum-pinned-toggle") {
                            ForumPinnedToggle(forumPresentation.pinnedThreads.size, showsPinnedThreads) { showsPinnedThreads = !showsPinnedThreads }
                        }
                        items(visibleThreads, key = { it.id + ":" + it.matchedPost }) { thread -> Row(
                            modifier = if (r.kind == "forum") Modifier.animateItem(
                                fadeInSpec = tween(200),
                                placementSpec = tween(280, easing = FastOutSlowInEasing),
                                fadeOutSpec = tween(160)
                            ) else Modifier,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (managing) Checkbox(checked = thread.id in selected, onCheckedChange = { selected = if (it) selected + thread.id else selected - thread.id })
                            ThreadRow(
                                vm, thread, vm.fontSize, !vm.manualMedia, homeFeed = r.kind == "home", forumCategory = r.extra.takeIf { r.kind == "forum" },
                                onForum = if (managing || thread.forum.isBlank()) null else { { vm.navigate(Route("forum", thread.forum)) } },
                                onUser = if (managing) null else { { if (thread.author.id.toLongOrNull()?.let { it > 0 } == true) vm.navigate(Route("profile", thread.author.id, thread.author.name)) } },
                                onBlock = if (managing || thread.forum.isBlank()) null else { {
                                    runCatching {
                                        val key = java.security.MessageDigest.getInstance("SHA-256").digest(thread.forum.toByteArray()).joinToString("") { "%02x".format(it) }
                                        vm.store.put("block", "forum:$key", JSONObject().put("type", "forum").put("value", thread.forum))
                                        vm.load(1)
                                    }.onFailure { vm.report("屏蔽未能保存") }
                                } }
                            ) {
                                if (managing) selected = if (thread.id in selected) selected - thread.id else selected + thread.id
                                else if (r.kind == "saved") vm.offlineThread(thread.id)
                                else vm.navigate(Route("thread", thread.id, thread.title, thread.matchedPost))
                            }
                        } }
                        if (vm.data.hasMore && r.kind !in setOf("history", "saved")) item { AutomaticPageFooter(vm) }
                    }
                    }
                }
            }
        }
    }
    AutoReplyCatchUpPrompt(vm)
    if (confirmLogout) AlertDialog(onDismissRequest = { confirmLogout = false }, title = { Text("退出登录？") }, text = { Text("将清除本机凭据、网页 Cookie 和登录网页数据。") }, confirmButton = { TextButton(onClick = { confirmLogout = false; vm.logout() }) { Text("退出") } }, dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("取消") } })
    if (confirmBatch) AlertDialog(onDismissRequest = { confirmBatch = false }, title = { Text(if (r.kind == "favorites") "取消选中的账号收藏？" else "删除选中的本机记录？") }, text = { Text("将处理 ${selected.size} 条记录。${if (r.kind == "saved") "已下载的对应离线媒体也会删除。" else ""}") }, confirmButton = { TextButton(onClick = { confirmBatch = false; vm.removeEntries(r.kind, selected) { selected = emptySet(); managing = false } }) { Text("确认") } }, dismissButton = { TextButton(onClick = { confirmBatch = false }) { Text("取消") } })
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("删除本人主题？") }, text = { Text("这会向贴吧提交永久删除请求。请确认当前主题确实需要删除。") }, confirmButton = { TextButton(onClick = {
        confirmDelete = false
        val target = vm.data.thread
        if (target != null) vm.action(onSuccess = { vm.navigate(Route("profile", vm.account?.uid.orEmpty(), "我的资料")) }) { a -> vm.api.deleteOwnThread(a, target); "主题删除结果已确认" }
    }) { Text("确认删除") } }, dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } })
    if (blockDialog) AlertDialog(onDismissRequest = { blockDialog = false }, title = { Text("添加屏蔽") }, text = { Column {
        Row { listOf("keyword" to "关键词", "user" to "用户", "forum" to "贴吧").forEach { (key, text) -> FilterChip(selected = blockType == key, onClick = { blockType = key }, label = { Text(text) }) } }
        OutlinedTextField(value = blockText, onValueChange = { if (it.length <= 512) blockText = it else vm.report("屏蔽内容最多 512 字") }, label = { Text("屏蔽内容") })
    } }, confirmButton = { TextButton(onClick = { if (blockText.isNotBlank()) {
        val id = blockType + ":" + java.security.MessageDigest.getInstance("SHA-256").digest(blockText.trim().toByteArray()).joinToString("") { "%02x".format(it) }
        runCatching { vm.store.put("block", id, JSONObject().put("type", blockType).put("value", blockText.trim())) }.onSuccess { blockDialog = false; blockText = "" }.onFailure { vm.report("屏蔽未能保存，请检查本机空间") }
    } }) { Text("添加") } }, dismissButton = { TextButton(onClick = { blockDialog = false }) { Text("取消") } })
}

@Composable fun MenuRow(title: String, onClick: () -> Unit) { Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) { Text(title, Modifier.padding(horizontal = 20.dp, vertical = 16.dp)); HorizontalDivider() } }
@Composable fun EmptyMessage(text: String) { Box(Modifier.fillMaxWidth().padding(36.dp), contentAlignment = Alignment.Center) { Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant) } }

@OptIn(kotlinx.coroutines.FlowPreview::class)
@Composable fun PostScreen(vm: AppViewModel, padding: PaddingValues = PaddingValues()) {
    val p = vm.data.thread
    val r = vm.route
    val showReplyBar = (r.kind == "thread" && p != null || r.kind == "subposts") && vm.repliesEnabled
    val direction = LocalLayoutDirection.current
    val listPadding = PaddingValues(start = padding.calculateStartPadding(direction), top = padding.calculateTopPadding(), end = padding.calculateEndPadding(direction), bottom = padding.calculateBottomPadding() + if (showReplyBar) 78.dp else 0.dp)
    if (vm.loading && vm.data.posts.isEmpty()) return
    key(r.kind, r.id, r.extra) {
    val positionId = if (r.kind == "offline") "offline:${r.id}" else r.id
    val stored = remember(positionId) { if (r.kind in setOf("thread", "offline")) vm.store.get("position", positionId) else null }
    val targetSubpost = if (r.kind == "subposts") r.extra.split(':').getOrNull(2) else null
    val scroll = remember { vm.readerListState(targetSubpost?.let { id -> vm.data.posts.indexOfFirst { it.id == id }.coerceAtLeast(0) } ?: stored?.optInt("index", 0)?.coerceIn(0, vm.data.posts.size + 2) ?: 0, stored?.optInt("offset", 0)?.coerceIn(0, 1000000) ?: 0) }
    val readerSort = vm.sort
    val readerOnlyAuthor = vm.onlyAuthor
    LaunchedEffect(scroll, r.kind, readerSort, readerOnlyAuthor) {
        if (r.kind in setOf("thread", "offline")) snapshotFlow { scroll.firstVisibleItemIndex to scroll.firstVisibleItemScrollOffset }.drop(1).distinctUntilChanged().debounce(300).collect { (index, offset) ->
            val (visiblePage, pageIndex) = if (r.kind == "thread") threadReadingPosition(index, vm.data.posts.map { it.id }, vm.postPages, vm.page) else 1 to index
            try { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { vm.store.put("position", positionId, JSONObject().put("page", visiblePage).put("index", pageIndex).put("offset", offset).put("total-pages", p?.pages ?: 1).put("sort", readerSort).put("only-author", readerOnlyAuthor)) } }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { vm.report("阅读位置未能保存，请检查本机剩余空间") }
        }
    }
    val replyTarget = if (r.kind == "subposts") InlineReplyTarget(r.title, r.id, r.extra.substringBefore(':')) else p?.let { InlineReplyTarget(it.thread.forum, r.id) }
    FloatingReplyReader(vm, padding, showReplyBar, replyTarget) { readerModifier ->
    PullRefreshContent(vm, readerModifier, padding.calculateTopPadding()) {
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface), state = scroll, contentPadding = listPadding) {
        if (p != null) item {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
                vm.data.posts.firstOrNull { it.floor == 1 }?.let { post ->
                    PostAuthorHeader(vm, post, mainPost = true)
                    Spacer(Modifier.height(16.dp))
                }
                NativeEmoticonText(TiebaEmoticon.parts(p.thread.title), fontSize = (vm.fontSize + 5).sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
            }
        }
        itemsIndexed(vm.data.posts, key = { _, post -> post.id }) { index, post ->
            if (r.kind == "thread" && index == vm.data.posts.indexOfFirst { it.floor != 1 }) ReplyControls(vm)
            if (r.kind == "subposts" || post.floor != 1) ReplyCommentRow(vm, post)
            else {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                SelectionContainer { Column { ContentBlocksView(post.blocks, vm) } }
                val metadata = commentMetadata(post.timestamp, post.ipAddress, PostTimestampFormat.MAIN_POST)
                if (metadata.isNotBlank()) Text(metadata, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp))
                if (r.kind == "thread") Row {
                    if (post.subCount > 0 && p != null) TextButton(onClick = { vm.navigate(Route("subposts", r.id, title = p.thread.forum, extra = "${post.id}:${p.forumId}")) }) { Text("${post.subCount} 条楼中楼") }
                }
                if (r.kind == "offline") post.children.forEach { child ->
                    Column(Modifier.padding(start = 16.dp, top = 8.dp)) { Text(child.author.name, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium); ContentBlocksView(child.blocks, vm) }
                }
            }; HorizontalDivider()
            }
        }
        if (r.kind == "thread" && vm.data.posts.none { it.floor != 1 }) item { ReplyControls(vm) }
        if (r.kind in setOf("thread", "subposts") && vm.data.hasMore) item { AutomaticPageFooter(vm) }
    }
    }
    }
    }
}

@Composable fun ContentBlockView(block: Block, vm: AppViewModel) {
    val trustedUrl = Policy.mediaUrl(block.url)
    val context = LocalContext.current
    val local = remember(block.localFile) { OfflineLibrary.safeFile(context, block.localFile) }
    var loaded by remember(block.url, block.localFile) { mutableStateOf(local != null || !vm.manualMedia && vm.route.kind != "offline") }
    var preview by remember(block.url) { mutableStateOf(false) }
    when {
        block.type == 1 && Policy.webpage(block.url) != null -> TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(block.url))) }) { Text(block.text.ifBlank { block.url }) }
        block.type == 4 && block.url.startsWith("user:") -> TextButton(onClick = { val id = block.url.removePrefix("user:"); if (id.toLongOrNull()?.let { it > 0 } == true) vm.navigate(Route("profile", id, block.text)) }) { Text(block.text) }
        block.type == 2 -> NativeEmoticonText(listOf(block), fontSize = vm.fontSize.sp)
        trustedUrl != null && block.type in setOf(3, 20) -> {
            var ratio by remember(block.url, block.localFile) { mutableStateOf<Float?>(null) }
            if (!loaded) TextButton(onClick = { loaded = true }) { Text("加载图片") }
            else AsyncImage(model = local ?: trustedUrl, contentDescription = "帖子图片，点击放大", contentScale = androidx.compose.ui.layout.ContentScale.FillWidth,
                onSuccess = { state -> val image = state.result.drawable; if (image.intrinsicWidth > 0 && image.intrinsicHeight > 0) ratio = image.intrinsicWidth.toFloat() / image.intrinsicHeight },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).then(ratio?.let { Modifier.aspectRatio(it) } ?: Modifier.height(60.dp)).clip(androidx.compose.foundation.shape.RoundedCornerShape(IOSLayout.innerRadius)).clickable { preview = true })
            if (preview) androidx.compose.ui.window.Dialog(onDismissRequest = { preview = false }, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
                FrostedImagePreview(local ?: trustedUrl, onClose = { preview = false }, onSave = { (context as? MainActivity)?.saveMedia(block) })
            }
        }
        trustedUrl != null && block.type in setOf(5, 10) -> {
            Row { TextButton(onClick = { preview = true }) { Text(if (block.type == 10) "播放语音" else "播放视频") }; TextButton(onClick = { (context as? MainActivity)?.saveMedia(block) }) { Text("保存") } }
            if (preview) androidx.compose.ui.window.Dialog(onDismissRequest = { preview = false }) { MediaPlayer(local?.toURI()?.toString() ?: block.url) }
        }
        else -> Text(block.text.ifBlank { if (block.url.isNotBlank()) block.url else "" }, fontSize = vm.fontSize.sp, fontFamily = vm.readerTypeface?.let { androidx.compose.ui.text.font.FontFamily(it) }, modifier = Modifier.padding(vertical = 3.dp))
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable fun MediaPlayer(url: String) {
    val context = LocalContext.current
    require(Policy.mediaUrl(url) != null || runCatching { java.io.File(java.net.URI(url)).canonicalPath.startsWith(java.io.File(context.filesDir, "offline").canonicalPath + "/") }.getOrDefault(false))
    val player = remember(url) { androidx.media3.exoplayer.ExoPlayer.Builder(context)
        .setMediaSourceFactory(androidx.media3.exoplayer.source.DefaultMediaSourceFactory(androidx.media3.datasource.DefaultDataSource.Factory(context, androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(mediaClient)))).build().apply {
        setAudioAttributes(androidx.media3.common.AudioAttributes.Builder().setUsage(androidx.media3.common.C.USAGE_MEDIA).setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
        setMediaItem(androidx.media3.common.MediaItem.fromUri(url)); prepare(); playWhenReady = true
    } }
    AndroidView(factory = { androidx.media3.ui.PlayerView(it).apply { this.player = player } }, modifier = Modifier.fillMaxWidth().height(300.dp))
    DisposableEffect(player) {
        val owner = context as? androidx.lifecycle.LifecycleOwner
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event -> if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) player.pause() }
        owner?.lifecycle?.addObserver(observer)
        onDispose { owner?.lifecycle?.removeObserver(observer); player.release() }
    }
}
