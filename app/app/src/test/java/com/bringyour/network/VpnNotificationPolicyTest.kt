package com.bringyour.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnNotificationPolicyTest {
    private fun presentation(
        connectRequested: Boolean = true,
        systemAlwaysOn: Boolean = false,
        providerCount: Long = 0,
        // a window with providers is satisfied unless a test says otherwise
        minSatisfied: Boolean = 0 < providerCount,
        failed: Boolean = false,
        locationProviderCount: Int = 0,
        tunnelStarted: Boolean = true,
        name: String? = "Chicago",
        city: String? = null,
        region: String? = null,
        country: String? = null,
    ) = vpnNotificationPresentation(
        connectRequested = connectRequested,
        systemAlwaysOn = systemAlwaysOn,
        providerCount = providerCount,
        minSatisfied = minSatisfied,
        failed = failed,
        locationProviderCount = locationProviderCount,
        tunnelStarted = tunnelStarted,
        locationName = name,
        city = city,
        region = region,
        country = country,
        bestAvailableLabel = "Best available provider",
    )

    @Test
    fun remoteConnectionShowsLocationProvidersAndDisconnect() {
        val result = presentation(providerCount = 3)

        assertEquals(VpnNotificationStatus.CONNECTED, result.status)
        assertEquals("Chicago", result.locationLabel)
        assertEquals(3, result.providerCount)
        assertTrue(result.showDisconnect)
    }

    @Test
    fun pendingConnectionShowsConnectingWithZeroProviders() {
        val result = presentation(providerCount = 0)

        assertEquals(VpnNotificationStatus.CONNECTING, result.status)
        assertEquals(0, result.providerCount)
        assertTrue(result.showDisconnect)
    }

    @Test
    fun nonRemoteVpnModeDoesNotOfferDisconnect() {
        val result = presentation(connectRequested = false, providerCount = 9)

        assertEquals(VpnNotificationStatus.ACTIVE, result.status)
        assertEquals(null, result.locationLabel)
        assertFalse(result.showDisconnect)
    }

    @Test
    fun alwaysOnPolicyHidesAnIneffectiveDisconnectAction() {
        val result = presentation(systemAlwaysOn = true, providerCount = 1)

        assertEquals(VpnNotificationStatus.CONNECTED, result.status)
        assertFalse(result.showDisconnect)
    }

    @Test
    fun locationFallsBackThroughSpecificFieldsThenBestAvailable() {
        assertEquals(
            "Paris",
            presentation(name = " ", city = "Paris", region = "Île-de-France", country = "France")
                .locationLabel,
        )
        assertEquals(
            "Best available provider",
            presentation(name = null, city = "", region = " ", country = null).locationLabel,
        )
    }

    @Test
    fun aFormingWindowIsConnectingThoughItHasProviders() {
        // the connect screen says connecting until the window is satisfied
        val result = presentation(providerCount = 2, minSatisfied = false)

        assertEquals(VpnNotificationStatus.CONNECTING, result.status)
        assertEquals(2, result.providerCount)
    }

    @Test
    fun aFailedConnectSaysSoAndKeepsDisconnect() {
        val result = presentation(providerCount = 0, failed = true)

        assertEquals(VpnNotificationStatus.CONNECT_FAILED, result.status)
        assertTrue(result.showDisconnect)
    }

    @Test
    fun aConnectionWithTheTunnelDownSaysSo() {
        val result = presentation(providerCount = 3, tunnelStarted = false)

        assertEquals(VpnNotificationStatus.TUNNEL_DISCONNECTED, result.status)
        assertTrue(result.showDisconnect)
    }

    @Test
    fun aLocationWithFewerProvidersIsConnectedOnceAllAreAdded() {
        val result = presentation(providerCount = 1, minSatisfied = false, locationProviderCount = 1)

        assertEquals(VpnNotificationStatus.CONNECTED, result.status)
    }

    @Test
    fun providerCountIsSafeForNotificationResourceFormatting() {
        assertEquals(0, presentation(providerCount = -1).providerCount)
        assertEquals(Int.MAX_VALUE, presentation(providerCount = Long.MAX_VALUE).providerCount)
    }
}
