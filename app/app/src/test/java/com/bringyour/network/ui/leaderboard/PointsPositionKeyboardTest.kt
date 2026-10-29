package com.bringyour.network.ui.leaderboard

import com.bringyour.network.ui.components.KeyboardScroll
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The keyboard on the points list's position thumb (PointsLeaderboardTab):
 * the arrows move it a hundredth of the ranking, the page keys a tenth, and
 * Home and End to the ends, always within 1..N.
 */
class PointsPositionKeyboardTest {

    @Test
    fun theArrowsMoveAHundredthOfTheRanking() {
        assertEquals(1_240L, PointsLeaderboardIndicator.keyRank(KeyboardScroll.LineDown, rank = 1_000L, total = 24_000L))
        assertEquals(760L, PointsLeaderboardIndicator.keyRank(KeyboardScroll.LineUp, rank = 1_000L, total = 24_000L))
    }

    @Test
    fun thePageKeysMoveATenth() {
        assertEquals(3_400L, PointsLeaderboardIndicator.keyRank(KeyboardScroll.PageDown, rank = 1_000L, total = 24_000L))
        assertEquals(1L, PointsLeaderboardIndicator.keyRank(KeyboardScroll.PageUp, rank = 1_000L, total = 24_000L))
    }

    @Test
    fun homeAndEndGoToTheEnds() {
        assertEquals(1L, PointsLeaderboardIndicator.keyRank(KeyboardScroll.Top, rank = 9_000L, total = 24_000L))
        assertEquals(24_000L, PointsLeaderboardIndicator.keyRank(KeyboardScroll.Bottom, rank = 9_000L, total = 24_000L))
    }

    @Test
    fun aShortRankingMovesOneRankAtLeastAndStaysInIt() {
        assertEquals(6L, PointsLeaderboardIndicator.keyRank(KeyboardScroll.LineDown, rank = 5L, total = 40L))
        assertEquals(9L, PointsLeaderboardIndicator.keyRank(KeyboardScroll.PageDown, rank = 5L, total = 40L))
        assertEquals(40L, PointsLeaderboardIndicator.keyRank(KeyboardScroll.PageDown, rank = 39L, total = 40L))
        assertEquals(1L, PointsLeaderboardIndicator.keyRank(KeyboardScroll.LineUp, rank = 1L, total = 40L))
        // nothing ranked yet
        assertEquals(1L, PointsLeaderboardIndicator.keyRank(KeyboardScroll.LineDown, rank = 1L, total = 0L))
    }
}
