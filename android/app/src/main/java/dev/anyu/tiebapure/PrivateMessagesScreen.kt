package dev.anyu.tiebapure

import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Face
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.TextUnit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@Composable internal fun RefreshMessageBadges(vm: AppViewModel, viewing: String? = null) {
    val account = vm.account ?: return
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle, account.uid, account.bduss, account.stoken, viewing) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                vm.refreshMessageBadges(account, viewing)
                delay(30_000)
            }
        }
    }
}

@Composable internal fun MessageTitleWithUnread(title: String, unread: Boolean, fontSize: TextUnit = TextUnit.Unspecified) {
    Box(if (unread) Modifier.padding(end = 6.dp).semantics { stateDescription = "有未读消息" } else Modifier) {
        Text(title, fontSize = fontSize)
        if (unread) UnreadMessageBadge(Modifier.align(Alignment.TopEnd).offset(x = 5.dp, y = (-2).dp))
    }
}

@Composable private fun UnreadMessageBadge(modifier: Modifier = Modifier, count: Int? = null) {
    val red = Color(0xffff3b30)
    if (count == null) {
        Box(modifier.size(8.dp).background(red, CircleShape))
    } else {
        val label = if (count > 99) "99+" else count.toString()
        val paint = remember {
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE
                typeface = Typeface.DEFAULT_BOLD
            }
        }
        val glyph = remember { Rect() }
        Canvas(
            modifier.size(21.dp).background(red, CircleShape)
                .clearAndSetSemantics { contentDescription = "$count 条未读私信" },
        ) {
            paint.textSize = 10.dp.toPx()
            paint.getTextBounds(label, 0, label.length, glyph)
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawText(
                    label, center.x - (glyph.left + glyph.right) / 2f,
                    center.y - (glyph.top + glyph.bottom) / 2f, paint
                )
            }
        }
    }
}

@Composable fun MessagesScreen(vm: AppViewModel, padding: PaddingValues) {
    val tab = vm.route.extra
    RefreshMessageBadges(vm, viewing = tab)
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("" to "回复我的", "at" to "@我的", "private" to "私信").forEach { (value, title) ->
                FilterChip(selected = tab == value, onClick = { vm.selectMessageTab(value) }, label = { MessageTitleWithUnread(title, vm.hasUnreadMessages(value)) })
            }
        }
        val account = vm.account
        if (account == null) {
            EmptyMessage("登录后可以查看回复、@我的和私信消息")
            TextButton(onClick = { vm.navigate(Route("login")) }) { Text("登录百度账号") }
        } else if (tab == "private") {
            key(account.uid, account.bduss, account.stoken, vm.route.id) {
                PrivateMessagesScreen(vm, account, Modifier.weight(1f).padding(bottom = padding.calculateBottomPadding()))
            }
        } else {
            PullRefreshContent(vm, Modifier.weight(1f), 0.dp) {
                LazyColumn(contentPadding = PaddingValues(bottom = padding.calculateBottomPadding())) {
                    if (vm.data.threads.isEmpty() && !vm.loading && vm.error.isBlank()) item { EmptyMessage("暂无消息") }
                    items(vm.data.threads, key = { it.id + ":" + it.matchedPost }) { thread ->
                        ThreadRow(vm, thread, vm.fontSize, !vm.manualMedia) { vm.navigate(Route("thread", thread.id, thread.title, thread.matchedPost)) }
                    }
                    if (vm.data.hasMore) item { TextButton(onClick = vm::loadNextPage, enabled = !vm.loading) { Text("加载更多") } }
                }
            }
        }
    }
}

@Composable private fun PrivateMessagesScreen(vm: AppViewModel, account: Account, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val voice = remember { PrivateVoiceRecorder(context) }
    var image by remember { mutableStateOf<PrivateMessageImage?>(null) }
    var preparing by remember { mutableStateOf(false) }
    var showEmoticons by remember { mutableStateOf(false) }
    var previewImage by remember { mutableStateOf<String?>(null) }
    var playVoice by remember { mutableStateOf<String?>(null) }
    var conversations by remember { mutableStateOf(emptyList<PrivateConversation>()) }
    var messages by remember { mutableStateOf(emptyList<PrivateChatMessage>()) }
    var selected by remember(vm.route.id) { mutableStateOf(vm.route.id.takeIf { (it.toLongOrNull() ?: 0) > 0 }?.let { runCatching { PrivateConversation.recipient(User(it, vm.route.title), account.uid) }.getOrNull() }) }
    var revision by remember { mutableIntStateOf(0) }
    var generation by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var markingRead by remember { mutableStateOf(false) }
    var uncertain by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf("") }
    var cursor by remember { mutableStateOf<Long?>(null) }
    var hasMore by remember { mutableStateOf(false) }
    var newMessage by remember { mutableStateOf(false) }
    var chooseFollowing by remember { mutableStateOf(false) }
    var recipient by remember { mutableStateOf("") }
    var resolving by remember { mutableStateOf(false) }
    var recipientError by remember { mutableStateOf<String?>(null) }
    var action by remember { mutableStateOf<Job?>(null) }
    var active by remember { mutableStateOf(true) }
    var microphoneGeneration by remember { mutableStateOf<Int?>(null) }
    val current = { active && account.sessionMatches(vm.account) }
    val hostView = androidx.compose.ui.platform.LocalView.current
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(15000)
            if (current() && hostView.hasWindowFocus() && !loading && !sending && !markingRead && !preparing && !voice.recording && microphoneGeneration == null) revision++
        }
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null && current()) {
            preparing = true
            val requestGeneration = generation
            action = scope.launch {
                try {
                    val prepared = withContext(Dispatchers.IO) { PrivateMessageImage.read(context, uri) }
                    ensureActive(); if (current() && generation == requestGeneration) image = prepared
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { if (current() && generation == requestGeneration) error = e.message }
                finally { if (current() && generation == requestGeneration) preparing = false }
            }
        }
    }
    val microphone = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val requestGeneration = microphoneGeneration
        microphoneGeneration = null
        if (current() && requestGeneration == generation && selected != null) {
            if (granted) runCatching { voice.start() }.onFailure { error = "无法开始录音，请稍后重试" }
            else error = "请在系统设置中允许麦克风权限后录制语音"
        }
    }
    LaunchedEffect(voice.error) { voice.error?.let { error = it } }
    fun openRecipient(user: User) {
        if (!current()) return
        action?.cancel(); generation++; voice.discard(); image = null; preparing = false
        selected = PrivateConversation.recipient(user, account.uid)
        messages = emptyList(); draft = ""; uncertain = false; error = null; newMessage = false; chooseFollowing = false
    }
    DisposableEffect(Unit) {
        val lifecycle = (context as? androidx.lifecycle.LifecycleOwner)?.lifecycle
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP && voice.recording) runCatching { voice.stop() }.onFailure { error = it.message }
        }
        lifecycle?.addObserver(observer)
        onDispose { active = false; action?.cancel(); voice.discard(); lifecycle?.removeObserver(observer) }
    }
    fun backToList() { action?.cancel(); generation++; voice.discard(); image = null; preparing = false; sending = false; selected = null; messages = emptyList(); draft = ""; uncertain = false; error = null }
    BackHandler(enabled = selected != null) { backToList() }
    LaunchedEffect(selected?.id, revision) {
        val requestGeneration = ++generation
        if (selected == null) messages = emptyList()
        loading = true; error = null; cursor = null; hasMore = false
        try {
            val peer = selected
            if (peer != null) {
                val page = vm.api.privateMessages(account, peer)
                ensureActive(); if (!current() || generation != requestGeneration) return@LaunchedEffect
                messages = page.items; hasMore = page.hasMore; cursor = page.cursor
                vm.updatePrivateUnread(account, vm.api.cachedPrivateUnread(account))
                if (uncertain) error = PrivateSendUnknown().message
            } else {
                val page = vm.api.privateConversations(account)
                ensureActive(); if (!current() || generation != requestGeneration) return@LaunchedEffect
                conversations = page.items; hasMore = page.hasMore; cursor = page.cursor; loading = false
                vm.updatePrivateUnread(account, page.items.any { it.unread > 0 })
                for (item in page.items) {
                    val profile = try { vm.api.profile(item.userId, account) } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
                    ensureActive(); if (!current() || generation != requestGeneration) return@LaunchedEffect
                    if (profile != null) conversations = conversations.map { if (it.id == item.id) it.copy(name = profile.user.name, portrait = profile.user.portrait) else it }
                }
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { if (current() && generation == requestGeneration) error = e.message }
        finally { if (current() && generation == requestGeneration) loading = false }
    }
    fun loadMore() {
        val before = cursor ?: return
        if (loading || sending || preparing || voice.recording || microphoneGeneration != null) return
        loading = true
        val requestGeneration = generation
        val target = selected
        action = scope.launch {
            try {
                val peer = target
                if (peer != null) {
                    val page = vm.api.privateMessages(account, peer, before)
                    ensureActive(); if (!current() || generation != requestGeneration) return@launch
                    val fresh = page.items.filter { item -> messages.none { it.id == item.id } }
                    messages = fresh + messages; hasMore = page.hasMore && fresh.isNotEmpty(); cursor = page.cursor
                } else {
                    val page = vm.api.privateConversations(account, before)
                    ensureActive(); if (!current() || generation != requestGeneration) return@launch
                    val fresh = page.items.filter { item -> conversations.none { it.id == item.id } }
                    conversations = conversations + fresh; hasMore = page.hasMore && fresh.isNotEmpty(); cursor = page.cursor
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (current() && generation == requestGeneration) error = e.message }
            finally { if (current() && generation == requestGeneration) loading = false }
        }
    }
    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selected != null) {
                TextButton(onClick = { backToList() }) { Text("‹ 会话列表") }
                Text(selected!!.name, Modifier.weight(1f), maxLines = 1)
            } else {
                Text("私信", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                TextButton(
                    enabled = !loading && !markingRead && conversations.any { it.unread > 0 },
                    onClick = {
                        markingRead = true
                        val requestGeneration = generation
                        action = scope.launch {
                            try {
                                withContext(Dispatchers.IO) { vm.api.markAllPrivateRead(account) }
                                if (current() && requestGeneration == generation) {
                                    conversations = conversations.map { it.copy(unread = 0) }
                                    vm.updatePrivateUnread(account, false)
                                }
                            } catch (e: CancellationException) { throw e }
                            catch (e: Exception) { if (current()) error = e.message ?: "无法标记已读" }
                            finally { markingRead = false }
                        }
                    }
                ) { Text("一键已读") }
                TextButton(enabled = !markingRead, onClick = { recipient = ""; recipientError = null; chooseFollowing = false; newMessage = true }) { Text("发消息") }
            }
            TextButton(onClick = { revision++ }, enabled = !loading && !sending && !markingRead && !preparing && !voice.recording && microphoneGeneration == null) { Text("刷新") }
        }
        error?.let { Text(it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error) }
        val peer = selected
        if (peer == null) {
            LazyColumn(Modifier.weight(1f)) {
                if (conversations.isEmpty() && !loading && error == null) item { EmptyMessage("暂无私信，点击“发消息”开始会话") }
                items(conversations, key = { it.id }) { item ->
                    Row(Modifier.fillMaxWidth().clickable(enabled = !markingRead) { selected = item; messages = emptyList(); draft = ""; uncertain = false; error = null }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box {
                            IOSAvatar(item.name, item.portrait, diameter = 44.dp)
                            if (item.unread > 0) {
                                UnreadMessageBadge(Modifier.align(Alignment.TopEnd).offset(x = 5.dp, y = (-5).dp), count = item.unread)
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) { Text(item.name); Text(item.preview, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2) }
                        if (item.timestamp > 0) {
                            Text(privateConversationDateText(item.timestamp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    }
                    HorizontalDivider(Modifier.padding(start = 72.dp))
                }
                if (hasMore) item { TextButton(onClick = { loadMore() }, enabled = !loading) { Text("加载更多会话") } }
            }
        } else {
            val list = rememberLazyListState()
            LaunchedEffect(messages.lastOrNull()?.id) { if (messages.isNotEmpty() && !loading) list.animateScrollToItem(messages.lastIndex + if (hasMore) 1 else 0) }
            LazyColumn(Modifier.weight(1f), state = list, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (hasMore) item { TextButton(onClick = { loadMore() }, enabled = !loading && !sending && !preparing && !voice.recording) { Text("加载更早消息") } }
                if (messages.isEmpty() && !loading && error == null) item { EmptyMessage("暂无消息") }
                items(messages, key = { it.id }) { item ->
                    val mine = item.senderId == account.uid
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (mine && item.read) Text("已读", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Surface(color = if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium) {
                                when {
                                    item.type == 2 && item.mediaURL != null -> coil.compose.AsyncImage(item.mediaURL, "私信图片，点按查看", Modifier.sizeIn(maxWidth = 240.dp, maxHeight = 260.dp).clickable { previewImage = item.mediaURL }.padding(6.dp))
                                    item.voiceMD5 != null -> TextButton(onClick = { playVoice = PrivateMessageContent.voiceURL(item.voiceMD5) }) { Text("▶ 语音 ${item.duration}秒") }
                                    else -> NativeEmoticonText(TiebaEmoticon.parts(item.text), fontSize = 17.sp, modifier = Modifier.padding(12.dp).widthIn(max = 280.dp))
                                }
                            }
                        }
                        if (item.timestamp > 0) Text(SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(item.timestamp * 1000)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            // The screen already reserves the keyboard inset supplied by FrostedAppShell.
            Column(Modifier.fillMaxWidth().testTag("private-chat-composer").padding(12.dp)) {
                if (preparing) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (image != null) Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(image!!.preview, "待发送图片", Modifier.size(56.dp))
                    Text("图片将单独发送", Modifier.weight(1f).padding(horizontal = 8.dp), style = MaterialTheme.typography.labelSmall)
                    TextButton(onClick = { image = null }, enabled = !sending) { Text("移除") }
                }
                if (voice.recording || voice.attachment != null) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (voice.recording) "正在录音，最长60秒" else "语音 ${voice.duration}秒", Modifier.weight(1f))
                    TextButton(onClick = voice::discard, enabled = !sending) { Text("取消录音") }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconButton(onClick = {
                        if (voice.recording) runCatching { voice.stop() }.onFailure { error = it.message }
                        else { microphoneGeneration = generation; microphone.launch(android.Manifest.permission.RECORD_AUDIO) }
                    }, modifier = Modifier.size(56.dp), enabled = !sending && !preparing && image == null && microphoneGeneration == null) {
                        Icon(if (voice.recording) Icons.Default.Close else TiebaIcons.Microphone, if (voice.recording) "停止录音" else "录制语音", Modifier.size(24.dp))
                    }
                    OutlinedTextField(value = draft, onValueChange = { if (it.length <= 5000) draft = it }, placeholder = { Text("输入私信") }, modifier = Modifier.weight(1f).heightIn(min = 56.dp), shape = RoundedCornerShape(24.dp), maxLines = 5, enabled = !sending && !voice.recording)
                    IconButton(onClick = { showEmoticons = !showEmoticons }, modifier = Modifier.size(56.dp), enabled = !sending && !voice.recording) { Icon(Icons.Default.Face, "贴吧表情", Modifier.size(24.dp)) }
                    IconButton(onClick = { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, modifier = Modifier.size(56.dp), enabled = !sending && !preparing && !voice.recording && voice.attachment == null && microphoneGeneration == null) { Icon(TiebaIcons.Photo, "选择私信图片", Modifier.size(24.dp)) }
                }
                if (showEmoticons) LazyRow(Modifier.fillMaxWidth().height(48.dp)) {
                    items(TiebaEmoticon.names.entries.toList()) { (id, label) ->
                        IconButton(onClick = { draft += "#($label)" }, enabled = !sending) {
                            coil.compose.AsyncImage("file:///android_asset/tieba-emoticons/image_emoticon$id.png", "插入$label", Modifier.size(28.dp))
                        }
                    }
                }
                TextButton(modifier = Modifier.align(Alignment.End), enabled = !sending && !uncertain && !loading && !preparing && !voice.recording && microphoneGeneration == null && (image != null || voice.attachment != null || draft.isNotBlank() && draft.length <= 5000), onClick = {
                    if (!current() || sending || uncertain) return@TextButton
                    sending = true; error = null
                    val text = draft
                    val attachment = image
                    val audio = voice.attachment
                    val duration = voice.duration
                    val requestGeneration = generation
                    action = scope.launch {
                        try {
                            ensureActive(); if (!current() || generation != requestGeneration) return@launch
                            val type: Int
                            val payload = when {
                                attachment != null -> { type = 2; vm.api.uploadPrivateImage(account, attachment.bytes, attachment.width, attachment.height) }
                                audio != null -> { type = 3; vm.api.uploadPrivateVoice(account, audio.readBytes(), duration) }
                                else -> { type = 1; text }
                            }
                            ensureActive(); if (!current() || generation != requestGeneration) return@launch
                            val id = vm.api.sendPrivateMessage(account, peer, payload, UUID.randomUUID().toString().replace("-", "").take(10).lowercase(), type)
                            ensureActive(); if (!current() || generation != requestGeneration) return@launch
                            messages = messages + PrivateChatMessage(id, account.uid, if (type == 1) text else if (type == 2) "[图片消息]" else "[语音消息]", System.currentTimeMillis() / 1000, type, if (type == 2) PrivateMessageContent.imageURL(payload) else null, if (type == 3) PrivateMessageContent.voiceMD5(payload) else null, if (type == 3) duration else 0)
                            if (type == 1) draft = "" else if (type == 2) image = null else voice.discard()
                        } catch (e: CancellationException) { throw e }
                        catch (e: Exception) { if (current() && generation == requestGeneration) { uncertain = e is PrivateSendUnknown; error = e.message } }
                        finally { if (current() && generation == requestGeneration) sending = false }
                    }
                }) { Text(if (sending) "发送中" else if (image != null) "发送图片" else if (voice.attachment != null) "发送语音" else "发送") }
            }
        }
    }
    previewImage?.let { url -> androidx.compose.ui.window.Dialog(onDismissRequest = { previewImage = null }) {
        coil.compose.AsyncImage(url, "私信图片", Modifier.fillMaxWidth().clickable { previewImage = null })
    } }
    playVoice?.let { url -> androidx.compose.ui.window.Dialog(onDismissRequest = { playVoice = null }) { MediaPlayer(url) } }
    if (newMessage && chooseFollowing) FollowedPrivateRecipientPicker(vm, account, onDismiss = { chooseFollowing = false }, onSelected = { openRecipient(it) })
    else if (newMessage) AlertDialog(onDismissRequest = { action?.cancel(); resolving = false; newMessage = false }, title = { Text("发起私信") }, text = {
        Column {
            TextButton(onClick = { chooseFollowing = true }, enabled = !resolving) { Text("从关注的人中选择") }
            Text("输入对方主页显示的数字 ID，也可从其主页点击“发私信”。打开会话不会发送消息。")
            OutlinedTextField(value = recipient, onValueChange = { recipient = it }, label = { Text("用户 ID") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), enabled = !resolving)
            recipientError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = {
        TextButton(enabled = !resolving && recipient.isNotBlank(), onClick = {
            resolving = true; recipientError = null
            val id = recipient.trim()
            val requestGeneration = generation
            action = scope.launch {
                try {
                    val user = vm.api.userByTiebaId(id, account)
                    ensureActive(); if (!current() || generation != requestGeneration) return@launch
                    openRecipient(user)
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { if (current()) recipientError = e.message }
                finally { if (current()) resolving = false }
            }
        }) { Text(if (resolving) "正在查找…" else "打开会话") }
    }, dismissButton = { TextButton(onClick = { action?.cancel(); resolving = false; newMessage = false }) { Text("取消") } })
}

@Composable internal fun FollowedPrivateRecipientPicker(vm: AppViewModel, account: Account, onDismiss: () -> Unit, onSelected: (User) -> Unit) {
    var users by remember { mutableStateOf(emptyList<User>()) }
    var page by remember { mutableIntStateOf(1) }
    var retry by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var hasMore by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(page, retry) {
        loading = true; error = null
        try {
            val rows = vm.api.relationships(account.uid, account, false, page)
            ensureActive(); if (!account.sessionMatches(vm.account)) return@LaunchedEffect
            val fresh = rows.distinctBy { it.id }.filter { user -> users.none { it.id == user.id } }
            users = users + fresh
            hasMore = rows.isNotEmpty() && fresh.isNotEmpty()
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { if (account.sessionMatches(vm.account)) error = e.message }
        finally { loading = false }
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("选择关注的人") }, text = {
        Column {
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (users.isEmpty() && !loading && error == null) Text("暂无关注的人")
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                items(users, key = { it.id }) { user ->
                    Row(Modifier.fillMaxWidth().clickable(enabled = user.id != account.uid && user.id.toLongOrNull()?.let { it > 0 && it.toString() == user.id } == true) { onSelected(user) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        IOSAvatar(user.name, user.portrait, diameter = 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(user.name, modifier = Modifier.weight(1f))
                    }
                }
                if (hasMore && !loading && error == null) item { TextButton(onClick = { page++ }) { Text("加载更多") } }
            }
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { retry++ }, enabled = !loading) { Text("重试") }
            }
        }
    }, confirmButton = {}, dismissButton = { TextButton(onClick = onDismiss) { Text("返回") } })
}
