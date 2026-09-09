package com.bringyour.network.ui.connect

import androidx.compose.animation.core.FloatDecayAnimationSpec
import kotlin.math.abs

/**
 * The velocity a fling of [initialVelocity] still has after it has covered
 * [travelledPx] under [decay], keeping the fling's sign. Zero when the fling
 * would have stopped within that distance; the full velocity when nothing was
 * travelled.
 *
 * The connect sheet uses this to carry a flick that opens the drawer on into
 * the drawer's content: the sheet's own travel is treated as the first part of
 * one fling, and the content receives what is left.
 */
internal fun residualFlingVelocity(
    decay: FloatDecayAnimationSpec,
    initialVelocity: Float,
    travelledPx: Float,
): Float {
    if (initialVelocity == 0f || travelledPx <= 0f) {
        return initialVelocity
    }
    val totalPx = abs(decay.getTargetValue(0f, initialVelocity))
    if (totalPx <= travelledPx) {
        return 0f
    }
    // the decay's displacement is monotone in time, so the moment the fling
    // has covered the travelled distance is found by bisection
    var lo = 0L
    var hi = decay.getDurationNanos(0f, initialVelocity)
    repeat(48) {
        val mid = (lo + hi) / 2
        if (abs(decay.getValueFromNanos(mid, 0f, initialVelocity)) < travelledPx) {
            lo = mid
        } else {
            hi = mid
        }
    }
    return decay.getVelocityFromNanos(hi, 0f, initialVelocity)
}
