package dev.anyu.tiebapure

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

internal enum class PostTimestampFormat(pattern: String) {
    MAIN_POST("HH:mm:ss'「'MM-dd'」'"),
    SUBPOST("HH:mm:ss'「'MM-dd'」'");

    val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern(pattern, Locale.ROOT)
}

internal fun postTimestampText(epoch: Long, format: PostTimestampFormat, zone: ZoneId = ZoneId.systemDefault()): String {
    if (epoch !in 1..253402300799L) return ""
    return Instant.ofEpochSecond(epoch).atZone(zone).format(format.formatter)
}

fun readerDateText(epoch: Long, now: Long = System.currentTimeMillis() / 1000, zone: ZoneId = ZoneId.systemDefault()): String {
    if (epoch !in 1..253402300799L) return ""
    val elapsed = (now - epoch).coerceAtLeast(0)
    if (elapsed < 60) return "刚刚"
    if (elapsed < 3600) return "${elapsed / 60}分钟前"
    val date = Instant.ofEpochSecond(epoch).atZone(zone)
    val today = Instant.ofEpochSecond(now).atZone(zone).toLocalDate()
    val pattern = when {
        date.toLocalDate() == today -> "HH:mm"
        date.toLocalDate() == today.minusDays(1) -> "'昨天 'HH:mm"
        date.year == today.year -> "MM-dd HH:mm"
        else -> "yyyy-MM-dd"
    }
    return DateTimeFormatter.ofPattern(pattern).format(date)
}

private val conversationClock = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
private val conversationWeekday = DateTimeFormatter.ofPattern("EEEE", Locale.SIMPLIFIED_CHINESE)
private val conversationMonthDay = DateTimeFormatter.ofPattern("M月d日", Locale.SIMPLIFIED_CHINESE)

internal fun privateConversationDateText(epoch: Long, now: Long = System.currentTimeMillis() / 1000, zone: ZoneId = ZoneId.systemDefault()): String {
    if (epoch !in 1..253402300799L) return ""
    val time = Instant.ofEpochSecond(epoch).atZone(zone)
    val date = time.toLocalDate()
    val today = Instant.ofEpochSecond(now).atZone(zone).toLocalDate()
    val monday = TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)
    return when {
        date == today -> time.format(conversationClock)
        date == today.minusDays(1) -> "昨天 ${time.format(conversationClock)}"
        date.with(monday) == today.with(monday) -> time.format(conversationWeekday)
        else -> time.format(conversationMonthDay)
    }
}
