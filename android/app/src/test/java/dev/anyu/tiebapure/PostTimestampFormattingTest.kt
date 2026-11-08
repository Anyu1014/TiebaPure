package dev.anyu.tiebapure

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class PostTimestampFormattingTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    @Test fun mainPostAndSubpostUseTheRequestedAbsoluteFormats() {
        val time = Instant.parse("2026-10-25T00:58:07Z").epochSecond
        assertEquals("08:58:07「10-25」", postTimestampText(time, PostTimestampFormat.MAIN_POST, zone))
        assertEquals("08:58:07「10-25」", postTimestampText(time, PostTimestampFormat.SUBPOST, zone))
        assertEquals("08:58:07「10-25」  墨西哥", commentMetadata(time, "IP属地：墨西哥", PostTimestampFormat.MAIN_POST, zone))
        assertEquals("08:58:07「10-25」  河北", commentMetadata(time, "河北", PostTimestampFormat.SUBPOST, zone))
        assertEquals("08:58:07「10-25」  河北", commentMetadata(time, "河北", zone = zone))
    }
    @Test fun localDayRolloverAndMissingTimesStayCorrect() {
        val time = Instant.parse("2026-12-31T16:02:03Z").epochSecond
        assertEquals("00:02:03「01-01」", postTimestampText(time, PostTimestampFormat.MAIN_POST, zone))
        assertEquals("00:02:03「01-01」", postTimestampText(time, PostTimestampFormat.SUBPOST, zone))
        assertEquals("16:02:03「12-31」", postTimestampText(time, PostTimestampFormat.MAIN_POST, ZoneId.of("UTC")))
        for (missing in listOf(0L, -1L, Long.MAX_VALUE)) {
            assertEquals("", postTimestampText(missing, PostTimestampFormat.MAIN_POST, zone))
            assertEquals("浙江", commentMetadata(missing, "浙江", PostTimestampFormat.SUBPOST, zone))
        }
    }
}
