package dev.anyu.tiebapure

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.*
import androidx.compose.ui.semantics.*
import coil.compose.AsyncImage
import androidx.compose.ui.platform.testTag
import org.json.JSONObject

@Composable fun IOSAvatar(name: String, portrait: String, modifier: Modifier = Modifier, diameter: Dp = 32.dp) {
    val url = Policy.avatar(portrait)
    var loaded by remember(url) { mutableStateOf(false) }
    val label = name.trim().ifBlank { "贴" }
    Box(modifier.size(diameter).clip(CircleShape).background(IOSLayout.blue.copy(alpha = 0.12f)).testTag(if (loaded) "avatar-loaded" else "avatar-fallback"), contentAlignment = Alignment.Center) {
        if (!loaded) Text(String(Character.toChars(label.codePointAt(0))), color = IOSLayout.blue, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        if (url != null) AsyncImage(url, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop, onSuccess = { loaded = true }, onError = { loaded = false })
    }
}

@Composable fun ForumAvatar(vm: AppViewModel, name: String, portrait: String = "", modifier: Modifier = Modifier, diameter: Dp = 32.dp) {
    val known = Policy.avatar(portrait)
    val avatar = known ?: vm.forumAvatars[name].orEmpty()
    LaunchedEffect(name, known) { if (known == null) vm.ensureForumAvatar(name) }
    IOSAvatar(name, avatar, modifier, diameter)
}

@Composable fun ThreadRow(vm: AppViewModel, thread: Thread, size: Float, loadMedia: Boolean, homeFeed: Boolean = false, forumCategory: String? = null, onForum: (() -> Unit)? = null, onUser: (() -> Unit)? = null, onBlock: (() -> Unit)? = null, click: () -> Unit) {
    val forumFeed = forumCategory != null
    var menu by remember(thread.id) { mutableStateOf(false) }
    val surface = MaterialTheme.colorScheme.surface
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    val date = remember(thread.timestamp) { readerDateText(thread.timestamp) }
    Column(Modifier.fillMaxWidth().then(if (homeFeed) Modifier.padding(horizontal = IOSLayout.feedInset, vertical = 6.dp) else Modifier).clip(RoundedCornerShape(if (homeFeed) IOSLayout.cardRadius else 0.dp)).background(surface).clickable(onClick = click).padding(horizontal = IOSLayout.cardInset, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val name = if (forumFeed) thread.author.name.ifBlank { "贴吧用户" } else thread.forum.takeIf { it.isNotBlank() }?.let { if (it.endsWith("吧")) it else it + "吧" } ?: thread.author.name
            val openIdentity = if (forumFeed) onUser else onForum
            Box(Modifier.size(44.dp).clickable(enabled = openIdentity != null, onClick = { openIdentity?.invoke() }), contentAlignment = Alignment.Center) {
                if (forumFeed || thread.forum.isBlank()) IOSAvatar(name, thread.author.portrait) else ForumAvatar(vm, thread.forum, thread.forumAvatar)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(if (forumFeed) 4.dp else 0.dp)) {
                Text(name, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = if (forumFeed) FontWeight.Medium else FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.clickable(enabled = openIdentity != null, onClick = { openIdentity?.invoke() }))
                if (forumFeed) {
                    if (date.isNotBlank()) Row(Modifier.clickable(enabled = onUser != null, onClick = { onUser?.invoke() }), verticalAlignment = Alignment.CenterVertically) {
                        val published = forumCategory == "publish"
                        Icon(if (published) TiebaIcons.Clock else TiebaIcons.Replies, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.width(4.dp))
                        Text(date + if (published) "发布" else "回复", fontSize = 12.sp, lineHeight = 16.sp, color = secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                } else Text(thread.author.name + if (date.isBlank()) "" else " · $date", fontSize = 12.sp, lineHeight = 16.sp, color = secondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.clickable(enabled = onUser != null, onClick = { onUser?.invoke() }))
            }
            if (onBlock != null) Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(44.dp)) { Icon(Icons.Default.MoreVert, "更多帖子操作", modifier = Modifier.rotate(90f).size(22.dp)) }
                DropdownMenu(menu, { menu = false }) { DropdownMenuItem(text = { Text("屏蔽${thread.forum}吧") }, onClick = { menu = false; onBlock() }) }
            }
        }
        if (thread.title.isNotBlank()) NativeEmoticonText(TiebaEmoticon.parts(thread.title), fontSize = size.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
        if (thread.summary.isNotBlank()) NativeEmoticonText(TiebaEmoticon.parts(thread.summary), fontSize = size.sp, maxLines = 2, color = secondary)
        if (loadMedia && thread.previews.isNotEmpty()) BoxWithConstraints {
            val single = thread.previews.size == 1
            val height = maxWidth / if (single) 2 else 3
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                thread.previews.take(3).forEach { url -> Box(Modifier.weight(1f).height(height).clip(RoundedCornerShape(IOSLayout.innerRadius))) {
                    AsyncImage(url, "帖子预览图", Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                } }
            }
            if (thread.previews.size > 3) Row(Modifier.align(Alignment.BottomEnd).padding(8.dp).background(Color.Black.copy(alpha = 0.55f), CircleShape).padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(12.dp)) {
                    drawRect(Color.White, topLeft = Offset(this.size.width * .05f, this.size.height * .05f), size = androidx.compose.ui.geometry.Size(this.size.width * .7f, this.size.height * .7f), style = Stroke(1.dp.toPx()))
                    drawRect(Color.White, topLeft = Offset(this.size.width * .25f, this.size.height * .25f), size = androidx.compose.ui.geometry.Size(this.size.width * .7f, this.size.height * .7f), style = Stroke(1.dp.toPx()))
                }
                Spacer(Modifier.width(4.dp)); Text(thread.previews.size.toString(), color = Color.White, fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.heightIn(min = 44.dp).clickable(onClick = click), verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(17.dp)) {
                    val width = this.size.width; val height = this.size.height
                    val path = Path().apply { moveTo(width * .12f, height * .12f); lineTo(width * .88f, height * .12f); lineTo(width * .88f, height * .72f); lineTo(width * .52f, height * .72f); lineTo(width * .35f, height * .92f); lineTo(width * .35f, height * .72f); lineTo(width * .12f, height * .72f); close() }
                    drawPath(path, secondary, style = Stroke(1.5.dp.toPx()))
                }
                Spacer(Modifier.width(5.dp)); Text(thread.replies.toString(), fontSize = 15.sp, color = secondary)
            }
            Row(Modifier.heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.ThumbUp, "点赞数", tint = secondary, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(5.dp)); Text(thread.likes?.toString() ?: "—", fontSize = 15.sp, color = secondary) }
        }
    }
    if (!homeFeed) HorizontalDivider(Modifier.padding(start = 16.dp), thickness = 0.5.dp)
}

@Composable fun GroupHeader(title: String, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 20.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) { Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.weight(1f)); trailing() }
}

@Composable fun IOSGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(IOSLayout.groupRadius)).background(MaterialTheme.colorScheme.surface), content = content)
}

@Composable fun IOSRow(title: String, icon: ImageVector, unread: Boolean = false, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp).heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = IOSLayout.blue, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f).padding(vertical = 11.dp)) {
            MessageTitleWithUnread(title, unread, fontSize = 17.sp)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(17.dp))
    }
    HorizontalDivider(Modifier.padding(start = 48.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
}

@Composable fun IOSMeScreen(vm: AppViewModel, padding: PaddingValues, onAccount: () -> Unit) {
    RefreshMessageBadges(vm)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
        item { GroupHeader("账号") }
        item { IOSGroup {
            if (vm.account == null) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.Info, null, tint = IOSLayout.blue); Spacer(Modifier.width(10.dp)); Text("未登录也可以浏览公开帖子", fontSize = 17.sp) }
                IOSRow("手机号验证码登录", Icons.Outlined.Phone, onClick = onAccount)
            } else {
                val account = vm.account!!
                Row(Modifier.fillMaxWidth().clickable { vm.navigate(Route("profile", account.uid, account.name)) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    IOSAvatar(account.name, account.portrait, diameter = 48.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) { Text(account.name, fontSize = 17.sp, fontWeight = FontWeight.SemiBold); Text("UID ${account.uid}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(17.dp))
                }
                IOSRow("消息", Icons.Outlined.Notifications, unread = vm.hasUnreadMessages()) { vm.navigate(Route("messages")) }
                IOSRow("关注的用户", Icons.Outlined.Person) { vm.navigate(Route("following", vm.account!!.uid)) }
                IOSRow("关注的吧", TiebaIcons.Star) { vm.navigate(Route("forums"), root = true) }
            }
        } }
        item { GroupHeader("浏览") }
        item { IOSGroup {
            IOSRow("帖子收藏", TiebaIcons.Star) { vm.navigate(Route("favorites")) }
            IOSRow("我的点赞", Icons.Outlined.ThumbUp) { vm.navigate(Route("likes")) }
            IOSRow("浏览历史", Icons.Outlined.DateRange) { vm.navigate(Route("history")) }
        } }
        item { GroupHeader("应用") }
        item { IOSGroup {
            IOSRow("设置", Icons.Outlined.Settings) { vm.navigate(Route("settings")) }
            IOSRow("关于 TiebaPure", Icons.Outlined.Info) { vm.navigate(Route("about")) }
        } }
    }
}

@Composable fun IOSForumHubScreen(vm: AppViewModel, padding: PaddingValues) {
    var name by remember { mutableStateOf("") }
    var managing by remember { mutableStateOf(false) }
    var clearRecent by remember { mutableStateOf(false) }
    var revision by remember { mutableIntStateOf(0) }
    val recent = remember(vm.route, vm.data, revision) { vm.store.list("forum").map { forumEntry(it.second, it.first) }.filter { it.name.isNotBlank() }.distinctBy { it.name }.take(9) }
    fun open() { if (name.isNotBlank()) vm.navigate(Route("forum", name.trim().removeSuffix("吧"))) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
        item { GroupHeader("打开贴吧") }
        item { IOSGroup { Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(name, { if (it.length <= 100) name = it }, Modifier.weight(1f).heightIn(min = 44.dp).padding(vertical = 11.dp), singleLine = true, textStyle = androidx.compose.ui.text.TextStyle(fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go, autoCorrectEnabled = false), keyboardActions = KeyboardActions(onGo = { open() }), decorationBox = { inner -> Box { if (name.isEmpty()) Text("输入吧名", fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant); inner() } })
            IconButton(onClick = { open() }, enabled = name.isNotBlank(), modifier = Modifier.size(44.dp)) { Icon(Icons.Default.ArrowForward, "进入贴吧", tint = IOSLayout.blue) }
        } } }
        if (recent.isNotEmpty()) {
            item { GroupHeader("最近浏览") {
                if (managing) TextButton(onClick = { clearRecent = true }) { Text("清空", fontSize = 13.sp) }
                TextButton(onClick = { managing = !managing }) { Text(if (managing) "完成" else "管理", fontSize = 13.sp) }
            } }
            item { ForumGrid(vm, recent, onRemove = if (managing) { { forum -> runCatching { vm.store.remove("forum", forum); revision++ }.onFailure { vm.report("最近浏览未能更新") } } } else null) { vm.navigate(Route("forum", it)) } }
        }
        item { GroupHeader("关注贴吧") { if (vm.account != null) TextButton(onClick = { vm.signAll() }) { Text("一键签到", fontSize = 13.sp) } } }
        if (vm.account == null || vm.data.forums.isEmpty()) item { IOSGroup { Text(if (vm.account == null) "登录后显示关注的贴吧" else "还没有关注的贴吧", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        else item { ForumGrid(vm, vm.data.forums, onSign = { id, name -> vm.action { a -> vm.api.signForum(a, id, name) } }) { vm.navigate(Route("forum", it)) } }
    }
    if (clearRecent) AlertDialog(onDismissRequest = { clearRecent = false }, title = { Text("清空最近浏览的贴吧？") }, text = { Text("只会清除本机记录，不影响你关注的贴吧。") }, confirmButton = { TextButton(onClick = { clearRecent = false; runCatching { vm.store.clear("forum"); revision++ }.onFailure { vm.report("最近浏览未能更新") } }) { Text("清空") } }, dismissButton = { TextButton(onClick = { clearRecent = false }) { Text("取消") } })
}

@Composable private fun ForumGrid(vm: AppViewModel, forums: List<ForumEntry>, onSign: ((String, String) -> Unit)? = null, onRemove: ((String) -> Unit)? = null, onOpen: (String) -> Unit) {
    var signingTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    IOSGroup { BoxWithConstraints(Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
        val columns = ((maxWidth + 12.dp) / 88.dp).toInt().coerceAtLeast(1)
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        forums.chunked(columns).forEach { row -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            row.forEach { (id, name, avatar) -> Column(Modifier.weight(1f).combinedClickable(onClick = { onOpen(name) }, onLongClickLabel = "贴吧签到", onLongClick = if (onSign != null && id.toLongOrNull()?.let { it > 0 } == true) { { signingTarget = id to name } } else null), horizontalAlignment = Alignment.CenterHorizontally) {
                Box { ForumAvatar(vm, name, avatar, Modifier.padding(bottom = 8.dp), diameter = 52.dp); if (onRemove != null) IconButton(onClick = { onRemove(name) }, modifier = Modifier.align(Alignment.TopEnd).size(28.dp).background(MaterialTheme.colorScheme.surface, CircleShape)) { Icon(Icons.Default.Close, "移除$name", modifier = Modifier.size(14.dp)) } }
                Text(if (name.endsWith("吧")) name else name + "吧", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                DropdownMenu(expanded = signingTarget == (id to name), onDismissRequest = { signingTarget = null }) { DropdownMenuItem(text = { Text("签到") }, onClick = { signingTarget = null; onSign?.invoke(id, name) }) }
            } }
            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
        } }
        }
    } }
}

@Composable fun IOSCollectionStar(selected: Boolean, diameter: androidx.compose.ui.unit.Dp = 22.dp) {
    val color = MaterialTheme.colorScheme.onSurface
    Canvas(Modifier.size(diameter).semantics { contentDescription = if (selected) "取消收藏帖子" else "收藏帖子" }) {
        val path = Path()
        for (i in 0 until 10) {
            val radius = size.minDimension * if (i % 2 == 0) 0.46f else 0.21f
            val angle = i * Math.PI / 5 - Math.PI / 2
            val x = center.x + kotlin.math.cos(angle).toFloat() * radius
            val y = center.y + kotlin.math.sin(angle).toFloat() * radius
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        drawPath(path, color, style = if (selected) androidx.compose.ui.graphics.drawscope.Fill else Stroke(1.5.dp.toPx()))
    }
}
