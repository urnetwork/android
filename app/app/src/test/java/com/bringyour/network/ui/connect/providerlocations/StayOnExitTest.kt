package com.bringyour.network.ui.connect.providerlocations

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * "Stay on this exit" in the provider locations screen: which rows offer it,
 * and the one-provider location it connects to.
 */
class StayOnExitTest {

    private val clientId = "018f2b6e-3c4d-7a8b-9c0d-1e2f3a4b5c6d"
    private val otherClientId = "0192aaaa-bbbb-7ccc-8ddd-eeeeffff0001"

    private fun row(
        clientId: String = this.clientId,
        city: String = "",
        region: String = "",
        country: String = "",
        countryCode: String = "",
    ) = ProviderLocationRow(
        clientId = clientId,
        country = country,
        countryCode = countryCode,
        region = region,
        city = city,
        hasLocation = city.isNotEmpty() || region.isNotEmpty() || country.isNotEmpty(),
        lat = null,
        lon = null,
        connectedSinceMillis = 0,
        ipFamily = "dualstack",
        ipFamilyLabel = "both",
    )

    @Test
    fun nameIsTheShortIdThenCityAndCountry() {
        assertEquals(
            "018f…5c6d · Berlin, Germany",
            stayOnExitName(row(city = "Berlin", region = "Land Berlin", country = "Germany")),
        )
    }

    @Test
    fun nameUsesTheRegionWhenTheCityIsUnknown() {
        assertEquals(
            "018f…5c6d · California, United States",
            stayOnExitName(row(region = "California", country = "United States")),
        )
        assertEquals("018f…5c6d · Iceland", stayOnExitName(row(country = "Iceland")))
    }

    @Test
    fun nameIsTheShortIdWhenTheLocationIsUnknown() {
        assertEquals("018f…5c6d", stayOnExitName(row()))
    }

    @Test
    fun targetIsTheProviderClientIdWithItsLocation() {
        assertEquals(
            StayOnExitTarget(
                clientId = clientId,
                name = "018f…5c6d · Osaka, Japan",
                city = "Osaka",
                region = "Osaka",
                country = "Japan",
                countryCode = "jp",
            ),
            stayOnExitTarget(row(city = "Osaka", region = "Osaka", country = "Japan", countryCode = "jp")),
        )
    }

    @Test
    fun noTargetWithoutAClientId() {
        assertNull(stayOnExitTarget(row(clientId = "")))
        assertNull(stayOnExitTarget(row(clientId = "  ")))
    }

    @Test
    fun onlyTheSelectedRowOffersToStay() {
        assertEquals(StayOnExitState.OFFER, stayOnExitState(row(), clientId, null))
        assertEquals(StayOnExitState.NONE, stayOnExitState(row(clientId = otherClientId), clientId, null))
        // nothing selected (no providers) offers nothing
        assertEquals(StayOnExitState.NONE, stayOnExitState(row(), null, null))
    }

    @Test
    fun theProviderAlreadyStayedOnSaysSoInsteadOfOffering() {
        // selected or not, the stayed provider never offers itself again
        assertEquals(StayOnExitState.STAYING, stayOnExitState(row(), clientId, clientId))
        assertEquals(StayOnExitState.STAYING, stayOnExitState(row(), otherClientId, clientId))
        // another selected provider can still be stayed on instead
        assertEquals(
            StayOnExitState.OFFER,
            stayOnExitState(row(clientId = otherClientId), otherClientId, clientId),
        )
    }

    @Test
    fun clientIdsMatchIgnoringCase() {
        assertEquals(StayOnExitState.STAYING, stayOnExitState(row(), null, clientId.uppercase()))
        assertEquals(StayOnExitState.OFFER, stayOnExitState(row(), clientId.uppercase(), null))
    }

    @Test
    fun aRowWithoutAClientIdNeverOffers() {
        assertEquals(StayOnExitState.NONE, stayOnExitState(row(clientId = ""), "", null))
        assertEquals(StayOnExitState.NONE, stayOnExitState(row(clientId = ""), null, ""))
    }
}
