package com.bringyour.network.ui.components

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The slow internet tunnel's geometry, depth table and motion timing
 * (apple/DELAY.md §2 and §3), checked against the canvas's Tunnel component,
 * and the ic_tunnel_1..4 status icons against the geometry they were
 * generated from.
 */
class TunnelGeometryTest {

    private val tolerance = 1e-3f

    // gradle runs unit tests with the module directory as the working
    // directory; the other candidates cover runners that start a level up
    private fun moduleFile(path: String): File {
        val file = listOf("", "app/", "app/app/")
            .map { File(it + path) }
            .firstOrNull { it.isFile }
        assertNotNull("$path not found", file)
        return file!!
    }

    @Test
    fun theSquaresAt24MatchTheCanvasReference() {
        // the canvas's Tunnel component at 24 px: each square's border box
        // (its left and top, its side) and stroke, and its opacity
        val reference = listOf(
            listOf(0f, 24f, 2.256f, 1.00f),
            listOf(4.0728f, 16.5744f, 1.3536f, 0.92f),
            listOf(6.8786f, 11.4668f, 0.8122f, 0.84f),
            listOf(8.8157f, 7.9455f, 0.4873f, 0.76f),
            listOf(10.1553f, 5.5131f, 0.2924f, 0.68f),
            listOf(11.0834f, 3.8299f, 0.1754f, 0.60f),
        )
        val squares = tunnelSquares(24f)
        assertEquals(TUNNEL_SQUARE_COUNT, squares.size)
        squares.zip(reference).forEachIndexed { index, (square, expected) ->
            val (left, side, stroke, opacity) = expected
            assertEquals("square $index left", left, square.start - square.strokeWidth / 2, tolerance)
            assertEquals("square $index side", side, square.side + square.strokeWidth, tolerance)
            assertEquals("square $index stroke", stroke, square.strokeWidth, tolerance)
            assertEquals("square $index opacity", opacity, square.opacity, tolerance)
        }
    }

    @Test
    fun theOuterSquareFitsTheBoxAndEachNestedStrokeThins() {
        val squares = tunnelSquares(160f)
        val outer = squares.first()
        assertEquals(0.094f * 160f, outer.strokeWidth, tolerance)
        // inset by half its stroke: the stroke's outer edge is the box
        assertEquals(0f, outer.start - outer.strokeWidth / 2, tolerance)
        assertEquals(160f, outer.end + outer.strokeWidth / 2, tolerance)
        squares.zipWithNext().forEach { (a, b) ->
            assertEquals(a.strokeWidth * 0.6f, b.strokeWidth, tolerance)
            assertEquals(a.opacity - 0.08f, b.opacity, tolerance)
        }
    }

    @Test
    fun eachNestedSquareIsTheOuterScaledAboutTheVanishingPoint() {
        val size = 100f
        val vanishingPoint = tunnelVanishingPoint(size)
        assertEquals(55f, vanishingPoint, tolerance)
        val squares = tunnelSquares(size)
        val outer = squares.first()
        squares.forEachIndexed { index, square ->
            val scale = Math.pow(0.7, index.toDouble()).toFloat()
            assertEquals(vanishingPoint + scale * (outer.start - vanishingPoint), square.start, tolerance)
            assertEquals(vanishingPoint + scale * (outer.end - vanishingPoint), square.end, tolerance)
        }
    }

    @Test
    fun theLeftAndTopGapsOpenWiderThanTheRightAndBottom() {
        val squares = tunnelSquares(24f)
        val (outer, next) = squares
        val near = (next.start - outer.start) - (outer.strokeWidth + next.strokeWidth) / 2
        val far = (outer.end - next.end) - (outer.strokeWidth + next.strokeWidth) / 2
        // about 1.65 times (§2.1)
        assertEquals(1.65f, near / far, 0.02f)
    }

    @Test
    fun sixSquaresStayClearOfEachOtherAt16() {
        val squares = tunnelSquares(16f)
        squares.zipWithNext().forEachIndexed { index, (a, b) ->
            val far = (a.end - b.end) - (a.strokeWidth + b.strokeWidth) / 2
            assertTrue("squares $index and ${index + 1} touch on the tight side", 0f < far)
        }
    }

    @Test
    fun theDepthFollowsTheTable() {
        assertEquals(0, tunnelDepth(0.0))
        assertEquals(1, tunnelDepth(0.25))
        assertEquals(2, tunnelDepth(0.5))
        assertEquals(4, tunnelDepth(0.75))
        assertEquals(6, tunnelDepth(1.0))
    }

    @Test
    fun aQualityBetweenTheTablesPointsInterpolatesAndRounds() {
        // any quality above 0 shows at least the first square
        assertEquals(1, tunnelDepth(0.01))
        assertEquals(1, tunnelDepth(0.2))
        // halfway from 0.5 (two) to 0.75 (four)
        assertEquals(3, tunnelDepth(0.625))
        assertEquals(5, tunnelDepth(0.875))
        assertEquals(6, tunnelDepth(1.5))
    }

    @Test
    fun noQualityShowsNoSquares() {
        assertEquals(0, tunnelDepth(-0.25))
        assertEquals(0, tunnelDepth(Double.NaN))
    }

    @Test
    fun theStatusIconLevelsDrawTheTablesDepths() {
        assertEquals(listOf(1, 2, 4, 6), tunnelLevelDepths)
        assertEquals(0, tunnelLevel(0.0))
        assertEquals(1, tunnelLevel(0.25))
        assertEquals(2, tunnelLevel(0.5))
        assertEquals(3, tunnelLevel(0.75))
        assertEquals(4, tunnelLevel(1.0))
        // between the table's points: never more squares than the indicator
        assertEquals(2, tunnelLevel(0.625))
        assertEquals(3, tunnelLevel(0.875))
    }

    @Test
    fun aDeeperTunnelFadesItsSquaresInFromTheOutside() {
        val motions = (0 until TUNNEL_SQUARE_COUNT).map { tunnelSquareMotion(it, fromDepth = 0, toDepth = 6) }
        assertTrue(motions.all { it.visible })
        assertEquals(listOf(0, 110, 220, 330, 440, 550), motions.map { it.delayMillis })
        assertTrue(motions.all { it.durationMillis == 420 })
        // so 0 to the full tunnel builds in 970 ms
        assertEquals(970, tunnelChangeMillis(0, 6))
    }

    @Test
    fun aShallowerTunnelFadesItsSquaresOutFromTheInside() {
        val motions = (0 until TUNNEL_SQUARE_COUNT).map { tunnelSquareMotion(it, fromDepth = 6, toDepth = 0) }
        assertTrue(motions.none { it.visible })
        assertEquals(listOf(450, 360, 270, 180, 90, 0), motions.map { it.delayMillis })
        assertTrue(motions.all { it.durationMillis == 320 })
        // so the full tunnel clears in 770 ms
        assertEquals(770, tunnelChangeMillis(6, 0))
    }

    @Test
    fun aChangeCountsItsStaggerFromTheFirstSquareThatChanges() {
        // deeper, 2 to 4: the two squares that stay never fade
        val deeper = (0 until TUNNEL_SQUARE_COUNT).map { tunnelSquareMotion(it, fromDepth = 2, toDepth = 4) }
        assertEquals(listOf(true, true, true, true, false, false), deeper.map { it.visible })
        assertEquals(listOf(0, 0, 0, 110, 0, 0), deeper.map { it.delayMillis })
        // shallower, 4 to 2: the innermost goes first
        val shallower = (0 until TUNNEL_SQUARE_COUNT).map { tunnelSquareMotion(it, fromDepth = 4, toDepth = 2) }
        assertEquals(listOf(true, true, false, false, false, false), shallower.map { it.visible })
        assertEquals(90, shallower[2].delayMillis)
        assertEquals(0, shallower[3].delayMillis)
        assertEquals(320, shallower[2].durationMillis)
    }

    @Test
    fun aSkippedLevelRunsTheSameStagger() {
        // 0.25 to 1: one square to six, every new square in turn
        val motions = (1 until TUNNEL_SQUARE_COUNT).map { tunnelSquareMotion(it, fromDepth = 1, toDepth = 6) }
        assertEquals(listOf(0, 110, 220, 330, 440), motions.map { it.delayMillis })
        assertEquals(860, tunnelChangeMillis(1, 6))
        assertEquals(0, tunnelChangeMillis(4, 4))
    }

    @Test
    fun aClearingPillWaitsForTheOutermostSquare() {
        assertEquals(450, tunnelClearDelayMillis(6))
        assertEquals(270, tunnelClearDelayMillis(4))
        assertEquals(0, tunnelClearDelayMillis(1))
        assertEquals(0, tunnelClearDelayMillis(0))
        // the outermost square starts to fade when the pill does
        assertEquals(tunnelSquareMotion(0, fromDepth = 6, toDepth = 0).delayMillis, tunnelClearDelayMillis(6))
    }

    @Test
    fun theStatusIconsAreTheGeometryAt24() {
        val squares = tunnelSquares(24f)
        tunnelLevelDepths.forEachIndexed { index, depth ->
            val level = index + 1
            val xml = moduleFile("src/main/res/drawable/ic_tunnel_$level.xml").readText()
            assertTrue(xml, xml.contains("android:width=\"24dp\"") && xml.contains("android:height=\"24dp\""))
            assertTrue(xml, xml.contains("android:viewportWidth=\"24\"") && xml.contains("android:viewportHeight=\"24\""))
            val paths = Regex("""<path\b([^>]*)/>""").findAll(xml).map { it.groupValues[1] }.toList()
            assertEquals("ic_tunnel_$level squares", depth, paths.size)
            paths.forEachIndexed { pathIndex, attributes ->
                fun attribute(name: String): String? =
                    Regex("""android:$name="([^"]*)"""").find(attributes)?.groupValues?.get(1)
                val square = squares[pathIndex]
                val numbers = Regex("""[0-9.]+""").findAll(attribute("pathData")!!).map { it.value.toFloat() }.toList()
                // M start,start H end V end H start Z
                val expected = listOf(square.start, square.start, square.end, square.end, square.start)
                assertEquals("ic_tunnel_$level square $pathIndex path", expected.size, numbers.size)
                expected.zip(numbers).forEach { (want, got) -> assertEquals(want, got, tolerance) }
                assertEquals(square.strokeWidth, attribute("strokeWidth")!!.toFloat(), tolerance)
                assertEquals(square.opacity, attribute("strokeAlpha")!!.toFloat(), tolerance)
                // square corners, a white silhouette like ic_status, no fill
                assertEquals("miter", attribute("strokeLineJoin"))
                assertEquals("#FFFFFFFF", attribute("strokeColor"))
                assertFalse(attributes, attributes.contains("fillColor"))
            }
        }
    }
}
