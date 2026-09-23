package com.bringyour.network.acceptance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhysicalRouteEvidenceTest {
    private val us = PhysicalCountryCandidate("US", "us-country-id", true, false)

    @Test
    fun `public selection requires exactly one explicit US country pool`() {
        assertEquals(1, physicalUsCountryIndex(listOf(us.copy(countryCode = "CA"), us)))
        for (candidates in listOf(emptyList(), listOf(us.copy(bestAvailable = true)),
            listOf(us.copy(locationId = null)), listOf(us.copy(isCountry = false)),
            listOf(us.copy(countryCode = "CA")), listOf(us, us))) {
            assertNull(physicalUsCountryIndex(candidates))
        }
    }

    @Test
    fun `live country cannot be copied from a requested location or a partial provider set`() {
        val live = PhysicalProviderEvidence("provider", "US", true)
        assertEquals("US", physicalLiveCountry(listOf(live, live.copy(clientId = "second"))))
        for (providers in listOf(emptyList(), listOf(live.copy(hasLocation = false)),
            listOf(live, live.copy(countryCode = "CA")), listOf(live.copy(countryCode = "")))) {
            assertEquals("", physicalLiveCountry(providers))
        }
    }

    @Test
    fun `Auto identity uses fresh carrier bytes not old totals or the mode preference`() {
        val baseline = mapOf("h1" to PhysicalCarrierBytes(100, 200))
        assertEquals("", physicalSelectedCarriers(baseline, baseline, true))
        val actual = baseline + ("h3" to PhysicalCarrierBytes(1, 2))
        assertEquals("h3", physicalSelectedCarriers(baseline, actual, true))
        assertEquals("", physicalSelectedCarriers(baseline, actual, false))
        assertEquals("h1+h3", physicalSelectedCarriers(baseline,
            actual + ("h1" to PhysicalCarrierBytes(101, 200)), true))
        assertEquals("", physicalSelectedCarriers(baseline,
            mapOf("h1" to PhysicalCarrierBytes(1, 300), "unknown" to PhysicalCarrierBytes(1, 1)), true))
    }

    @Test
    fun `peer identity requires the current target and every live provider to match`() {
        val peer = PhysicalProviderEvidence("peer-a", "", false)
        assertEquals("peer-a", physicalSelectedPeer("peer-a", listOf(peer), true))
        assertEquals("", physicalSelectedPeer("peer-a", listOf(peer), false))
        assertEquals("", physicalSelectedPeer("peer-a", emptyList(), true))
        assertEquals("", physicalSelectedPeer("peer-a", listOf(peer.copy(clientId = "peer-b")), true))
        assertEquals("", physicalSelectedPeer("peer-a", listOf(peer, peer.copy(clientId = "peer-b")), true))
        assertEquals("", physicalSelectedPeer(null, listOf(peer), true))
    }
}
