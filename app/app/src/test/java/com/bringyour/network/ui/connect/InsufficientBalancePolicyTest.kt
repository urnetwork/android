package com.bringyour.network.ui.connect

import com.bringyour.network.VpnPacketFlowMode
import com.bringyour.network.vpnPacketFlowMode
import com.bringyour.network.ui.shared.models.ConnectStatus
import com.bringyour.network.ui.shared.viewmodels.Plan
import com.bringyour.network.widgets.WidgetBalanceSnapshot
import com.bringyour.network.R
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
        // what each post said
        val said = mutableListOf<Pair<Boolean, RefusedTraffic>>()
        override fun disconnect() { events.add("disconnect") }
        override fun postNotice(dataLimit: Boolean, traffic: RefusedTraffic) {
            events.add("post")
            said.add(dataLimit to traffic)
        }
        override fun cancelNotice() { events.add("cancel") }
    }

    private fun InsufficientBalanceMonitor.observe(
        insufficientBalance: Boolean = true,
        currentPlan: Plan = Plan.Basic,
        isPollingSubscriptionBalance: Boolean = false,
        connectRequested: Boolean = true,
        dataLimit: Boolean = false,
        tunnelStarted: Boolean = true,
    ) = update(
        insufficientBalance,
        currentPlan,
        isPollingSubscriptionBalance,
        connectRequested,
        dataLimit,
        tunnelStarted,
    )

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
            override fun postNotice(dataLimit: Boolean, traffic: RefusedTraffic) {
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
                dataLimit = false,
                tunnelStarted = true,
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
        ),
        tunnelStarted = true,
    )

    @Test
    fun outOfBalanceNoticeLeadsWithTheRefreshWhileConnected() {
        for (status in requestedStatuses) {
            assertEquals(
                "$status",
                OutOfBalanceNotice(refresh = true, traffic = RefusedTraffic.HELD),
                outOfBalanceNoticeFor(insufficientBalance = true, connectStatus = status),
            )
        }
    }

    @Test
    fun outOfBalanceNoticeShowsTheRefreshWhenDisconnected() {
        // the upgrade button alone read as a paywall: say when the free data
        // comes back, with Why? to the data sheet; nothing is held in a tunnel
        assertEquals(
            OutOfBalanceNotice(refresh = true, traffic = RefusedTraffic.NONE),
            outOfBalanceNoticeFor(insufficientBalance = true, connectStatus = ConnectStatus.DISCONNECTED),
        )
    }

    @Test
    fun noOutOfBalanceNoticeOutsideTheGate() {
        val none = OutOfBalanceNotice(refresh = false, traffic = RefusedTraffic.NONE)
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
    fun reservedWhenOpenConnectionsHoldEnoughToBringDataBack() {
        // the reported case: barely used, but open (or abandoned) connections
        // hold the balance as Pending
        assertEquals(
            OutOfBalanceKind.RESERVED,
            outOfBalanceKind(balance(balanceByteCount = 0, openTransferByteCount = 30L * 1024 * 1024 * 1024)),
        )
        assertEquals(
            OutOfBalanceKind.RESERVED,
            outOfBalanceKind(
                balance(
                    balanceByteCount = BALANCE_RECOVERY_THRESHOLD_BYTES - 1,
                    openTransferByteCount = BALANCE_RECOVERY_THRESHOLD_BYTES,
                )
            ),
        )
    }

    @Test
    fun exhaustedWhenLittleOrNothingIsReserved() {
        assertEquals(OutOfBalanceKind.EXHAUSTED, outOfBalanceKind(balance(balanceByteCount = 0, openTransferByteCount = 0)))
        assertEquals(
            OutOfBalanceKind.EXHAUSTED,
            outOfBalanceKind(balance(balanceByteCount = 0, openTransferByteCount = BALANCE_RECOVERY_THRESHOLD_BYTES - 1)),
        )
    }

    @Test
    fun neitherReservedNorExhaustedWithoutAnOutOfBalanceReading() {
        assertEquals(OutOfBalanceKind.UNKNOWN, outOfBalanceKind(null))
        assertEquals(OutOfBalanceKind.UNKNOWN, outOfBalanceKind(balance(isPro = true)))
        // the reading says data is available: the block is about to clear
        assertEquals(
            OutOfBalanceKind.UNKNOWN,
            outOfBalanceKind(
                balance(
                    balanceByteCount = BALANCE_RECOVERY_THRESHOLD_BYTES,
                    openTransferByteCount = 30L * 1024 * 1024 * 1024,
                )
            ),
        )
    }

    @Test
    fun outOfBalanceNoticeSaysReservedOrExhaustedOnlyInTheGate() {
        for (kind in OutOfBalanceKind.entries) {
            for (status in requestedStatuses + ConnectStatus.DISCONNECTED) {
                val gated = connectActionButtons(
                    insufficientBalance = true,
                    currentPlan = Plan.Basic,
                    isPollingSubscriptionBalance = false,
                    connectStatus = status,
                    displayReconnectTunnel = false,
                )
                assertEquals("$kind $status", kind, outOfBalanceNotice(gated, kind, tunnelStarted = true).kind)
                val funded = connectActionButtons(
                    insufficientBalance = false,
                    currentPlan = Plan.Basic,
                    isPollingSubscriptionBalance = false,
                    connectStatus = status,
                    displayReconnectTunnel = false,
                )
                assertEquals("$kind $status funded", OutOfBalanceKind.UNKNOWN, outOfBalanceNotice(funded, kind, tunnelStarted = true).kind)
            }
        }
    }

    @Test
    fun outOfBalanceNoticeSaysTheConnectComesBackByItself() {
        val held = connectActionButtons(
            insufficientBalance = true,
            currentPlan = Plan.Basic,
            isPollingSubscriptionBalance = false,
            connectStatus = ConnectStatus.CONNECTED,
            displayReconnectTunnel = false,
        )
        val blockedDisconnected = connectActionButtons(
            insufficientBalance = true,
            currentPlan = Plan.Basic,
            isPollingSubscriptionBalance = false,
            connectStatus = ConnectStatus.DISCONNECTED,
            displayReconnectTunnel = false,
        )
        val ungatedDisconnected = connectActionButtons(
            insufficientBalance = false,
            currentPlan = Plan.Basic,
            isPollingSubscriptionBalance = false,
            connectStatus = ConnectStatus.DISCONNECTED,
            displayReconnectTunnel = false,
        )
        val waiting = BalanceRecoveryState(startWaiting = false, retriesLeft = true)
        val startWaiting = BalanceRecoveryState(startWaiting = true, retriesLeft = true)
        val spent = BalanceRecoveryState(startWaiting = false, retriesLeft = false)

        // a held connection is rebuilt by itself; Disconnect is its way out
        val heldNotice = outOfBalanceNotice(held, recovery = waiting, tunnelStarted = true)
        assertTrue(heldNotice.willReconnect)
        assertFalse(heldNotice.cancel)
        // a refused start waits with Cancel, also once the gate has lifted
        for (buttons in listOf(blockedDisconnected, ungatedDisconnected)) {
            val notice = outOfBalanceNotice(buttons, recovery = startWaiting, tunnelStarted = true)
            assertTrue("$buttons", notice.willReconnect)
            assertTrue("$buttons", notice.cancel)
        }
        // nothing the user asked for is waiting: no promise
        assertFalse(outOfBalanceNotice(blockedDisconnected, recovery = waiting, tunnelStarted = true).willReconnect)
        assertFalse(outOfBalanceNotice(ungatedDisconnected, recovery = waiting, tunnelStarted = true).willReconnect)
        // the retries are used up: no promise
        assertFalse(outOfBalanceNotice(held, recovery = spent, tunnelStarted = true).willReconnect)
        assertFalse(outOfBalanceNotice(held, tunnelStarted = true).willReconnect)
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

    // ---- what the traffic is doing while refused ----

    @Test
    fun trafficIsHeldOnlyByARunningTunnel() {
        assertEquals(RefusedTraffic.HELD, refusedTraffic(connectRequested = true, tunnelStarted = true))
        assertEquals(RefusedTraffic.NOT_PROTECTED, refusedTraffic(connectRequested = true, tunnelStarted = false))
        assertEquals(RefusedTraffic.NONE, refusedTraffic(connectRequested = false, tunnelStarted = true))
        assertEquals(RefusedTraffic.NONE, refusedTraffic(connectRequested = false, tunnelStarted = false))
    }

    @Test
    fun theLineForTheTrafficSaysWhatIsTrue() {
        assertEquals(R.string.insufficient_balance_held_notice, refusedTrafficTextId(false, RefusedTraffic.HELD))
        assertEquals(R.string.data_limit_held_notice, refusedTrafficTextId(true, RefusedTraffic.HELD))
        for (dataLimit in listOf(false, true)) {
            assertEquals(R.string.conn_not_protected, refusedTrafficTextId(dataLimit, RefusedTraffic.NOT_PROTECTED))
            assertEquals(null, refusedTrafficTextId(dataLimit, RefusedTraffic.NONE))
        }
    }

    @Test
    fun theAlertDoesNotClaimHeldWithNoTunnel() {
        // the defect: a connection requested with no tunnel running, told its
        // traffic is held in the tunnel
        for (status in requestedStatuses) {
            val buttons = connectActionButtons(
                insufficientBalance = true,
                currentPlan = Plan.Basic,
                isPollingSubscriptionBalance = false,
                connectStatus = status,
                displayReconnectTunnel = false,
            )
            assertEquals("$status", RefusedTraffic.NOT_PROTECTED, outOfBalanceNotice(buttons, tunnelStarted = false).traffic)
            assertEquals("$status", RefusedTraffic.HELD, outOfBalanceNotice(buttons, tunnelStarted = true).traffic)
        }
        val disconnected = connectActionButtons(
            insufficientBalance = true,
            currentPlan = Plan.Basic,
            isPollingSubscriptionBalance = false,
            connectStatus = ConnectStatus.DISCONNECTED,
            displayReconnectTunnel = false,
        )
        assertEquals(RefusedTraffic.NONE, outOfBalanceNotice(disconnected, tunnelStarted = false).traffic)
    }

    @Test
    fun theNotificationDoesNotClaimHeldWithNoTunnel() {
        val session = RecordingSession()
        InsufficientBalanceMonitor(session).observe(tunnelStarted = false)
        assertEquals(listOf(false to RefusedTraffic.NOT_PROTECTED), session.said)
    }

    @Test
    fun theNotificationFollowsTheTunnelInPlace() {
        val session = RecordingSession()
        val monitor = InsufficientBalanceMonitor(session)
        // the refusal lands before the tunnel is up
        monitor.observe(tunnelStarted = false)
        monitor.observe(tunnelStarted = false)
        assertEquals(1, session.said.size)
        // the tunnel comes up: the notice up is replaced, not repeated
        monitor.observe(tunnelStarted = true)
        monitor.observe(tunnelStarted = true)
        assertEquals(
            listOf(false to RefusedTraffic.NOT_PROTECTED, false to RefusedTraffic.HELD),
            session.said,
        )
        // it goes away under the notice
        monitor.observe(tunnelStarted = false)
        assertEquals(false to RefusedTraffic.NOT_PROTECTED, session.said.last())
        assertEquals(3, session.said.size)
        // a disconnect removes it, and it does not come back within the episode
        monitor.observe(connectRequested = false)
        monitor.observe(tunnelStarted = true)
        assertEquals(3, session.said.size)
        assertEquals("cancel", session.events.last())
        assertFalse("disconnect" in session.events)
    }

    // ---- a paying account refused: the data limit ----

    @Test
    fun aDataLimitIsAPayingAccountsRefusal() {
        val gate = DataLimitGate()
        assertTrue(gate.observe(true, Plan.Supporter, false))
        assertFalse(gate.observe(false, Plan.Supporter, false))
        assertFalse(DataLimitGate().observe(true, Plan.Basic, false))
        assertFalse(DataLimitGate().observe(false, Plan.Supporter, false))
        // a poll defers it
        val polled = DataLimitGate()
        assertFalse(polled.observe(true, Plan.Supporter, true))
        assertTrue(polled.observe(true, Plan.Supporter, false))
    }

    @Test
    fun aRefusalFromBeforeThePurchaseIsNoDataLimit() {
        val gate = DataLimitGate()
        assertFalse(gate.observe(true, Plan.Basic, false))
        assertFalse(gate.observe(true, Plan.Basic, true))
        assertFalse(gate.observe(true, Plan.Supporter, true))
        // the purchase landed with the old refusal still standing
        assertFalse(gate.observe(true, Plan.Supporter, false))
        assertFalse(gate.observe(true, Plan.Supporter, false))
        // it clears, and the next one the Supporter plan got for itself
        assertFalse(gate.observe(false, Plan.Supporter, false))
        assertTrue(gate.observe(true, Plan.Supporter, false))
    }

    @Test
    fun aDataLimitPostsOncePerEpisodeInItsOwnWordsAndNeverDisconnects() {
        val session = RecordingSession()
        val monitor = InsufficientBalanceMonitor(session)
        monitor.observe(currentPlan = Plan.Supporter, dataLimit = true)
        monitor.observe(currentPlan = Plan.Supporter, dataLimit = true)
        assertEquals(listOf(true to RefusedTraffic.HELD), session.said)
        monitor.observe(currentPlan = Plan.Supporter, dataLimit = true, tunnelStarted = false)
        assertEquals(true to RefusedTraffic.NOT_PROTECTED, session.said.last())
        monitor.observe(insufficientBalance = false, currentPlan = Plan.Supporter)
        assertEquals("cancel", session.events.last())
        monitor.observe(currentPlan = Plan.Supporter, dataLimit = true)
        assertEquals(3, session.said.size)
        assertFalse("disconnect" in session.events)
    }

    @Test
    fun aPurchaseDoesNotAnnounceADataLimit() {
        val session = RecordingSession()
        val monitor = InsufficientBalanceMonitor(session)
        val gate = DataLimitGate()
        // out of balance and told so, then the plan turns Supporter over the old refusal
        monitor.observe(dataLimit = gate.observe(true, Plan.Basic, false))
        monitor.observe(currentPlan = Plan.Supporter, dataLimit = gate.observe(true, Plan.Supporter, false))
        assertEquals(listOf(false to RefusedTraffic.HELD), session.said)
    }

    @Test
    fun aDataLimitHasNothingToBuy() {
        // the supporter plan is never in the upgrade gate, so the buttons stay what
        // they were: Disconnect, and no Upgrade or free refresh
        val buttons = connectActionButtons(
            insufficientBalance = false,
            currentPlan = Plan.Supporter,
            isPollingSubscriptionBalance = false,
            connectStatus = ConnectStatus.CONNECTED,
            displayReconnectTunnel = false,
        )
        assertFalse(buttons.upgrade)
        assertTrue(buttons.disconnect)
        assertEquals(
            OutOfBalanceNotice(refresh = false, traffic = RefusedTraffic.NONE),
            outOfBalanceNotice(buttons, tunnelStarted = true),
        )
    }

    private fun mainSource(relative: String): String {
        val file = listOf("", "app/", "app/app/")
            .map { java.io.File(it + "src/main/java/com/bringyour/network/" + relative) }
            .firstOrNull { it.isFile }
        assertTrue("missing $relative", file != null)
        return file!!.readText()
    }

    // The notification, the alert and the status need Android to run, so they are
    // read: each says what the policy says, and none names the held line itself.
    @Test
    fun everySurfaceReadsThePolicy() {
        val application = mainSource("MainApplication.kt")
        assertFalse(application.contains("R.string.insufficient_balance_held_notice"))
        assertTrue(application.contains(".refusedTrafficTextId(dataLimit, traffic)"))
        assertTrue(application.contains("if (dataLimit) R.string.data_limit_reached else R.string.insufficient_balance"))
        assertTrue(application.contains("internal val dataLimitGate by lazy"))

        val actions = mainSource("ui/connect/ConnectActions.kt")
        assertFalse(actions.contains("R.string.insufficient_balance_held_notice"))
        assertTrue(actions.contains("refusedTrafficTextId(dataLimit = false, traffic = outOfBalanceNotice.traffic)"))
        assertTrue(actions.contains("tunnelStarted = tunnelStarted,"))
        assertTrue(actions.contains("acceptance.data_limit_notice"))

        val effect = mainSource("ui/connect/InsufficientBalanceNoticeEffect.kt")
        assertTrue(effect.contains("application.dataLimitGate.observe("))
        assertTrue(effect.contains("connectViewModel.setDataLimitReached(dataLimit)"))
        assertTrue(effect.contains("tunnelStarted = tunnelConnected,"))
        assertTrue(effect.contains("connectRequested, tunnelConnected)"))

        val screen = mainSource("ui/connect/ConnectScreen.kt")
        assertTrue(screen.contains("insufficientBalance = displayInsufficientBalance || dataLimitReached,"))
        val indicator = mainSource("ui/connect/ConnectStatusIndicator.kt")
        assertTrue(indicator.contains("dataLimitReached -> stringResource(id = R.string.data_limit_reached)"))
    }

    // Where the store's traffic rides the tunnel (the Play flavor), a held tunnel stops
    // the purchase, so the alert says to disconnect first, while it holds and only then.
    @Test
    fun disconnectFirstOnlyWhereCheckoutRidesAHoldingTunnel() {
        for (status in requestedStatuses) {
            val gated = connectActionButtons(
                insufficientBalance = true,
                currentPlan = Plan.Basic,
                isPollingSubscriptionBalance = false,
                connectStatus = status,
                displayReconnectTunnel = false,
            )
            assertTrue("$status", gated.disconnect)
            assertTrue("$status", outOfBalanceNotice(gated, tunnelStarted = true, checkoutRidesTunnel = true).disconnectFirst)
            // the flavors whose checkout runs in the app, which the tunnel leaves out
            assertFalse("$status", outOfBalanceNotice(gated, tunnelStarted = true, checkoutRidesTunnel = false).disconnectFirst)
            // no tunnel running: the store's traffic goes out the normal way
            assertFalse("$status", outOfBalanceNotice(gated, tunnelStarted = false, checkoutRidesTunnel = true).disconnectFirst)
            // a paying account at a limit is offered nothing to pay for
            val paying = connectActionButtons(
                insufficientBalance = true,
                currentPlan = Plan.Supporter,
                isPollingSubscriptionBalance = false,
                connectStatus = status,
                displayReconnectTunnel = false,
            )
            assertFalse("$status", outOfBalanceNotice(paying, tunnelStarted = true, checkoutRidesTunnel = true).disconnectFirst)
        }
        // nothing requested: nothing to disconnect from
        val disconnected = connectActionButtons(
            insufficientBalance = true,
            currentPlan = Plan.Basic,
            isPollingSubscriptionBalance = false,
            connectStatus = ConnectStatus.DISCONNECTED,
            displayReconnectTunnel = false,
        )
        assertFalse(outOfBalanceNotice(disconnected, tunnelStarted = true, checkoutRidesTunnel = true).disconnectFirst)
    }

    private fun flavorSource(relative: String): String {
        val file = listOf("", "app/", "app/app/")
            .map { java.io.File(it + "src/" + relative) }
            .firstOrNull { it.isFile }
        assertTrue("missing $relative", file != null)
        return file!!.readText()
    }

    // The alert shows the line the policy decides, from this flavor's fact, beside the
    // Disconnect it names. That press only disconnects: the provide mode stands.
    @Test
    fun theAlertSaysDisconnectFirstFromTheFlavorsCheckout() {
        val actions = mainSource("ui/connect/ConnectActions.kt")
        assertTrue(actions.contains("checkoutRidesTunnel = PLAN_CHECKOUT_RIDES_TUNNEL,"))
        assertTrue(actions.contains("if (outOfBalanceNotice.disconnectFirst) {"))
        assertTrue(actions.contains("stringResource(id = R.string.insufficient_balance_disconnect_first_kill_switch)"))
        assertTrue(actions.contains("acceptance.insufficient_balance_disconnect_first"))
        assertTrue(actions.contains("if (actionButtons.disconnect) {\n                    disconnectButton(Modifier.weight(1f))"))
        val model = mainSource("ui/connect/ConnectViewModel.kt")
        val disconnect = model.substring(model.indexOf("val disconnect: () -> Unit = {"))
        val body = disconnect.substring(0, disconnect.indexOf("\n    }\n"))
        assertTrue(body.contains("runCommand(connect = false) { it.disconnect() }"))
        assertFalse(body.contains("rovide"))
    }

    // What each flavor's answer rests on. The tunnel leaves out this app's own uid, and
    // the store apps are not among the other apps it leaves out, so Play Billing, which
    // runs in the Play Store and Play services, rides it while the web view and the
    // Stripe sheet, in this app, do not.
    @Test
    fun eachFlavorsCheckoutGoesWhereItsAnswerSays() {
        val service = mainSource("MainService.kt")
        assertTrue(service.contains("builder.addDisallowedApplication(packageName)"))
        for (storeApp in listOf("com.android.vending", "com.google.android.gms")) {
            assertFalse(storeApp, service.contains(storeApp))
        }
        assertTrue(flavorSource("google/java/com/bringyour/network/MainActivity.kt").contains("launchBillingFlow("))
        val play = flavorSource("google/java/com/bringyour/network/ui/upgrade/PlanPurchasers.kt")
        assertTrue(play.contains("PLAN_CHECKOUT_RIDES_TUNNEL = true"))
        val web = flavorSource("webPay/java/com/bringyour/network/ui/upgrade/PlanPurchasers.kt")
        assertTrue(web.contains("WebView(context)"))
        assertFalse(web.contains("ACTION_VIEW"))
        assertTrue(web.contains("PLAN_CHECKOUT_RIDES_TUNNEL = false"))
        val sheet = flavorSource("stripeSheet/java/com/bringyour/network/ui/upgrade/PlanPurchasers.kt")
        assertTrue(sheet.contains("PaymentSheet.Builder"))
        assertFalse(sheet.contains("ACTION_VIEW"))
        assertTrue(sheet.contains("PLAN_CHECKOUT_RIDES_TUNNEL = false"))
    }
}
