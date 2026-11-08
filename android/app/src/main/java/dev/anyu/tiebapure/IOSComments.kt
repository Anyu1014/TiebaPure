package dev.anyu.tiebapure

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.ZoneId
import java.util.Locale

internal fun commentLikeCount(count: Long): String = when {
    count >= 1_000_000 -> String.format(Locale.ROOT, "%.1fm", count / 1_000_000.0)
    count >= 1_000 -> String.format(Locale.ROOT, "%.1fk", count / 1_000.0)
    else -> count.coerceAtLeast(0).toString()
}

internal fun commentMetadata(timestamp: Long, region: String, format: PostTimestampFormat? = null, zone: ZoneId = ZoneId.systemDefault()): String {
    val date = postTimestampText(timestamp, format ?: PostTimestampFormat.SUBPOST, zone)
    return listOf(date, region.removePrefix("IP属地：").removePrefix("IP属地:").trim()).filter(String::isNotBlank).joinToString("  ")
}

@Composable fun ReplyControls(vm: AppViewModel) {
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(false to "全部回复", true to "只看楼主").forEach { (value, label) ->
            Text(label, fontSize = 15.sp, fontWeight = if (vm.onlyAuthor == value) FontWeight.SemiBold else FontWeight.Normal, color = if (vm.onlyAuthor == value) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.heightIn(min = 44.dp).wrapContentHeight().clickable { vm.selectReplies(authorOnly = value) }.padding(end = 16.dp))
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.width(142.dp)) {
            listOf(2 to "热门", 0 to "正序", 1 to "倒序").forEach { (value, label) ->
                Box(Modifier.weight(1f).height(44.dp).background(if (vm.sort == value) MaterialTheme.colorScheme.surface else Color.Transparent, RoundedCornerShape(50)).clickable { vm.selectReplies(order = value) }.semantics { selected = vm.sort == value; role = Role.RadioButton }, contentAlignment = Alignment.Center) {
                    Text(label, fontSize = 14.sp, fontWeight = if (vm.sort == value) FontWeight.SemiBold else FontWeight.Normal, color = if (vm.sort == value) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable fun PostAuthorHeader(vm: AppViewModel, post: Post, mainPost: Boolean = false) {
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    val route = vm.route
    val offline = route.kind == "offline"
    var liked by remember(post.id, post.liked) { mutableStateOf(post.liked) }
    val count = (if (mainPost) vm.data.thread?.thread?.likes ?: post.likes else post.likes)?.let { (it + if (liked == post.liked) 0 else if (liked) 1 else -1).coerceAtLeast(0) }
    val threadAuthor = vm.data.thread?.thread?.author?.id
    val isThreadAuthor = mainPost || post.author.id.toLongOrNull()?.let { it > 0 && post.author.id == threadAuthor } == true
    val openUser = { if (post.author.id.toLongOrNull()?.let { it > 0 } == true) vm.navigate(Route("profile", post.author.id, post.author.name)) }
        Row(Modifier.fillMaxWidth().testTag("post-author-${post.id}"), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clickable(onClick = openUser), contentAlignment = Alignment.CenterStart) { IOSAvatar(post.author.name, post.author.portrait, diameter = if (mainPost) 40.dp else 36.dp) }
            Spacer(Modifier.width(4.dp))
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text(post.author.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false).clickable(onClick = openUser))
                if (post.authorLevel > 0) Text(if (post.authorLevelName.isBlank()) "Lv.${post.authorLevel}" else "${post.authorLevel} ${post.authorLevelName.replace("\n", " ")}", fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 8.dp).widthIn(max = 110.dp).background(Color(0xFFFFA448).copy(alpha = .2f), RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 2.dp))
                if (isThreadAuthor) Text("楼主", fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                    modifier = Modifier.padding(start = 5.dp).background(IOSLayout.blue.copy(alpha = .16f), RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 2.dp))
            }
            if (!offline) IconButton(onClick = {
                if (route.kind == "subposts") vm.compose(route.title, route.id, route.extra.substringBefore(':'), subpost = post.id, replyTo = post.author.name)
                else vm.data.thread?.let { vm.compose(it.thread.forum, route.id, post.id, post.floor) }
            }, modifier = Modifier.size(48.dp).testTag("post-comment-${post.id}")) {
                Icon(TiebaIcons.Comment, "评论", tint = secondary, modifier = Modifier.size(18.dp))
            }
            Row(Modifier.heightIn(min = 44.dp).testTag("post-like-${post.id}").clickable(enabled = !offline && vm.likesEnabled) {
                val desired = !liked
                vm.action(onSuccess = { vm.applyPostLike(route.id, post.id, mainPost, desired); liked = desired }) { account -> vm.api.like(account, route.id, post.id, if (mainPost) LikeObject.THREAD else if (route.kind == "subposts") LikeObject.SUBPOST else LikeObject.POST, desired); if (desired) "已点赞" else "已取消点赞" }
            }.padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.ThumbUp, if (liked) "取消点赞" else "点赞", tint = if (liked) IOSLayout.blue else secondary, modifier = Modifier.size(18.dp))
                Text(count?.let(::commentLikeCount) ?: "赞", fontSize = 14.sp, color = if (liked) IOSLayout.blue else secondary, modifier = Modifier.padding(start = 5.dp))
            }
        }
}

@Composable fun ReplyCommentRow(vm: AppViewModel, post: Post) {
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    val route = vm.route
    val offline = route.kind == "offline"
    var expanded by remember(post.id) { mutableStateOf(false) }
    val openSubposts: () -> Unit = { vm.data.thread?.let { vm.navigate(Route("subposts", route.id, title = it.thread.forum, extra = "${post.id}:${it.forumId}")) } }
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 16.dp, vertical = 12.dp).semantics { contentDescription = "回复：${post.author.name}" }) {
        PostAuthorHeader(vm, post)
        Column(Modifier.padding(start = 48.dp)) {
            SelectionContainer { Column { ContentBlocksView(post.blocks, vm) } }
            val metadata = listOfNotNull(
                post.floor.takeIf { it > 0 && route.kind != "subposts" }?.let { "${it}楼" },
                commentMetadata(post.timestamp, post.ipAddress, if (route.kind == "subposts") PostTimestampFormat.SUBPOST else null).takeIf(String::isNotBlank)
            ).joinToString(" · ")
            if (metadata.isNotBlank() || !offline && vm.account?.uid == post.author.id) Row(Modifier.fillMaxWidth().padding(top = 5.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(metadata, fontSize = 12.sp, color = secondary, modifier = Modifier.weight(1f))
                DeleteCommentAction(vm, post)
            }
            if (post.children.isNotEmpty() || !offline && post.subCount > 0 && route.kind == "thread") {
                Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background, RoundedCornerShape(IOSLayout.innerRadius)).padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    post.children.take(if (expanded) Int.MAX_VALUE else 3).forEach { child ->
                        NativeEmoticonText(child.blocks, prefix = child.author.name + "：", fontSize = 15.sp, maxLines = if (offline) Int.MAX_VALUE else 6,
                            modifier = Modifier.clickable(enabled = !offline && route.kind == "thread", onClick = openSubposts))
                    }
                    if (offline && post.children.size > 3) Text(if (expanded) "收起回复" else "展开全部${post.children.size}条本地回复", color = IOSLayout.blue, fontSize = 13.sp, modifier = Modifier.heightIn(min = 32.dp).wrapContentHeight().clickable { expanded = !expanded })
                    else if (!offline && route.kind == "thread") Text("查看全部${maxOf(post.subCount, post.children.size)}条回复 ›", color = IOSLayout.blue, fontSize = 13.sp, modifier = Modifier.heightIn(min = 32.dp).wrapContentHeight().clickable(onClick = openSubposts))
                }
            }
        }
    }
    HorizontalDivider(Modifier.padding(horizontal = 16.dp), thickness = .5.dp)
}

@Composable private fun DeleteCommentAction(vm: AppViewModel, post: Post) {
    val route = vm.route
    val subpost = route.kind == "subposts"
    val fid = if (subpost) route.extra.split(':').getOrNull(1).orEmpty() else vm.data.thread?.forumId.orEmpty()
    val forum = if (subpost) route.title else vm.data.thread?.thread?.forum.orEmpty()
    if (route.kind !in setOf("thread", "subposts") || vm.account?.uid != post.author.id || post.author.id.toLongOrNull()?.let { it > 0 } != true || !subpost && post.floor <= 1 || fid.toLongOrNull()?.let { it > 0 } != true) return
    var confirm by remember(post.id, vm.account?.uid) { mutableStateOf(false) }
    var deleting by remember(post.id, vm.account?.uid) { mutableStateOf(false) }
    Text(if (deleting) "删除中…" else "删除", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 12.dp).testTag("delete-comment-${post.id}").clickable(enabled = !deleting, role = Role.Button) { confirm = true })
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("删除这条评论？") }, text = { Text("仅删除你发布的这条评论，此操作无法撤销。") }, confirmButton = {
        TextButton(onClick = {
            if (deleting) return@TextButton
            confirm = false; deleting = true
            vm.action(onSuccess = { deleting = false; vm.removeDeletedComment(post.id, route) }) { account ->
                try { vm.api.deleteOwnComment(account, route.id, fid, forum, post, subpost); "评论已删除" }
                finally { kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable + kotlinx.coroutines.Dispatchers.Main) { deleting = false } }
            }
        }) { Text("确认删除", color = MaterialTheme.colorScheme.error) }
    }, dismissButton = { TextButton(onClick = { confirm = false }) { Text("取消") } })
}
