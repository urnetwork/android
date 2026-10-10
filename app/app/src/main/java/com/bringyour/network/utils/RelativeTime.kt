package com.bringyour.network.utils

import android.icu.text.RelativeDateTimeFormatter
import android.text.format.DateUtils

/**
 * A past time relative to now, from the platform formatters, which localize it
 * for every locale. Shared by the split rule actions and Account -> Sessions.
 *
 * - Under five seconds, and for a time ahead of this clock, it reads as "now"
 *   (ICU's RelativeDateTimeFormatter).
 * - Otherwise DateUtils abbreviates the seconds, minutes, hours or days ago,
 *   and from seven days on (DateUtils.WEEK_IN_MILLIS) gives the date instead,
 *   with the year when it is not this year. [dateFlags] are added DateUtils
 *   format flags for that date, such as DateUtils.FORMAT_ABBREV_MONTH.
 */
fun relativeTime(
    timeMillis: Long,
    nowMillis: Long = System.currentTimeMillis(),
    dateFlags: Int = 0,
): String = formatRelativeTime(timeMillis, nowMillis, dateFlags, SystemRelativeTimeFormats)

// under this, a time reads as now
internal const val RELATIVE_TIME_NOW_MILLIS = 5_000L

/** The two platform calls, apart so the choice between them runs without android. */
internal interface RelativeTimeFormats {
    fun now(): String

    // DateUtils.getRelativeTimeSpanString at one second resolution
    fun span(timeMillis: Long, nowMillis: Long, flags: Int): String
}

internal object SystemRelativeTimeFormats : RelativeTimeFormats {
    override fun now(): String = RelativeDateTimeFormatter.getInstance()
        .format(RelativeDateTimeFormatter.Direction.PLAIN, RelativeDateTimeFormatter.AbsoluteUnit.NOW)

    override fun span(timeMillis: Long, nowMillis: Long, flags: Int): String =
        DateUtils.getRelativeTimeSpanString(
            timeMillis,
            nowMillis,
            DateUtils.SECOND_IN_MILLIS,
            flags,
        ).toString()
}

internal fun formatRelativeTime(
    timeMillis: Long,
    nowMillis: Long,
    dateFlags: Int,
    formats: RelativeTimeFormats,
): String {
    if (nowMillis - timeMillis < RELATIVE_TIME_NOW_MILLIS) {
        return formats.now()
    }
    return formats.span(timeMillis, nowMillis, DateUtils.FORMAT_ABBREV_RELATIVE or dateFlags)
}
