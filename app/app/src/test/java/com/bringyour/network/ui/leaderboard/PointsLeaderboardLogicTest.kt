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

class PointsLeaderboardBackwardPagingTest {
    @Test
    fun asksForThePageBeforeNearTheWindowStart() {
        assertTrue(PointsLeaderboardPaging.shouldLoadMoreBefore(firstVisibleRowIndex = 0, rowCount = 50, isLoading = false, hasMoreBefore = true))
        assertTrue(PointsLeaderboardPaging.shouldLoadMoreBefore(firstVisibleRowIndex = -2, rowCount = 50, isLoading = false, hasMoreBefore = true))
        assertTrue(PointsLeaderboardPaging.shouldLoadMoreBefore(firstVisibleRowIndex = 10, rowCount = 50, isLoading = false, hasMoreBefore = true))
        assertFalse(PointsLeaderboardPaging.shouldLoadMoreBefore(firstVisibleRowIndex = 11, rowCount = 50, isLoading = false, hasMoreBefore = true))
    }

    @Test
    fun neverAsksWhileLoadingAtTheTopOrAfterAnError() {
        assertFalse(PointsLeaderboardPaging.shouldLoadMoreBefore(firstVisibleRowIndex = 0, rowCount = 50, isLoading = true, hasMoreBefore = true))
        assertFalse(PointsLeaderboardPaging.shouldLoadMoreBefore(firstVisibleRowIndex = 0, rowCount = 50, isLoading = false, hasMoreBefore = false))
        assertFalse(PointsLeaderboardPaging.shouldLoadMoreBefore(firstVisibleRowIndex = 0, rowCount = 50, isLoading = false, hasMoreBefore = true, hasError = true))
        assertFalse(PointsLeaderboardPaging.shouldLoadMoreBefore(firstVisibleRowIndex = 0, rowCount = 0, isLoading = false, hasMoreBefore = true))
    }
}

class PointsLeaderboardIndicatorTest {
    @Test
    fun thumbMarksThePositionAndTheWindow() {
        val top = PointsLeaderboardIndicator.thumb(position = 1, windowRows = 50, total = 1000, trackPx = 1000f, minThumbPx = 0f)!!
        assertEquals(0f, top.topPx, 1e-4f)
        assertEquals(50f, top.heightPx, 1e-4f)
        val end = PointsLeaderboardIndicator.thumb(position = 1000, windowRows = 50, total = 1000, trackPx = 1000f, minThumbPx = 0f)!!
        assertEquals(950f, end.topPx, 1e-4f)
        assertEquals(1000f, end.topPx + end.heightPx, 1e-4f)
    }

    @Test
    fun thumbHonoursTheMinimumAndNeverOverhangs() {
        val t = PointsLeaderboardIndicator.thumb(position = 25_000, windowRows = 50, total = 25_698, trackPx = 800f, minThumbPx = 44f)!!
        assertEquals(44f, t.heightPx, 1e-4f)
        assertTrue(t.topPx + t.heightPx <= 800f + 1e-3f)
        val small = PointsLeaderboardIndicator.thumb(position = 1, windowRows = 6, total = 6, trackPx = 30f, minThumbPx = 44f)!!
        assertEquals(30f, small.heightPx, 1e-4f)
        assertEquals(0f, small.topPx, 1e-4f)
    }

    @Test
    fun thumbIsNullWithoutAWindow() {
        assertNull(PointsLeaderboardIndicator.thumb(position = 0, windowRows = 0, total = 10, trackPx = 100f, minThumbPx = 0f))
        assertNull(PointsLeaderboardIndicator.thumb(position = 1, windowRows = 5, total = 0, trackPx = 100f, minThumbPx = 0f))
        assertNull(PointsLeaderboardIndicator.thumb(position = 1, windowRows = 5, total = 10, trackPx = 0f, minThumbPx = 0f))
    }

    @Test
    fun rankFromFractionCoversTheWholeOrder() {
        assertEquals(1L, PointsLeaderboardIndicator.rankAt(0f, 25_698))
        assertEquals(25_698L, PointsLeaderboardIndicator.rankAt(1f, 25_698))
        assertEquals(12_850L, PointsLeaderboardIndicator.rankAt(0.5f, 25_698))
        assertEquals(1L, PointsLeaderboardIndicator.rankAt(-1f, 100))
        assertEquals(100L, PointsLeaderboardIndicator.rankAt(2f, 100))
        assertEquals(1L, PointsLeaderboardIndicator.rankAt(0.7f, 0))
    }

    @Test
    fun dragFractionRoundTripsThroughTheThumb() {
        val total = 25_698L
        for (rank in listOf(1L, 2L, 700L, 12_345L, 25_698L)) {
            val thumb = PointsLeaderboardIndicator.thumb(position = rank, windowRows = 50, total = total, trackPx = 1600f, minThumbPx = 44f)!!
            val fraction = PointsLeaderboardIndicator.fractionAt(thumb.topPx, 1600f, thumb.heightPx)
            assertEquals(rank, PointsLeaderboardIndicator.rankAt(fraction, total))
        }
        assertEquals(0f, PointsLeaderboardIndicator.fractionOf(1, 1), 1e-6f)
        assertEquals(0f, PointsLeaderboardIndicator.fractionAt(10f, 44f, 44f), 1e-6f)
    }

    @Test
    fun indicatorHidesForShortLists() {
        assertFalse(PointsLeaderboardIndicator.isVisible(total = 0, loadedRows = 0, visibleRows = 10, hasMoreBefore = false, hasMoreAfter = false))
        assertFalse(PointsLeaderboardIndicator.isVisible(total = 6, loadedRows = 6, visibleRows = 10, hasMoreBefore = false, hasMoreAfter = false))
        assertTrue(PointsLeaderboardIndicator.isVisible(total = 6, loadedRows = 6, visibleRows = 4, hasMoreBefore = false, hasMoreAfter = false))
        assertTrue(PointsLeaderboardIndicator.isVisible(total = 900, loadedRows = 50, visibleRows = 60, hasMoreBefore = false, hasMoreAfter = true))
        assertTrue(PointsLeaderboardIndicator.isVisible(total = 900, loadedRows = 50, visibleRows = 60, hasMoreBefore = true, hasMoreAfter = false))
    }
}

class LeaderboardTabResetTest {
    @Test
    fun pointsTabReloadsOnlyAfterASeek() {
        assertEquals(LeaderboardTabReset.Action.SCROLL_TO_TOP, LeaderboardTabReset.onTabTap(isPointsTab = true, firstLoadedPosition = 1))
        assertEquals(LeaderboardTabReset.Action.SCROLL_TO_TOP, LeaderboardTabReset.onTabTap(isPointsTab = true, firstLoadedPosition = 0))
        assertEquals(LeaderboardTabReset.Action.RELOAD_FROM_TOP, LeaderboardTabReset.onTabTap(isPointsTab = true, firstLoadedPosition = 501))
    }

    @Test
    fun dataTabAlwaysScrolls() {
        assertEquals(LeaderboardTabReset.Action.SCROLL_TO_TOP, LeaderboardTabReset.onTabTap(isPointsTab = false, firstLoadedPosition = 900))
    }
}
