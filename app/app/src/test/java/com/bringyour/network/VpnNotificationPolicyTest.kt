package com.bringyour.network

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
        slowInternetPhase: SlowInternetNoticePhase = SlowInternetNoticePhase.NONE,
        transportDelay: TransportDelayUi = TransportDelayUi.None,
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
        slowInternetPhase = slowInternetPhase,
        transportDelay = transportDelay,
    )

    private val slowDelay = TransportDelayUi(
        upQuality = 0.25,
        downQuality = 0.75,
        upBitsPerSecond = 48_000,
        downBitsPerSecond = 85_000,
        upQueueDelayMillis = 640,
        downQueueDelayMillis = 1_200,
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

    @Test
    fun aSlowConnectionSwapsTheConnectedContent() {
        val result = presentation(
            providerCount = 3,
            slowInternetPhase = SlowInternetNoticePhase.SLOW,
            transportDelay = slowDelay,
        )

        assertEquals(VpnNotificationStatus.CONNECTED, result.status)
        // the worse direction's 0.75 draws ic_tunnel_3
        assertEquals(SlowInternetPresentation.Slow(level = 3, fallback = false, delay = slowDelay), result.slowInternet)
        // the same notification: Disconnect stays
        assertTrue(result.showDisconnect)
    }

    @Test
    fun theStatusIconFollowsTheLevel() {
        val levels = listOf(0.25, 0.5, 0.75, 1.0).map { quality ->
            val slow = presentation(
                providerCount = 3,
                slowInternetPhase = SlowInternetNoticePhase.SLOW,
                transportDelay = TransportDelayUi(upQuality = quality),
            ).slowInternet as SlowInternetPresentation.Slow
            slow.level
        }
        assertEquals(listOf(1, 2, 3, 4), levels)
    }

    @Test
    fun aFallbackConnectionSaysSo() {
        val result = presentation(
            providerCount = 3,
            slowInternetPhase = SlowInternetNoticePhase.SLOW,
            transportDelay = slowDelay.copy(fallback = true),
        )

        assertEquals(true, (result.slowInternet as SlowInternetPresentation.Slow).fallback)
    }

    @Test
    fun backToNormalFollowsTheSlowContent() {
        val result = presentation(providerCount = 3, slowInternetPhase = SlowInternetNoticePhase.RECOVERED)

        assertEquals(SlowInternetPresentation.Recovered, result.slowInternet)
        assertTrue(result.showDisconnect)
    }

    @Test
    fun aSlowReadingTheDwellHasNotShownKeepsTheNormalContent() {
        val result = presentation(providerCount = 3, transportDelay = slowDelay)

        assertEquals(null, result.slowInternet)
    }

    @Test
    fun theSlowContentNeverStandsInForAnotherStatus() {
        // connecting, failed, tunnel down, and a VPN without a connection
        // each say what they are; "still connected" would not be true
        for (result in listOf(
            presentation(providerCount = 0, slowInternetPhase = SlowInternetNoticePhase.SLOW, transportDelay = slowDelay),
            presentation(providerCount = 0, failed = true, slowInternetPhase = SlowInternetNoticePhase.SLOW, transportDelay = slowDelay),
            presentation(providerCount = 3, tunnelStarted = false, slowInternetPhase = SlowInternetNoticePhase.SLOW, transportDelay = slowDelay),
            presentation(connectRequested = false, slowInternetPhase = SlowInternetNoticePhase.RECOVERED),
        )) {
            assertTrue(result.status != VpnNotificationStatus.CONNECTED)
            assertEquals(null, result.slowInternet)
        }
    }

    @Test
    fun aSlowReadingAtZeroStillGetsAStatusIcon() {
        val result = presentation(providerCount = 3, slowInternetPhase = SlowInternetNoticePhase.SLOW)

        assertEquals(1, (result.slowInternet as SlowInternetPresentation.Slow).level)
    }

    @Test
    fun theServiceFoldsTransportDelayReportsIntoOneUpdate() {
        // the listener fires on the sdk's thread and nothing else throttles
        // the notification (apple/DELAY.md §7.2): each report requests one
        // coalesced main thread read, as the tile's status listeners do
        val file = listOf("", "app/", "app/app/")
            .map { File(it + "src/main/java/com/bringyour/network/MainService.kt") }
            .firstOrNull { it.isFile }
        assertNotNull(file)
        val source = file!!.readText()
        fun function(signature: String): String {
            val from = source.indexOf(signature)
            assertTrue(signature, 0 <= from)
            return source.substring(from, source.indexOf("\n    }\n", from))
        }

        val attach = function("    private fun attachToCurrentDevice() {")
        assertTrue(attach, attach.contains("CoalescedPost(post = { mainHandler.post(it) })"))
        assertTrue(attach, attach.contains("addTransportDelayChangeListener { transportDelayUpdate.request() }"))
        // the listener does not report the current value: it is read once registered
        assertTrue(
            attach,
            attach.indexOf("addTransportDelayChangeListener") < attach.lastIndexOf("updateSlowInternetNotice(notify = false)"),
        )

        val detach = function("    private fun detachDeviceBindings(closePacketFlow: Boolean) {")
        assertTrue(detach, detach.contains("transportDelaySub?.close()"))
        assertTrue(detach, detach.contains("mainHandler.removeCallbacks(slowInternetCheck)"))
        assertTrue(detach, detach.contains("slowInternetNotice = SlowInternetNotice()"))
    }
}
