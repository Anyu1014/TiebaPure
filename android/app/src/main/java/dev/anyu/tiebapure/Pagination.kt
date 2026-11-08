package dev.anyu.tiebapure

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.material3.pulltorefresh.*

internal fun List<Post>.inReplyOrder(sort: Int): List<Post> {
    val main = filter { it.floor == 1 }
    val replies = filter { it.floor != 1 }
    return main + when (sort) {
        0 -> replies.sortedBy { it.floor }
        1 -> replies.sortedByDescending { it.floor }
        else -> replies
    }
}

internal fun Thread.withThreadCounters(updated: Thread): Thread = copy(
    replies = updated.replies,
    // A matching reply has its own count, independent of its parent thread.
    likes = if (matchedPost.isEmpty()) updated.likes ?: likes else likes
)

internal fun ScreenData.withThreadCounters(updated: Thread): ScreenData = copy(
    threads = threads.map { if (it.id == updated.id) it.withThreadCounters(updated) else it },
    thread = thread?.let { if (it.thread.id == updated.id) it.copy(thread = it.thread.copy(replies = updated.replies, likes = updated.likes)) else it }
)

internal fun ScreenData.replacingComments(previous: ScreenData): ScreenData {
    val freshMain = posts.firstOrNull { it.floor == 1 }
    val main = previous.posts.filter { it.floor == 1 }.ifEmpty { listOfNotNull(freshMain) }.map {
        it.copy(likes = thread?.thread?.likes ?: it.likes, liked = freshMain?.liked ?: it.liked)
    }
    val updatedPosts = main + posts.filter { it.floor != 1 }
    return copy(
        posts = updatedPosts,
        thread = thread?.let { incoming ->
            incoming.copy(thread = previous.thread?.thread?.copy(replies = incoming.thread.replies, likes = incoming.thread.likes) ?: incoming.thread, posts = updatedPosts)
        }
    )
}

internal fun ScreenData.appendTo(previous: ScreenData): ScreenData {
    val merged = copy(
        threads = (previous.threads + threads).distinctBy { it.id to it.matchedPost },
        posts = (previous.posts + posts).distinctBy { it.id },
        users = (previous.users + users).distinctBy { it.id },
        likes = likes?.let { it.copy(items = (previous.likes?.items.orEmpty() + it.items).distinctBy { item -> item.id }) }
    )
    // shortcut: a fully blocked page stops pagination; track server cursors if hidden pages must be skipped.
    val progressed = merged.threads.size > previous.threads.size || merged.posts.size > previous.posts.size || merged.users.size > previous.users.size
    return merged.copy(hasMore = hasMore && (likes != null || progressed))
}

internal fun threadReadingPosition(index: Int, postIds: List<String>, pages: Map<String, Int>, fallbackPage: Int): Pair<Int, Int> {
    if (postIds.isEmpty()) return fallbackPage to 0
    if (index <= 0) return (pages[postIds.first()] ?: fallbackPage) to 0
    val postIndex = (index - 1).coerceAtMost(postIds.lastIndex)
    val page = pages[postIds[postIndex]] ?: fallbackPage
    return page to (postIds.take(postIndex).count { pages[it] == page } + 1)
}

@Composable fun AutomaticPageFooter(vm: AppViewModel) {
    LaunchedEffect(vm.route, vm.page, vm.loading, vm.error) { vm.loadNextPage() }
    Box(Modifier.fillMaxWidth().heightIn(min = 52.dp), contentAlignment = Alignment.Center) {
        if (vm.loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
        else if (vm.error.isNotBlank()) TextButton(onClick = vm::retryLoad) { Text("加载失败，点击重试") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun PullRefreshContent(vm: AppViewModel, modifier: Modifier, topInset: Dp, content: @Composable BoxScope.() -> Unit) {
    if (vm.route.kind == "offline") { Box(modifier, content = content); return }
    val state = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = vm.refreshing,
        onRefresh = { if (!vm.loading) vm.load(1) },
        modifier = modifier,
        state = state,
        indicator = { PullToRefreshDefaults.Indicator(state, vm.refreshing, Modifier.align(Alignment.TopCenter).padding(top = topInset)) },
        content = content
    )
}
