package com.bringyour.network.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    private fun bookFold(width: Float) = WindowFold(vertical = true, start = width / 2f, end = width / 2f)

    // a half-open tabletop posture: the hinge across the middle
    private fun tabletopFold(height: Float) = WindowFold(vertical = false, start = height / 2f, end = height / 2f)

    // a book-style foldable open flat: its crease down the middle, dividing nothing
    private fun flatFold(width: Float) =
        WindowFold(vertical = true, start = width / 2f, end = width / 2f, separating = false)

    @Test
    fun navigationIsTheRailFromMediumWidth() {
        assertEquals(NavigationLayout.BottomBar, navigationLayoutFor(phonePortrait.first, phonePortrait.second, tabletop = false))
        assertEquals(NavigationLayout.BottomBar, navigationLayoutFor(flipCover.first, flipCover.second, tabletop = false))
        assertEquals(NavigationLayout.BottomBar, navigationLayoutFor(599f, 900f, tabletop = false))
        assertEquals(NavigationLayout.Rail, navigationLayoutFor(600f, 900f, tabletop = false))
        // a book-style foldable open and upright, and a tablet held upright
        assertEquals(NavigationLayout.Rail, navigationLayoutFor(fold8InnerPortrait.first, fold8InnerPortrait.second, tabletop = false))
        assertEquals(NavigationLayout.Rail, navigationLayoutFor(tabletLandscape.second, tabletLandscape.first, tabletop = false))
        // the near-square inner screen keeps the rail in both orientations, so
        // rotating it changes nothing
        assertEquals(NavigationLayout.Rail, navigationLayoutFor(pixelFoldInner.first, pixelFoldInner.second, tabletop = false))
        assertEquals(NavigationLayout.Rail, navigationLayoutFor(pixelFoldInner.second, pixelFoldInner.first, tabletop = false))
        assertEquals(NavigationLayout.Rail, navigationLayoutFor(fold8InnerLandscape.first, fold8InnerLandscape.second, tabletop = false))
        assertEquals(NavigationLayout.Rail, navigationLayoutFor(tabletLandscape.first, tabletLandscape.second, tabletop = false))
        assertEquals(NavigationLayout.Rail, navigationLayoutFor(largeDesktop.first, largeDesktop.second, tabletop = false))
    }

    @Test
    fun navigationIsTheBarInTabletopAndInShortWindows() {
        // a full-height rail would cross the fold
        assertEquals(NavigationLayout.BottomBar, navigationLayoutFor(fold8InnerLandscape.first, fold8InnerLandscape.second, tabletop = true))
        // a medium-wide window under 480dp tall
        assertEquals(NavigationLayout.BottomBar, navigationLayoutFor(700f, 479f, tabletop = false))
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
        val fold = WindowFold(vertical = true, start = 466.5f, end = 466.5f)
        val split = sideBySideSplit(containerStart = 80f, width = 853f, fold = fold, sidePaneOnLeft = true)
        assertEquals(386.5f, split.firstEnd, 0.01f)
        assertEquals(386.5f, split.secondStart, 0.01f)
    }

    @Test
    fun anOccludingHingeSeparatesThePanes() {
        // a hinge that hides 24dp of the window (two screens): no pane draws under it
        val fold = WindowFold(vertical = true, start = 540f, end = 564f)
        val split = sideBySideSplit(containerStart = 0f, width = 1104f, fold = fold, sidePaneOnLeft = true)
        assertEquals(540f, split.firstEnd, 0f)
        assertEquals(564f, split.secondStart, 0f)
    }

    @Test
    fun aFoldNearTheEdgeIsNotTheSplit() {
        // the window only just reaches over the fold: a 120dp pane is no pane
        val fold = WindowFold(vertical = true, start = 1000f, end = 1000f)
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
        val fold = WindowFold(vertical = true, start = 400f, end = 400f)
        val ltr = sideBySideSplit(containerStart = 0f, width = 800f, fold = fold, sidePaneOnLeft = true)
        val rtl = sideBySideSplit(containerStart = 0f, width = 800f, fold = fold, sidePaneOnLeft = false)
        assertEquals(ltr, rtl)
        assertEquals(400f, sidePaneWidth(rtl, 800f, sidePaneOnLeft = false), 0f)
    }

    @Test
    fun stackedSplitsInHalfOrAtTheFold() {
        assertEquals(PaneSplit(400f, 400f), stackedSplit(containerTop = 24f, height = 800f, fold = null))

        // tabletop: the hinge at 352dp in window terms, the panes from the window's top
        val fold = WindowFold(vertical = false, start = 352f, end = 352f)
        assertEquals(PaneSplit(352f, 352f), stackedSplit(containerTop = 0f, height = 624f, fold = fold))
        // the same with the panes starting under a 24dp status bar
        assertEquals(PaneSplit(328f, 328f), stackedSplit(containerTop = 24f, height = 600f, fold = fold))
    }

    @Test
    fun aVerticalFoldDoesNotSplitAStack() {
        val fold = WindowFold(vertical = true, start = 350f, end = 350f)
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
        val fold = WindowFold(vertical = false, start = 600f, end = 600f)
        assertEquals(WindowRegion(0f, 0f, 933f, 600f), foldSafeDialogRegion(933f, 700f, fold, startOnLeft = true))
    }

    @Test
    fun aFlatFoldSplitsSideBySidePanesAtTheHinge() {
        // the Fold8's inner screen open flat in landscape, the panes beside an
        // 80dp rail: 40/60 would split 45dp before the crease, at 341dp
        val split = sideBySideSplit(containerStart = 80f, width = 853f, fold = flatFold(933f), sidePaneOnLeft = true)
        assertEquals(386.5f, split.firstEnd, 0.01f)
        assertEquals(386.5f, split.secondStart, 0.01f)
    }

    @Test
    fun theHingeWinsOverTheSidePaneBounds() {
        // past the side pane's 400dp maximum
        val wide = sideBySideSplit(containerStart = 0f, width = 1200f, fold = flatFold(1200f), sidePaneOnLeft = true)
        assertEquals(PaneSplit(600f, 600f), wide)
        // and under its 300dp minimum, the main pane well over its own
        val crease = WindowFold(vertical = true, start = 250f, end = 250f, separating = false)
        assertEquals(PaneSplit(250f, 250f), sideBySideSplit(containerStart = 0f, width = 800f, fold = crease, sidePaneOnLeft = true))
    }

    @Test
    fun aFlatFoldNearTheEdgeIsNotTheSplit() {
        // 150dp from the container's edge: 40/60 instead
        val crease = WindowFold(vertical = true, start = 150f, end = 150f, separating = false)
        val split = sideBySideSplit(containerStart = 0f, width = 1000f, fold = crease, sidePaneOnLeft = true)
        assertEquals(SIDE_PANE_MAX_WIDTH_DP, split.firstEnd, 0f)
    }

    @Test
    fun aFlatFoldThatSqueezesTheMainPaneIsNotTheSplit() {
        // the crease would leave the connect pane 220dp, under its 240dp minimum
        val crease = WindowFold(vertical = true, start = 340f, end = 340f, separating = false)
        val split = sideBySideSplit(containerStart = 0f, width = 560f, fold = crease, sidePaneOnLeft = true)
        assertEquals(SIDE_PANE_MIN_WIDTH_DP, split.firstEnd, 0f)
        assertTrue(MAIN_PANE_MIN_WIDTH_DP <= mainPaneWidth(split, 560f, sidePaneOnLeft = true))
        // right to left, the main pane is the left one
        val rtlCrease = WindowFold(vertical = true, start = 220f, end = 220f, separating = false)
        val rtl = sideBySideSplit(containerStart = 0f, width = 560f, fold = rtlCrease, sidePaneOnLeft = false)
        assertEquals(560f - SIDE_PANE_MIN_WIDTH_DP, rtl.firstEnd, 0f)
    }

    @Test
    fun aHalfOpenFoldIsTheSplitEvenWhenTheMainPaneIsNarrow() {
        // a pane across a half-open fold is bent in two, so the fold still wins
        val fold = WindowFold(vertical = true, start = 340f, end = 340f)
        assertEquals(PaneSplit(340f, 340f), sideBySideSplit(containerStart = 0f, width = 560f, fold = fold, sidePaneOnLeft = true))
    }

    @Test
    fun aFlatFoldDecidesNeitherTheArrangementNorAStack() {
        // open flat in portrait: medium width still stacks, as on iOS
        assertEquals(
            ConnectArrangement.Stacked,
            connectArrangementFor(fold8InnerPortrait.first, fold8InnerPortrait.second, flatFold(fold8InnerPortrait.first)),
        )
        // a flat crease across a stack does not move its split
        val across = WindowFold(vertical = false, start = 350f, end = 350f, separating = false)
        assertEquals(PaneSplit(400f, 400f), stackedSplit(containerTop = 0f, height = 800f, fold = across))
    }

    @Test
    fun besideABookFoldADialogOpensOnTheSideItWasOpenedFrom() {
        // opened from the right pane in a left-to-right layout
        assertEquals(
            WindowRegion(466.5f, 0f, 933f, 704f),
            foldSafeDialogRegion(933f, 704f, bookFold(933f), startOnLeft = true, anchorX = 700f),
        )
        // and from the left pane in a right-to-left one
        assertEquals(
            WindowRegion(0f, 0f, 466.5f, 704f),
            foldSafeDialogRegion(933f, 704f, bookFold(933f), startOnLeft = false, anchorX = 100f),
        )
    }

    @Test
    fun inTabletopADialogOpensBelowTheFoldWhereverItWasOpenedFrom() {
        // the controls' half (Google's tabletop guidance), even when the
        // touch that opened it was above the fold
        assertEquals(
            WindowRegion(0f, 352f, 933f, 704f),
            foldSafeDialogRegion(933f, 704f, tabletopFold(704f), startOnLeft = true, anchorX = 100f),
        )
    }

    @Test
    fun aDialogKeepsClearOfTheBarsAndTheKeyboard() {
        // tabletop with a 330dp keyboard over the half below the fold: the
        // half above, under the 24dp status bar
        val typing = WindowRegion(0f, 24f, 933f, 704f - 330f)
        assertEquals(
            WindowRegion(0f, 24f, 933f, 352f),
            foldSafeDialogRegion(933f, 704f, tabletopFold(704f), startOnLeft = true, usable = typing),
        )
        // book posture: the start side, above the keyboard
        val book = WindowRegion(0f, 24f, 933f, 704f - 300f)
        assertEquals(
            WindowRegion(0f, 24f, 466.5f, 404f),
            foldSafeDialogRegion(933f, 704f, bookFold(933f), startOnLeft = true, usable = book),
        )
    }

    @Test
    fun aFlatFoldLeavesDialogsAndSheetsAsTheyAre() {
        assertEquals(
            WindowRegion(0f, 0f, 933f, 704f),
            foldSafeDialogRegion(933f, 704f, flatFold(933f), startOnLeft = true, anchorX = 700f),
        )
        assertNull(foldSafeSheetRegion(933f, 704f, flatFold(933f), startOnLeft = true, anchorX = 700f))
    }

    @Test
    fun besideABookFoldASheetOpensInOneHalf() {
        assertEquals(WindowRegion(0f, 0f, 466.5f, 704f), foldSafeSheetRegion(933f, 704f, bookFold(933f), startOnLeft = true))
        assertEquals(
            WindowRegion(466.5f, 0f, 933f, 704f),
            foldSafeSheetRegion(933f, 704f, bookFold(933f), startOnLeft = true, anchorX = 800f),
        )
        // in tabletop it keeps its width and opens to the half below the fold instead
        assertNull(foldSafeSheetRegion(933f, 704f, tabletopFold(704f), startOnLeft = true))
        assertNull(foldSafeSheetRegion(933f, 704f, null, startOnLeft = true))
        // halves too narrow for it: it keeps its width
        assertNull(foldSafeSheetRegion(400f, 800f, bookFold(400f), startOnLeft = true))
    }

    @Test
    fun mediumWidthStartsAt600dp() {
        assertEquals(false, isMediumWidthOrWider(599.9f))
        assertEquals(true, isMediumWidthOrWider(600f))
    }
}
