package com.bringyour.network.ui.connect.providerlocations

import com.bringyour.network.ui.connect.EXTENDER_RING_GAP_DP
import com.bringyour.network.ui.connect.EXTENDER_RING_STROKE_DP
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The extenders on the provider details rows (EXTENDER.md K9): the sdk's
 * paired address and color lists, the row's value, and the rings on the row
 * and globe dots.
 */
class ProviderExtenderTest {

    private val first = ProviderExtender(ip = "203.0.113.7", colorHex = "aabbcc")
    private val second = ProviderExtender(ip = "2001:db8::5", colorHex = "ddeeff")

    // K2's stroke plus gap: how much of the radius one ring takes
    private val ringStep = EXTENDER_RING_STROKE_DP + EXTENDER_RING_GAP_DP

    private fun row(extenders: List<ProviderExtender>) = ProviderLocationRow(
        clientId = "01234567-89ab-cdef-0123-456789abcdef",
        country = "",
        countryCode = "us",
        region = "",
        city = "",
        hasLocation = true,
        lat = null,
        lon = null,
        connectedSinceMillis = 0,
        extenders = extenders,
    )

    @Test
    fun pairsTheSdkListsByIndex() {
        assertEquals(
            listOf(first, second),
            providerExtenders(ips = "203.0.113.7,2001:db8::5", colorHexes = "aabbcc,ddeeff"),
        )
    }

    @Test
    fun aDirectRouteHasNoExtenders() {
        assertEquals(listOf<ProviderExtender>(), providerExtenders(ips = "", colorHexes = ""))
        assertEquals(listOf<ProviderExtender>(), providerExtenders(ips = null, colorHexes = null))
        assertEquals(listOf<Int>(), row(extenders = listOf()).extenderRingArgb)
    }

    // The sdk keeps the two lists the same length; an unpaired tail is dropped
    // rather than drawn in a made-up color.
    @Test
    fun anUnpairedTailIsDropped() {
        assertEquals(
            listOf(first),
            providerExtenders(ips = "203.0.113.7,198.51.100.2", colorHexes = "aabbcc"),
        )
        assertEquals(
            listOf(first),
            providerExtenders(ips = "203.0.113.7", colorHexes = "aabbcc,ddeeff"),
        )
    }

    // The color is the sdk's for the address (K3), never one computed here: the
    // sdk's pinned value for 192.0.2.1 reaches the ring as given.
    @Test
    fun theColorIsTheSdks() {
        val extenders = providerExtenders(ips = "192.0.2.1", colorHexes = "3cdd67")

        assertEquals(listOf(ProviderExtender(ip = "192.0.2.1", colorHex = "3cdd67")), extenders)
        assertEquals(listOf(0xFF3CDD67.toInt()), row(extenders = extenders).extenderRingArgb)
    }

    // A transport migration changes only the extenders, so they are part of the
    // row's value: an equal row would not be republished.
    @Test
    fun extendersArePartOfTheRow() {
        val extended = row(extenders = listOf(first))

        assertNotEquals(row(extenders = listOf()), extended)
        assertNotEquals(row(extenders = listOf(first, second)), extended)
        assertNotEquals(row(extenders = listOf(second)), extended)
        assertEquals(row(extenders = listOf(first)), extended)
    }

    // The row dot (40 dp) has room for all three rings at the connect grid's
    // full stroke (K2), at any density; a fourth extender dashes the outermost.
    @Test
    fun theRowDotHoldsThreeRings() {
        val four = listOf(
            first,
            second,
            ProviderExtender(ip = "198.51.100.2", colorHex = "112233"),
            ProviderExtender(ip = "198.51.100.3", colorHex = "445566"),
        )
        for (density in listOf(1f, 2.625f)) {
            val geometry = providerRowDotGeometry(row(extenders = four), density)

            assertEquals(3, geometry.ringRadiiPx.size)
            assertTrue(geometry.dashedOutermost)
            assertEquals(EXTENDER_RING_STROKE_DP * density, geometry.strokePx, 0.001f)
            assertEquals((20f - 3 * ringStep) * density, geometry.dotRadiusPx, 0.001f)
            // the outermost ring's outer edge is the dot's edge
            assertEquals(
                20f * density,
                geometry.ringRadiiPx.last() + geometry.strokePx / 2f,
                0.001f,
            )
        }
    }

    // A provider reached directly keeps the plain dot it always had.
    @Test
    fun aDirectRowDotIsThePlainDot() {
        val geometry = providerRowDotGeometry(row(extenders = listOf()), 2f)

        assertEquals(40f, geometry.dotRadiusPx, 0f)
        assertEquals(listOf<Float>(), geometry.ringRadiiPx)
        assertFalse(geometry.dashedOutermost)
    }

    // The globe dot is 14 units across in the globe's space: one ring fits at
    // full stroke, a second extender collapses into that one ring dashed rather
    // than two thinned ones, and the whole glyph scales with the globe.
    @Test
    fun theGlobeDotHoldsOneRing() {
        val unit = 0.65f

        val direct = providerGlobeDotGeometry(row(extenders = listOf()), unit)
        assertTrue(direct.ringRadiiPx.isEmpty())
        assertEquals(7f * unit, direct.dotRadiusPx, 0.0001f)

        val one = providerGlobeDotGeometry(row(extenders = listOf(first)), unit)
        assertEquals(1, one.ringRadiiPx.size)
        assertFalse(one.dashedOutermost)
        assertEquals((7f - ringStep) * unit, one.dotRadiusPx, 0.0001f)
        assertEquals(EXTENDER_RING_STROKE_DP * unit, one.strokePx, 0.0001f)
        // the ring's outer edge is the dot's edge
        assertEquals(7f * unit, one.ringRadiiPx[0] + one.strokePx / 2f, 0.0001f)

        val migratingRow = row(extenders = listOf(first, second))
        val migrating = providerGlobeDotGeometry(migratingRow, unit)
        assertEquals(1, migrating.ringRadiiPx.size)
        assertTrue(migrating.dashedOutermost)
        assertEquals(EXTENDER_RING_STROKE_DP * unit, migrating.strokePx, 0.0001f)
        // the rings take the row's colors from the innermost out, so the one
        // dashed ring is the first extender's
        assertEquals(0xFFAABBCC.toInt(), migratingRow.extenderRingArgb.first())
    }

    @Test
    fun theGlobeDotScalesWithTheGlobe() {
        val extended = row(extenders = listOf(first))
        val atOne = providerGlobeDotGeometry(extended, 1f)

        for (unit in listOf(0.65f, 2f)) {
            val scaled = providerGlobeDotGeometry(extended, unit)

            assertEquals(atOne.dotRadiusPx * unit, scaled.dotRadiusPx, 0.0001f)
            assertEquals(atOne.strokePx * unit, scaled.strokePx, 0.0001f)
            assertEquals(atOne.ringRadiiPx[0] * unit, scaled.ringRadiiPx[0], 0.0001f)
            assertEquals(atOne.dashedOutermost, scaled.dashedOutermost)
        }
    }
}
