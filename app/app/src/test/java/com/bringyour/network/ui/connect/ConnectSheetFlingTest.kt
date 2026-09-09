package com.bringyour.network.ui.connect

import androidx.compose.animation.core.FloatExponentialDecaySpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ConnectSheetFlingTest {

    // an exponential decay is memoryless, so a fling restarted from its
    // residual velocity must cover exactly the distance that was left
    private val decay = FloatExponentialDecaySpec(frictionMultiplier = 1f, absVelocityThreshold = 0.1f)

    @Test
    fun nothingTravelledKeepsTheWholeVelocity() {
        assertEquals(-4000f, residualFlingVelocity(decay, -4000f, 0f), 0f)
        assertEquals(-4000f, residualFlingVelocity(decay, -4000f, -10f), 0f)
        assertEquals(0L, flingTravelTimeNanos(decay, -4000f, 0f))
    }

    @Test
    fun zeroVelocityStaysZero() {
        assertEquals(0f, residualFlingVelocity(decay, 0f, 300f), 0f)
        assertEquals(0f, carriedContentDistancePx(decay, 0f, 300f, 500_000_000L), 0f)
    }

    @Test
    fun travellingTheWholeFlingLeavesNothing() {
        val total = abs(decay.getTargetValue(0f, -4000f))
        assertEquals(0f, residualFlingVelocity(decay, -4000f, total), 0f)
        assertEquals(0f, residualFlingVelocity(decay, -4000f, total + 1f), 0f)
        assertEquals(decay.getDurationNanos(0f, -4000f), flingTravelTimeNanos(decay, -4000f, total + 1f))
        assertEquals(0f, carriedContentDistancePx(decay, -4000f, total + 1f, 5_000_000_000L), 0f)
    }

    @Test
    fun partialTravelLeavesTheDistanceThatRemains() {
        val initial = -6000f
        val total = abs(decay.getTargetValue(0f, initial))
        for (fraction in listOf(0.1f, 0.5f, 0.9f)) {
            val travelled = total * fraction
            val residual = residualFlingVelocity(decay, initial, travelled)
            assertTrue("residual keeps the fling's sign", residual < 0f)
            assertTrue("residual is slower than the start", abs(residual) < abs(initial))
            val remaining = abs(decay.getTargetValue(0f, residual))
            assertEquals(total - travelled, remaining, total * 0.01f)
        }
    }

    @Test
    fun moreTravelLeavesLessVelocityAndComesLater() {
        val a = abs(residualFlingVelocity(decay, -5000f, 200f))
        val b = abs(residualFlingVelocity(decay, -5000f, 800f))
        val c = abs(residualFlingVelocity(decay, -5000f, 1400f))
        assertTrue(a > b && b > c)
        val ta = flingTravelTimeNanos(decay, -5000f, 200f)
        val tb = flingTravelTimeNanos(decay, -5000f, 800f)
        val tc = flingTravelTimeNanos(decay, -5000f, 1400f)
        assertTrue(ta < tb && tb < tc)
    }

    @Test
    fun travelTimeIsWhereTheFlingReachesTheDistance() {
        val t = flingTravelTimeNanos(decay, -5000f, 900f)
        assertEquals(900f, abs(decay.getValueFromNanos(t, 0f, -5000f)), 0.5f)
    }

    @Test
    fun carriedDistanceContinuesTheFlingPastTheSheet() {
        val initial = -5000f
        val travelled = 900f
        val total = abs(decay.getTargetValue(0f, initial))
        assertEquals(0f, carriedContentDistancePx(decay, initial, travelled, 0L), 0f)
        val early = carriedContentDistancePx(decay, initial, travelled, 50_000_000L)
        val late = carriedContentDistancePx(decay, initial, travelled, 400_000_000L)
        assertTrue("content starts moving right away", early > 0f)
        assertTrue("content keeps going", late > early)
        // the whole remainder of the fling, and not a pixel more
        val done = carriedContentDistancePx(decay, initial, travelled, 60_000_000_000L)
        assertEquals(total - travelled, done, 0.5f)
    }

    @Test
    fun downwardFlingsKeepTheirSign() {
        val residual = residualFlingVelocity(decay, 3000f, 100f)
        assertTrue(residual > 0f && residual < 3000f)
    }
}
