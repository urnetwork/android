package com.bringyour.network.ui.stats

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferChartVisibilityTest {
    @Test
    fun fullyVisibleChartIntersectsViewport() {
        assertTrue(
            transferChartIntersectsViewport(
                Rect(left = 10f, top = 20f, right = 90f, bottom = 80f),
                viewportWidth = 100,
                viewportHeight = 100,
            ),
        )
    }

    @Test
    fun partiallyVisibleChartIntersectsViewport() {
        assertTrue(
            transferChartIntersectsViewport(
                Rect(left = 10f, top = 90f, right = 90f, bottom = 120f),
                viewportWidth = 100,
                viewportHeight = 100,
            ),
        )
    }

    @Test
    fun chartBelowViewportDoesNotIntersect() {
        assertFalse(
            transferChartIntersectsViewport(
                Rect(left = 10f, top = 100f, right = 90f, bottom = 180f),
                viewportWidth = 100,
                viewportHeight = 100,
            ),
        )
    }

    @Test
    fun chartAboveViewportDoesNotIntersect() {
        assertFalse(
            transferChartIntersectsViewport(
                Rect(left = 10f, top = -80f, right = 90f, bottom = 0f),
                viewportWidth = 100,
                viewportHeight = 100,
            ),
        )
    }

    @Test
    fun emptyViewportDoesNotIntersect() {
        assertFalse(
            transferChartIntersectsViewport(
                Rect(left = 0f, top = 0f, right = 10f, bottom = 10f),
                viewportWidth = 0,
                viewportHeight = 100,
            ),
        )
    }
}
