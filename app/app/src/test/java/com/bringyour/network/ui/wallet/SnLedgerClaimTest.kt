package com.bringyour.network.ui.wallet

import com.bringyour.network.R
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The v2 claim: the dialog the tile's Claim opens, the progress of
 * SnLedgerClaim's callback (Sent with its transaction, Confirmed with the
 * vault's amount, Failed with the SDK's code), and the read after Done. UX-4
 * is APPS-SPEC.md's Claim rule; UX-3 its unavailable rule.
 *
 * Plain JVM: a fake protocol source in place of the SDK.
 */
class SnLedgerClaimTest {

    private val gasKey = SnGasKeyState(SampleProtocolSource.SAMPLE_GAS_ADDRESS, SampleProtocolSource.SAMPLE_GAS_MIRROR)

    private val pool1 = SnLedgerAccountState(1, 42, 5_120_000_000, 4_120_000_000, 1_000_000_000, false, 0)
    private val pool2 = SnLedgerAccountState(2, 7, 900_000_000, 310_000_000, 590_000_000, false, 0)
    private val paidUp = pool2.copy(claimableRao = 0, paidRao = 900_000_000)

    private val txHash1 = "0x" + "1a".repeat(32)
    private val txHash2 = "0x" + "2b".repeat(32)

    /** Answers with fixed results, counts the ledger reads and keeps the claim's callback. */
    private class FakeSource(
        var accounts: Result<List<SnLedgerAccountState>>,
        var gasTao: Double = 0.124,
        val key: SnGasKeyState?,
    ) : EarningsProtocolSource by NoProtocolSource {
        var reads = 0
        var claims = 0
        var onEvent: (SnLedgerClaimEvent) -> Unit = {}
        // when set, the next ledger read waits for it
        var readGate: CompletableDeferred<Unit>? = null

        override val available: Boolean = true
        override suspend fun ledgerAccounts(): Result<List<SnLedgerAccountState>> {
            readGate?.await()
            reads += 1
            return accounts
        }
        override fun ledgerClaimConfigured(): Boolean = true
        override fun ledgerClaim(onEvent: (SnLedgerClaimEvent) -> Unit) {
            claims += 1
            this.onEvent = onEvent
        }
        override fun gasKey(): SnGasKeyState? = key
        override suspend fun gasBalance(): Result<SnGasBalanceState> =
            Result.success(SnGasBalanceState("0", gasTao))
    }

    private fun sourceWith(vararg accounts: SnLedgerAccountState) =
        FakeSource(Result.success(accounts.toList()), key = gasKey)

    private fun flowFor(source: FakeSource, hasWallet: Boolean = true): SnLedgerFlow =
        SnLedgerFlow(CoroutineScope(Dispatchers.Unconfined)) { hasWallet }.also { it.reset(source) }

    private fun pending(account: SnLedgerAccountState) =
        SnLedgerClaimProgress(account.noId, account.index, account.claimableRao, SnLedgerClaimStatus.PENDING, null, null)

    // ---- the rows

    @Test
    fun `a claim starts with a pending row per account with something to claim`() {
        // UX-4: one transaction per account
        assertEquals(listOf(pending(pool1), pending(pool2)), snLedgerClaimStart(listOf(pool1, paidUp.copy(noId = 3), pool2)))
    }

    @Test
    fun `sent rows carry the transaction for the explorer link`() {
        // UX-4: Sent with an explorer link
        assertEquals(
            listOf(pending(pool1).copy(status = SnLedgerClaimStatus.SENT, txHash = txHash1), pending(pool2)),
            snLedgerClaimProgressAfter(listOf(pending(pool1), pending(pool2)), SnLedgerClaimEvent.Sent(1, 42, txHash1))
        )
    }

    @Test
    fun `confirmed rows carry the vault's amount`() {
        // UX-4: Confirmed with the amount from the vault's Claimed event, not the read one
        val sent = listOf(pending(pool1).copy(status = SnLedgerClaimStatus.SENT, txHash = txHash1))
        assertEquals(
            listOf(pending(pool1).copy(status = SnLedgerClaimStatus.CONFIRMED, txHash = txHash1, amountRao = 4_119_999_999)),
            snLedgerClaimProgressAfter(sent, SnLedgerClaimEvent.Confirmed(1, 42, txHash1, 4_119_999_999))
        )
    }

    @Test
    fun `a failed row keeps the SDK's message and its transaction`() {
        // UX-4: a receipt revert after Sent keeps the explorer link
        val sent = listOf(pending(pool1).copy(status = SnLedgerClaimStatus.SENT, txHash = txHash1))
        assertEquals(
            listOf(
                pending(pool1).copy(
                    status = SnLedgerClaimStatus.FAILED,
                    txHash = txHash1,
                    message = "claim_failed: TooManyStakingHotkeys",
                )
            ),
            snLedgerClaimProgressAfter(sent, SnLedgerClaimEvent.Failed(1, 42, "claim_failed: TooManyStakingHotkeys"))
        )
    }

    @Test
    fun `already claimed is not a failure`() {
        // UX-4: a third party claimed first (front-running is success); vault2 paid the same coldkey
        assertEquals(
            listOf(pending(pool1).copy(status = SnLedgerClaimStatus.ALREADY_CLAIMED, message = "already_claimed: NothingToClaim")),
            snLedgerClaimProgressAfter(listOf(pending(pool1)), SnLedgerClaimEvent.Failed(1, 42, "already_claimed: NothingToClaim"))
        )
    }

    @Test
    fun `a failure about no one account fails every row not sent`() {
        // UX-4: the SDK reports it with pool 0 (no wallet, not configured, the ledger or chain unreachable)
        val sent = pending(pool1).copy(status = SnLedgerClaimStatus.SENT, txHash = txHash1)
        assertEquals(
            listOf(sent, pending(pool2).copy(status = SnLedgerClaimStatus.FAILED, message = "ledger_unavailable: timeout")),
            snLedgerClaimProgressAfter(listOf(sent, pending(pool2)), SnLedgerClaimEvent.Failed(0, 0, "ledger_unavailable: timeout"))
        )
        // index 0 is an account in a pool: only pool 0 is about no account
        assertEquals(
            listOf(SnLedgerClaimProgress(1, 0, 0, SnLedgerClaimStatus.FAILED, null, "proof_mismatch: x")),
            snLedgerClaimProgressAfter(emptyList(), SnLedgerClaimEvent.Failed(1, 0, "proof_mismatch: x"))
        )
    }

    @Test
    fun `a claim that cannot start ends with every row failed`() {
        // UX-4: others -> the message
        val source = sourceWith(pool1, pool2)
        val flow = flowFor(source)
        flow.open()
        flow.claim()
        source.onEvent(SnLedgerClaimEvent.Failed(0, 0, "chain_not_configured: vault2 address: not set"))
        source.onEvent(SnLedgerClaimEvent.Done)
        val failed = listOf(pool1, pool2).map {
            pending(it).copy(status = SnLedgerClaimStatus.FAILED, message = "chain_not_configured: vault2 address: not set")
        }
        assertEquals(SnLedgerClaimDialogState.Finished(failed, 0), flow.dialog.value)
    }

    @Test
    fun `an event for an account without a row adds it`() {
        // the SDK reads the accounts again and may claim one the dialog did not list
        assertEquals(
            listOf(pending(pool1), SnLedgerClaimProgress(2, 7, 0, SnLedgerClaimStatus.SENT, txHash2, null)),
            snLedgerClaimProgressAfter(listOf(pending(pool1)), SnLedgerClaimEvent.Sent(2, 7, txHash2))
        )
        // Done changes no row
        assertEquals(listOf(pending(pool1)), snLedgerClaimProgressAfter(listOf(pending(pool1)), SnLedgerClaimEvent.Done))
    }

    @Test
    fun `done totals the confirmed rows and fails the rows never sent`() = runBlocking {
        // UX-4
        val confirmed = pending(pool1).copy(status = SnLedgerClaimStatus.CONFIRMED, txHash = txHash1)
        val already = pending(pool2).copy(status = SnLedgerClaimStatus.ALREADY_CLAIMED, message = "already_claimed: x")
        val unsent = pending(pool2.copy(noId = 3))
        assertEquals(
            SnLedgerClaimDialogState.Finished(
                listOf(confirmed, already, unsent.copy(status = SnLedgerClaimStatus.FAILED)),
                confirmedRao = pool1.claimableRao,
            ),
            snLedgerClaimFinished(listOf(confirmed, already, unsent), gasKey) { error("no gas read") }
        )
    }

    @Test
    fun `done with every account short of gas is the gas top-up`() = runBlocking {
        // UX-4: needs_gas goes to the existing top-up, with the gas balance read again
        val short = listOf(pending(pool1), pending(pool2)).map {
            it.copy(status = SnLedgerClaimStatus.FAILED, message = "needs_gas: balance 0 below 0.0025 TAO")
        }
        assertEquals(
            SnLedgerClaimDialogState.NeedsGas(gasKey, 0.0004, pool1.claimableRao + pool2.claimableRao),
            snLedgerClaimFinished(short, gasKey) { 0.0004 }
        )
        // one account through: the rows, not the top-up
        val mixed = listOf(short[0], pending(pool2).copy(status = SnLedgerClaimStatus.CONFIRMED))
        assertEquals(
            SnLedgerClaimDialogState.Finished(mixed, pool2.claimableRao),
            snLedgerClaimFinished(mixed, gasKey) { error("no gas read") }
        )
    }

    @Test
    fun `failures read as the app's strings, others as the SDK's detail`() {
        // UX-4: needs_gas and an unreachable rpc have their own strings
        assertEquals(R.string.add_tao_for_gas, snLedgerClaimFailureRes("needs_gas: balance 0"))
        assertEquals(R.string.chain_rpc_unreachable, snLedgerClaimFailureRes("chain_rpc_unreachable: dial tcp"))
        for (message in listOf(
            "claim_failed: TooManyStakingHotkeys",
            "not_claimable: below the runtime's transfer floor",
            "proof_mismatch: the root does not match vault2",
            "ledger_unavailable: 404",
        )) {
            assertNull(message, snLedgerClaimFailureRes(message))
        }
        assertEquals("TooManyStakingHotkeys", snLedgerClaimFailureDetail("claim_failed: TooManyStakingHotkeys"))
        assertEquals("no code", snLedgerClaimFailureDetail("no code"))
        assertEquals("proof_mismatch:", snLedgerClaimFailureDetail("proof_mismatch:"))
    }

    // ---- the dialog

    @Test
    fun `the dialog lists the claimable accounts with the gas key`() {
        // UX-4
        assertEquals(
            SnLedgerClaimDialogState.Ready(listOf(pool1), pool1.claimableRao, gasKey, 0.124),
            snLedgerClaimDialog(SnLedgerLoad.Loaded(listOf(pool1, paidUp)), gasKey, 0.124)
        )
        // the gas balance could not be read: the SDK's preflight decides
        assertEquals(
            SnLedgerClaimDialogState.Ready(listOf(pool1), pool1.claimableRao, gasKey, null),
            snLedgerClaimDialog(SnLedgerLoad.Loaded(listOf(pool1)), gasKey, null)
        )
    }

    @Test
    fun `a gas key that cannot pay opens on the top-up`() {
        // UX-4: the existing needs-gas flow, before anything is sent
        assertEquals(
            SnLedgerClaimDialogState.NeedsGas(gasKey, 0.001, pool1.claimableRao),
            snLedgerClaimDialog(SnLedgerLoad.Loaded(listOf(pool1)), gasKey, 0.001)
        )
        // with nothing to claim there is nothing to pay for
        assertEquals(
            SnLedgerClaimDialogState.Ready(emptyList(), 0, gasKey, 0.001),
            snLedgerClaimDialog(SnLedgerLoad.Loaded(listOf(paidUp)), gasKey, 0.001)
        )
    }

    @Test
    fun `an unreadable ledger opens on unavailable`() {
        // UX-3
        assertEquals(SnLedgerClaimDialogState.Unavailable, snLedgerClaimDialog(SnLedgerLoad.Unavailable, gasKey, 0.124))
        assertEquals(SnLedgerClaimDialogState.Unavailable, snLedgerClaimDialog(SnLedgerLoad.Loading, gasKey, 0.124))
    }

    // ---- the flow

    @Test
    fun `a read maps to loaded or unavailable`() = runBlocking {
        // UX-3: a failed read (ledger_unavailable) is unavailable, never an empty list
        val source = sourceWith(pool1)
        val flow = flowFor(source)
        flow.refresh()
        assertEquals(SnLedgerLoad.Loaded(listOf(pool1)), flow.load.value)
        assertTrue(flow.claimConfigured.value)
        source.accounts = Result.failure(SnProtocolException("ledger_unavailable", "refused the ledger answer"))
        flow.refresh()
        assertEquals(SnLedgerLoad.Unavailable, flow.load.value)
    }

    @Test
    fun `a configured tile is loading, not zero, during the first read`() {
        // UX-3: no amounts before the read answers
        val source = sourceWith(pool1)
        val gate = CompletableDeferred<Unit>()
        source.readGate = gate
        val flow = flowFor(source)
        CoroutineScope(Dispatchers.Unconfined).launch { flow.refresh() }
        assertEquals(
            SnLedgerTile(accounts = null, unavailable = false, showClaim = true, claimEnabled = false),
            snLedgerTile(flow.load.value, flow.claimConfigured.value)
        )
        gate.complete(Unit)
        assertEquals(SnLedgerLoad.Loaded(listOf(pool1)), flow.load.value)
    }

    @Test
    fun `a read the last device answers late is dropped`() {
        val source = sourceWith(pool1)
        val gate = CompletableDeferred<Unit>()
        source.readGate = gate
        val flow = flowFor(source)
        CoroutineScope(Dispatchers.Unconfined).launch { flow.refresh() }
        flow.reset(sourceWith(pool2))
        gate.complete(Unit)
        assertEquals(SnLedgerLoad.Loading, flow.load.value)
    }

    @Test
    fun `without a wallet nothing is read`() = runBlocking {
        val source = sourceWith(pool1)
        val flow = flowFor(source, hasWallet = false)
        flow.refresh()
        assertEquals(SnLedgerLoad.Loading, flow.load.value)
        assertEquals(0, source.reads)
    }

    @Test
    fun `claim sends once and follows each account to done, then reads again`() {
        // UX-4
        val source = sourceWith(pool1, pool2)
        val flow = flowFor(source)
        flow.open()
        assertEquals(
            SnLedgerClaimDialogState.Ready(listOf(pool1, pool2), pool1.claimableRao + pool2.claimableRao, gasKey, 0.124),
            flow.dialog.value
        )
        flow.claim()
        assertEquals(1, source.claims)
        assertEquals(SnLedgerClaimDialogState.Sending(listOf(pending(pool1), pending(pool2))), flow.dialog.value)

        source.onEvent(SnLedgerClaimEvent.Sent(1, 42, txHash1))
        source.onEvent(SnLedgerClaimEvent.Confirmed(1, 42, txHash1, 4_120_000_000))
        source.onEvent(SnLedgerClaimEvent.Sent(2, 7, txHash2))
        source.onEvent(SnLedgerClaimEvent.Failed(2, 7, "claim_failed: TooManyStakingHotkeys"))
        val rows = listOf(
            pending(pool1).copy(status = SnLedgerClaimStatus.CONFIRMED, txHash = txHash1),
            pending(pool2).copy(status = SnLedgerClaimStatus.FAILED, txHash = txHash2, message = "claim_failed: TooManyStakingHotkeys"),
        )
        assertEquals(SnLedgerClaimDialogState.Sending(rows), flow.dialog.value)

        // the account the runtime refused stays claimable
        source.accounts = Result.success(listOf(pool1.copy(claimableRao = 0, paidRao = 5_120_000_000), pool2))
        val readsBefore = source.reads
        source.onEvent(SnLedgerClaimEvent.Done)
        assertEquals(SnLedgerClaimDialogState.Finished(rows, 4_120_000_000), flow.dialog.value)
        assertEquals(readsBefore + 1, source.reads)
        assertEquals(SnLedgerLoad.Loaded(source.accounts.getOrThrow()), flow.load.value)
    }

    @Test
    fun `already claimed reads the accounts again`() {
        // UX-4: already_claimed -> refresh
        val source = sourceWith(pool1)
        val flow = flowFor(source)
        flow.open()
        flow.claim()
        source.onEvent(SnLedgerClaimEvent.Failed(1, 42, "already_claimed: NothingToClaim"))
        source.accounts = Result.success(listOf(pool1.copy(claimableRao = 0, paidRao = 5_120_000_000)))
        source.onEvent(SnLedgerClaimEvent.Done)
        assertEquals(
            SnLedgerClaimDialogState.Finished(
                listOf(pending(pool1).copy(status = SnLedgerClaimStatus.ALREADY_CLAIMED, message = "already_claimed: NothingToClaim")),
                0,
            ),
            flow.dialog.value
        )
        assertEquals(SnLedgerLoad.Loaded(source.accounts.getOrThrow()), flow.load.value)
    }

    @Test
    fun `needs gas ends on the top-up and retry opens the dialog again`() {
        // UX-4: needs_gas -> the existing gas top-up flow
        val source = sourceWith(pool1)
        val flow = flowFor(source)
        flow.open()
        flow.claim()
        source.gasTao = 0.0
        source.onEvent(SnLedgerClaimEvent.Failed(1, 42, "needs_gas: balance 0"))
        source.onEvent(SnLedgerClaimEvent.Done)
        assertEquals(SnLedgerClaimDialogState.NeedsGas(gasKey, 0.0, pool1.claimableRao), flow.dialog.value)

        // the user funds the gas key and retries
        source.gasTao = 0.05
        flow.retry()
        assertEquals(SnLedgerClaimDialogState.Ready(listOf(pool1), pool1.claimableRao, gasKey, 0.05), flow.dialog.value)
    }

    @Test
    fun `a closed dialog's claim still reads the accounts after done`() {
        // UX-4: refresh the accounts after Done
        val source = sourceWith(pool1)
        val flow = flowFor(source)
        flow.open()
        flow.claim()
        flow.close()
        source.onEvent(SnLedgerClaimEvent.Sent(1, 42, txHash1))
        assertNull(flow.dialog.value)
        val readsBefore = source.reads
        source.onEvent(SnLedgerClaimEvent.Done)
        assertNull(flow.dialog.value)
        assertEquals(readsBefore + 1, source.reads)
    }

    @Test
    fun `reopening during a claim shows it instead of starting another`() {
        val source = sourceWith(pool1)
        val flow = flowFor(source)
        flow.open()
        flow.claim()
        flow.close()
        source.onEvent(SnLedgerClaimEvent.Sent(1, 42, txHash1))
        val readsBefore = source.reads
        flow.open()
        assertEquals(
            SnLedgerClaimDialogState.Sending(listOf(pending(pool1).copy(status = SnLedgerClaimStatus.SENT, txHash = txHash1))),
            flow.dialog.value
        )
        assertEquals(readsBefore, source.reads)
        flow.claim()
        flow.retry()
        assertEquals(1, source.claims)
    }

    @Test
    fun `an unreadable ledger keeps the dialog unavailable until retried`() {
        // UX-3
        val source = FakeSource(Result.failure(SnProtocolException("ledger_unavailable", "timeout")), key = gasKey)
        val flow = flowFor(source)
        flow.open()
        assertEquals(SnLedgerClaimDialogState.Unavailable, flow.dialog.value)
        flow.claim()
        assertEquals(0, source.claims)
        source.accounts = Result.success(listOf(pool1))
        flow.retry()
        assertEquals(SnLedgerClaimDialogState.Ready(listOf(pool1), pool1.claimableRao, gasKey, 0.124), flow.dialog.value)
    }

    @Test
    fun `nothing to claim sends nothing`() {
        val source = sourceWith(paidUp)
        val flow = flowFor(source)
        flow.open()
        flow.claim()
        assertEquals(0, source.claims)
        assertEquals(SnLedgerClaimDialogState.Ready(emptyList(), 0, gasKey, 0.124), flow.dialog.value)
    }

    @Test
    fun `a dialog closed while loading stays closed`() {
        val source = sourceWith(pool1)
        val gate = CompletableDeferred<Unit>()
        source.readGate = gate
        val flow = flowFor(source)
        flow.open()
        assertEquals(SnLedgerClaimDialogState.Loading, flow.dialog.value)
        flow.close()
        gate.complete(Unit)
        assertNull(flow.dialog.value)
    }

    @Test
    fun `a new device drops the last one's claim`() {
        val source = sourceWith(pool1)
        val flow = flowFor(source)
        flow.open()
        flow.claim()
        val next = sourceWith(pool2)
        flow.reset(next)
        assertNull(flow.dialog.value)
        assertEquals(SnLedgerLoad.Loading, flow.load.value)
        // the last device's events change nothing, and its Done reads nothing
        source.onEvent(SnLedgerClaimEvent.Sent(1, 42, txHash1))
        source.onEvent(SnLedgerClaimEvent.Done)
        assertNull(flow.dialog.value)
        assertEquals(0, next.reads)
        assertEquals(SnLedgerLoad.Loading, flow.load.value)
        // and the new device claims afresh
        flow.open()
        flow.claim()
        assertEquals(1, next.claims)
    }
}
