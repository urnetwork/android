package com.bringyour.network.ui.connect

import com.bringyour.network.VpnPacketFlowMode
import com.bringyour.network.vpnPacketFlowMode
import com.bringyour.network.ui.shared.models.ConnectStatus
import com.bringyour.network.ui.shared.viewmodels.Plan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InsufficientBalancePolicyTest {

    private val requestedStatuses = listOf(
        ConnectStatus.CONNECTING,
        ConnectStatus.DESTINATION_SET,
        ConnectStatus.CONNECTED,
    )

    @Test
    fun gateKeepsDisconnectForEveryRequestedStatus() {
        // the reported dead end: the gate replaced every action with upgrade
        for (status in requestedStatuses) {
            for (reconnect in listOf(false, true)) {
                val buttons = connectActionButtons(
                    insufficientBalance = true,
                    currentPlan = Plan.Basic,
                    isPollingSubscriptionBalance = false,
                    connectStatus = status,
                    displayReconnectTunnel = reconnect,
                )
                assertEquals(
                    "$status reconnect=$reconnect",
                    ConnectActionButtons(upgrade = true, connect = false, disconnect = true, reconnect = false),
                    buttons,
                )
            }
        }
    }

    @Test
    fun gateWhileDisconnectedShowsOnlyUpgrade() {
        assertEquals(
            ConnectActionButtons(upgrade = true, connect = false, disconnect = false, reconnect = false),
            connectActionButtons(
                insufficientBalance = true,
                currentPlan = Plan.Basic,
                isPollingSubscriptionBalance = false,
                connectStatus = ConnectStatus.DISCONNECTED,
                displayReconnectTunnel = false,
            ),
        )
    }

    @Test
    fun outsideGateActionsAreUnchanged() {
        // (insufficient balance, plan, polling) combinations that are not gated
        val ungated = listOf(
            Triple(false, Plan.Basic, false),
            Triple(true, Plan.Supporter, false),
            Triple(true, Plan.Basic, true),
            Triple(false, Plan.Supporter, true),
        )
        for ((insufficient, plan, polling) in ungated) {
            for (status in ConnectStatus.entries) {
                for (reconnect in listOf(false, true)) {
                    val requested = status != ConnectStatus.DISCONNECTED
                    assertEquals(
                        "$insufficient $plan $polling $status $reconnect",
                        ConnectActionButtons(
                            upgrade = false,
                            connect = !requested,
                            disconnect = requested && !reconnect,
                            reconnect = reconnect,
                        ),
                        connectActionButtons(
                            insufficientBalance = insufficient,
                            currentPlan = plan,
                            isPollingSubscriptionBalance = polling,
                            connectStatus = status,
                            displayReconnectTunnel = reconnect,
                        ),
                    )
                }
            }
        }
    }

    @Test
    fun roundButtonDisconnectsInGate() {
        for (status in requestedStatuses) {
            assertEquals(
                "$status",
                ConnectButtonTapAction.DISCONNECT,
                connectButtonTapAction(
                    insufficientBalance = true,
                    currentPlan = Plan.Basic,
                    isPollingSubscriptionBalance = false,
                    connectStatus = status,
                ),
            )
        }
    }

    @Test
    fun roundButtonConnectBehaviorUnchanged() {
        for (insufficient in listOf(false, true)) {
            for (plan in Plan.entries) {
                for (polling in listOf(false, true)) {
                    assertEquals(
                        ConnectButtonTapAction.CONNECT,
                        connectButtonTapAction(insufficient, plan, polling, ConnectStatus.DISCONNECTED),
                    )
                }
            }
        }
        assertEquals(
            ConnectButtonTapAction.COUNT_CONNECTED_TAP,
            connectButtonTapAction(false, Plan.Basic, false, ConnectStatus.CONNECTED),
        )
        assertEquals(
            ConnectButtonTapAction.COUNT_CONNECTED_TAP,
            connectButtonTapAction(true, Plan.Supporter, false, ConnectStatus.CONNECTED),
        )
        assertEquals(
            ConnectButtonTapAction.NONE,
            connectButtonTapAction(false, Plan.Basic, false, ConnectStatus.CONNECTING),
        )
        assertEquals(
            ConnectButtonTapAction.NONE,
            connectButtonTapAction(true, Plan.Basic, true, ConnectStatus.CONNECTING),
        )
    }

    private fun autoDisconnect(
        insufficientBalance: Boolean = true,
        currentPlan: Plan = Plan.Basic,
        isPollingSubscriptionBalance: Boolean = false,
        connectRequested: Boolean = true,
        killSwitch: Boolean = false,
        gateSinceMillis: Long? = 1_000L,
        nowMillis: Long = 1_000L + INSUFFICIENT_BALANCE_DISCONNECT_GRACE_MILLIS,
    ): Boolean = insufficientBalanceAutoDisconnect(
        insufficientBalance = insufficientBalance,
        currentPlan = currentPlan,
        isPollingSubscriptionBalance = isPollingSubscriptionBalance,
        connectRequested = connectRequested,
        killSwitch = killSwitch,
        gateSinceMillis = gateSinceMillis,
        nowMillis = nowMillis,
    )

    @Test
    fun graceIsShortAndBounded() {
        assertTrue(INSUFFICIENT_BALANCE_DISCONNECT_GRACE_MILLIS in 10_000L..30_000L)
    }

    @Test
    fun autoDisconnectsOnceGraceElapsed() {
        assertTrue(autoDisconnect())
        assertTrue(autoDisconnect(nowMillis = 1_000L + 10 * INSUFFICIENT_BALANCE_DISCONNECT_GRACE_MILLIS))
    }

    @Test
    fun waitsOutTheGrace() {
        assertFalse(autoDisconnect(nowMillis = 1_000L))
        assertFalse(autoDisconnect(nowMillis = 1_000L + INSUFFICIENT_BALANCE_DISCONNECT_GRACE_MILLIS - 1))
        assertFalse(autoDisconnect(gateSinceMillis = null))
    }

    @Test
    fun killSwitchKeepsConnectRequest() {
        assertFalse(autoDisconnect(killSwitch = true))
    }

    @Test
    fun supporterAndPollingNeverAutoDisconnect() {
        assertFalse(autoDisconnect(currentPlan = Plan.Supporter))
        assertFalse(autoDisconnect(isPollingSubscriptionBalance = true))
    }

    @Test
    fun nothingToDisconnectWithoutGateOrRequest() {
        assertFalse(autoDisconnect(insufficientBalance = false))
        assertFalse(autoDisconnect(connectRequested = false))
    }

    @Test
    fun autoDisconnectReleasesCaptureWithoutKillSwitch() {
        // before: connect stays requested and the tunnel captures with no exit
        assertEquals(
            VpnPacketFlowMode.DENYLIST,
            vpnPacketFlowMode(offline = false, connected = false, killSwitch = false, connectRequested = true, includedAppIds = emptySet()),
        )
        assertTrue(autoDisconnect(killSwitch = false))
        // after: the cleared request lets the tunnel escape
        assertEquals(
            VpnPacketFlowMode.ESCAPE,
            vpnPacketFlowMode(offline = false, connected = false, killSwitch = false, connectRequested = false, includedAppIds = emptySet()),
        )
    }

    @Test
    fun killSwitchStillFailsClosedAfterManualDisconnect() {
        assertFalse(autoDisconnect(killSwitch = true))
        assertEquals(
            VpnPacketFlowMode.DENYLIST,
            vpnPacketFlowMode(offline = false, connected = false, killSwitch = true, connectRequested = true, includedAppIds = emptySet()),
        )
        // a manual disconnect clears the request; the kill switch keeps capture
        assertEquals(
            VpnPacketFlowMode.DENYLIST,
            vpnPacketFlowMode(offline = false, connected = false, killSwitch = true, connectRequested = false, includedAppIds = emptySet()),
        )
    }
}
