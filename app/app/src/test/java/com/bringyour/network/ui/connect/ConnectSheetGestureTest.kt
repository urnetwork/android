package com.bringyour.network.ui.connect

import com.bringyour.network.ui.connect.ConnectSheetGesture.Kind
import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectSheetGestureTest {

    @Test
    fun touchOnAClosedOrMovingDrawerIsADrawerGesture() {
        val gesture = ConnectSheetGesture()
        gesture.onDown(sheetSettledOpen = false)
        assertEquals(Kind.Sheet, gesture.kind)
        // the first movement does not change a decided gesture
        gesture.onFirstMovement(deltaY = -30f, contentAtTop = true)
        assertEquals(Kind.Sheet, gesture.kind)
    }

    @Test
    fun touchOnTheOpenDrawerIsDecidedByItsFirstMovement() {
        val gesture = ConnectSheetGesture()
        gesture.onDown(sheetSettledOpen = true)
        assertEquals(Kind.Undecided, gesture.kind)
        gesture.onFirstMovement(deltaY = 12f, contentAtTop = true)
        assertEquals("pulling down at the top closes", Kind.Sheet, gesture.kind)

        gesture.onDown(sheetSettledOpen = true)
        gesture.onFirstMovement(deltaY = 12f, contentAtTop = false)
        assertEquals("pulling down mid-content scrolls", Kind.Content, gesture.kind)

        gesture.onDown(sheetSettledOpen = true)
        gesture.onFirstMovement(deltaY = -12f, contentAtTop = true)
        assertEquals("pushing up at the top scrolls", Kind.Content, gesture.kind)
    }

    @Test
    fun onceDecidedTheKindSticksForTheTouch() {
        val gesture = ConnectSheetGesture()
        gesture.onDown(sheetSettledOpen = true)
        gesture.onFirstMovement(deltaY = -12f, contentAtTop = true)
        gesture.onFirstMovement(deltaY = 40f, contentAtTop = true)
        assertEquals(Kind.Content, gesture.kind)
    }

    @Test
    fun drawerGestureNeverScrollsTheContent() {
        // the drawer reached the top mid-drag: the upward remainder is swallowed
        assertEquals(-25f, ConnectSheetGesture.preScrollConsumedY(Kind.Sheet, -25f), 0f)
        // a downward remainder passes through so the sheet can close
        assertEquals(0f, ConnectSheetGesture.preScrollConsumedY(Kind.Sheet, 25f), 0f)
        // the release fling upward is swallowed, downward reaches the sheet
        assertEquals(-3000f, ConnectSheetGesture.preFlingConsumedY(Kind.Sheet, -3000f), 0f)
        assertEquals(0f, ConnectSheetGesture.preFlingConsumedY(Kind.Sheet, 3000f), 0f)
        // and the sheet keeps whatever is left after the content
        assertEquals(0f, ConnectSheetGesture.postScrollConsumedY(Kind.Sheet, 25f), 0f)
        assertEquals(0f, ConnectSheetGesture.postFlingConsumedY(Kind.Sheet, 3000f), 0f)
    }

    @Test
    fun contentGestureNeverMovesTheDrawer() {
        assertEquals(0f, ConnectSheetGesture.preScrollConsumedY(Kind.Content, -25f), 0f)
        assertEquals(0f, ConnectSheetGesture.preFlingConsumedY(Kind.Content, -3000f), 0f)
        // the content reached the top: the remainder and the fling stop there
        assertEquals(25f, ConnectSheetGesture.postScrollConsumedY(Kind.Content, 25f), 0f)
        assertEquals(3000f, ConnectSheetGesture.postFlingConsumedY(Kind.Content, 3000f), 0f)
    }

    @Test
    fun undecidedGestureLetsEverythingThrough() {
        assertEquals(0f, ConnectSheetGesture.preScrollConsumedY(Kind.Undecided, -25f), 0f)
        assertEquals(0f, ConnectSheetGesture.postScrollConsumedY(Kind.Undecided, 25f), 0f)
        assertEquals(0f, ConnectSheetGesture.preFlingConsumedY(Kind.Undecided, -25f), 0f)
        assertEquals(0f, ConnectSheetGesture.postFlingConsumedY(Kind.Undecided, 25f), 0f)
    }
}
