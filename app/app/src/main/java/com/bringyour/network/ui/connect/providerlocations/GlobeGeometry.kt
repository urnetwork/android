package com.bringyour.network.ui.connect.providerlocations

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** A point in the 600x600 virtual drawing space. */
internal data class GlobePoint(val x: Float, val y: Float)

/** The result of resolving a wheel drag; see [GlobeGeometry.wheelStep]. */
internal data class WheelStep(val steps: Int, val remainingTravel: Float)

/**
 * Orthographic globe projection math, a faithful port of the web globe
 * (d3.geoOrthographic with clipAngle 90, see ur.io Globe.jsx). Everything is
 * in a 600x600 virtual space with the globe centered at (300, 300); the
 * composable maps virtual space to its canvas.
 *
 * Rotation is the d3 `projection.rotate([lambda, phi])` convention, degrees:
 * the globe is rotated by (+lambda, +phi), so the point centered on screen is
 * (lon = -lambda, lat = -phi).
 */
internal object GlobeGeometry {
    const val VIRTUAL_SIZE = 600f
    const val CENTER = 300f

    private const val DEGREES_TO_RADIANS = PI / 180.0

    /**
     * Projects (lon, lat) under rotation (rotLambda, rotPhi) at the given
     * scale (globe radius in virtual px; the web starts at 300). Returns null
     * for points on the back hemisphere (angular distance to the view center
     * greater than 90 degrees), matching d3 clipAngle(90).
     */
    fun project(
        lonDeg: Float,
        latDeg: Float,
        rotLambdaDeg: Float,
        rotPhiDeg: Float,
        scale: Float,
    ): GlobePoint? {
        val towardViewer = rotateTowardViewer(lonDeg, latDeg, rotLambdaDeg, rotPhiDeg)
        if (towardViewer < 0.0) return null
        val right = rotateRight(lonDeg, latDeg, rotLambdaDeg)
        val up = rotateUp(lonDeg, latDeg, rotLambdaDeg, rotPhiDeg)
        return GlobePoint(
            (CENTER + scale * right).toFloat(),
            (CENTER - scale * up).toFloat(),
        )
    }

    /**
     * Like [project], but never null: back-hemisphere points are clamped to
     * the silhouette circle of radius [scale] in their azimuthal direction,
     * so polygon fills that cross the horizon stay on the visible disk. The
     * exact antipode of the view center has no direction; it clamps to
     * (CENTER + scale, CENTER) deterministically.
     */
    fun projectClamped(
        lonDeg: Float,
        latDeg: Float,
        rotLambdaDeg: Float,
        rotPhiDeg: Float,
        scale: Float,
    ): GlobePoint {
        val towardViewer = rotateTowardViewer(lonDeg, latDeg, rotLambdaDeg, rotPhiDeg)
        val right = rotateRight(lonDeg, latDeg, rotLambdaDeg)
        val up = rotateUp(lonDeg, latDeg, rotLambdaDeg, rotPhiDeg)
        if (towardViewer >= 0.0) {
            return GlobePoint(
                (CENTER + scale * right).toFloat(),
                (CENTER - scale * up).toFloat(),
            )
        }
        val length = sqrt(right * right + up * up)
        if (length < 1e-9) {
            return GlobePoint(CENTER + scale, CENTER)
        }
        return GlobePoint(
            (CENTER + scale * right / length).toFloat(),
            (CENTER - scale * up / length).toFloat(),
        )
    }

    /**
     * Cosine of the angular distance from (lon, lat) to the view center under
     * the given rotation. The point is on the visible hemisphere iff >= 0.
     */
    fun cosAngleToCenter(
        lonDeg: Float,
        latDeg: Float,
        rotLambdaDeg: Float,
        rotPhiDeg: Float,
    ): Float = rotateTowardViewer(lonDeg, latDeg, rotLambdaDeg, rotPhiDeg).toFloat()

    /** The rotation that centers (lon, lat) on screen: (-lon, -lat). */
    fun rotationCentering(lonDeg: Float, latDeg: Float): Pair<Float, Float> =
        Pair(-lonDeg, -latDeg)

    /**
     * Componentwise interpolation between two rotations with longitude taking
     * the shorter way around (so 170 -> -170 passes through 180, not 0) and
     * phi clamped to [-90, 90].
     */
    fun lerpRotation(
        from: Pair<Float, Float>,
        to: Pair<Float, Float>,
        t: Float,
    ): Pair<Float, Float> {
        var deltaLambda = (to.first - from.first) % 360f
        if (deltaLambda > 180f) deltaLambda -= 360f
        if (deltaLambda < -180f) deltaLambda += 360f
        return Pair(
            from.first + deltaLambda * t,
            (from.second + (to.second - from.second) * t).coerceIn(-90f, 90f),
        )
    }

    /**
     * Drag sensitivity in degrees of rotation per virtual px of drag at the
     * given scale. Web parity (Globe.jsx drag handler):
     *
     *     const k = width / projection.scale() / (3 * Math.PI);
     *     projection.rotate([r[0] + event.dx * k, r[1] - event.dy * k]);
     *
     * projection.rotate() takes degrees, so k is degrees per px: at the
     * initial scale 300, k = 600 / 300 / (3 * pi) = 2 / (3 * pi) = 0.21221
     * degrees per px, i.e. dragging across the full 600 px width turns the
     * globe by ~127 degrees. Callers apply +dx * k to lambda and -dy * k to
     * phi, as the web does.
     */
    fun dragDegreesPerVirtualPx(scale: Float): Float =
        (VIRTUAL_SIZE / scale / (3.0 * PI)).toFloat()

    /**
     * Graticule polylines in lon/lat degrees, [lon0, lat0, lon1, lat1, ...],
     * matching d3.geoGraticule() defaults (d3-geo graticule.js): minor
     * meridians every 10 degrees of lon (skipping multiples of 90) spanning
     * lat -80..80, minor parallels every 10 degrees of lat from -80..80
     * (skipping the equator) spanning lon -180..180, major meridians at
     * -180, -90, 0 and 90 spanning the full lat range, and the equator as
     * the single major parallel. Lines are sampled every 2.5 degrees (d3's
     * default precision; d3 emits sparse meridians and lets projected
     * adaptive resampling curve them, which comes to the same drawn shape).
     *
     * Rotation-independent, computed once; [project] applies rotation at
     * draw time.
     */
    fun graticule(): List<FloatArray> = cachedGraticule

    /**
     * One horizontal drag resolved against the wheel's hysteresis threshold:
     * how many providers to advance, and the travel carried into the next
     * step. Swiping left (negative travel) advances forward, matching the
     * globe spinning east under the finger.
     *
     * The leftover travel is what makes it hysteretic: after a step the user
     * must drag another full threshold to step again, so a finger resting near
     * the boundary cannot flicker between two providers.
     *
     * Only the travel-to-steps conversion lives here: the wheel itself — the
     * centroid-relative provider order and the clamping at its ends — is the
     * sdk's ProviderLocationsViewController, shared by every platform.
     */
    fun wheelStep(travel: Float, threshold: Float): WheelStep {
        if (threshold <= 0f) {
            return WheelStep(0, 0f)
        }
        // truncates toward zero, so a fast drag can cross several steps at once
        val steps = (-travel / threshold).toInt()
        return WheelStep(steps, travel + steps * threshold)
    }

    /**
     * Fit-center layout: the virtual space is scaled to the SMALLER canvas
     * dimension and centered in both, so the globe fits whole and stays
     * centered whatever the box's aspect ratio.
     */
    fun unitFor(canvasWidth: Float, canvasHeight: Float): Float =
        min(canvasWidth, canvasHeight) / VIRTUAL_SIZE

    /** A point in the 600-unit virtual space -> canvas px, fit-centered. */
    fun toCanvas(point: GlobePoint, canvasWidth: Float, canvasHeight: Float): GlobePoint {
        val unit = unitFor(canvasWidth, canvasHeight)
        return GlobePoint(
            canvasWidth / 2f + (point.x - CENTER) * unit,
            canvasHeight / 2f + (point.y - CENTER) * unit,
        )
    }

    /** Canvas px -> the 600-unit virtual space; the inverse of [toCanvas]. */
    fun toVirtual(x: Float, y: Float, canvasWidth: Float, canvasHeight: Float): GlobePoint {
        val unit = unitFor(canvasWidth, canvasHeight)
        if (unit <= 0f) {
            return GlobePoint(CENTER, CENTER)
        }
        return GlobePoint(
            CENTER + (x - canvasWidth / 2f) / unit,
            CENTER + (y - canvasHeight / 2f) / unit,
        )
    }

    /**
     * Index of the point nearest to (x, y) within [radius] (inclusive, in
     * virtual px), or -1 if none is in range. Ties keep the earliest index.
     */
    fun nearestWithin(
        x: Float,
        y: Float,
        points: List<GlobePoint>,
        radius: Float,
    ): Int {
        val radiusSquared = radius * radius
        var best = -1
        var bestDistanceSquared = Float.MAX_VALUE
        for (i in points.indices) {
            val dx = points[i].x - x
            val dy = points[i].y - y
            val distanceSquared = dx * dx + dy * dy
            if (distanceSquared <= radiusSquared && distanceSquared < bestDistanceSquared) {
                best = i
                bestDistanceSquared = distanceSquared
            }
        }
        return best
    }

    // Rotation, ported from d3-geo rotation.js rotateRadians(dl, dp, 0):
    // rotationLambda(dl) first adds dl to the longitude, then
    // rotationPhiGamma(dp, 0) takes the unit vector
    //     x = cos(lambda1) * cos(phi1)   // toward the viewer
    //     y = sin(lambda1) * cos(phi1)   // screen right
    //     z = sin(phi1)                  // screen up (north)
    // and returns [atan2(y, x * cos(dp) - z * sin(dp)),
    //              asin(z * cos(dp) + x * sin(dp))]
    // which is the rotation about the screen-right axis mapping
    //     (x, y, z) -> (x cos dp - z sin dp, y, z cos dp + x sin dp).
    // The orthographic raw projection of the rotated (lambda2, phi2) is
    //     [cos(phi2) * sin(lambda2), sin(phi2)] = (rotated y, rotated z)
    // and cos(angular distance to the view center) = cos(phi2) * cos(lambda2)
    // = rotated x, so the three component functions below are the whole
    // pipeline with no inverse trig.

    /** Rotated x: cosine of the angular distance to the view center. */
    private fun rotateTowardViewer(
        lonDeg: Float,
        latDeg: Float,
        rotLambdaDeg: Float,
        rotPhiDeg: Float,
    ): Double {
        val lambda = (lonDeg + rotLambdaDeg) * DEGREES_TO_RADIANS
        val phi = latDeg * DEGREES_TO_RADIANS
        val deltaPhi = rotPhiDeg * DEGREES_TO_RADIANS
        return cos(lambda) * cos(phi) * cos(deltaPhi) - sin(phi) * sin(deltaPhi)
    }

    /** Rotated y: the raw orthographic screen-right coordinate, [-1, 1]. */
    private fun rotateRight(lonDeg: Float, latDeg: Float, rotLambdaDeg: Float): Double {
        val lambda = (lonDeg + rotLambdaDeg) * DEGREES_TO_RADIANS
        val phi = latDeg * DEGREES_TO_RADIANS
        return sin(lambda) * cos(phi)
    }

    /** Rotated z: the raw orthographic screen-up coordinate, [-1, 1]. */
    private fun rotateUp(
        lonDeg: Float,
        latDeg: Float,
        rotLambdaDeg: Float,
        rotPhiDeg: Float,
    ): Double {
        val lambda = (lonDeg + rotLambdaDeg) * DEGREES_TO_RADIANS
        val phi = latDeg * DEGREES_TO_RADIANS
        val deltaPhi = rotPhiDeg * DEGREES_TO_RADIANS
        return sin(phi) * cos(deltaPhi) + cos(lambda) * cos(phi) * sin(deltaPhi)
    }

    // Graticule construction, ported from d3-geo graticule.js defaults:
    // extentMajor [[-180, -90 + eps], [180, 90 - eps]], extentMinor
    // [[-180, -80 - eps], [180, 80 + eps]], stepMinor [10, 10], stepMajor
    // [90, 360], precision 2.5. d3's lines() emits major meridians
    // range(-180, 180, 90), major parallels range(0, 90 - eps, 360) (the
    // equator only), minor meridians range(-180, 180, 10) filtered to
    // abs(lon % 90) > eps, and minor parallels range(-80, 80 + eps, 10)
    // filtered to abs(lat % 360) > eps.

    private const val GRATICULE_EPSILON = 1e-6
    private const val GRATICULE_PRECISION = 2.5

    private val cachedGraticule: List<FloatArray> by lazy { buildGraticule() }

    private fun buildGraticule(): List<FloatArray> {
        val lines = ArrayList<FloatArray>(53)
        // major meridians every 90 degrees, pole to pole
        var lon = -180.0
        while (lon < 180.0) {
            lines.add(
                meridian(lon, -90.0 + GRATICULE_EPSILON, 90.0 - GRATICULE_EPSILON)
            )
            lon += 90.0
        }
        // the equator, the only major parallel
        lines.add(parallel(0.0))
        // minor meridians every 10 degrees, spanning lat -80..80
        lon = -180.0
        while (lon < 180.0) {
            if (abs(lon % 90.0) > GRATICULE_EPSILON) {
                lines.add(
                    meridian(lon, -80.0 - GRATICULE_EPSILON, 80.0 + GRATICULE_EPSILON)
                )
            }
            lon += 10.0
        }
        // minor parallels every 10 degrees from -80..80, skipping the equator
        var lat = -80.0
        while (lat <= 80.0 + GRATICULE_EPSILON) {
            if (abs(lat) > GRATICULE_EPSILON) {
                lines.add(parallel(lat))
            }
            lat += 10.0
        }
        return lines
    }

    /**
     * Sample positions along [start, end] every [GRATICULE_PRECISION]
     * degrees with d3 range semantics: start + i * step for i in 0 until
     * ceil((end - eps - start) / step), then the exact end appended.
     */
    private fun sampleSpan(start: Double, end: Double): DoubleArray {
        val steps = ceil((end - GRATICULE_EPSILON - start) / GRATICULE_PRECISION).toInt()
        return DoubleArray(steps + 1) { i ->
            if (i == steps) end else start + i * GRATICULE_PRECISION
        }
    }

    private fun meridian(lon: Double, latStart: Double, latEnd: Double): FloatArray {
        val lats = sampleSpan(latStart, latEnd)
        val line = FloatArray(lats.size * 2)
        for (i in lats.indices) {
            line[2 * i] = lon.toFloat()
            line[2 * i + 1] = lats[i].toFloat()
        }
        return line
    }

    private fun parallel(lat: Double): FloatArray {
        val lons = sampleSpan(-180.0, 180.0)
        val line = FloatArray(lons.size * 2)
        for (i in lons.indices) {
            line[2 * i] = lons[i].toFloat()
            line[2 * i + 1] = lat.toFloat()
        }
        return line
    }
}
