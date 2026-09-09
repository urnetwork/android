package com.bringyour.network.ui.leaderboard

/**
 * Pure rules behind the points leaderboard screen, kept free of Compose and of
 * the sdk (whose class init loads the native library) so they unit test on the
 * jvm. The sdk view controller owns the data; these only decide WHEN the
 * screen asks it for more and HOW the emoji editor reads a validation.
 */
object PointsLeaderboardPaging {

    /** rows from the end at which the next page is requested */
    const val LOAD_MORE_THRESHOLD = 10

    /**
     * True when the list has scrolled close enough to its end that the next
     * page should be requested. `lastVisibleRowIndex` is the index into the
     * ROWS (header and footer items excluded); -1 when no row is visible.
     * The controller itself refuses a second in-flight page and a page past
     * the end, so this only avoids asking in the first place.
     *
     * A failed page never auto-retries: the screen re-evaluates this rule on
     * every loading flip, so without the `hasError` gate a page that keeps
     * failing near the end of the list was requested again the instant it
     * failed, a hot retry loop against the api. The footer's Try again is the
     * retry.
     */
    fun shouldLoadMore(
        lastVisibleRowIndex: Int,
        rowCount: Int,
        isLoading: Boolean,
        isEndReached: Boolean,
        hasError: Boolean = false,
        threshold: Int = LOAD_MORE_THRESHOLD,
    ): Boolean {
        if (rowCount <= 0 || isLoading || isEndReached || hasError || lastVisibleRowIndex < 0) {
            return false
        }
        return lastVisibleRowIndex >= rowCount - 1 - threshold
    }

    /**
     * True when the list has scrolled close enough to the start of a seeked
     * window that the page before it should be requested. `firstVisibleRowIndex`
     * is the index into the ROWS of the first visible item (negative while the
     * header above the rows is on screen). Same error gate as forward paging.
     */
    fun shouldLoadMoreBefore(
        firstVisibleRowIndex: Int,
        rowCount: Int,
        isLoading: Boolean,
        hasMoreBefore: Boolean,
        hasError: Boolean = false,
        threshold: Int = LOAD_MORE_THRESHOLD,
    ): Boolean {
        if (rowCount <= 0 || isLoading || !hasMoreBefore || hasError) {
            return false
        }
        return firstVisibleRowIndex <= threshold
    }
}

/** Why the sdk rejected an emoji tag; mirrors `Sdk.EmojiTagReason*`. */
enum class EmojiTagError {
    EMPTY,
    TOO_MANY,
    NOT_EMOJI,
}

object EmojiTagEditor {

    // the sdk's reason strings (`Sdk.EmojiTagReasonEmpty` etc.), repeated here
    // as literals so this file never touches the sdk class
    private const val REASON_EMPTY = "empty"
    private const val REASON_TOO_MANY = "too_many"
    private const val REASON_NOT_EMOJI = "not_emoji"

    /** The editor error for a rejected validation; null when the tag is ok. */
    fun errorFor(ok: Boolean, reason: String?): EmojiTagError? {
        if (ok) {
            return null
        }
        return when (reason) {
            REASON_EMPTY -> EmojiTagError.EMPTY
            REASON_TOO_MANY -> EmojiTagError.TOO_MANY
            // an unknown reason from a newer sdk still reads as "not emoji":
            // the only other way a tag is rejected
            else -> EmojiTagError.NOT_EMOJI
        }
    }

    /**
     * Save is offered only for a valid tag that differs from what is stored.
     * The sdk's normalized form is what gets sent, so the comparison is on it.
     */
    fun canSave(ok: Boolean, normalized: String, currentTag: String, isSaving: Boolean): Boolean {
        return ok && !isSaving && normalized.isNotEmpty() && normalized != currentTag
    }

    /**
     * An empty field is not an error while the user is still typing (or
     * clearing): the counter reads "0 / max" instead of "add an emoji".
     */
    fun showsError(text: String, error: EmojiTagError?): Boolean {
        return error != null && !(text.isEmpty() && error == EmojiTagError.EMPTY)
    }

    /**
     * The tag without its last emoji: the editor's backspace. One emoji can
     * be several code points (skin tones, flags, ZWJ sequences), so the cut
     * is at the last grapheme boundary, never inside a sequence.
     */
    fun dropLastEmoji(tag: String): String {
        if (tag.isEmpty()) {
            return tag
        }
        val it = java.text.BreakIterator.getCharacterInstance()
        it.setText(tag)
        val end = it.last()
        val start = it.previous()
        if (start == java.text.BreakIterator.DONE || start < 0 || start >= end) {
            return ""
        }
        return tag.substring(0, start)
    }
}

/**
 * Pure geometry of the draggable position indicator on the points list
 * (mmm/DESIGNSTYLE.md "Long ranked lists"): the track spans positions 1..N of
 * the sdk's total order, the thumb's top marks the position at the top of the
 * screen and its length is the loaded window, and a drag maps the thumb's
 * place on the track back to a rank. Free of Compose so it unit tests on the
 * jvm; the composable feeds it pixels and gets pixels back.
 */
object PointsLeaderboardIndicator {
    /** The thumb never shrinks below this, so it stays a hand-sized target. */
    const val MIN_THUMB_DP = 44

    /** Thumb top and height in pixels on the track. */
    data class Thumb(val topPx: Float, val heightPx: Float)

    /**
     * The thumb whose top marks `position` (1-based) of `total`, sized to the
     * loaded window of `windowRows` rows, on a track `trackPx` tall with a
     * `minThumbPx` floor. The thumb travels the track minus its own height so
     * its top reaches position 1 at the top and position N at the bottom.
     * Null when there is nothing to show.
     */
    fun thumb(position: Long, windowRows: Long, total: Long, trackPx: Float, minThumbPx: Float): Thumb? {
        if (total <= 0L || position <= 0L || windowRows <= 0L || trackPx <= 0f) {
            return null
        }
        val windowPx = (windowRows.toFloat() / total.toFloat()).coerceIn(0f, 1f) * trackPx
        val heightPx = maxOf(windowPx, minThumbPx).coerceIn(0f, trackPx)
        val topPx = fractionOf(position, total) * (trackPx - heightPx)
        return Thumb(topPx = topPx, heightPx = heightPx)
    }

    /**
     * The rank under the thumb's top edge for a fraction of its travel,
     * clamped to 1..total; 0 is the first rank and 1 the last.
     */
    fun rankAt(fraction: Float, total: Long): Long {
        if (total <= 0L) {
            return 1L
        }
        val f = fraction.coerceIn(0f, 1f)
        return (1L + Math.round(f * (total - 1).toFloat())).coerceIn(1L, total)
    }

    /** The fraction of the thumb's travel that puts its top edge at `rank`. */
    fun fractionOf(rank: Long, total: Long): Float {
        if (total <= 1L) {
            return 0f
        }
        return ((rank.coerceIn(1L, total) - 1).toFloat() / (total - 1).toFloat()).coerceIn(0f, 1f)
    }

    /** The fraction of travel for a thumb top at `topPx` given its `thumb`. */
    fun fractionAt(topPx: Float, trackPx: Float, thumbHeightPx: Float): Float {
        val travel = trackPx - thumbHeightPx
        if (travel <= 0f) {
            return 0f
        }
        return (topPx / travel).coerceIn(0f, 1f)
    }

    /**
     * Whether the indicator is shown at all: only for a list longer than the
     * viewport (more rows than fit, or more pages than loaded) with something
     * ranked.
     */
    fun isVisible(total: Long, loadedRows: Int, visibleRows: Int, hasMoreBefore: Boolean, hasMoreAfter: Boolean): Boolean {
        if (total <= 0L || loadedRows <= 0) {
            return false
        }
        return hasMoreBefore || hasMoreAfter || loadedRows > visibleRows
    }
}

/**
 * What tapping a leaderboard tab does to its list (design rule: a tab tap,
 * including re-tapping the selected tab, scrolls to the top).
 */
object LeaderboardTabReset {
    enum class Action { SCROLL_TO_TOP, RELOAD_FROM_TOP }

    /**
     * For the Points list: a window that no longer starts at position 1 (after
     * a seek) reloads from the top, otherwise the list just scrolls up. The
     * Data list always just scrolls.
     */
    fun onTabTap(isPointsTab: Boolean, firstLoadedPosition: Long): Action {
        if (isPointsTab && firstLoadedPosition > 1L) {
            return Action.RELOAD_FROM_TOP
        }
        return Action.SCROLL_TO_TOP
    }
}
