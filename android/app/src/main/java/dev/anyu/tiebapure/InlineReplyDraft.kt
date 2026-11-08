package dev.anyu.tiebapure

internal data class InlineReplyTarget(val forum: String, val thread: String, val parent: String? = null, val floor: Int = 1, val subpost: String? = null, val replyTo: String = "") {
    val draftKey get() = listOf(thread, parent.orEmpty(), subpost.orEmpty()).joinToString(":")
    val prompt get() = if (parent == null) "回复帖子" else if (replyTo.isNotBlank()) "回复 $replyTo" else "回复第 $floor 楼"
}
internal data class InlineReplyDraft(val text: String = "", val images: List<String> = emptyList())
