package com.bringyour.network.ui.connect.providerlocations

import kotlin.math.abs
import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GlobeGeometryTest {
    @Test
    fun projectsCardinalPointsAtIdentityRotation() {
        // orthographic at rotation (0, 0), scale 300: the view center (0, 0)
        // lands at (300, 300); (90, 0) is the right limb, (0, 90) the top
        assertPoint(300f, 300f, GlobeGeometry.project(0f, 0f, 0f, 0f, 300f))
        assertPoint(600f, 300f, GlobeGeometry.project(90f, 0f, 0f, 0f, 300f))
        assertPoint(0f, 300f, GlobeGeometry.project(-90f, 0f, 0f, 0f, 300f))
        assertPoint(300f, 0f, GlobeGeometry.project(0f, 90f, 0f, 0f, 300f))
        assertPoint(300f, 600f, GlobeGeometry.project(0f, -90f, 0f, 0f, 300f))
    }

    @Test
    fun backHemisphereProjectsToNull() {
        // the antipode of the view center: cos(angle to center) = -1
        assertNull(GlobeGeometry.project(180f, 0f, 0f, 0f, 300f))
        assertNull(GlobeGeometry.project(0f, 0f, 180f, 0f, 300f))
        assertEquals(-1f, GlobeGeometry.cosAngleToCenter(180f, 0f, 0f, 0f), 1e-6f)
        assertEquals(1f, GlobeGeometry.cosAngleToCenter(0f, 0f, 0f, 0f), 1e-6f)
    }

    @Test
    fun rotationCenteringLandsThePointAtScreenCenter() {
        val rotation = GlobeGeometry.rotationCentering(-122.4f, 37.8f)
        assertEquals(122.4f, rotation.first, 0f)
        assertEquals(-37.8f, rotation.second, 0f)

        assertPoint(
            300f,
            300f,
            GlobeGeometry.project(-122.4f, 37.8f, rotation.first, rotation.second, 300f),
        )
        assertEquals(
            1f,
            GlobeGeometry.cosAngleToCenter(-122.4f, 37.8f, rotation.first, rotation.second),
            1e-6f,
        )
    }

    @Test
    fun projectClampedMatchesProjectOnTheVisibleHemisphere() {
        val projected = GlobeGeometry.project(30f, 40f, 10f, -20f, 300f)
        val clamped = GlobeGeometry.projectClamped(30f, 40f, 10f, -20f, 300f)
        assertNotNull(projected)
        assertEquals(projected!!.x, clamped.x, 1e-4f)
        assertEquals(projected.y, clamped.y, 1e-4f)
    }

    @Test
    fun projectClampedPutsBackPointsOnTheSilhouetteCircle() {
        // the exact antipode has no azimuthal direction; clamps to (600, 300)
        val antipode = GlobeGeometry.projectClamped(180f, 0f, 0f, 0f, 300f)
        assertEquals(300.0, distanceToCenter(antipode), 1e-2)
        assertEquals(600f, antipode.x, 1e-2f)
        assertEquals(300f, antipode.y, 1e-2f)

        // (135, 45) at rotation (0, 0): rotated vector is
        // x = cos(135) cos(45) = -0.5 (behind), y = sin(135) cos(45) = 0.5,
        // z = sin(45) = 0.7071068, so the azimuthal direction (y, z)
        // normalized by sqrt(0.5^2 + 0.7071068^2) = 0.8660254 gives
        // px = 300 + 300 * 0.5 / 0.8660254 = 473.205
        // py = 300 - 300 * 0.7071068 / 0.8660254 = 55.051
        val back = GlobeGeometry.projectClamped(135f, 45f, 0f, 0f, 300f)
        assertEquals(300.0, distanceToCenter(back), 1e-2)
        assertEquals(473.205f, back.x, 1e-2f)
        assertEquals(55.051f, back.y, 1e-2f)
    }

    @Test
    fun lerpRotationTakesTheShortWayAroundTheDateLine() {
        // 170 -> -170 is 20 degrees through the date line, not 340 back;
        // the midpoint is the date line itself (180 and -180 are the same)
        val mid = GlobeGeometry.lerpRotation(Pair(170f, 0f), Pair(-170f, 0f), 0.5f)
        assertEquals(180f, abs(mid.first), 1e-4f)
        assertEquals(0f, mid.second, 0f)
    }

    @Test
    fun lerpRotationTreatsLongitudesModulo360() {
        // 350 is -10: from 10 the short way is backward 20 degrees
        val mid = GlobeGeometry.lerpRotation(Pair(10f, 0f), Pair(350f, 0f), 0.5f)
        assertEquals(0f, mid.first, 1e-4f)

        val end = GlobeGeometry.lerpRotation(Pair(10f, 0f), Pair(350f, 0f), 1f)
        assertEquals(-10f, end.first, 1e-4f)

        val start = GlobeGeometry.lerpRotation(Pair(10f, 20f), Pair(350f, -40f), 0f)
        assertEquals(10f, start.first, 0f)
        assertEquals(20f, start.second, 0f)
    }

    @Test
    fun lerpRotationInterpolatesAndClampsPhi() {
        val mid = GlobeGeometry.lerpRotation(Pair(0f, -30f), Pair(0f, 50f), 0.5f)
        assertEquals(10f, mid.second, 1e-4f)

        // phi never leaves [-90, 90] even for out-of-range endpoints
        val clamped = GlobeGeometry.lerpRotation(Pair(0f, 80f), Pair(0f, 120f), 1f)
        assertEquals(90f, clamped.second, 0f)
    }

    @Test
    fun dragSensitivityMatchesTheWebFormula() {
        // Globe.jsx: k = width / projection.scale() / (3 * Math.PI), applied
        // to projection.rotate() which takes degrees. At scale 300:
        // 600 / 300 / (3 * pi) = 2 / (3 * pi) = 0.2122066 degrees per px.
        assertEquals(0.2122066f, GlobeGeometry.dragDegreesPerVirtualPx(300f), 1e-4f)
        // doubling the zoom halves the sensitivity
        assertEquals(
            GlobeGeometry.dragDegreesPerVirtualPx(300f) / 2f,
            GlobeGeometry.dragDegreesPerVirtualPx(600f),
            1e-6f,
        )
    }

    @Test
    fun graticuleHasTheD3DefaultLineStructure() {
        val lines = GlobeGeometry.graticule()
        assertEquals(53, lines.size)

        var meridians = 0
        var parallels = 0
        for (line in lines) {
            assertTrue(line.size >= 4)
            assertEquals(0, line.size % 2)
            val constantLon = allEqual(line, offset = 0)
            val constantLat = allEqual(line, offset = 1)
            assertTrue("line is neither a meridian nor a parallel", constantLon || constantLat)
            if (constantLon) meridians++ else parallels++
        }
        // 4 major meridians (-180, -90, 0, 90) + 32 minor (every 10 degrees
        // skipping multiples of 90); the equator + 16 minor parallels
        assertEquals(36, meridians)
        assertEquals(17, parallels)
    }

    @Test
    fun graticuleStaysInWorldBoundsWithD3Extents() {
        var fullMeridians = 0
        var minorMeridians = 0
        for (line in GlobeGeometry.graticule()) {
            var minLat = 90f
            var maxLat = -90f
            for (i in line.indices step 2) {
                assertTrue(line[i] >= -180.0001f && line[i] <= 180.0001f)
                assertTrue(line[i + 1] >= -90.0001f && line[i + 1] <= 90.0001f)
                minLat = minOf(minLat, line[i + 1])
                maxLat = maxOf(maxLat, line[i + 1])
            }
            if (allEqual(line, offset = 0)) {
                // major meridians run pole to pole, minor ones stop at 80
                if (maxLat > 85f) fullMeridians++ else minorMeridians++
                if (maxLat <= 85f) {
                    assertEquals(80f, maxLat, 1e-3f)
                    assertEquals(-80f, minLat, 1e-3f)
                }
            } else {
                // parallels span the full longitude range
                assertEquals(-180f, line[0], 1e-3f)
                assertEquals(180f, line[line.size - 2], 1e-3f)
            }
        }
        assertEquals(4, fullMeridians)
        assertEquals(32, minorMeridians)
    }

    @Test
    fun graticuleIsSampledEvery2Point5Degrees() {
        for (line in GlobeGeometry.graticule()) {
            val varyingOffset = if (allEqual(line, offset = 0)) 1 else 0
            var maxStep = 0f
            for (i in (varyingOffset + 2) until line.size step 2) {
                val step = line[i] - line[i - 2]
                // monotone, never a gap wider than the 2.5 degree precision
                // (the final segment may be shorter where the span is not an
                // exact multiple of the step)
                assertTrue(step >= -1e-4f)
                assertTrue(step <= 2.5f + 1e-3f)
                maxStep = maxOf(maxStep, step)
            }
            assertEquals(2.5f, maxStep, 1e-3f)
        }
    }

    @Test
    fun nearestWithinPicksTheClosestPointInRange() {
        val points = listOf(
            GlobePoint(100f, 100f),
            GlobePoint(200f, 200f),
            GlobePoint(105f, 100f),
        )
        assertEquals(0, GlobeGeometry.nearestWithin(101f, 100f, points, 10f))
        assertEquals(2, GlobeGeometry.nearestWithin(104f, 100f, points, 10f))
        assertEquals(1, GlobeGeometry.nearestWithin(201f, 199f, points, 10f))
    }

    @Test
    fun nearestWithinRespectsTheRadius() {
        val points = listOf(GlobePoint(0f, 0f))
        assertEquals(-1, GlobeGeometry.nearestWithin(300f, 300f, points, 5f))
        // the radius is inclusive: distance from (3, 4) to (0, 0) is 5
        assertEquals(0, GlobeGeometry.nearestWithin(3f, 4f, points, 5f))
        assertEquals(-1, GlobeGeometry.nearestWithin(3f, 4.01f, points, 5f))
        assertEquals(-1, GlobeGeometry.nearestWithin(0f, 0f, emptyList(), 100f))
    }

    private fun assertPoint(expectedX: Float, expectedY: Float, actual: GlobePoint?) {
        assertNotNull(actual)
        assertEquals(expectedX, actual!!.x, 1e-3f)
        assertEquals(expectedY, actual.y, 1e-3f)
    }

    // The globe is a scroll wheel when providers are present: a horizontal
    // drag steps the selection once it passes the hysteresis threshold.
    @Test
    fun aDragShorterThanTheThresholdDoesNotStep() {
        val step = GlobeGeometry.wheelStep(travel = -49f, threshold = 50f)
        assertEquals(0, step.steps)
        // the travel is carried, so continuing the same drag still steps
        assertEquals(-49f, step.remainingTravel, 1e-4f)
    }

    @Test
    fun swipingLeftAdvancesAndSwipingRightGoesBack() {
        assertEquals(1, GlobeGeometry.wheelStep(-50f, 50f).steps)
        assertEquals(-1, GlobeGeometry.wheelStep(50f, 50f).steps)
    }

    @Test
    fun aFastDragCrossesSeveralStepsAtOnce() {
        val step = GlobeGeometry.wheelStep(travel = -170f, threshold = 50f)
        assertEquals(3, step.steps)
        // 20px of the drag is left over toward the next step
        assertEquals(-20f, step.remainingTravel, 1e-4f)
    }

    // the hysteresis: after stepping, another full threshold is required, so a
    // finger resting at the boundary cannot flicker between two providers
    @Test
    fun steppingConsumesExactlyOneThresholdOfTravel() {
        var travel = -50f
        val first = GlobeGeometry.wheelStep(travel, 50f)
        assertEquals(1, first.steps)
        assertEquals(0f, first.remainingTravel, 1e-4f)

        // jitter back and forth around the boundary must not step again
        travel = first.remainingTravel
        for (jitter in listOf(-20f, 15f, -18f, 12f)) {
            travel += jitter
            val step = GlobeGeometry.wheelStep(travel, 50f)
            assertEquals(0, step.steps)
            travel = step.remainingTravel
        }
    }

    @Test
    fun aNonPositiveThresholdNeverSteps() {
        assertEquals(0, GlobeGeometry.wheelStep(-1000f, 0f).steps)
    }

    // The wheel order and the step clamping are the sdk's
    // ProviderLocationsViewController (provider_locations_view_controller.go,
    // tested there); this module only converts drag travel to step counts.

    // fit center: the globe scales to the smaller canvas dimension and centers
    // in both, so a wide (non-square) box neither crops nor offsets it
    @Test
    fun unitFitsTheSmallerDimension() {
        assertEquals(1f, GlobeGeometry.unitFor(600f, 600f), 1e-4f)
        // 800x600 -> fits the 600 height
        assertEquals(1f, GlobeGeometry.unitFor(800f, 600f), 1e-4f)
        // 600x450 (the 0.75 height ratio) -> fits the 450 height
        assertEquals(0.75f, GlobeGeometry.unitFor(600f, 450f), 1e-4f)
    }

    @Test
    fun virtualCenterMapsToTheCanvasCenterOfAWideBox() {
        val center = GlobeGeometry.toCanvas(
            GlobePoint(GlobeGeometry.CENTER, GlobeGeometry.CENTER),
            800f,
            600f,
        )
        assertEquals(400f, center.x, 1e-3f)
        assertEquals(300f, center.y, 1e-3f)
    }

    @Test
    fun theGlobeEdgesStayInsideAWideBox() {
        val width = 800f
        val height = 600f
        // the extreme points of the virtual space (the sphere's bounding box)
        val left = GlobeGeometry.toCanvas(GlobePoint(0f, GlobeGeometry.CENTER), width, height)
        val right = GlobeGeometry.toCanvas(
            GlobePoint(GlobeGeometry.VIRTUAL_SIZE, GlobeGeometry.CENTER),
            width,
            height,
        )
        val top = GlobeGeometry.toCanvas(GlobePoint(GlobeGeometry.CENTER, 0f), width, height)
        val bottom = GlobeGeometry.toCanvas(
            GlobePoint(GlobeGeometry.CENTER, GlobeGeometry.VIRTUAL_SIZE),
            width,
            height,
        )
        // fits the height exactly, and is inset horizontally (centered)
        assertEquals(0f, top.y, 1e-3f)
        assertEquals(height, bottom.y, 1e-3f)
        assertEquals(100f, left.x, 1e-3f)
        assertEquals(700f, right.x, 1e-3f)
        assertEquals(width / 2f - left.x, right.x - width / 2f, 1e-3f)
    }

    @Test
    fun toVirtualInvertsToCanvas() {
        for ((width, height) in listOf(600f to 600f, 800f to 600f, 400f to 700f)) {
            for (point in listOf(
                GlobePoint(GlobeGeometry.CENTER, GlobeGeometry.CENTER),
                GlobePoint(120f, 480f),
                GlobePoint(590f, 10f),
            )) {
                val canvas = GlobeGeometry.toCanvas(point, width, height)
                val back = GlobeGeometry.toVirtual(canvas.x, canvas.y, width, height)
                assertEquals(point.x, back.x, 1e-2f)
                assertEquals(point.y, back.y, 1e-2f)
            }
        }
    }

    private fun distanceToCenter(point: GlobePoint): Double =
        hypot(
            (point.x - GlobeGeometry.CENTER).toDouble(),
            (point.y - GlobeGeometry.CENTER).toDouble(),
        )

    private fun allEqual(line: FloatArray, offset: Int): Boolean {
        for (i in (offset + 2) until line.size step 2) {
            if (abs(line[i] - line[offset]) > 1e-4f) return false
        }
        return true
    }
}
