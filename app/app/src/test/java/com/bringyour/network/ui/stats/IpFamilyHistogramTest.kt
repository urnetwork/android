package com.bringyour.network.ui.stats

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The histogram's pure parts: the grouping of added providers into the
 * both / v4 / v6 rows, and the dot size derived from the connect widget's
 * canvas and the live grid width.
 */
class IpFamilyHistogramTest {

    private fun point(clientId: String, ipFamily: String, added: Boolean = true) =
        IpFamilyPoint(clientId = clientId, ipFamily = ipFamily, added = added)

    @Test
    fun rowsAreAlwaysAllThreeInDisplayOrder() {
        val rows = ipFamilyHistogramRows(listOf())
        assertEquals(
            listOf(IpFamilyRowKind.BOTH, IpFamilyRowKind.V4, IpFamilyRowKind.V6),
            rows.map { it.kind },
        )
        assertEquals(listOf(0, 0, 0), rows.map { it.clientIds.size })
    }

    @Test
    fun addedProvidersAreGroupedByCategory() {
        val rows = ipFamilyHistogramRows(
            listOf(
                point("c", "dualstack"),
                point("a", "v4-only"),
                point("b", "v6-only"),
                point("d", "dualstack"),
            )
        )
        assertEquals(listOf("c", "d"), rows[0].clientIds)
        assertEquals(listOf("a"), rows[1].clientIds)
        assertEquals(listOf("b"), rows[2].clientIds)
    }

    @Test
    fun onlyAddedProvidersAreCounted() {
        val rows = ipFamilyHistogramRows(
            listOf(
                point("added", "dualstack", added = true),
                point("evaluating", "dualstack", added = false),
                point("failed", "v6-only", added = false),
            )
        )
        assertEquals(listOf("added"), rows[0].clientIds)
        assertEquals(listOf<String>(), rows[1].clientIds)
        assertEquals(listOf<String>(), rows[2].clientIds)
    }

    @Test
    fun legacyAndUnknownCategoriesReadAsV4() {
        val rows = ipFamilyHistogramRows(
            listOf(
                point("legacy", ""),
                point("future", "v7-only"),
            )
        )
        assertEquals(listOf("future", "legacy"), rows[1].clientIds)
        assertEquals(IpFamilyRowKind.V4, IpFamilyRowKind.fromFamily(""))
        assertEquals(IpFamilyRowKind.V4, IpFamilyRowKind.fromFamily("anything"))
    }

    @Test
    fun dotsWithinARowAreOrderedByClientId() {
        val rows = ipFamilyHistogramRows(
            listOf(point("z", "dualstack"), point("m", "dualstack"), point("a", "dualstack"))
        )
        assertEquals(listOf("a", "m", "z"), rows[0].clientIds)
    }

    @Test
    fun labelsMapToRows() {
        assertEquals(IpFamilyRowKind.BOTH, IpFamilyRowKind.fromLabel("both"))
        assertEquals(IpFamilyRowKind.V4, IpFamilyRowKind.fromLabel("v4"))
        assertEquals(IpFamilyRowKind.V6, IpFamilyRowKind.fromLabel("v6"))
        // legacy and unknown labels read as v4, like the categories
        assertEquals(IpFamilyRowKind.V4, IpFamilyRowKind.fromLabel(""))
    }

    @Test
    fun dotDiameterIsTheWidgetCellMinusThePointPadding() {
        // a 248dp canvas at 2x density holding 16 points per side: 31px cells
        assertEquals(31f - 1f, ipFamilyDotDiameterPx(496f, 16, 1f), 1e-4f)
        // the widget grows with the window: 24 points per side
        assertEquals(496f / 24f - 1f, ipFamilyDotDiameterPx(496f, 24, 1f), 1e-4f)
    }

    @Test
    fun dotDiameterFallsBackToTheSdkMinimumGridBeforeAGridExists() {
        assertEquals(ipFamilyDotDiameterPx(496f, 16, 1f), ipFamilyDotDiameterPx(496f, null, 1f), 1e-4f)
        assertEquals(ipFamilyDotDiameterPx(496f, 16, 1f), ipFamilyDotDiameterPx(496f, 0, 1f), 1e-4f)
    }

    @Test
    fun dotDiameterNeverCollapsesBelowOnePixel() {
        assertEquals(1f, ipFamilyDotDiameterPx(4f, 16, 1f), 1e-4f)
    }
}
