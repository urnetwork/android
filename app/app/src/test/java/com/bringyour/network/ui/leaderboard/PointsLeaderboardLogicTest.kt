package com.bringyour.network.ui.leaderboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PointsLeaderboardLogicTest {

    @Test
    fun loadsMoreWithinThresholdOfTheEnd() {
        // 50 rows, threshold 10: rows 39..49 visible at the bottom ask for more
        assertTrue(PointsLeaderboardPaging.shouldLoadMore(39, 50, isLoading = false, isEndReached = false))
        assertTrue(PointsLeaderboardPaging.shouldLoadMore(49, 50, isLoading = false, isEndReached = false))
        assertFalse(PointsLeaderboardPaging.shouldLoadMore(38, 50, isLoading = false, isEndReached = false))
        assertFalse(PointsLeaderboardPaging.shouldLoadMore(0, 50, isLoading = false, isEndReached = false))
    }

    @Test
    fun neverLoadsWhileLoadingAtTheEndOrWithoutRows() {
        assertFalse(PointsLeaderboardPaging.shouldLoadMore(49, 50, isLoading = true, isEndReached = false))
        assertFalse(PointsLeaderboardPaging.shouldLoadMore(49, 50, isLoading = false, isEndReached = true))
        // the first page is the controller's `start`, not a scroll
        assertFalse(PointsLeaderboardPaging.shouldLoadMore(-1, 0, isLoading = false, isEndReached = false))
        assertFalse(PointsLeaderboardPaging.shouldLoadMore(-1, 50, isLoading = false, isEndReached = false))
    }

    @Test
    fun aFailedPageIsNotRetriedByScrolling() {
        // near the end with the controller reporting an error: the footer's
        // Try again retries, not the scroll rule (which re-runs on every
        // loading flip and would otherwise hammer the api)
        assertFalse(PointsLeaderboardPaging.shouldLoadMore(49, 50, isLoading = false, isEndReached = false, hasError = true))
        assertTrue(PointsLeaderboardPaging.shouldLoadMore(49, 50, isLoading = false, isEndReached = false, hasError = false))
    }

    @Test
    fun shortListsAskForMoreAsSoonAsTheyShow() {
        // fewer rows than the threshold: the last row is always within reach
        assertTrue(PointsLeaderboardPaging.shouldLoadMore(2, 3, isLoading = false, isEndReached = false))
        assertTrue(PointsLeaderboardPaging.shouldLoadMore(0, 3, isLoading = false, isEndReached = false))
    }

    @Test
    fun mapsTheSdkReasons() {
        assertNull(EmojiTagEditor.errorFor(ok = true, reason = ""))
        assertEquals(EmojiTagError.EMPTY, EmojiTagEditor.errorFor(ok = false, reason = "empty"))
        assertEquals(EmojiTagError.TOO_MANY, EmojiTagEditor.errorFor(ok = false, reason = "too_many"))
        assertEquals(EmojiTagError.NOT_EMOJI, EmojiTagEditor.errorFor(ok = false, reason = "not_emoji"))
        // a reason this build does not know still blocks the save as not-emoji
        assertEquals(EmojiTagError.NOT_EMOJI, EmojiTagEditor.errorFor(ok = false, reason = "something_new"))
        assertEquals(EmojiTagError.NOT_EMOJI, EmojiTagEditor.errorFor(ok = false, reason = null))
    }

    @Test
    fun saveNeedsAValidChangedTag() {
        assertTrue(EmojiTagEditor.canSave(ok = true, normalized = "🐬🔥", currentTag = "", isSaving = false))
        assertTrue(EmojiTagEditor.canSave(ok = true, normalized = "🐬🔥", currentTag = "🐬", isSaving = false))
        assertFalse(EmojiTagEditor.canSave(ok = true, normalized = "🐬🔥", currentTag = "🐬🔥", isSaving = false))
        assertFalse(EmojiTagEditor.canSave(ok = false, normalized = "", currentTag = "", isSaving = false))
        assertFalse(EmojiTagEditor.canSave(ok = true, normalized = "🐬", currentTag = "", isSaving = true))
    }

    @Test
    fun anEmptyFieldReadsAsACounterNotAnError() {
        assertFalse(EmojiTagEditor.showsError("", EmojiTagError.EMPTY))
        assertFalse(EmojiTagEditor.showsError("", null))
        assertTrue(EmojiTagEditor.showsError("abc", EmojiTagError.NOT_EMOJI))
        assertTrue(EmojiTagEditor.showsError("🐬🐬🐬🐬🐬🐬🐬", EmojiTagError.TOO_MANY))
        // whitespace-only is not "empty" to the user: the sdk rejects it as
        // not-emoji and that must show
        assertTrue(EmojiTagEditor.showsError(" ", EmojiTagError.NOT_EMOJI))
    }

    @Test
    fun backspaceDropsOneEmojiAtATime() {
        assertEquals("🐬", EmojiTagEditor.dropLastEmoji("🐬🔥"))
        assertEquals("", EmojiTagEditor.dropLastEmoji("🐬"))
        assertEquals("", EmojiTagEditor.dropLastEmoji(""))
        // a flag is two code points but one emoji
        assertEquals("🐬", EmojiTagEditor.dropLastEmoji("🐬🇫🇷"))
    }
}
