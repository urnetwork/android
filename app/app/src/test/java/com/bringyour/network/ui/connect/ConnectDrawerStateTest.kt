package com.bringyour.network.ui.connect

import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectDrawerStateTest {

    // offsets grow downward: the expanded drawer sits at 800, the collapsed one at 2200
    private val expanded = 800f
    private val collapsed = 2200f
    private val velocityThreshold = 350f
    private val positionalThreshold = 150f

    private fun target(offset: Float, velocity: Float, from: ConnectDrawerValue) =
        ConnectDrawerState.releaseTarget(
            offset = offset,
            velocity = velocity,
            from = from,
            expandedOffset = expanded,
            collapsedOffset = collapsed,
            velocityThresholdPx = velocityThreshold,
            positionalThresholdPx = positionalThreshold,
        )

    @Test
    fun aFastReleaseGoesTheWayItMoves() {
        assertEquals(ConnectDrawerValue.Expanded, target(2150f, -2000f, ConnectDrawerValue.Collapsed))
        assertEquals(ConnectDrawerValue.Collapsed, target(850f, 2000f, ConnectDrawerValue.Expanded))
        // even against the distance travelled so far
        assertEquals(ConnectDrawerValue.Collapsed, target(900f, 400f, ConnectDrawerValue.Collapsed))
        assertEquals(ConnectDrawerValue.Expanded, target(2100f, -400f, ConnectDrawerValue.Expanded))
    }

    @Test
    fun aSlowReleaseNeedsTheTravelDistance() {
        // from collapsed, opening
        assertEquals(ConnectDrawerValue.Collapsed, target(2100f, -50f, ConnectDrawerValue.Collapsed))
        assertEquals(ConnectDrawerValue.Expanded, target(2050f, -50f, ConnectDrawerValue.Collapsed))
        assertEquals(ConnectDrawerValue.Expanded, target(1000f, 0f, ConnectDrawerValue.Collapsed))
        // from expanded, closing
        assertEquals(ConnectDrawerValue.Expanded, target(900f, 50f, ConnectDrawerValue.Expanded))
        assertEquals(ConnectDrawerValue.Collapsed, target(950f, 50f, ConnectDrawerValue.Expanded))
        assertEquals(ConnectDrawerValue.Collapsed, target(2000f, 0f, ConnectDrawerValue.Expanded))
    }

    @Test
    fun aSlowReleaseBackTowardsRestStaysAtRest() {
        assertEquals(ConnectDrawerValue.Collapsed, target(2200f, 100f, ConnectDrawerValue.Collapsed))
        assertEquals(ConnectDrawerValue.Expanded, target(800f, -100f, ConnectDrawerValue.Expanded))
    }
}
