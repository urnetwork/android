package com.bringyour.network.ui.connect

import com.bringyour.network.widgets.WidgetBalanceSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The self-recovery of a connect insufficient balance blocked: the refused
 * start or the held connection is retried once the balance is back, once per
 * recovery and a bounded number of times in a row, and never for a user who
 * did not ask to connect.
 */
class BalanceRecoveryTest {

    private val low = BALANCE_RECOVERY_THRESHOLD_BYTES - 1
    private val back = BALANCE_RECOVERY_THRESHOLD_BYTES

    private fun reading(availableByteCount: Long, fetchedAtMillis: Long) = WidgetBalanceSnapshot(
        updatedAtMillis = fetchedAtMillis,
        startBalanceByteCount = 30L * 1024 * 1024 * 1024,
        balanceByteCount = availableByteCount,
        openTransferByteCount = 0,
        isPro = false,
    )

    private fun BalanceRecovery<String?>.held(availableByteCount: Long, atMillis: Long) =
        observe(gate = true, connectRequested = true, balance = reading(availableByteCount, atMillis), nowMillis = atMillis)

    private fun BalanceRecovery<String?>.disconnected(availableByteCount: Long, atMillis: Long, gate: Boolean = false) =
        observe(gate = gate, connectRequested = false, balance = reading(availableByteCount, atMillis), nowMillis = atMillis)

    @Test
    fun neverConnectsAUserWhoDidNotAsk() {
        val recovery = BalanceRecovery<String?>()
        var t = 0L
        repeat(5) {
            // disconnected and blocked or not: the balance runs out and comes back
            for (gate in listOf(true, false)) {
                t += 60_000
                assertEquals(BalanceRecoveryStep.None, recovery.disconnected(0, t, gate))
                t += 60_000
                assertEquals(BalanceRecoveryStep.None, recovery.disconnected(back, t, gate))
            }
            // connected and never held
            t += 60_000
            assertEquals(
                BalanceRecoveryStep.None,
                recovery.observe(gate = false, connectRequested = true, balance = reading(back, t), nowMillis = t),
            )
        }
        assertFalse(recovery.state.startWaiting)
    }

    @Test
    fun refusedStartIsRetriedOnceWhenDataIsBack() {
        val recovery = BalanceRecovery<String?>()
        recovery.startRefused("de", nowMillis = 1_000)
        assertTrue(recovery.state.startWaiting)
        assertEquals(BalanceRecoveryStep.None, recovery.disconnected(0, 60_000, gate = true))
        // reserved data returned (or the free refresh)
        assertEquals(BalanceRecoveryStep.Start("de"), recovery.disconnected(back, 120_000))
        assertFalse(recovery.state.startWaiting)
        assertEquals(BalanceRecoveryStep.None, recovery.disconnected(back, 180_000))
    }

    @Test
    fun aBalanceReadBeforeTheBlockNeverRetries() {
        val recovery = BalanceRecovery<String?>()
        recovery.startRefused("de", nowMillis = 100_000)
        // the cached reading the app had before the block still says available
        assertEquals(BalanceRecoveryStep.None, recovery.disconnected(back, 40_000))
        assertTrue(recovery.state.startWaiting)
        // a reading taken after the block decides
        assertEquals(BalanceRecoveryStep.Start("de"), recovery.disconnected(back, 160_000))
    }

    @Test
    fun heldConnectionIsRebuiltWhenReservedDataReturns() {
        val recovery = BalanceRecovery<String?>()
        assertEquals(BalanceRecoveryStep.None, recovery.held(0, 60_000))
        assertTrue(recovery.state.retriesLeft)
        assertEquals(BalanceRecoveryStep.Rebuild, recovery.held(back, 120_000))
    }

    @Test
    fun aStaleReadingAtTheStartOfAHoldDoesNotRebuild() {
        val recovery = BalanceRecovery<String?>()
        // the hold is first seen with the reading from before the balance ran out
        assertEquals(
            BalanceRecoveryStep.None,
            recovery.observe(gate = true, connectRequested = true, balance = reading(back, 10_000), nowMillis = 70_000),
        )
        assertEquals(BalanceRecoveryStep.None, recovery.held(0, 130_000))
        assertEquals(BalanceRecoveryStep.Rebuild, recovery.held(back, 190_000))
    }

    @Test
    fun oneRetryPerRecovery() {
        val recovery = BalanceRecovery<String?>()
        recovery.held(0, 60_000)
        assertEquals(BalanceRecoveryStep.Rebuild, recovery.held(back, 120_000))
        // still blocked on later readings: no second retry until the balance
        // runs out and comes back again
        assertEquals(BalanceRecoveryStep.None, recovery.held(back, 180_000))
        assertEquals(BalanceRecoveryStep.None, recovery.held(back, 240_000))
        assertEquals(BalanceRecoveryStep.None, recovery.held(0, 300_000))
        assertEquals(BalanceRecoveryStep.Rebuild, recovery.held(back, 360_000))
    }

    @Test
    fun retriesAreBounded() {
        val recovery = BalanceRecovery<String?>()
        var rebuilds = 0
        var t = 0L
        repeat(10) {
            t += 60_000
            recovery.held(0, t)
            t += 60_000
            if (recovery.held(back, t) == BalanceRecoveryStep.Rebuild) {
                rebuilds += 1
            }
        }
        assertEquals(BALANCE_RECOVERY_MAX_RETRIES, rebuilds)
        assertFalse(recovery.state.retriesLeft)
    }

    @Test
    fun aNewAskRefillsTheRetries() {
        val recovery = BalanceRecovery<String?>()
        var t = 0L
        repeat(BALANCE_RECOVERY_MAX_RETRIES) {
            t += 60_000
            recovery.held(0, t)
            t += 60_000
            recovery.held(back, t)
        }
        assertFalse(recovery.state.retriesLeft)
        t += 60_000
        recovery.startRefused("fr", nowMillis = t)
        assertTrue(recovery.state.retriesLeft)
        t += 60_000
        assertEquals(BalanceRecoveryStep.Start("fr"), recovery.held(back, t))
    }

    @Test
    fun aConnectionThatStaysUpRefillsTheRetries() {
        val recovery = BalanceRecovery<String?>()
        var t = 0L
        repeat(BALANCE_RECOVERY_MAX_RETRIES) {
            t += 60_000
            recovery.held(0, t)
            t += 60_000
            recovery.held(back, t)
        }
        assertFalse(recovery.state.retriesLeft)
        // connected out of the block, but not for long enough yet
        val lastRetry = t
        recovery.observe(gate = false, connectRequested = true, balance = reading(back, t + 1), nowMillis = lastRetry + 60_000)
        assertFalse(recovery.state.retriesLeft)
        recovery.observe(
            gate = false,
            connectRequested = true,
            balance = reading(back, lastRetry + BALANCE_RECOVERY_BUDGET_RESET_MILLIS),
            nowMillis = lastRetry + BALANCE_RECOVERY_BUDGET_RESET_MILLIS,
        )
        assertTrue(recovery.state.retriesLeft)
    }

    @Test
    fun cancelAndDisconnectStopTheWait() {
        val cancelled = BalanceRecovery<String?>()
        cancelled.startRefused("de", nowMillis = 1_000)
        cancelled.clear()
        assertFalse(cancelled.state.startWaiting)
        assertEquals(BalanceRecoveryStep.None, cancelled.disconnected(0, 60_000))
        assertEquals(BalanceRecoveryStep.None, cancelled.disconnected(back, 120_000))

        // a held connection the user disconnects is not reconnected
        val disconnected = BalanceRecovery<String?>()
        disconnected.held(0, 60_000)
        disconnected.clear()
        assertEquals(BalanceRecoveryStep.None, disconnected.disconnected(0, 120_000, gate = true))
        assertEquals(BalanceRecoveryStep.None, disconnected.disconnected(back, 180_000, gate = true))
    }

    @Test
    fun aHoldThatEndsByItselfIsNotRebuilt() {
        val recovery = BalanceRecovery<String?>()
        recovery.held(0, 60_000)
        // the connection got contracts again on its own
        recovery.observe(gate = false, connectRequested = true, balance = reading(back, 120_000), nowMillis = 120_000)
        assertEquals(
            BalanceRecoveryStep.None,
            recovery.observe(gate = false, connectRequested = true, balance = reading(back, 180_000), nowMillis = 180_000),
        )
    }

    @Test
    fun dataIsBackOnlyAtTheThreshold() {
        val recovery = BalanceRecovery<String?>()
        recovery.startRefused(null, nowMillis = 1_000)
        assertEquals(BalanceRecoveryStep.None, recovery.disconnected(low, 60_000))
        assertTrue(recovery.state.startWaiting)
        // a null target (the best available provider) is still the start the user asked for
        assertEquals(BalanceRecoveryStep.Start(null), recovery.disconnected(back, 120_000))
    }

    @Test
    fun anUnknownBalanceWaits() {
        val recovery = BalanceRecovery<String?>()
        recovery.startRefused("de", nowMillis = 1_000)
        assertEquals(
            BalanceRecoveryStep.None,
            recovery.observe(gate = true, connectRequested = false, balance = null, nowMillis = 60_000),
        )
        assertTrue(recovery.state.startWaiting)
    }

    @Test
    fun theLatestRefusedStartWinsOverTheHeldConnection() {
        val recovery = BalanceRecovery<String?>()
        recovery.held(0, 60_000)
        // held at one location, the user picked another and was refused
        recovery.startRefused("jp", nowMillis = 90_000)
        assertEquals(BalanceRecoveryStep.Start("jp"), recovery.held(back, 120_000))
    }
}
