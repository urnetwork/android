package com.bringyour.network.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The window rules of the foldable design, at the screens that matter
 * (apple/DUO.md): phones, flip and book-style foldables folded, unfolded,
 * half open and rotated, tablets, split screen and short windows.
 */
class AdaptiveLayoutTest {

    // window sizes in dp, at the 420dpi reference density of the research
    private val phonePortrait = 412f to 915f
    private val phoneLandscape = 915f to 412f
    private val smallPhoneLandscape = 780f to 360f
    private val flipCover = 400f to 400f
    private val fold8InnerPortrait = 704f to 933f
    private val fold8InnerLandscape = 933f to 704f
    private val pixelFoldInner = 791f to 820f
    private val tabletLandscape = 1280f to 800f
    private val largeDesktop = 1600f to 1000f

    // a half-open book posture: the hinge down the middle of the window
    private fun bookFold(width: Float) = SeparatingFold(vertical = true, start = width / 2f, end = width / 2f)

    // a half-open tabletop posture: the hinge across the middle
    private fun tabletopFold(height: Float) = SeparatingFold(vertical = false, start = height / 2f, end = height / 2f)

    @Test
    fun navigationIsTheRailOnlyAtExpandedWidth() {
        assertEquals(NavigationLayout.BottomBar, navigationLayoutFor(phonePortrait.first, phonePortrait.second, tabletop = false))
        assertEquals(NavigationLayout.BottomBar, navigationLayoutFor(fold8InnerPortrait.first, fold8InnerPortrait.second, tabletop = false))
        // the near-square inner screen keeps the bar in both orientations: it
        // never crosses the 840dp line, so rotating it changes nothing
        assertEquals(NavigationLayout.BottomBar, navigationLayoutFor(pixelFoldInner.first, pixelFoldInner.second, tabletop = false))
        assertEquals(NavigationLayout.BottomBar, navigationLayoutFor(pixelFoldInner.second, pixelFoldInner.first, tabletop = false))
        assertEquals(NavigationLayout.Rail, navigationLayoutFor(fold8InnerLandscape.first, fold8InnerLandscape.second, tabletop = false))
        assertEquals(NavigationLayout.Rail, navigationLayoutFor(tabletLandscape.first, tabletLandscape.second, tabletop = false))
        assertEquals(NavigationLayout.Rail, navigationLayoutFor(largeDesktop.first, largeDesktop.second, tabletop = false))
    }

    @Test
    fun navigationIsTheBarInTabletopAndInShortWindows() {
        // a full-height rail would cross the fold
        assertEquals(NavigationLayout.BottomBar, navigationLayoutFor(fold8InnerLandscape.first, fold8InnerLandscape.second, tabletop = true))
        // a landscape phone is expanded wide but compact tall
        assertEquals(NavigationLayout.BottomBar, navigationLayoutFor(phoneLandscape.first, phoneLandscape.second, tabletop = false))
    }

    @Test
    fun compactWidthKeepsThePhoneDrawer() {
        assertEquals(ConnectArrangement.Drawer, connectArrangementFor(phonePortrait.first, phonePortrait.second, null))
        assertEquals(ConnectArrangement.Drawer, connectArrangementFor(flipCover.first, flipCover.second, null))
        // a flip phone half open stays the phone layout; its drawer stops at the fold
        assertEquals(ConnectArrangement.Drawer, connectArrangementFor(phonePortrait.first, phonePortrait.second, tabletopFold(phonePortrait.second)))
        // half of a tablet in split screen
        assertEquals(ConnectArrangement.Drawer, connectArrangementFor(599f, 800f, null))
    }

    @Test
    fun mediumWidthStacksAndExpandedWidthSitsSideBySide() {
        assertEquals(ConnectArrangement.Stacked, connectArrangementFor(fold8InnerPortrait.first, fold8InnerPortrait.second, null))
        assertEquals(ConnectArrangement.Stacked, connectArrangementFor(pixelFoldInner.first, pixelFoldInner.second, null))
        assertEquals(ConnectArrangement.Stacked, connectArrangementFor(600f, 900f, null))
        assertEquals(ConnectArrangement.SideBySide, connectArrangementFor(fold8InnerLandscape.first, fold8InnerLandscape.second, null))
        assertEquals(ConnectArrangement.SideBySide, connectArrangementFor(tabletLandscape.first, tabletLandscape.second, null))
        assertEquals(ConnectArrangement.SideBySide, connectArrangementFor(largeDesktop.first, largeDesktop.second, null))
    }

    @Test
    fun aShortWindowNeverStacks() {
        assertEquals(ConnectArrangement.SideBySide, connectArrangementFor(smallPhoneLandscape.first, smallPhoneLandscape.second, null))
        assertEquals(ConnectArrangement.SideBySide, connectArrangementFor(phoneLandscape.first, phoneLandscape.second, null))
    }

    @Test
    fun theFoldDecidesTheArrangement() {
        // book posture: side by side even at medium width
        assertEquals(
            ConnectArrangement.SideBySide,
            connectArrangementFor(pixelFoldInner.first, pixelFoldInner.second, bookFold(pixelFoldInner.first)),
        )
        // tabletop: stacked even at expanded width
        assertEquals(
            ConnectArrangement.Stacked,
            connectArrangementFor(fold8InnerLandscape.first, fold8InnerLandscape.second, tabletopFold(fold8InnerLandscape.second)),
        )
    }

    @Test
    fun sideBySideSplitsFortySixtyWithoutAFold() {
        // 853dp of content beside an 80dp rail: 40% is 341dp
        val split = sideBySideSplit(containerStart = 80f, width = 853f, fold = null, sidePaneOnLeft = true)
        assertEquals(853f * SIDE_PANE_FRACTION, split.firstEnd, 0.01f)
        assertEquals(split.firstEnd, split.secondStart, 0f)
    }

    @Test
    fun sideBySideCapsTheSidePaneInLargeWindowsAndFloorsItInNarrowOnes() {
        val large = sideBySideSplit(containerStart = 80f, width = 1520f, fold = null, sidePaneOnLeft = true)
        assertEquals(SIDE_PANE_MAX_WIDTH_DP, large.firstEnd, 0f)

        // a short medium window with the bar below: 40% of 600dp is too narrow
        val narrow = sideBySideSplit(containerStart = 0f, width = 600f, fold = null, sidePaneOnLeft = true)
        assertEquals(SIDE_PANE_MIN_WIDTH_DP, narrow.firstEnd, 0f)
        assertEquals(true, 600f - narrow.secondStart >= MAIN_PANE_MIN_WIDTH_DP)
    }

    @Test
    fun sideBySideSplitsAtTheHinge() {
        // the hinge at 466.5dp in window terms, the panes beside an 80dp rail
        val fold = SeparatingFold(vertical = true, start = 466.5f, end = 466.5f)
        val split = sideBySideSplit(containerStart = 80f, width = 853f, fold = fold, sidePaneOnLeft = true)
        assertEquals(386.5f, split.firstEnd, 0.01f)
        assertEquals(386.5f, split.secondStart, 0.01f)
    }

    @Test
    fun anOccludingHingeSeparatesThePanes() {
        // a hinge that hides 24dp of the window (two screens): no pane draws under it
        val fold = SeparatingFold(vertical = true, start = 540f, end = 564f)
        val split = sideBySideSplit(containerStart = 0f, width = 1104f, fold = fold, sidePaneOnLeft = true)
        assertEquals(540f, split.firstEnd, 0f)
        assertEquals(564f, split.secondStart, 0f)
    }

    @Test
    fun aFoldNearTheEdgeIsNotTheSplit() {
        // the window only just reaches over the fold: a 120dp pane is no pane
        val fold = SeparatingFold(vertical = true, start = 1000f, end = 1000f)
        val split = sideBySideSplit(containerStart = 0f, width = 1120f, fold = fold, sidePaneOnLeft = true)
        assertEquals(SIDE_PANE_MAX_WIDTH_DP, split.firstEnd, 0f)
    }

    @Test
    fun rightToLeftPutsTheSidePaneOnTheRight() {
        val split = sideBySideSplit(containerStart = 0f, width = 1000f, fold = null, sidePaneOnLeft = false)
        assertEquals(600f, split.firstEnd, 0f)
        assertEquals(400f, sidePaneWidth(split, 1000f, sidePaneOnLeft = false), 0f)
        assertEquals(600f, mainPaneWidth(split, 1000f, sidePaneOnLeft = false), 0f)
    }

    @Test
    fun theHingeSplitIsTheSameInBothDirections() {
        val fold = SeparatingFold(vertical = true, start = 400f, end = 400f)
        val ltr = sideBySideSplit(containerStart = 0f, width = 800f, fold = fold, sidePaneOnLeft = true)
        val rtl = sideBySideSplit(containerStart = 0f, width = 800f, fold = fold, sidePaneOnLeft = false)
        assertEquals(ltr, rtl)
        assertEquals(400f, sidePaneWidth(rtl, 800f, sidePaneOnLeft = false), 0f)
    }

    @Test
    fun stackedSplitsInHalfOrAtTheFold() {
        assertEquals(PaneSplit(400f, 400f), stackedSplit(containerTop = 24f, height = 800f, fold = null))

        // tabletop: the hinge at 352dp in window terms, the panes from the window's top
        val fold = SeparatingFold(vertical = false, start = 352f, end = 352f)
        assertEquals(PaneSplit(352f, 352f), stackedSplit(containerTop = 0f, height = 624f, fold = fold))
        // the same with the panes starting under a 24dp status bar
        assertEquals(PaneSplit(328f, 328f), stackedSplit(containerTop = 24f, height = 600f, fold = fold))
    }

    @Test
    fun aVerticalFoldDoesNotSplitAStack() {
        val fold = SeparatingFold(vertical = true, start = 350f, end = 350f)
        assertEquals(PaneSplit(400f, 400f), stackedSplit(containerTop = 0f, height = 800f, fold = fold))
    }

    @Test
    fun theGraphicKeepsItsSizeOnAPhoneAndShrinksInAShortWindow() {
        // a phone in portrait has room for the whole 256dp graphic
        assertEquals(
            CONNECT_GRAPHIC_FULL_DP,
            connectGraphicSize(380f, 540f, CONNECT_GRAPHIC_MIN_DP, CONNECT_GRAPHIC_FULL_DP),
            0f,
        )
        // a short window shrinks it to the room it has
        assertEquals(180f, connectGraphicSize(380f, 180f, CONNECT_GRAPHIC_MIN_DP, CONNECT_GRAPHIC_FULL_DP), 0f)
        // and no smaller than the minimum
        assertEquals(CONNECT_GRAPHIC_MIN_DP, connectGraphicSize(380f, 20f, CONNECT_GRAPHIC_MIN_DP, CONNECT_GRAPHIC_FULL_DP), 0f)
        // a pane grows it, to its maximum
        assertEquals(
            CONNECT_GRAPHIC_PANE_MAX_DP,
            connectGraphicSize(500f, 700f, CONNECT_GRAPHIC_MIN_DP, CONNECT_GRAPHIC_PANE_MAX_DP),
            0f,
        )
    }

    @Test
    fun theDrawerOpensToTwoThirdsOfATallLayout() {
        assertEquals(800f * 2f / 3f, connectDrawerExpandedHeight(800f, 220f, 2f / 3f, null), 0.01f)
    }

    @Test
    fun theDrawerNeverRunsPastTheTopOfAShortWindow() {
        // two thirds of a 300dp window is less than the peek: the drawer opens
        // to the peek, never past the window
        assertEquals(220f, connectDrawerExpandedHeight(300f, 220f, 2f / 3f, null), 0f)
        // a window shorter than the peek caps the drawer at the window
        assertEquals(180f, connectDrawerExpandedHeight(180f, 220f, 2f / 3f, null), 0f)
    }

    @Test
    fun inTabletopTheDrawerStopsAtTheFold() {
        // a flip phone half open: 830dp tall, the fold's bottom edge 457dp down
        assertEquals(830f - 457f, connectDrawerExpandedHeight(830f, 220f, 2f / 3f, foldBottom = 457f), 0f)
        // a fold outside the layout does not cap it
        assertEquals(830f * 2f / 3f, connectDrawerExpandedHeight(830f, 220f, 2f / 3f, foldBottom = 900f), 0.01f)
    }

    @Test
    fun dialogsOpenOnOneSideOfAFold() {
        // no fold: the whole window
        assertEquals(WindowRegion(0f, 0f, 933f, 704f), foldSafeDialogRegion(933f, 704f, null, startOnLeft = true))
        // tabletop: the half below the fold
        assertEquals(
            WindowRegion(0f, 352f, 933f, 704f),
            foldSafeDialogRegion(933f, 704f, tabletopFold(704f), startOnLeft = true),
        )
        // book posture: the start side
        assertEquals(
            WindowRegion(0f, 0f, 466.5f, 704f),
            foldSafeDialogRegion(933f, 704f, bookFold(933f), startOnLeft = true),
        )
        assertEquals(
            WindowRegion(466.5f, 0f, 933f, 704f),
            foldSafeDialogRegion(933f, 704f, bookFold(933f), startOnLeft = false),
        )
    }

    @Test
    fun aSideTooSmallForADialogFallsBackToTheOther() {
        // a tabletop fold low in the window leaves 100dp below it: the dialog goes above
        val fold = SeparatingFold(vertical = false, start = 600f, end = 600f)
        assertEquals(WindowRegion(0f, 0f, 933f, 600f), foldSafeDialogRegion(933f, 700f, fold, startOnLeft = true))
    }

    @Test
    fun mediumWidthStartsAt600dp() {
        assertEquals(false, isMediumWidthOrWider(599.9f))
        assertEquals(true, isMediumWidthOrWider(600f))
    }
}
