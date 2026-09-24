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
    fun `public selection accepts the lowercase country code emitted by the API`() {
        for (countryCode in listOf("us", "Us", "uS", "US")) {
            assertEquals(1, physicalUsCountryIndex(listOf(
                us.copy(countryCode = "ca"), us.copy(countryCode = countryCode))))
        }
        // Case differences never turn an ambiguous pool into a unique choice.
        assertNull(physicalUsCountryIndex(listOf(us, us.copy(countryCode = "us"))))
        for (candidate in listOf(us.copy(countryCode = "us", bestAvailable = true),
            us.copy(countryCode = "us", locationId = " "),
            us.copy(countryCode = "us", isCountry = false))) {
            assertNull(physicalUsCountryIndex(listOf(candidate)))
        }
    }

    @Test
    fun `country evidence rejects malformed codes and Unicode case lookalikes`() {
        for (countryCode in listOf("", " ", " US", "us ", "usa", "u\u017f", "\uff35\uff33", "u1")) {
            assertNull(physicalUsCountryIndex(listOf(us.copy(countryCode = countryCode))))
            assertEquals("", physicalLiveCountry(listOf(
                PhysicalProviderEvidence("provider", countryCode, true))))
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
    fun `live provider evidence normalizes API country codes before the US gate`() {
        val live = PhysicalProviderEvidence("provider", "us", true)
        assertEquals("US", physicalLiveCountry(listOf(live)))
        assertEquals("US", physicalLiveCountry(listOf(live, live.copy(clientId = "second", countryCode = "US"))))
        assertEquals("CA", physicalLiveCountry(listOf(live.copy(countryCode = "ca"))))
        for (provider in listOf(live.copy(countryCode = "ca"), live.copy(countryCode = ""),
            live.copy(countryCode = "u\u017f"), live.copy(hasLocation = false))) {
            assertEquals("", physicalLiveCountry(listOf(live, provider)))
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
