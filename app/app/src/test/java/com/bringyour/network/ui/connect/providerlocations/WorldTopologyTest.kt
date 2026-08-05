package com.bringyour.network.ui.connect.providerlocations

import java.io.File
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class WorldTopologyTest {
    companion object {
        // gradle runs unit tests with the module directory as the working
        // directory; the other candidates cover runners that start a level up
        private val assetCandidates = listOf(
            File("src/main/assets/world-110m.json"),
            File("app/src/main/assets/world-110m.json"),
            File("app/app/src/main/assets/world-110m.json"),
        )

        private val topology: WorldTopology? by lazy {
            assetCandidates.firstOrNull { it.isFile }
                ?.let { WorldTopology.decode(it.readText()) }
        }
    }

    private fun requireTopology(): WorldTopology {
        assumeTrue("world-110m.json asset not found", topology != null)
        return topology!!
    }

    @Test
    fun decodesAll177Countries() {
        val world = requireTopology()
        assertEquals(177, world.countries.size)
        assertTrue(world.countries.all { it.isoNumeric.isNotEmpty() })
    }

    @Test
    fun knownCountriesArePresent() {
        val world = requireTopology()
        val ids = world.countries.map { it.isoNumeric }.toSet()
        assertTrue("USA missing", "840" in ids)
        assertTrue("Australia missing", "036" in ids)
    }

    @Test
    fun ringsAreWellFormedPolylines() {
        val world = requireTopology()
        var ringCount = 0
        for (country in world.countries) {
            assertTrue(country.rings.isNotEmpty())
            for (ring in country.rings) {
                ringCount++
                assertEquals("odd float count", 0, ring.size % 2)
                assertTrue("ring under 4 points", ring.size >= 8)
            }
        }
        assertTrue(ringCount >= 100)
    }

    @Test
    fun coordinatesAreWithinWorldBounds() {
        val world = requireTopology()
        for (country in world.countries) {
            for (ring in country.rings) {
                for (i in ring.indices step 2) {
                    assertTrue(ring[i] >= -180.0001f && ring[i] <= 180.0001f)
                    assertTrue(ring[i + 1] >= -90.0001f && ring[i + 1] <= 90.0001f)
                }
            }
        }
    }

    @Test
    fun everyRingCloses() {
        // TopoJSON polygon rings close: the first point of the first arc
        // equals the last point of the last arc; a stitching bug (dropped or
        // duplicated shared endpoints) breaks this
        val world = requireTopology()
        for (country in world.countries) {
            for (ring in country.rings) {
                assertEquals(country.isoNumeric, ring[0], ring[ring.size - 2], 1e-3f)
                assertEquals(country.isoNumeric, ring[1], ring[ring.size - 1], 1e-3f)
            }
        }
    }

    @Test
    fun totalPointCountIsInTheExpectedBand() {
        // world-110m decodes to ~10,500 ring points; far fewer means dropped
        // arcs, far more means shared endpoints were not deduplicated
        val world = requireTopology()
        val totalPoints = world.countries.sumOf { country ->
            country.rings.sumOf { it.size / 2 }
        }
        assertTrue("total points $totalPoints", totalPoints in 5_000..60_000)
    }

    @Test
    fun dequantizesKnownUsaCoordinates() {
        // independently decoded (python): the first ring of the USA
        // MultiPolygon (Hawaii) has 17 points and starts at
        // (-155.541355, 19.084175)
        val world = requireTopology()
        val usa = world.countries.firstOrNull { it.isoNumeric == "840" }
        assertNotNull(usa)
        val firstRing = usa!!.rings.first()
        assertEquals(17 * 2, firstRing.size)
        assertEquals(-155.541355f, firstRing[0], 5e-4f)
        assertEquals(19.084175f, firstRing[1], 5e-4f)
        assertTrue(abs(firstRing[0] - firstRing[firstRing.size - 2]) < 1e-3f)
    }
}
