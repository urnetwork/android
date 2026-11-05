package com.bringyour.network.acceptance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivateRelayOnlyTest {
    @Test fun controlsPrecedeConnectionAndDoNotAffectOrdinarySessions() {
        val events = mutableListOf<String>()
        fun configure(enabled: Boolean) = configurePrivateRelayOnly(enabled,
            { events.add("retire-provider") }, { events.add("direct-off") }, { events.add("client-h1") },
            { events.add("provider-h1") }, { events.add("verify") })
        configure(false)
        assertTrue(events.isEmpty())
        configure(true)
        events.add("start")
        assertEquals(listOf("retire-provider", "direct-off", "client-h1", "provider-h1", "verify", "start"), events)
        assertFalse(requirePrivateRelayOnly(null, "hosted-h1-api-alt-off-scoped-v2"))
        assertTrue(requirePrivateRelayOnly("true", ""))
    }
    @Test(expected = IllegalArgumentException::class)
    fun scopedMemoryCannotAdoptPrivateRelay() { requirePrivateRelayOnly("true", "hosted-h1-api-alt-off-scoped-v2") }
    @Test(expected = IllegalArgumentException::class)
    fun malformedArgumentFails() { requirePrivateRelayOnly("false", "") }
    @Test fun capturedPinSurvivesMutationAndIgnoresDecoyFirst() {
        var file: String? = "expected-peer"
        var reads = 0
        val pin = capturePrivateRelayPin { reads++; file }
        file = "decoy"
        assertEquals("expected-peer", listOf("decoy", "expected-peer").first { privateRelayPeerMatches(pin, it) })
        file = null
        assertFalse(privateRelayPeerMatches(pin, "decoy"))
        assertFalse(privateRelayPeerMatches(pin, null))
        assertTrue(privateRelayPeerMatches(pin, "expected-peer"))
        assertEquals(1, reads)
    }
    @Test(expected = IllegalArgumentException::class)
    fun missingPinRefusesBeforeLookup() { capturePrivateRelayPin { null } }
    @Test(expected = IllegalArgumentException::class)
    fun emptyPinRefusesBeforeLookup() { capturePrivateRelayPin { "  " } }
    @Test(expected = IllegalArgumentException::class)
    fun publicPeerModeRefused() { requirePrivateRelayPeerMode(false, "h1") }
    @Test(expected = IllegalArgumentException::class)
    fun h3Refused() { requirePrivateRelayPeerMode(true, "h3") }
    @Test fun networkH1Accepted() { requirePrivateRelayPeerMode(true, "h1") }
}
