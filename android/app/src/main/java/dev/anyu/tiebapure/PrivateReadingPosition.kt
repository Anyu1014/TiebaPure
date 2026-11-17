package dev.anyu.tiebapure

internal data class PrivateReadingPosition(val anchor: String, val offset: Int, val latest: String, val atBottom: Boolean)

internal object PrivateReadingPolicy {
    fun boundary(position: PrivateReadingPosition?, ids: List<String>): Int? {
        if (position == null || !position.atBottom) return null
        val index = ids.indexOf(position.latest)
        return (index + 1).takeIf { index >= 0 && it < ids.size }
    }
    fun target(position: PrivateReadingPosition?, ids: List<String>): String? {
        if (position == null) return null
        return if (position.atBottom) boundary(position, ids)?.let { ids[it] } else position.anchor.takeIf { it in ids }
    }
    fun needsHistory(position: PrivateReadingPosition?, ids: List<String>, hasMore: Boolean): Boolean {
        val id = if (position?.atBottom == true) position.latest else position?.anchor
        return id != null && ids.isNotEmpty() && id !in ids && hasMore
    }
}
