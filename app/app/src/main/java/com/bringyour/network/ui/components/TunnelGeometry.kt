package com.bringyour.network.ui.components

import com.bringyour.network.R
import kotlin.math.pow
import kotlin.math.roundToInt

/*
 * The slow internet tunnel (apple/DELAY.md §2 and §3): nested square outlines
 * whose strokes thin as they nest. Each square is the outer one scaled toward
 * a vanishing point past the center, so the left and top gaps open wider than
 * the right and bottom. The geometry and the motion timing are pure, so the
 * connect screen's TunnelIndicator, the VPN notification's large icon and the
 * ic_tunnel_1..4 status icons share them, and they are unit tested
 * (TunnelGeometryTest). The canvas's Tunnel component is the reference.
 */

/** The squares the tunnel has: six fit legibly at 16 dp, a seventh would crowd the tight side. */
internal const val TUNNEL_SQUARE_COUNT = 6

/** The outer square's stroke, relative to the box. The outer square is inset by half of it. */
internal const val TUNNEL_OUTER_STROKE = 0.094f

/** Each nested square's stroke, relative to the stroke of the square outside it. */
internal const val TUNNEL_STROKE_DECAY = 0.60f

/** Each nested square is the one outside it scaled by this, about the vanishing point. */
internal const val TUNNEL_SHRINK = 0.70f

/** The vanishing point, on both axes, relative to the box: past the center. */
internal const val TUNNEL_VANISHING_POINT = 0.55f

/** Each nested square is this much less opaque than the one outside it. */
internal const val TUNNEL_OPACITY_STEP = 0.08f

/**
 * One square of the tunnel, in the units of the box it was laid out in. The
 * square is the same on both axes: its stroke's centerline runs from [start]
 * to [end] on each. Corners are square, with mitered joins; rounded corners
 * soften the tunnel.
 */
internal data class TunnelSquare(
    val start: Float,
    val end: Float,
    val strokeWidth: Float,
    val opacity: Float,
) {
    /** The side of the stroke's centerline square. */
    val side: Float get() = end - start
}

/** The vanishing point on both axes of a box of `size`, the point the squares scale about. */
internal fun tunnelVanishingPoint(size: Float): Float = TUNNEL_VANISHING_POINT * size

/** All six squares of the tunnel in a box of `size`, outermost first. */
internal fun tunnelSquares(size: Float): List<TunnelSquare> {
    val outerStroke = TUNNEL_OUTER_STROKE * size
    val half = outerStroke / 2
    val vanishingPoint = tunnelVanishingPoint(size)
    return (0 until TUNNEL_SQUARE_COUNT).map { index ->
        // the outer square's stroke centerline, scaled about the vanishing point
        val scale = TUNNEL_SHRINK.pow(index)
        TunnelSquare(
            start = vanishingPoint + scale * (half - vanishingPoint),
            end = vanishingPoint + scale * (size - half - vanishingPoint),
            strokeWidth = outerStroke * TUNNEL_STROKE_DECAY.pow(index),
            opacity = 1f - TUNNEL_OPACITY_STEP * index,
        )
    }
}

/** The squares shown at each quantized quality: 0.25 one, 0.5 two, 0.75 four, 1 the full tunnel. */
private val tunnelDepthTable = listOf(0.25 to 1.0, 0.5 to 2.0, 0.75 to 4.0, 1.0 to 6.0)

/**
 * How many squares show for a quality, the worse direction's: none at 0 (the
 * indicator is hidden), then the depth table. A quality between the table's
 * points, should the sdk's quality step change from 0.25, interpolates and
 * rounds; any quality above 0 shows at least one square.
 */
internal fun tunnelDepth(quality: Double): Int {
    // also NaN: nothing to show
    if (!(0.0 < quality)) {
        return 0
    }
    val (firstQuality, firstDepth) = tunnelDepthTable.first()
    if (quality <= firstQuality) {
        return firstDepth.roundToInt()
    }
    for ((lower, upper) in tunnelDepthTable.zipWithNext()) {
        val (lowerQuality, lowerDepth) = lower
        val (upperQuality, upperDepth) = upper
        if (quality <= upperQuality) {
            val fraction = (quality - lowerQuality) / (upperQuality - lowerQuality)
            return (lowerDepth + (upperDepth - lowerDepth) * fraction).roundToInt()
        }
    }
    return TUNNEL_SQUARE_COUNT
}

/** The status icons' depths: ic_tunnel_1 to ic_tunnel_4 draw this many squares. */
internal val tunnelLevelDepths = listOf(1, 2, 4, 6)

/**
 * The status icon for a quality, 1 to 4 for ic_tunnel_1 to ic_tunnel_4, 0 at
 * quality 0: the icon with the most squares that does not draw more than the
 * in-app indicator does. The notification's icon is a static drawable per
 * level, so it cannot animate (apple/DELAY.md §3).
 */
internal fun tunnelLevel(quality: Double): Int {
    val depth = tunnelDepth(quality)
    return tunnelLevelDepths.count { it <= depth }
}

/** The status icon for a level, 1 to 4: the notification's small icon and the dashboard widget's tunnel. */
internal fun tunnelLevelIcon(level: Int): Int = when (level) {
    1 -> R.drawable.ic_tunnel_1
    2 -> R.drawable.ic_tunnel_2
    3 -> R.drawable.ic_tunnel_3
    else -> R.drawable.ic_tunnel_4
}

/** A square fades in over this long as the tunnel deepens. */
internal const val TUNNEL_FADE_IN_MILLIS = 420

/** Squares start fading in this far apart, from the outside inward, so several overlap mid-fade. */
internal const val TUNNEL_FADE_IN_STAGGER_MILLIS = 110

/** A square fades out over this long as the tunnel gets shallower. */
internal const val TUNNEL_FADE_OUT_MILLIS = 320

/** Squares start fading out this far apart, from the inside outward. */
internal const val TUNNEL_FADE_OUT_STAGGER_MILLIS = 90

/**
 * A hidden square waits this much larger, about the vanishing point, and
 * settles to its size as it fades in, so it seems to recede into the tunnel.
 * Dropped under reduced motion; the fades stay.
 */
internal const val TUNNEL_HIDDEN_SCALE = 1.06f

/** How one square animates for a change of depth. */
internal data class TunnelSquareMotion(
    /** The square shows at the new depth. */
    val visible: Boolean,
    val durationMillis: Int,
    val delayMillis: Int,
)

/**
 * How square [index] (0 outermost) animates when the depth changes from
 * [fromDepth] to [toDepth]. The squares never move: a deeper tunnel fades its
 * new squares in from the outside inward, a shallower one fades the squares
 * it drops out from the inside outward, each counted from the first square
 * that changes, so a jump between levels runs the same stagger. Squares that
 * do not change keep their state.
 */
internal fun tunnelSquareMotion(index: Int, fromDepth: Int, toDepth: Int): TunnelSquareMotion = when (index) {
    in fromDepth until toDepth -> TunnelSquareMotion(
        visible = true,
        durationMillis = TUNNEL_FADE_IN_MILLIS,
        delayMillis = (index - fromDepth) * TUNNEL_FADE_IN_STAGGER_MILLIS,
    )
    in toDepth until fromDepth -> TunnelSquareMotion(
        visible = false,
        durationMillis = TUNNEL_FADE_OUT_MILLIS,
        delayMillis = (fromDepth - 1 - index) * TUNNEL_FADE_OUT_STAGGER_MILLIS,
    )
    else -> TunnelSquareMotion(
        visible = index < toDepth,
        durationMillis = TUNNEL_FADE_IN_MILLIS,
        delayMillis = 0,
    )
}

/**
 * How long a change from [fromDepth] to [toDepth] takes, from the change to
 * the end of the last square's fade: 0 to 6 builds in 970 ms, 6 to 0 clears
 * in 770 ms.
 */
internal fun tunnelChangeMillis(fromDepth: Int, toDepth: Int): Int =
    (0 until TUNNEL_SQUARE_COUNT)
        .filter { index -> (index < fromDepth) != (index < toDepth) }
        .maxOfOrNull { index ->
            val motion = tunnelSquareMotion(index, fromDepth, toDepth)
            motion.delayMillis + motion.durationMillis
        } ?: 0

/**
 * How long after the tunnel starts clearing from [fromDepth] the outermost
 * square starts to fade. The pill around the tunnel waits this long, then
 * fades with it.
 */
internal fun tunnelClearDelayMillis(fromDepth: Int): Int =
    (fromDepth.coerceIn(0, TUNNEL_SQUARE_COUNT) - 1).coerceAtLeast(0) * TUNNEL_FADE_OUT_STAGGER_MILLIS
