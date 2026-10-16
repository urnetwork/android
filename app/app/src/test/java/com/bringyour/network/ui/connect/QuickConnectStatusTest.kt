package com.bringyour.network.ui.connect

import com.bringyour.network.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * What the Quick Settings tile, the dashboard widget and the VPN notification
 * say about the connection (owner report 2026-10-16, as the Apple app's menu
 * bar since ba2a36d3, e46555b7 and 64cd2c1b): connected only once the window
 * holds its providers and the tunnel is up, a failed connect never as
 * connected, a forming one and a tap's state as connecting, the tunnel down
 * in the connect screen's words, and no connection to act on while signed
 * out.
 */
class QuickConnectStatusTest {

    private fun status(
        configured: Boolean = true,
        requested: Boolean = true,
        startWaiting: Boolean = false,
        connectQueued: Boolean = false,
        minSatisfied: Boolean = false,
        failed: Boolean = false,
        addedProviderCount: Int = 0,
        locationProviderCount: Int = 0,
        tunnelStarted: Boolean = true,
    ) = quickConnectStatus(
        configured = configured,
        requested = requested,
        startWaiting = startWaiting,
        connectQueued = connectQueued,
        minSatisfied = minSatisfied,
        failed = failed,
        addedProviderCount = addedProviderCount,
        locationProviderCount = locationProviderCount,
        tunnelStarted = tunnelStarted,
    )

    @Test
    fun connectedOnlyOnceTheWindowHoldsItsProvidersAndTheTunnelIsUp() {
        assertEquals(QuickConnectStatus.CONNECTED, status(minSatisfied = true, addedProviderCount = 4))
        assertEquals(
            QuickConnectStatus.TUNNEL_DISCONNECTED,
            status(minSatisfied = true, addedProviderCount = 4, tunnelStarted = false),
        )
    }

    @Test
    fun aFormingWindowIsConnecting() {
        // providers added, the window not satisfied yet: the connect screen
        // says connecting, and so do the surfaces
        assertEquals(QuickConnectStatus.CONNECTING, status())
        assertEquals(QuickConnectStatus.CONNECTING, status(addedProviderCount = 2))
        assertEquals(QuickConnectStatus.CONNECTING, status(addedProviderCount = 2, tunnelStarted = false))
    }

    @Test
    fun aFailedConnectIsNeverConnected() {
        for (tunnelStarted in listOf(false, true)) {
            assertEquals(QuickConnectStatus.CONNECT_FAILED, status(failed = true, tunnelStarted = tunnelStarted))
        }
    }

    @Test
    fun aRecoveredWindowWinsOverItsFailure() {
        assertEquals(QuickConnectStatus.CONNECTED, status(failed = true, minSatisfied = true, addedProviderCount = 1))
    }

    @Test
    fun aLocationWithFewerProvidersIsConnectedOnceAllAreAdded() {
        assertEquals(QuickConnectStatus.CONNECTED, status(addedProviderCount = 1, locationProviderCount = 1))
        assertEquals(QuickConnectStatus.CONNECTING, status(addedProviderCount = 1, locationProviderCount = 2))
    }

    @Test
    fun aTapsStateIsConnecting() {
        // a start waiting on its balance, or a connect still queued: what the
        // window says is about the connection before the tap
        for (connected in listOf(false, true)) {
            assertEquals(
                QuickConnectStatus.CONNECTING,
                status(requested = false, startWaiting = true, minSatisfied = connected, addedProviderCount = 3),
            )
            assertEquals(
                QuickConnectStatus.CONNECTING,
                status(connectQueued = true, minSatisfied = connected, failed = !connected),
            )
        }
    }

    @Test
    fun noConnectionAskedForIsDisconnected() {
        assertEquals(QuickConnectStatus.DISCONNECTED, status(requested = false, minSatisfied = true, addedProviderCount = 3))
    }

    @Test
    fun signedOutThereIsNoConnectionToActOn() {
        assertEquals(
            QuickConnectStatus.NOT_SIGNED_IN,
            status(configured = false, startWaiting = true, minSatisfied = true, addedProviderCount = 3),
        )
    }

    @Test
    fun onlyConnectedSaysConnected() {
        assertEquals(R.string.tile_status_connected, quickConnectStatusText(QuickConnectStatus.CONNECTED))
        assertEquals(R.string.conn_failed, quickConnectStatusText(QuickConnectStatus.CONNECT_FAILED))
        assertEquals(R.string.connecting_status_indicator, quickConnectStatusText(QuickConnectStatus.CONNECTING))
        assertEquals(
            R.string.reconnect_tunnel_status_indicator,
            quickConnectStatusText(QuickConnectStatus.TUNNEL_DISCONNECTED),
        )
        for (other in QuickConnectStatus.entries.filter { it != QuickConnectStatus.CONNECTED }) {
            assertNotEquals(other.name, R.string.tile_status_connected, quickConnectStatusText(other))
        }
    }
}
