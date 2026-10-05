package com.bringyour.network.ui.connect

import com.bringyour.network.VpnPacketFlowMode
import com.bringyour.network.vpnPacketFlowMode
import com.bringyour.network.ui.shared.models.ConnectStatus
import com.bringyour.network.ui.shared.viewmodels.Plan
import com.bringyour.network.widgets.WidgetBalanceSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InsufficientBalancePolicyTest {

    private val requestedStatuses = listOf(
        ConnectStatus.CONNECTING,
        ConnectStatus.DESTINATION_SET,
        ConnectStatus.CONNECTED,
        ConnectStatus.CONNECT_FAILED,
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
                            retry = status == ConnectStatus.CONNECT_FAILED && !reconnect,
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
    fun connectFailedOffersRetryBesideDisconnect() {
        // the sdk's CONNECT_FAILED: the session is still standing, so disconnect
        // stays, and retry connects to the selected location again
        assertEquals(
            ConnectActionButtons(upgrade = false, connect = false, disconnect = true, reconnect = false, retry = true),
            connectActionButtons(
                insufficientBalance = false,
                currentPlan = Plan.Basic,
                isPollingSubscriptionBalance = false,
                connectStatus = ConnectStatus.CONNECT_FAILED,
                displayReconnectTunnel = false,
            ),
        )
        // a tunnel to reconnect comes first: retrying the providers cannot help it
        assertEquals(
            ConnectActionButtons(upgrade = false, connect = false, disconnect = false, reconnect = true, retry = false),
            connectActionButtons(
                insufficientBalance = false,
                currentPlan = Plan.Basic,
                isPollingSubscriptionBalance = false,
                connectStatus = ConnectStatus.CONNECT_FAILED,
                displayReconnectTunnel = true,
            ),
        )
        // out of balance a retry cannot succeed: upgrade and disconnect only
        assertEquals(
            ConnectActionButtons(upgrade = true, connect = false, disconnect = true, reconnect = false, retry = false),
            connectActionButtons(
                insufficientBalance = true,
                currentPlan = Plan.Basic,
                isPollingSubscriptionBalance = false,
                connectStatus = ConnectStatus.CONNECT_FAILED,
                displayReconnectTunnel = false,
            ),
        )
    }

    @Test
    fun onlyAFailedConnectOffersRetry() {
        for (status in ConnectStatus.entries.filter { it != ConnectStatus.CONNECT_FAILED }) {
            val buttons = connectActionButtons(
                insufficientBalance = false,
                currentPlan = Plan.Basic,
                isPollingSubscriptionBalance = false,
                connectStatus = status,
                displayReconnectTunnel = false,
            )
            assertFalse("$status", buttons.retry)
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

    private fun balance(
        balanceByteCount: Long = 0,
        openTransferByteCount: Long = 0,
        isPro: Boolean = false,
    ) = WidgetBalanceSnapshot(
        updatedAtMillis = 0,
        startBalanceByteCount = 1_000_000_000,
        balanceByteCount = balanceByteCount,
        openTransferByteCount = openTransferByteCount,
        isPro = isPro,
    )

    // start connect: while disconnected the SDK has cleared the contract
    // status with the destination, so it reads not insufficient
    private fun startBlocked(
        accountBalance: WidgetBalanceSnapshot?,
        currentPlan: Plan = Plan.Basic,
        isPollingSubscriptionBalance: Boolean = false,
        contractInsufficientBalance: Boolean = false,
    ) = startConnectBlocked(
        contractInsufficientBalance = contractInsufficientBalance,
        accountBalanceExhausted = accountBalanceExhausted(accountBalance),
        currentPlan = currentPlan,
        isPollingSubscriptionBalance = isPollingSubscriptionBalance,
    )

    @Test
    fun quickConnectWhileOutOfBalanceDoesNotStartTunnel() {
        // the reported gap: the tile, shortcuts and widget button connected an
        // account with no balance, which only the connect screen refused
        val step = quickConnectStep(
            connectEnabled = false,
            connect = true,
            startConnectBlocked = startBlocked(balance()),
        )
        assertEquals(QuickConnectStep.UPGRADE, step)
    }

    @Test
    fun startConnectBlockedWhileDisconnectedOutOfBalance() {
        assertTrue(startBlocked(balance()))
        // the contract status alone, as the connection reports it, also blocks
        assertTrue(startBlocked(balance(balanceByteCount = 1), contractInsufficientBalance = true))
    }

    @Test
    fun startConnectGateMatchesConnectScreenSwap() {
        // the connect screen shows upgrade in place of connect exactly when a
        // start connect is blocked
        val balances = listOf(
            null,
            balance(),
            balance(balanceByteCount = 1),
            balance(openTransferByteCount = 1),
            balance(isPro = true),
        )
        for (accountBalance in balances) {
            for (contract in listOf(false, true)) {
                for (plan in Plan.entries) {
                    for (polling in listOf(false, true)) {
                        val buttons = connectActionButtons(
                            insufficientBalance = displayInsufficientBalance(
                                contractInsufficientBalance = contract,
                                accountBalanceExhausted = accountBalanceExhausted(accountBalance),
                                connectRequested = false,
                            ),
                            currentPlan = plan,
                            isPollingSubscriptionBalance = polling,
                            connectStatus = ConnectStatus.DISCONNECTED,
                            displayReconnectTunnel = false,
                        )
                        assertEquals(
                            "$accountBalance $contract $plan $polling",
                            buttons.upgrade,
                            startBlocked(accountBalance, plan, polling, contract),
                        )
                        assertEquals(!buttons.upgrade, buttons.connect)
                    }
                }
            }
        }
    }

    @Test
    fun startConnectAllowedWithBalanceSupporterOrPoll() {
        assertFalse("never fetched", startBlocked(null))
        assertFalse("available", startBlocked(balance(balanceByteCount = 1)))
        assertFalse("held in open contracts", startBlocked(balance(openTransferByteCount = 1)))
        assertFalse("server pro", startBlocked(balance(isPro = true)))
        assertFalse("supporter", startBlocked(balance(), currentPlan = Plan.Supporter))
        assertFalse("balance poll", startBlocked(balance(), isPollingSubscriptionBalance = true))
    }

    @Test
    fun quickConnectOutsideGateIsUnchanged() {
        assertEquals(QuickConnectStep.CONNECT, quickConnectStep(connectEnabled = false, connect = true, startConnectBlocked = false))
        assertEquals(QuickConnectStep.DISCONNECT, quickConnectStep(connectEnabled = true, connect = false, startConnectBlocked = false))
        // disconnect is always allowed, out of balance too
        assertEquals(QuickConnectStep.DISCONNECT, quickConnectStep(connectEnabled = true, connect = false, startConnectBlocked = true))
        assertEquals(QuickConnectStep.NONE, quickConnectStep(connectEnabled = false, connect = false, startConnectBlocked = true))
    }

    @Test
    fun alreadyConnectedIsNeverDroppedOrRefused() {
        // a requested connection (also the system restoring it) stays as is
        // whatever the balance: no disconnect, no upgrade hand-off
        for (blocked in listOf(false, true)) {
            assertEquals(
                "blocked=$blocked",
                QuickConnectStep.NONE,
                quickConnectStep(connectEnabled = true, connect = true, startConnectBlocked = blocked),
            )
        }
    }

    @Test
    fun accountBalanceOnlyGatesBeforeConnectIsRequested() {
        // once connected, the held-traffic state follows the connection's own
        // contract status; an exhausted account balance alone does not turn a
        // live connection into the out of balance state
        for (status in requestedStatuses) {
            val insufficient = displayInsufficientBalance(
                contractInsufficientBalance = false,
                accountBalanceExhausted = true,
                connectRequested = status != ConnectStatus.DISCONNECTED,
            )
            assertFalse("$status", insufficient)
            val buttons = connectActionButtons(
                insufficientBalance = insufficient,
                currentPlan = Plan.Basic,
                isPollingSubscriptionBalance = false,
                connectStatus = status,
                displayReconnectTunnel = false,
            )
            assertTrue("$status", buttons.disconnect)
        }
        assertTrue(
            displayInsufficientBalance(
                contractInsufficientBalance = false,
                accountBalanceExhausted = true,
                connectRequested = false,
            ),
        )
    }

    // start connect on the account balance the gate decides on: the cached
    // snapshot, or a fetch when it is not fresh. fetchResult null is a failed
    // or timed out fetch.
    private class StartConnectRun(
        cached: WidgetBalanceSnapshot?,
        nowMillis: Long,
        fetchResult: WidgetBalanceSnapshot?,
    ) {
        var fetchCount = 0
        var decided: WidgetBalanceSnapshot? = null
        var decisionCount = 0

        init {
            startConnectBalance(
                cached = cached,
                nowMillis = nowMillis,
                fetch = { onBalance ->
                    fetchCount += 1
                    onBalance(fetchResult)
                },
            ) { balance ->
                decisionCount += 1
                decided = balance
            }
        }

        val blocked get() = startConnectBlocked(
            contractInsufficientBalance = false,
            accountBalanceExhausted = accountBalanceExhausted(decided),
            currentPlan = Plan.Basic,
            isPollingSubscriptionBalance = false,
        )
    }

    private val now = 10 * 60 * 60_000L

    private fun balanceAt(updatedAtMillis: Long, balanceByteCount: Long = 0) =
        balance(balanceByteCount = balanceByteCount).copy(updatedAtMillis = updatedAtMillis)

    @Test
    fun staleZeroBalanceDoesNotSendAFundedAccountToUpgrade() {
        // the reported gap: the snapshot is refreshed every 30 minutes outside
        // the app, so a zero from before a purchase or refill blocked connect
        val stale = balanceAt(now - 30 * 60_000L)
        val funded = StartConnectRun(stale, now, fetchResult = balanceAt(now, balanceByteCount = 1_000))
        assertFalse("stale zero blocked a funded account", funded.blocked)
        assertEquals(1, funded.fetchCount)
        assertEquals(1, funded.decisionCount)
        // a failed fetch never blocks: the server refuses the contract anyway
        val failed = StartConnectRun(stale, now, fetchResult = null)
        assertFalse("failed fetch blocked", failed.blocked)
        assertEquals(1, failed.fetchCount)
    }

    @Test
    fun freshZeroBalanceBlocksWithoutFetching() {
        for (age in listOf(0L, 1_000L, START_CONNECT_BALANCE_MAX_AGE_MILLIS)) {
            val run = StartConnectRun(balanceAt(now - age), now, fetchResult = balanceAt(now, balanceByteCount = 1_000))
            assertEquals("age=$age", 0, run.fetchCount)
            assertTrue("age=$age", run.blocked)
        }
    }

    @Test
    fun freshStartWithAnEmptyAccountIsBlocked() {
        // nothing cached yet (fresh install or sign in): the fetched balance decides
        val empty = StartConnectRun(null, now, fetchResult = balanceAt(now))
        assertTrue("empty account started the tunnel", empty.blocked)
        assertEquals(1, empty.fetchCount)
        // just past the limit counts as stale
        val stale = StartConnectRun(balanceAt(now - START_CONNECT_BALANCE_MAX_AGE_MILLIS - 1), now, fetchResult = balanceAt(now))
        assertTrue("stale balance not fetched", stale.blocked)
        assertEquals(1, stale.fetchCount)
        // a snapshot from the future (clock change) is not trusted
        assertFalse(startConnectBalanceFresh(balanceAt(now + 1), now))
    }

    @Test
    fun balanceFetchDecidesOnce() {
        val decided = mutableListOf<WidgetBalanceSnapshot?>()
        val first = FirstBalance { decided.add(it) }
        val fetched = balanceAt(now, balanceByteCount = 1)
        first.offer(fetched)
        // the timeout after the answer is ignored
        first.offer(null)
        assertEquals(listOf<WidgetBalanceSnapshot?>(fetched), decided)
    }

    @Test
    fun connectedSessionIsNeverDroppedWhateverTheBalance() {
        // a requested connection stays as is with a fresh zero balance: the
        // quick surfaces never consult the gate for it
        val run = StartConnectRun(balanceAt(now), now, fetchResult = null)
        assertTrue(run.blocked)
        assertEquals(
            QuickConnectStep.NONE,
            quickConnectStep(connectEnabled = true, connect = true, startConnectBlocked = run.blocked),
        )
        // and running out while connected only tells the user
        val session = object : InsufficientBalanceSession {
            var disconnects = 0
            var notices = 0
            override fun disconnect() {
                disconnects += 1
            }
            override fun postNotice() {
                notices += 1
            }
            override fun cancelNotice() {}
        }
        val monitor = InsufficientBalanceMonitor(session)
        for (insufficientBalance in listOf(false, true, true, false, true)) {
            monitor.update(
                insufficientBalance = insufficientBalance,
                currentPlan = Plan.Basic,
                isPollingSubscriptionBalance = false,
                connectRequested = true,
            )
        }
        assertEquals(0, session.disconnects)
        assertEquals(2, session.notices)
    }

    private fun outOfBalanceNoticeFor(
        insufficientBalance: Boolean,
        currentPlan: Plan = Plan.Basic,
        isPollingSubscriptionBalance: Boolean = false,
        connectStatus: ConnectStatus,
    ): OutOfBalanceNotice = outOfBalanceNotice(
        connectActionButtons(
            insufficientBalance = insufficientBalance,
            currentPlan = currentPlan,
            isPollingSubscriptionBalance = isPollingSubscriptionBalance,
            connectStatus = connectStatus,
            displayReconnectTunnel = false,
        )
    )

    @Test
    fun outOfBalanceNoticeLeadsWithTheRefreshWhileConnected() {
        for (status in requestedStatuses) {
            assertEquals(
                "$status",
                OutOfBalanceNotice(refresh = true, held = true),
                outOfBalanceNoticeFor(insufficientBalance = true, connectStatus = status),
            )
        }
    }

    @Test
    fun outOfBalanceNoticeShowsTheRefreshWhenDisconnected() {
        // the upgrade button alone read as a paywall: say when the free data
        // comes back, with Why? to the data sheet; nothing is held in a tunnel
        assertEquals(
            OutOfBalanceNotice(refresh = true, held = false),
            outOfBalanceNoticeFor(insufficientBalance = true, connectStatus = ConnectStatus.DISCONNECTED),
        )
    }

    @Test
    fun noOutOfBalanceNoticeOutsideTheGate() {
        val none = OutOfBalanceNotice(refresh = false, held = false)
        for (status in requestedStatuses + ConnectStatus.DISCONNECTED) {
            assertEquals("$status funded", none, outOfBalanceNoticeFor(insufficientBalance = false, connectStatus = status))
            assertEquals(
                "$status supporter",
                none,
                outOfBalanceNoticeFor(insufficientBalance = true, currentPlan = Plan.Supporter, connectStatus = status),
            )
            assertEquals(
                "$status polling",
                none,
                outOfBalanceNoticeFor(insufficientBalance = true, isPollingSubscriptionBalance = true, connectStatus = status),
            )
        }
    }

    @Test
    fun upgradeShowsTheRefreshOnlyWhenABlockedConnectOpenedIt() {
        assertTrue(upgradeShowsFreeRefresh(openedByStartConnectBlock = true, currentPlan = Plan.Basic))
        // Get Pro, Account's Change, the onboarding links
        assertFalse(upgradeShowsFreeRefresh(openedByStartConnectBlock = false, currentPlan = Plan.Basic))
        // Pro gets no free daily grant
        assertFalse(upgradeShowsFreeRefresh(openedByStartConnectBlock = true, currentPlan = Plan.Supporter))
        assertFalse(upgradeShowsFreeRefresh(openedByStartConnectBlock = false, currentPlan = Plan.Supporter))
    }
}
