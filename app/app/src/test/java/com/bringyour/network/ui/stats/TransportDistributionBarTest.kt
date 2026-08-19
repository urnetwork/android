package com.bringyour.network.ui.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The bar's pure geometry: one interpolated boundary vector always tiles the
 * width, whatever the tween progress, and segments only exist for positive
 * widths.
 */
class TransportDistributionBarTest {

    private val epsilon = 1e-4f

    @Test
    fun interpolatedBoundariesTileTheWidthAtEveryProgress() {
        // sdk-style boundaries: 62% h3, 30% h1, 8% p2p (dns/dnspump/unknown idle)
        val from = listOf(0.62f, 0.92f, 0.92f, 0.92f, 1f, 1f)
        // the next tick: h1 leaves, dns enters
        val to = listOf(0.5f, 0.5f, 0.9f, 0.9f, 1f, 1f)
        for (step in 0..10) {
            val t = step / 10f
            val boundaries = interpolateBoundaries(from, to, t)
            assertEquals(6, boundaries.size)
            // monotonic, and the last boundary is exactly the full width
            for (i in 1 until boundaries.size) {
                assertTrue(boundaries[i - 1] <= boundaries[i] + epsilon)
            }
            assertEquals(1f, boundaries.last(), epsilon)
            val segments = transportSegments(boundaries, 200f, 1f)
            val covered = segments.sumOf { (it.end - it.start).toDouble() }.toFloat()
            assertEquals(200f, covered, 1e-2f)
        }
    }

    @Test
    fun interpolationTreatsMissingElementsAsZero() {
        val boundaries = interpolateBoundaries(listOf(), listOf(0.5f, 1f), 0.5f)
        assertEquals(listOf(0.25f, 0.5f), boundaries)
    }

    @Test
    fun segmentsSkipZeroWidthTransportsAndKeepTheirIndex() {
        // 62% h3, 30% h1, 8% p2p: dns, dnspump and unknown draw nothing
        val boundaries = listOf(0.62f, 0.92f, 0.92f, 0.92f, 1f, 1f)
        val segments = transportSegments(boundaries, 100f, 1f)
        assertEquals(listOf(0, 1, 4), segments.map { it.index })
        assertEquals(0f, segments[0].start, epsilon)
        assertEquals(62f, segments[0].end, epsilon)
        assertEquals(62f, segments[1].start, epsilon)
        assertEquals(92f, segments[1].end, epsilon)
        assertEquals(92f, segments[2].start, epsilon)
        assertEquals(100f, segments[2].end, epsilon)
        // no separator on the first visible segment; a full hairline between
        // the wide neighbours
        assertEquals(0f, segments[0].separatorWidth, epsilon)
        assertEquals(1f, segments[1].separatorWidth, epsilon)
        assertEquals(1f, segments[2].separatorWidth, epsilon)
    }

    @Test
    fun separatorEasesInWithANarrowSegment() {
        // a 2px sliver next to a wide segment gets a half-pixel separator
        val boundaries = listOf(0.98f, 1f)
        val segments = transportSegments(boundaries, 100f, 1f)
        assertEquals(2, segments.size)
        assertEquals(0.5f, segments[1].separatorWidth, epsilon)
    }

    @Test
    fun emptyOrIdleVectorsDrawNothing() {
        assertTrue(transportSegments(listOf(), 100f, 1f).isEmpty())
        assertTrue(transportSegments(listOf(0f, 0f, 0f, 0f, 0f, 0f), 100f, 1f).isEmpty())
        assertTrue(transportSegments(listOf(0.5f, 1f), 0f, 1f).isEmpty())
    }
}
