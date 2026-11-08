package dev.anyu.tiebapure

internal data class ForumPinnedPresentation(val pinnedThreads: List<Thread>, val visibleThreads: List<Thread>)

internal fun forumPinnedPresentation(threads: List<Thread>, showsPinnedThreads: Boolean): ForumPinnedPresentation {
    val (pinned, regular) = threads.partition { it.isTop }
    return ForumPinnedPresentation(pinned, if (showsPinnedThreads) pinned + regular else regular)
}
