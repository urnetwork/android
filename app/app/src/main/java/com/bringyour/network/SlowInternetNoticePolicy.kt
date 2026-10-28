package com.bringyour.network

/*
 * The VPN notification's slow internet dwell (apple/DELAY.md §7.2). The
 * in-app pill follows the sdk's level directly; the notification does not,
 * so a connection that dips for a few seconds does not flap the shade. The
 * notice appears only once the level has been above 0 for 30 s of the last
 * 60 s, then stays at least 60 s, and "Internet back to normal" shows for
 * 3 s before the normal content returns. Pure, fed from MainService on the
 * main thread with the monotonic clock, and unit tested
 * (SlowInternetNoticePolicyTest).
 */

/** The notice looks back this far for slow time. */
internal const val SLOW_INTERNET_NOTICE_WINDOW_MILLIS = 60_000L

/** The notice appears once the level has been above 0 this long within the window. */
internal const val SLOW_INTERNET_NOTICE_ENTER_MILLIS = 30_000L

/** Once it appears, the notice stays at least this long, slow or not. */
internal const val SLOW_INTERNET_NOTICE_MIN_SHOW_MILLIS = 60_000L

/** "Internet back to normal" shows this long before the normal content returns. */
internal const val SLOW_INTERNET_RECOVERED_MILLIS = 3_000L

/**
 * While the notice shows, the delay is read again this often: the sdk's
 * listener fires only when a level changes, and the numbers ride along.
 */
internal const val SLOW_INTERNET_NOTICE_REFRESH_MILLIS = 30_000L

internal enum class SlowInternetNoticePhase {
    /** The normal notification content. */
    NONE,

    /** The slow content: the tunnel, the title and the delay lines. */
    SLOW,

    /** "Internet back to normal", for [SLOW_INTERNET_RECOVERED_MILLIS]. */
    RECOVERED,
}

/** A time the level was above 0, from [startMillis] to [endMillis]. */
internal data class SlowInternetSpan(val startMillis: Long, val endMillis: Long)

/**
 * The dwell's state after an observation ([observe]). Times are the
 * monotonic clock's (SystemClock.elapsedRealtime), so they count sleep.
 */
internal data class SlowInternetNotice(
    val phase: SlowInternetNoticePhase = SlowInternetNoticePhase.NONE,
    /** When [phase] began. */
    val phaseSinceMillis: Long = 0L,
    /**
     * What the slow content shows: the latest slow reading. It stays while
     * the notice outlasts the slow level, so the minimum stay shows the
     * numbers it was slow with, not zeros.
     */
    val delay: TransportDelayUi = TransportDelayUi.None,
    /** When the current slow time began, null while the level is 0. */
    val slowSinceMillis: Long? = null,
    /** The slow times that ended within the window, oldest first. */
    val slowSpans: List<SlowInternetSpan> = emptyList(),
)

/**
 * The next state for a reading of the device's transport delay at
 * [nowMillis]. [active] is whether a connection is requested: without one the
 * notice and its history start over, so the next connection is judged on its
 * own. A reading changes the phase only at the dwell's edges: the notice
 * appears when the slow time in the window reaches the threshold, ends its
 * minimum stay with "back to normal" once the level is 0, and comes back
 * whenever the slow time is over the threshold again.
 */
internal fun SlowInternetNotice.observe(
    delay: TransportDelayUi,
    active: Boolean,
    nowMillis: Long,
): SlowInternetNotice {
    if (!active) {
        return SlowInternetNotice(phaseSinceMillis = nowMillis)
    }
    val slow = delay.slow
    val windowStartMillis = nowMillis - SLOW_INTERNET_NOTICE_WINDOW_MILLIS
    var spans = slowSpans.filter { windowStartMillis < it.endMillis }
    var since = slowSinceMillis
    if (slow && since == null) {
        since = nowMillis
    } else if (!slow && since != null) {
        if (since < nowMillis) {
            spans = spans + SlowInternetSpan(since, nowMillis)
        }
        since = null
    }
    val observed = copy(
        delay = if (slow) delay else this.delay,
        slowSinceMillis = since,
        slowSpans = spans,
    )
    val entered = slow &&
        SLOW_INTERNET_NOTICE_ENTER_MILLIS <= observed.slowMillisAt(nowMillis)
    return when (phase) {
        SlowInternetNoticePhase.NONE -> if (entered) {
            observed.copy(phase = SlowInternetNoticePhase.SLOW, phaseSinceMillis = nowMillis)
        } else {
            observed
        }
        SlowInternetNoticePhase.SLOW -> if (
            !slow && SLOW_INTERNET_NOTICE_MIN_SHOW_MILLIS <= nowMillis - phaseSinceMillis
        ) {
            observed.copy(phase = SlowInternetNoticePhase.RECOVERED, phaseSinceMillis = nowMillis)
        } else {
            observed
        }
        SlowInternetNoticePhase.RECOVERED -> when {
            entered -> observed.copy(phase = SlowInternetNoticePhase.SLOW, phaseSinceMillis = nowMillis)
            SLOW_INTERNET_RECOVERED_MILLIS <= nowMillis - phaseSinceMillis ->
                observed.copy(
                    phase = SlowInternetNoticePhase.NONE,
                    phaseSinceMillis = nowMillis,
                    delay = TransportDelayUi.None,
                )
            else -> observed
        }
    }
}

/**
 * Whether the notification says something else after an observation: a
 * phase change, or new numbers while the slow content shows. Anything else
 * (a slow reading too brief to show, the history) leaves it as it is.
 */
internal fun slowInternetNoticeChanged(previous: SlowInternetNotice, next: SlowInternetNotice): Boolean =
    previous.phase != next.phase ||
        (next.phase == SlowInternetNoticePhase.SLOW && previous.delay != next.delay)

/**
 * The reading the slow content shows, null while the notification says
 * anything else: what the dashboard widget's pill follows
 * (WidgetSnapshotWriter.publishSlowInternet). A widget has no recovered
 * state, so "back to normal" is none.
 */
internal val SlowInternetNotice.slowReading: TransportDelayUi?
    get() = if (phase == SlowInternetNoticePhase.SLOW) delay else null

/**
 * When the notice should be observed again with no new reading: the next
 * time its phase can change on its own (the slow time reaching the threshold,
 * the minimum stay or "back to normal" running out) or, while it shows a slow
 * level, the next refresh of its numbers. Null when nothing changes until the
 * level does.
 */
internal fun SlowInternetNotice.nextCheckAtMillis(nowMillis: Long): Long? {
    val candidates = when (phase) {
        SlowInternetNoticePhase.NONE -> listOfNotNull(enterAtMillis(nowMillis))
        SlowInternetNoticePhase.SLOW -> listOf(
            if (slowSinceMillis != null) {
                nowMillis + SLOW_INTERNET_NOTICE_REFRESH_MILLIS
            } else {
                // the level is 0 and the shown numbers stay: only the minimum stay can end
                phaseSinceMillis + SLOW_INTERNET_NOTICE_MIN_SHOW_MILLIS
            }
        )
        SlowInternetNoticePhase.RECOVERED -> listOfNotNull(
            enterAtMillis(nowMillis),
            phaseSinceMillis + SLOW_INTERNET_RECOVERED_MILLIS,
        )
    }
    return candidates.minOrNull()?.coerceAtLeast(nowMillis)
}

/** The slow time within the window that ends at [atMillis], the current slow time running until then. */
internal fun SlowInternetNotice.slowMillisAt(atMillis: Long): Long {
    val windowStartMillis = atMillis - SLOW_INTERNET_NOTICE_WINDOW_MILLIS
    fun overlap(startMillis: Long, endMillis: Long): Long =
        (minOf(endMillis, atMillis) - maxOf(startMillis, windowStartMillis)).coerceAtLeast(0L)
    val ended = slowSpans.sumOf { overlap(it.startMillis, it.endMillis) }
    return ended + (slowSinceMillis?.let { overlap(it, atMillis) } ?: 0L)
}

/**
 * When the slow time in the window first reaches the threshold if the level
 * stays above 0, null while it is 0. The slow time then grows at the clock's
 * rate, except while an ended slow time slides out of the window, when it
 * holds (the slow times never overlap, so at most one slides out at once).
 * So this walks from one slide's edge to the next.
 */
private fun SlowInternetNotice.enterAtMillis(nowMillis: Long): Long? {
    if (slowSinceMillis == null) {
        return null
    }
    var atMillis = nowMillis
    var slowMillis = slowMillisAt(atMillis)
    val edges = slowSpans
        .flatMap { listOf(it.startMillis, it.endMillis) }
        .map { it + SLOW_INTERNET_NOTICE_WINDOW_MILLIS }
        .filter { nowMillis < it }
        .sorted()
    for (edgeMillis in edges) {
        val needMillis = SLOW_INTERNET_NOTICE_ENTER_MILLIS - slowMillis
        if (needMillis <= 0L) {
            return atMillis
        }
        val sliding = slowSpans.any {
            it.startMillis + SLOW_INTERNET_NOTICE_WINDOW_MILLIS <= atMillis &&
                atMillis < it.endMillis + SLOW_INTERNET_NOTICE_WINDOW_MILLIS
        }
        if (!sliding) {
            if (needMillis <= edgeMillis - atMillis) {
                return atMillis + needMillis
            }
            slowMillis += edgeMillis - atMillis
        }
        atMillis = edgeMillis
    }
    // past the last edge nothing slides out: the slow time grows at the clock's rate
    return atMillis + (SLOW_INTERNET_NOTICE_ENTER_MILLIS - slowMillis).coerceAtLeast(0L)
}
