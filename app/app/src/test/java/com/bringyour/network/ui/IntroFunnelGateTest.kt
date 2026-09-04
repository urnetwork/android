package com.bringyour.network.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IntroFunnelGateTest {

    /** Stands in for the SDK local-state flag behind the gate. */
    private class FlagStore(var armed: Boolean) {
        var clears = 0
        fun gate() = IntroFunnelGate(
            readCanPrompt = { armed },
            clearCanPrompt = { armed = false; clears += 1 },
        )
    }

    @Test
    fun armedFlagAllowsOnce() {
        val store = FlagStore(armed = true)
        val gate = store.gate()
        assertTrue(gate.allow.value)

        gate.markPrompted()

        assertFalse(gate.allow.value)
        assertFalse(store.armed)
        assertEquals(1, store.clears)
    }

    @Test
    fun recreatedGateReadsTheClearedFlag() {
        // rotation recreates the activity; the flag store outlives it
        val store = FlagStore(armed = true)
        store.gate().markPrompted()

        val recreated = store.gate()

        assertFalse(recreated.allow.value)
    }

    @Test
    fun unarmedFlagNeverAllows() {
        val store = FlagStore(armed = false)
        val gate = store.gate()

        assertFalse(gate.allow.value)
        gate.refresh()
        assertFalse(gate.allow.value)
        assertEquals(0, store.clears)
    }

    @Test
    fun refreshFollowsTheStore() {
        val store = FlagStore(armed = false)
        val gate = store.gate()

        store.armed = true
        gate.refresh()

        assertTrue(gate.allow.value)
    }

    @Test
    fun proNetworksNeverSeeTheFunnel() {
        assertFalse(IntroFunnelGate.shouldPrompt(isPro = true, canPrompt = true))
        assertTrue(IntroFunnelGate.shouldPrompt(isPro = false, canPrompt = true))
        assertFalse(IntroFunnelGate.shouldPrompt(isPro = false, canPrompt = false))
    }
}
