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

    private class RecordingSession : InsufficientBalanceSession {
        val events = mutableListOf<String>()
        override fun disconnect() { events.add("disconnect") }
        override fun postNotice() { events.add("post") }
        override fun cancelNotice() { events.add("cancel") }
    }

    private fun InsufficientBalanceMonitor.observe(
        insufficientBalance: Boolean = true,
        currentPlan: Plan = Plan.Basic,
        isPollingSubscriptionBalance: Boolean = false,
        connectRequested: Boolean = true,
    ) = update(insufficientBalance, currentPlan, isPollingSubscriptionBalance, connectRequested)

    @Test
    fun outOfBalanceNeverDisconnects() {
        // the app must not drop the tunnel on its own: traffic would leave
        // outside it without the user knowing
        for (plan in Plan.entries) {
            for (polling in listOf(false, true)) {
                val session = RecordingSession()
                val monitor = InsufficientBalanceMonitor(session)
                repeat(3) {
                    monitor.observe(currentPlan = plan, isPollingSubscriptionBalance = polling)
                }
                assertFalse("$plan polling=$polling ${session.events}", "disconnect" in session.events)
            }
        }
    }

    @Test
    fun noticePostsOncePerEntry() {
        val session = RecordingSession()
        val monitor = InsufficientBalanceMonitor(session)
        monitor.observe()
        monitor.observe()
        monitor.observe()
        assertEquals(listOf("post"), session.events)
    }

    @Test
    fun noticeRearmsAfterEpisodeEnds() {
        val session = RecordingSession()
        val monitor = InsufficientBalanceMonitor(session)
        monitor.observe()
        monitor.observe(insufficientBalance = false)
        monitor.observe(insufficientBalance = false)
        monitor.observe()
        assertEquals(listOf("post", "cancel", "post"), session.events)
    }

    @Test
    fun noticeNeverForSupporterOrWhilePolling() {
        val session = RecordingSession()
        val monitor = InsufficientBalanceMonitor(session)
        monitor.observe(currentPlan = Plan.Supporter)
        monitor.observe(isPollingSubscriptionBalance = true)
        assertEquals(emptyList<String>(), session.events)
        // the poll ends with the balance still out: the gate now holds
        monitor.observe()
        monitor.observe(isPollingSubscriptionBalance = true)
        monitor.observe()
        assertEquals(listOf("post"), session.events)
    }

    @Test
    fun noticeOnlyWhileTrafficIsHeld() {
        val session = RecordingSession()
        val monitor = InsufficientBalanceMonitor(session)
        monitor.observe(connectRequested = false)
        assertEquals(emptyList<String>(), session.events)
        monitor.observe()
        // the user disconnects: the notice is no longer true, and it does not
        // come back within the episode
        monitor.observe(connectRequested = false)
        monitor.observe()
        assertEquals(listOf("post", "cancel"), session.events)
    }

    @Test
    fun captureKeptWhileOutOfBalanceUntilUserDisconnects() {
        // out of balance there is no live exit, but connect stays requested
        // (the monitor never disconnects), so the tunnel keeps capturing
        val session = RecordingSession()
        InsufficientBalanceMonitor(session).observe()
        val connectRequested = "disconnect" !in session.events
        assertTrue(connectRequested)
        for (killSwitch in listOf(false, true)) {
            assertEquals(
                VpnPacketFlowMode.DENYLIST,
                vpnPacketFlowMode(offline = false, connected = false, killSwitch = killSwitch, connectRequested = connectRequested, includedAppIds = emptySet()),
            )
        }
        // only the user's disconnect releases it, and only without the kill switch
        assertEquals(
            VpnPacketFlowMode.ESCAPE,
            vpnPacketFlowMode(offline = false, connected = false, killSwitch = false, connectRequested = false, includedAppIds = emptySet()),
        )
        assertEquals(
            VpnPacketFlowMode.DENYLIST,
            vpnPacketFlowMode(offline = false, connected = false, killSwitch = true, connectRequested = false, includedAppIds = emptySet()),
        )
    }
}
