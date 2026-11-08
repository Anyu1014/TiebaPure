package dev.anyu.tiebapure

import android.content.Intent
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.layer.*
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.motionEventSpy
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable internal fun FloatingReplyReader(vm: AppViewModel, padding: PaddingValues, visible: Boolean, defaultTarget: InlineReplyTarget?, content: @Composable (Modifier) -> Unit) {
    val target = vm.inlineReplyTarget?.takeIf { it.thread == defaultTarget?.thread && (vm.route.kind != "subposts" || it.parent == defaultTarget?.parent) } ?: defaultTarget
    val motion = remember(vm.route, vm.account?.uid, vm.bottomBarAutoCollapse) { BottomBarMotion() }
    var collapsed by remember(motion) { mutableStateOf(false) }
    var editing by remember(motion) { mutableStateOf(false) }
    val input = remember(motion) { BottomBarPanInput(motion) }
    val density = LocalDensity.current.density
    val observer = remember(input, density) { BottomBarTouchObserver(input, density) { collapsed = it } }
    DisposableEffect(observer) { onDispose { observer.close() } }
    LaunchedEffect(vm.inlineReplyFocusRequest) { motion.expand(); collapsed = false }
    val layer = rememberGraphicsLayer()
    var origin by remember { mutableStateOf(Offset.Zero) }
    Box(Modifier.fillMaxSize().motionEventSpy { if (visible && vm.bottomBarAutoCollapse && !editing && !vm.submitting) observer.observe(it) else observer.close() }) {
        content(Modifier.fillMaxSize().onGloballyPositioned { origin = it.localToScreen(Offset.Zero) }.drawWithContent {
            layer.record { this@drawWithContent.drawContent() }; drawLayer(layer)
        })
        if (visible && target != null) BoxWithConstraints(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = padding.calculateBottomPadding())) {
            val compact = collapsed && vm.bottomBarAutoCollapse
            val width by animateDpAsState(if (compact) 62.dp else minOf(maxWidth, 480.dp), spring(dampingRatio = 1f, stiffness = 380f), label = "reply-width")
            val alignment = if (!compact) Alignment.BottomCenter else when (vm.bottomBarPosition) { BottomBarPosition.LEFT -> Alignment.BottomStart; BottomBarPosition.CENTER -> Alignment.BottomCenter; BottomBarPosition.RIGHT -> Alignment.BottomEnd }
            Column(Modifier.align(alignment).width(width)) {
                if (compact) {
                    AnyuFrostedSurface(layer, origin, Modifier.size(62.dp), CircleShape) {
                        IconButton(onClick = { motion.expand(); collapsed = false }, modifier = Modifier.fillMaxSize().testTag("thread-reply-collapsed")) { Icon(TiebaIcons.Comment, "展开回复输入框") }
                    }
                } else key(target, vm.account?.uid) {
                    InlineReplyEditor(vm, target, AnyuBackdrop(layer, origin)) { editing = it }
                }
            }
        }
    }
}

@Composable private fun ColumnScope.InlineReplyEditor(vm: AppViewModel, target: InlineReplyTarget, backdrop: AnyuBackdrop, onEditing: (Boolean) -> Unit) {
    val account = vm.account
    val draft = vm.inlineReplyDrafts[target.draftKey] ?: InlineReplyDraft()
    val editor = remember { NativeEmoticonEditorState() }
    var emoticons by remember { mutableStateOf(false) }
    var mentions by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    val ime = WindowInsets.ime.asPaddingValues().calculateBottomPadding() > 0.dp
    LaunchedEffect(focused, ime, emoticons, mentions) { onEditing(focused && ime || emoticons || mentions) }
    DisposableEffect(Unit) { onDispose { onEditing(false) } }
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(9)) { uris ->
        if (account?.sessionMatches(vm.account) == true) {
            uris.forEach { runCatching { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
            val current = vm.inlineReplyDrafts[target.draftKey] ?: InlineReplyDraft()
            vm.inlineReplyDrafts[target.draftKey] = current.copy(images = (current.images + uris.map { it.toString() }).distinct().take(9))
        }
    }
    LaunchedEffect(vm.inlineReplyFocusRequest) {
        if (vm.takeInlineReplyFocusRequest()) editor.view?.let { view -> view.requestFocus(); (context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(view, InputMethodManager.SHOW_IMPLICIT) }
    }
    val foreground = MaterialTheme.colorScheme.onSurface.toArgb()
    val hint = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    if (target.parent != null || draft.images.isNotEmpty() || emoticons) {
        // Let the fixed input row take its height before measuring the panels above it.
        Column(Modifier.weight(1f, fill = false)) {
            if (target.parent != null) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(target.prompt, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, maxLines = 1)
                TextButton(onClick = vm::resetInlineReplyTarget, enabled = !vm.submitting) { Text("取消回复") }
            }
            if (draft.images.isNotEmpty()) LazyRow {
                items(draft.images) { uri -> Row(verticalAlignment = Alignment.CenterVertically) {
                    coil.compose.AsyncImage(uri, "待发送图片", Modifier.size(56.dp))
                    IconButton(onClick = { vm.inlineReplyDrafts[target.draftKey] = draft.copy(images = draft.images - uri) }, enabled = !vm.submitting) { Icon(Icons.Default.Close, "移除图片") }
                } }
            }
            if (emoticons) LazyRow(Modifier.fillMaxWidth().height(52.dp)) {
                items(TiebaEmoticon.names.entries.toList()) { (id, label) -> IconButton(onClick = { editor.view?.insertToken("#($label)") }, enabled = !vm.submitting) {
                    coil.compose.AsyncImage("file:///android_asset/tieba-emoticons/image_emoticon$id.png", "插入$label", Modifier.size(30.dp))
                } }
            }
        }
    }
    if (mentions && account != null) FollowedPrivateRecipientPicker(vm, account, onDismiss = { mentions = false }, onSelected = { user -> editor.view?.insertToken("@" + user.mentionName + " "); mentions = false })
    AnyuFrostedSurface(backdrop.layer, backdrop.origin, Modifier.fillMaxWidth().height(62.dp).testTag("thread-reply-bar")) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            AndroidView(modifier = Modifier.weight(3f).padding(start = 8.dp).height(44.dp).testTag("thread-reply-input"), factory = { NativeComposerEditText(it).also { view ->
                editor.view = view; view.minLines = 1; view.maxLines = 3; view.gravity = Gravity.CENTER_VERTICAL
                val inset = (6 * view.resources.displayMetrics.density).toInt(); view.setPadding(inset, 0, inset, 0)
            } }, update = { view ->
                editor.view = view; view.isEnabled = account != null && !vm.submitting
                view.hint = if (account == null) "登录后回复" else target.prompt
                view.contentDescription = target.prompt; view.setTextColor(foreground); view.setHintTextColor(hint)
                view.onRawChange = { text -> if (text.length <= 40000) vm.inlineReplyDrafts[target.draftKey] = (vm.inlineReplyDrafts[target.draftKey] ?: InlineReplyDraft()).copy(text = text) else vm.report("正文过长") }
                view.onFocusChangeListener = android.view.View.OnFocusChangeListener { _, value -> focused = value }
                view.setRaw(draft.text)
            })
            Row(Modifier.weight(2f).testTag("thread-reply-actions").padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { mentions = true },
                    modifier = Modifier.weight(1f).semantics { contentDescription = "艾特" },
                    enabled = account != null && !vm.submitting
                ) { Text("@") }
                IconButton(
                    onClick = { editor.view?.clearFocus(); keyboard?.hide(); emoticons = !emoticons },
                    modifier = Modifier.weight(1f),
                    enabled = account != null && !vm.submitting
                ) { Icon(Icons.Default.Face, "表情", Modifier.size(18.dp)) }
                IconButton(
                    onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.weight(1f),
                    enabled = account != null && !vm.submitting && draft.images.size < 9
                ) { Icon(TiebaIcons.Photo, "图片", Modifier.size(18.dp)) }
                IconButton(
                    onClick = { confirm = true },
                    modifier = Modifier.weight(1f).testTag("thread-reply-send"),
                    enabled = account != null && !vm.submitting && (draft.text.isNotBlank() || draft.images.isNotEmpty())
                ) { Icon(Icons.Default.Send, if (vm.submitting) "正在发送" else "发送回复", Modifier.size(18.dp)) }
            }
        }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("确认发送？") }, text = { Text("将以当前百度账号公开发布内容。遇到结果未知时，请先刷新确认，避免重复发送。") }, confirmButton = { TextButton(onClick = { confirm = false; editor.view?.clearFocus(); keyboard?.hide(); vm.submitInlineReply(target) }) { Text("确认发送") } }, dismissButton = { TextButton(onClick = { confirm = false }) { Text("取消") } })
}
