package com.bringyour.network.ui.connect

import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectSheetGeometryTest {
    @Test
    fun incompleteMeasurementUsesBaseHeight() {
        assertEquals(
            272,
            connectSheetPeekHeightPx(
                baseHeightPx = 248,
                dragHandleHeightPx = 0,
                foldMarkerOffsetPx = 160,
                foldGapPx = 16,
                unconsumedBottomInsetPx = 24,
            ),
        )
    }

    @Test
    fun peekEndsExactlyFoldGapPastTheMarkerAboveTheTabBar() {
        // phones: the tab bar below the sheet absorbs the whole system inset,
        // so the collapsed gap under the connect button is exactly foldGap
        assertEquals(
            32 + 176 + 16,
            connectSheetPeekHeightPx(
                baseHeightPx = 248,
                dragHandleHeightPx = 32,
                foldMarkerOffsetPx = 176,
                foldGapPx = 16,
                unconsumedBottomInsetPx = 0,
            ),
        )
    }

    @Test
    fun unconsumedInsetExtendsThePeekPastTheFoldGap() {
        // navigation rail: the sheet reaches the screen edge, so the fold gap
        // sits above the full system inset
        assertEquals(
            32 + 176 + 16 + 40,
            connectSheetPeekHeightPx(
                baseHeightPx = 248,
                dragHandleHeightPx = 32,
                foldMarkerOffsetPx = 176,
                foldGapPx = 16,
                unconsumedBottomInsetPx = 40,
            ),
        )
    }

    @Test
    fun repeatedMeasurementIsStable() {
        val first = connectSheetPeekHeightPx(248, 32, 176, 16, 0)
        val second = connectSheetPeekHeightPx(248, 32, 176, 16, 0)

        assertEquals(first, second)
    }

    @Test
    fun invalidGeometryCannotProduceNegativePeek() {
        assertEquals(0, connectSheetPeekHeightPx(-100, 0, 0, 0, -20))
    }
}
