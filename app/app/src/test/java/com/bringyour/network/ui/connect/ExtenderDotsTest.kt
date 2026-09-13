package com.bringyour.network.ui.connect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The extender ring geometry of a provider dot (EXTENDER.md K2) and the sdk
 * color hexes it is drawn from (K3).
 */
class ExtenderDotsTest {

    // a cell large enough that three 2 + 2 rings fit around the dot without
    // the small-cell scaling below
    private val cell = 64f
    private val radius = cell / 2f
    private val stroke = 2f
    private val gap = 2f

    private fun geometry(extenderCount: Int, cellSizePx: Float = cell) =
        extenderRingGeometry(
            cellSizePx = cellSizePx,
            extenderCount = extenderCount,
            strokePx = stroke,
            gapPx = gap,
        )

    @Test
    fun noExtendersDrawsThePlainDot() {
        val geometry = geometry(0)

        assertEquals(radius, geometry.dotRadiusPx, 0f)
        assertEquals(listOf<Float>(), geometry.ringRadiiPx)
        assertFalse(geometry.dashedOutermost)
    }

    @Test
    fun oneRingSitsAtTheCellEdgeAndShrinksTheDotByFour() {
        val geometry = geometry(1)

        assertEquals(1, geometry.ringRadiiPx.size)
        // the ring's outer edge is the cell edge, so the footprint is the same
        // as the plain dot's
        assertEquals(radius, geometry.ringRadiiPx[0] + geometry.strokePx / 2f, 0.001f)
        // 2 of stroke plus 2 of gap
        assertEquals(radius - 4f, geometry.dotRadiusPx, 0.001f)
        assertEquals(stroke, geometry.strokePx, 0f)
    }

    @Test
    fun ringsStepInwardByStrokePlusGapAndTheDotShrinksFourPerRing() {
        val geometry = geometry(3)

        assertEquals(listOf(radius - 9f, radius - 5f, radius - 1f), geometry.ringRadiiPx)
        assertEquals(radius - 12f, geometry.dotRadiusPx, 0.001f)
        // the gap between two rings is the stroke-free space between their edges
        val innerEdgeOfOuter = geometry.ringRadiiPx[1] - geometry.strokePx / 2f
        val outerEdgeOfInner = geometry.ringRadiiPx[0] + geometry.strokePx / 2f
        assertEquals(gap, innerEdgeOfOuter - outerEdgeOfInner, 0.001f)
        // and the gap between the dot and the first ring is the same
        assertEquals(
            gap,
            geometry.ringRadiiPx[0] - geometry.strokePx / 2f - geometry.dotRadiusPx,
            0.001f,
        )
        assertFalse(geometry.dashedOutermost)
    }

    @Test
    fun fourOrMoreExtendersCollapseIntoADashedThirdRing() {
        for (extenderCount in 4..9) {
            val geometry = geometry(extenderCount)

            assertEquals(3, geometry.ringRadiiPx.size)
            assertTrue(geometry.dashedOutermost)
            // the footprint of a provider with nine extenders is a provider's
            // footprint
            assertEquals(radius, geometry.ringRadiiPx.last() + geometry.strokePx / 2f, 0.001f)
        }
    }

    @Test
    fun aCellTooSmallForTheRingsScalesThemInsteadOfDroppingThem() {
        val small = 16f
        val geometry = geometry(3, cellSizePx = small)

        assertEquals(3, geometry.ringRadiiPx.size)
        assertTrue(geometry.strokePx < stroke)
        // the dot stays visible and the outermost ring stays on the cell edge
        assertTrue(0f < geometry.dotRadiusPx)
        assertEquals(small / 2f, geometry.ringRadiiPx.last() + geometry.strokePx / 2f, 0.001f)
    }

    @Test
    fun aCollapsedDotDrawsNothing() {
        // the widget animates the radius to zero as a provider is removed
        val geometry = geometry(2, cellSizePx = 0f)

        assertEquals(0f, geometry.dotRadiusPx, 0f)
        assertEquals(listOf<Float>(), geometry.ringRadiiPx)
    }

    @Test
    fun colorHexesSplitInTheSdkOrder() {
        assertEquals(
            listOf("3cdd67", "dd4f3c"),
            extenderColorHexList("3cdd67,dd4f3c"),
        )
        assertEquals(listOf<String>(), extenderColorHexList(""))
        assertEquals(listOf<String>(), extenderColorHexList(null))
        // a trailing separator is not an extender
        assertEquals(listOf("3cdd67"), extenderColorHexList("3cdd67,"))
    }

    @Test
    fun colorHexesParseAsOpaqueArgb() {
        // the sdk's pinned values for 192.0.2.1 and 2001:db8::1 (K3)
        assertEquals(0xFF3CDD67.toInt(), extenderColorArgb("3cdd67"))
        assertEquals(0xFFDD4F3C.toInt(), extenderColorArgb("dd4f3c"))
        assertEquals(0xFF3CDD67.toInt(), extenderColorArgb("#3cdd67"))
        assertNull(extenderColorArgb(""))
        assertNull(extenderColorArgb("3cdd6"))
        assertNull(extenderColorArgb("zzzzzz"))
    }

    @Test
    fun ringColorsDropWhatDoesNotParse() {
        assertEquals(
            listOf(0xFF3CDD67.toInt(), 0xFFDD4F3C.toInt()),
            extenderRingArgbList("3cdd67,nothex,dd4f3c"),
        )
        assertEquals(listOf<Int>(), extenderRingArgbList(""))
    }
}
