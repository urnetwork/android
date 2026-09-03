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
