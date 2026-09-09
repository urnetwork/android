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
    }

    @Test
    fun zeroVelocityStaysZero() {
        assertEquals(0f, residualFlingVelocity(decay, 0f, 300f), 0f)
    }

    @Test
    fun travellingTheWholeFlingLeavesNothing() {
        val total = abs(decay.getTargetValue(0f, -4000f))
        assertEquals(0f, residualFlingVelocity(decay, -4000f, total), 0f)
        assertEquals(0f, residualFlingVelocity(decay, -4000f, total + 1f), 0f)
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
    fun moreTravelLeavesLessVelocity() {
        val a = abs(residualFlingVelocity(decay, -5000f, 200f))
        val b = abs(residualFlingVelocity(decay, -5000f, 800f))
        val c = abs(residualFlingVelocity(decay, -5000f, 1400f))
        assertTrue(a > b && b > c)
    }

    @Test
    fun downwardFlingsKeepTheirSign() {
        val residual = residualFlingVelocity(decay, 3000f, 100f)
        assertTrue(residual > 0f && residual < 3000f)
    }
}
