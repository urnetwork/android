package com.bringyour.network.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TapSequenceGateTest {

    @Test
    fun fiveQuickTapsCompleteTheSequence() {
        val gate = TapSequenceGate(count = 5, windowMillis = 5_000L)
        assertFalse(gate.tap(0L))
        assertFalse(gate.tap(1_000L))
        assertFalse(gate.tap(2_000L))
        assertFalse(gate.tap(3_000L))
        assertTrue(gate.tap(4_000L))
        assertEquals(0, gate.progress)
    }

    @Test
    fun aGapLongerThanTheWindowResetsTheCount() {
        val gate = TapSequenceGate(count = 5, windowMillis = 5_000L)
        gate.tap(0L)
        gate.tap(1_000L)
        gate.tap(2_000L)
        assertEquals(3, gate.progress)
        // 5.001 s after the last tap: the sequence starts over with this tap
        assertFalse(gate.tap(7_001L))
        assertEquals(1, gate.progress)
        assertFalse(gate.tap(8_000L))
        assertFalse(gate.tap(9_000L))
        assertFalse(gate.tap(10_000L))
        assertTrue(gate.tap(11_000L))
    }

    @Test
    fun aTapExactlyAtTheWindowStillCounts() {
        val gate = TapSequenceGate(count = 2, windowMillis = 5_000L)
        gate.tap(0L)
        assertTrue(gate.tap(5_000L))
    }

    @Test
    fun tapsAfterACompletionStartANewSequence() {
        val gate = TapSequenceGate(count = 5, windowMillis = 5_000L)
        for (i in 0 until 4) assertFalse(gate.tap(i * 100L))
        assertTrue(gate.tap(400L))
        assertEquals(0, gate.progress)
        // the next four are not enough on their own
        for (i in 5 until 9) assertFalse(gate.tap(i * 100L))
        assertTrue(gate.tap(900L))
    }

    @Test
    fun resetDropsTheCount() {
        val gate = TapSequenceGate(count = 3, windowMillis = 5_000L)
        gate.tap(0L)
        gate.tap(100L)
        gate.reset()
        assertFalse(gate.tap(200L))
        assertFalse(gate.tap(300L))
        assertTrue(gate.tap(400L))
    }
}
