package com.bringyour.network.utils

import android.text.format.DateUtils
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The shared relative time (split rule actions, Account -> Sessions): which
 * platform format a time takes. The formats themselves are android's (ICU's
 * "now", DateUtils' spans and its date past seven days), which these JVM tests
 * replace with a recording fake.
 */
class RelativeTimeTest {

    private class RecordingFormats : RelativeTimeFormats {
        val calls = mutableListOf<String>()

        override fun now(): String {
            calls.add("now")
            return "now"
        }

        override fun span(timeMillis: Long, nowMillis: Long, flags: Int): String {
            calls.add("span:$timeMillis:$nowMillis:$flags")
            return "span"
        }
    }

    private val nowMillis = 1_800_000_000_000L

    @Test
    fun underFiveSecondsReadsAsNow() {
        val formats = RecordingFormats()
        assertEquals("now", formatRelativeTime(nowMillis, nowMillis, 0, formats))
        assertEquals("now", formatRelativeTime(nowMillis - 4_999, nowMillis, 0, formats))
        assertEquals(listOf("now", "now"), formats.calls)
    }

    @Test
    fun aTimeAheadOfThisClockReadsAsNow() {
        // the server's clock can run ahead of the device's
        val formats = RecordingFormats()
        assertEquals("now", formatRelativeTime(nowMillis + 120_000, nowMillis, 0, formats))
        assertEquals(listOf("now"), formats.calls)
    }

    @Test
    fun fromFiveSecondsDateUtilsAbbreviatesTheSpan() {
        val formats = RecordingFormats()
        val fiveSecondsAgo = nowMillis - 5_000
        val sixDaysAgo = nowMillis - 6 * DateUtils.DAY_IN_MILLIS
        val eightDaysAgo = nowMillis - 8 * DateUtils.DAY_IN_MILLIS
        for (timeMillis in listOf(fiveSecondsAgo, sixDaysAgo, eightDaysAgo)) {
            assertEquals("span", formatRelativeTime(timeMillis, nowMillis, 0, formats))
        }
        // DateUtils turns the span into the date itself from seven days on
        val flags = DateUtils.FORMAT_ABBREV_RELATIVE
        assertEquals(
            listOf(
                "span:$fiveSecondsAgo:$nowMillis:$flags",
                "span:$sixDaysAgo:$nowMillis:$flags",
                "span:$eightDaysAgo:$nowMillis:$flags",
            ),
            formats.calls,
        )
    }

    @Test
    fun dateFlagsStyleTheDateWithoutChangingTheSpan() {
        // Sessions abbreviates the month of a date past seven days; the split
        // rules keep DateUtils' plain flags
        val formats = RecordingFormats()
        val eightDaysAgo = nowMillis - 8 * DateUtils.DAY_IN_MILLIS
        formatRelativeTime(eightDaysAgo, nowMillis, DateUtils.FORMAT_ABBREV_MONTH, formats)
        assertEquals(
            listOf("span:$eightDaysAgo:$nowMillis:${DateUtils.FORMAT_ABBREV_RELATIVE or DateUtils.FORMAT_ABBREV_MONTH}"),
            formats.calls,
        )
    }
}
