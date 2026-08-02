package com.bringyour.network.ui.connect

import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectButtonAnimationTest {
    @Test
    fun pulseStartsAtOpaqueInitialDiameter() {
        val frame = tapToConnectPulseFrame(
            initialDiameter = 56f,
            targetDiameter = 82f,
            progress = 0f,
        )

        assertEquals(56f, frame.diameter, 0f)
        assertEquals(1f, frame.alpha, 0f)
    }

    @Test
    fun pulseMidpointInterpolatesDiameterAndAlpha() {
        val frame = tapToConnectPulseFrame(
            initialDiameter = 56f,
            targetDiameter = 82f,
            progress = 0.5f,
        )

        assertEquals(69f, frame.diameter, 0f)
        assertEquals(0.5f, frame.alpha, 0f)
    }

    @Test
    fun pulseEndsAtTransparentTargetDiameter() {
        val frame = tapToConnectPulseFrame(
            initialDiameter = 56f,
            targetDiameter = 82f,
            progress = 1f,
        )

        assertEquals(82f, frame.diameter, 0f)
        assertEquals(0f, frame.alpha, 0f)
    }

    @Test
    fun pulseClampsOutOfRangeProgress() {
        val frame = tapToConnectPulseFrame(
            initialDiameter = 56f,
            targetDiameter = 82f,
            progress = 2f,
        )

        assertEquals(82f, frame.diameter, 0f)
        assertEquals(0f, frame.alpha, 0f)
    }
}
