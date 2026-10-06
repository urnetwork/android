package com.bringyour.network.ui.connect

import com.bringyour.network.R
import com.bringyour.network.ui.shared.models.ConnectStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The connect button names itself to screen readers in every state but the
 * one whose visible "Tap to connect" names it. Once connected it drew only
 * the provider grid and had no name at all.
 */
class ConnectButtonDescriptionTest {

    private fun description(
        status: ConnectStatus,
        insufficientBalance: Boolean = false,
        isPollingSubscriptionBalance: Boolean = false,
        displayReconnectTunnel: Boolean = false,
    ) = connectButtonDescriptionId(
        status = status,
        insufficientBalance = insufficientBalance,
        isPollingSubscriptionBalance = isPollingSubscriptionBalance,
        displayReconnectTunnel = displayReconnectTunnel,
    )

    @Test
    fun `connected names the button`() {
        assertEquals(R.string.connected, description(ConnectStatus.CONNECTED))
    }

    @Test
    fun `each state names the button for what it shows`() {
        // the state, then the name; the balance states and a failure replace what is under them
        val cases = listOf(
            description(ConnectStatus.CONNECTING) to R.string.connecting_status_indicator,
            description(ConnectStatus.DESTINATION_SET) to R.string.connecting_status_indicator,
            description(ConnectStatus.CONNECT_FAILED) to R.string.conn_failed,
            description(ConnectStatus.CONNECTED, displayReconnectTunnel = true) to R.string.reconnect_tunnel_status_indicator,
            description(ConnectStatus.CONNECT_FAILED, displayReconnectTunnel = true) to R.string.conn_failed,
            description(ConnectStatus.CONNECTED, insufficientBalance = true) to R.string.insufficient_balance,
            description(ConnectStatus.CONNECT_FAILED, insufficientBalance = true) to R.string.insufficient_balance,
            description(
                ConnectStatus.DISCONNECTED,
                insufficientBalance = true,
                isPollingSubscriptionBalance = true,
            ) to R.string.processing_subscription_balance,
        )
        for ((index, case) in cases.withIndex()) {
            assertEquals("case $index", case.second, case.first)
        }
    }

    @Test
    fun `only the disconnected button, which reads tap to connect, has no name`() {
        val flags = listOf(false, true)
        for (status in ConnectStatus.entries) {
            for (insufficientBalance in flags) {
                for (isPolling in flags) {
                    for (reconnect in flags) {
                        val id = description(status, insufficientBalance, isPolling, reconnect)
                        val state = "$status insufficient=$insufficientBalance polling=$isPolling reconnect=$reconnect"
                        if (status == ConnectStatus.DISCONNECTED && !insufficientBalance && !reconnect) {
                            assertNull(state, id)
                        } else {
                            assertNotNull(state, id)
                        }
                    }
                }
            }
        }
    }
}
