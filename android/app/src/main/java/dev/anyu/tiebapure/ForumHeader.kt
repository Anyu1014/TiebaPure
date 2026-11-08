package dev.anyu.tiebapure

import androidx.compose.foundation.background
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*

@Composable fun ForumPinnedToggle(count: Int, expanded: Boolean, onToggle: () -> Unit) {
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    val arrowRotation by animateFloatAsState(if (expanded) 180f else 0f, tween(240, easing = FastOutSlowInEasing), label = "pinned-arrow")
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).heightIn(min = 44.dp)
        .testTag("forum-pinned-threads-toggle").semantics { stateDescription = if (expanded) "已展开" else "已收起" }
        .clickable(role = Role.Button, onClickLabel = if (expanded) "收起${count}条置顶内容" else "展开${count}条置顶内容", onClick = onToggle)
        .padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(TiebaIcons.Pin, null, Modifier.size(17.dp), tint = secondary)
        Text("置顶内容", fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Text(count.toString(), fontSize = 12.sp, color = secondary)
        Spacer(Modifier.weight(1f))
        Icon(TiebaIcons.ChevronDown, null, Modifier.size(14.dp).rotate(arrowRotation), tint = secondary)
    }
    HorizontalDivider(thickness = 0.5.dp)
}

@Composable fun ForumCategoryPicker(vm: AppViewModel) {
    val route = vm.route
    var menu by remember(route.id) { mutableStateOf(false) }
    var latestSort by remember(route.id) { mutableIntStateOf(if (route.extra == "publish") 1 else 0) }
    val featured = route.extra == "good"
    LaunchedEffect(route.extra) { if (!featured) latestSort = if (route.extra == "publish") 1 else 0 }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(min = 48.dp).testTag("forum-category-picker"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box {
            ForumCategoryLabel("最新", !featured, true, Modifier.testTag("forum-category-latest-menu").semantics {
                stateDescription = if (latestSort == 1) "发帖时间排序" else "回复时间排序"
            }) { menu = true }
            DropdownMenu(menu, { menu = false }) {
                listOf(0 to "回复时间排序", 1 to "发帖时间排序").forEach { (sort, title) ->
                    DropdownMenuItem(text = { Text(title) }, leadingIcon = {
                        if (latestSort == sort) Icon(Icons.Default.Check, null) else Spacer(Modifier.size(24.dp))
                    }, modifier = Modifier.testTag(if (sort == 0) "forum-sort-reply-time" else "forum-sort-publish-time"), onClick = {
                        menu = false
                        latestSort = sort
                        vm.selectForumCategory(sort)
                    })
                }
            }
        }
        ForumCategoryLabel("精华", featured, false, Modifier.testTag("forum-category-featured")) { vm.selectForumCategory(-1) }
    }
}

@Composable private fun ForumCategoryLabel(title: String, selected: Boolean, arrow: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val color = if (selected) IOSLayout.blue else MaterialTheme.colorScheme.onSurface.copy(alpha = .5f)
    Box(modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onClick).semantics { this.selected = selected }, contentAlignment = Alignment.Center) {
        Row(Modifier.background(if (selected) IOSLayout.blue.copy(alpha = .10f) else Color.Transparent, CircleShape).padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontSize = 14.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, color = color)
            if (arrow) Icon(TiebaIcons.ChevronDown, null, Modifier.size(12.dp), tint = color)
        }
    }
}

@Composable fun ForumToolbarActions(vm: AppViewModel, onSearch: () -> Unit) {
    val forum = vm.route.id
    val account = vm.account
    val scope = rememberCoroutineScope()
    var menu by remember(forum) { mutableStateOf(false) }
    var followed by remember(forum, account) { mutableStateOf<Boolean?>(null) }
    var busy by remember(forum, account) { mutableStateOf(false) }
    suspend fun readMembership() {
        val session = account ?: return
        busy = true
        try {
            val forums = withContext(Dispatchers.IO) { vm.api.followedForums(session) }
            if (session.sessionMatches(vm.account)) followed = forums.any { it.name.trim().removeSuffix("吧") == forum.trim().removeSuffix("吧") }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { if (session.sessionMatches(vm.account)) vm.report(e.message ?: "关注状态加载失败，请重试") }
        finally { busy = false }
    }
    LaunchedEffect(forum, account) { readMembership() }
    fun toggleFollow() {
        if (vm.account == null) { vm.report("请先登录百度账号"); return }
        if (busy) return
        val current = followed
        if (current == null) { scope.launch { readMembership() }; return }
        busy = true
        vm.action(onSuccess = { followed = !current }) { session ->
            try { vm.api.followForum(session, forum, !current); if (current) "已取消关注贴吧" else "已关注贴吧" }
            finally { withContext(NonCancellable + Dispatchers.Main) { busy = false } }
        }
    }
    val followLabel = if (followed == true) "取消关注贴吧" else if (account != null && followed == null) "刷新关注状态" else "关注贴吧"
    IconButton(onClick = ::toggleFollow, enabled = !busy, modifier = Modifier.width(44.dp).height(48.dp).testTag("forum-follow-button")) {
        if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        else Icon(if (followed == true) Icons.Default.Star else if (account != null && followed == null) Icons.Default.Refresh else TiebaIcons.Star, followLabel, Modifier.size(18.dp))
    }
    if (vm.newThreadsEnabled) IconButton(onClick = { vm.compose(forum) }, modifier = Modifier.width(44.dp).height(48.dp).testTag("forum-new-thread-button")) {
        Icon(TiebaIcons.Compose, "发布新帖", Modifier.size(18.dp))
    }
    IconButton(onClick = onSearch, modifier = Modifier.width(44.dp).height(48.dp).testTag("forum-search-button")) { Icon(Icons.Default.Search, "搜索本吧", Modifier.size(18.dp)) }
    Box {
        IconButton(onClick = { menu = true }, modifier = Modifier.width(44.dp).height(48.dp).testTag("forum-more-menu")) { Icon(Icons.Default.MoreVert, "更多贴吧操作", Modifier.rotate(90f).size(18.dp)) }
        DropdownMenu(menu, { menu = false }) {
            DropdownMenuItem(text = { Text("刷新") }, enabled = !vm.loading, onClick = { menu = false; vm.load(1) })
            DropdownMenuItem(text = { Text(followLabel) }, enabled = !busy, onClick = { menu = false; toggleFollow() })
        }
    }
}
