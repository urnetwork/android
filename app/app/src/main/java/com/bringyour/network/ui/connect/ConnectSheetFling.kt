package com.bringyour.network.ui.connect

import androidx.compose.animation.core.FloatDecayAnimationSpec
import kotlin.math.abs

/**
 * The moment, on the fling's own clock, at which a fling of [initialVelocity]
 * has covered [travelledPx] under [decay]: zero when nothing was travelled,
 * the fling's full duration when it would stop short of that distance.
 *
 * The connect sheet treats its own travel as the first part of one fling:
 * the drawer's content then continues the same curve from this moment, so
 * the motion reads as a single scroll across the sheet and its content.
 */
internal fun flingTravelTimeNanos(
    decay: FloatDecayAnimationSpec,
    initialVelocity: Float,
    travelledPx: Float,
): Long {
    if (initialVelocity == 0f) {
        return 0L
    }
    val durationNanos = decay.getDurationNanos(0f, initialVelocity)
    if (travelledPx <= 0f) {
        return 0L
    }
    val totalPx = abs(decay.getTargetValue(0f, initialVelocity))
    if (totalPx <= travelledPx) {
        return durationNanos
    }
    // the decay's displacement is monotone in time, so the moment the fling
    // has covered the travelled distance is found by bisection
    var lo = 0L
    var hi = durationNanos
    repeat(48) {
        val mid = (lo + hi) / 2
        if (abs(decay.getValueFromNanos(mid, 0f, initialVelocity)) < travelledPx) {
            lo = mid
        } else {
            hi = mid
        }
    }
    return hi
}

/**
 * The velocity a fling of [initialVelocity] still has after it has covered
 * [travelledPx] under [decay], keeping the fling's sign. Zero when the fling
 * would have stopped within that distance; the full velocity when nothing was
 * travelled.
 */
internal fun residualFlingVelocity(
    decay: FloatDecayAnimationSpec,
    initialVelocity: Float,
    travelledPx: Float,
): Float {
    if (initialVelocity == 0f || travelledPx <= 0f) {
        return initialVelocity
    }
    val durationNanos = decay.getDurationNanos(0f, initialVelocity)
    val travelTimeNanos = flingTravelTimeNanos(decay, initialVelocity, travelledPx)
    if (travelTimeNanos >= durationNanos) {
        return 0f
    }
    return decay.getVelocityFromNanos(travelTimeNanos, 0f, initialVelocity)
}

/**
 * How far the drawer's content should have scrolled [elapsedNanos] after it
 * took over a fling of [initialVelocity] whose first [travelledPx] went into
 * the sheet: the fling's displacement from its travel moment onward. Zero
 * before the content starts moving and never negative.
 */
internal fun carriedContentDistancePx(
    decay: FloatDecayAnimationSpec,
    initialVelocity: Float,
    travelledPx: Float,
    elapsedNanos: Long,
): Float {
    if (initialVelocity == 0f || elapsedNanos <= 0L) {
        return 0f
    }
    val durationNanos = decay.getDurationNanos(0f, initialVelocity)
    val travelTimeNanos = flingTravelTimeNanos(decay, initialVelocity, travelledPx)
    if (travelTimeNanos >= durationNanos) {
        return 0f
    }
    val t = minOf(durationNanos, travelTimeNanos + elapsedNanos)
    val distance = abs(decay.getValueFromNanos(t, 0f, initialVelocity)) - travelledPx.coerceAtLeast(0f)
    return distance.coerceAtLeast(0f)
}
