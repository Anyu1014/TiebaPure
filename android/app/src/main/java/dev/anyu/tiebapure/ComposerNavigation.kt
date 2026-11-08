package dev.anyu.tiebapure

internal fun MutableList<Route>.completeComposer(threadID: String, isReply: Boolean): Route {
    if (isReply) {
        val threadIndex = indexOfLast { it.kind == "thread" && it.id == threadID }
        val destinationIndex = if (threadIndex >= 0) threadIndex else indexOfLast { it.kind == "subposts" && it.id == threadID }
        if (destinationIndex >= 0) {
            val destination = this[destinationIndex]
            subList(destinationIndex, size).clear()
            return destination
        }
    }
    return Route("thread", threadID)
}
