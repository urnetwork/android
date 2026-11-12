package com.bringyour.network.ui.wallet

import com.bringyour.network.R
import com.bringyour.sdk.Sdk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The v2 claim: the dialog the tile's Claim opens, the progress of
 * SnLedgerClaim's callback (Sent with its transaction, Confirmed with the
 * vault's amount, Failed with the SDK's code), and the read after Done. UX-4
 * is APPS-SPEC.md's Claim rule; UX-3 its unavailable rule. CTF-n are capture
 * the flag's rules (sn CAPTURETHEFLAG.md §10, the sdk's batched claim): CTF-2
 * a pool that could not be read is unavailable and the others are claimed;
 * CTF-3 one claim takes the pool's account and capture the flag's in one
 * batch transaction; CTF-6 an account under the transfer floor, any pool's,
 * waits and asks for no claim; CTF-7 a gas key that can pay for one claim but
 * not both claims the largest, and the other stays claimable (owner,
 * 2026-11-11).
 *
 * Plain JVM: a fake protocol source in place of the SDK.
 */
class SnLedgerClaimTest {

    private val gasKey = SnGasKeyState(SampleProtocolSource.SAMPLE_GAS_ADDRESS, SampleProtocolSource.SAMPLE_GAS_MIRROR)

    private val pool1 = SnLedgerAccountState(1, 42, 5_120_000_000, 4_120_000_000, 1_000_000_000, false, 0)
    private val pool2 = SnLedgerAccountState(2, 7, 900_000_000, 310_000_000, 590_000_000, false, 0)
    private val paidUp = pool2.copy(claimableRao = 0, paidRao = 900_000_000)

    // operator 1's capture the flag pool, vault2 pool 2^32 + 1
    private val ctfNoId = Sdk.SnLedgerCtfPoolIdBase + 1
    private val ctf = SnLedgerAccountState(ctfNoId, 3, 96_051_320, 96_051_320, 0, false, 0, kind = Sdk.SnLedgerPoolKindCtf)

    private val txHash1 = "0x" + "1a".repeat(32)
    private val txHash2 = "0x" + "2b".repeat(32)

    /** Answers with fixed results, counts the ledger reads and keeps the claim's callback. */
    private class FakeSource(
        var accounts: Result<List<SnLedgerAccountState>>,
        var gasTao: Double = 0.124,
        val key: SnGasKeyState?,
        // the pools a partial answer could not read
        var unavailablePools: List<SnLedgerPool> = emptyList(),
    ) : EarningsProtocolSource by NoProtocolSource {
        var reads = 0
        var claims = 0
        var onEvent: (SnLedgerClaimEvent) -> Unit = {}
        // when set, the next ledger read waits for it
        var readGate: CompletableDeferred<Unit>? = null
        // GET /sn/ctf/client's unclaimed wins, one answer per read in turn, null
        // for a read that failed (the sdk's ctf_unavailable); the last one
        // answers every read after it
        var winAnswers: List<List<SnCtfWinState>?> = listOf(emptyList())
        var winReads = 0
        // when set, the next read of the wins waits for it
        var winGate: CompletableDeferred<Unit>? = null
        // the reads of the wins by the order they started, from 0: read n waits
        // for winGateFor[n] when there is one, and answers winAnswerFor[n] (null:
        // it fails) when there is one, else as winAnswers says
        var winStarts = 0
        val winGateFor = mutableMapOf<Int, CompletableDeferred<Unit>>()
        val winAnswerFor = mutableMapOf<Int, List<SnCtfWinState>?>()

        override val available: Boolean = true
        override suspend fun ledgerAccounts(): Result<SnLedgerAccounts> {
            readGate?.await()
            reads += 1
            return accounts.map { SnLedgerAccounts(it, unavailablePools) }
        }
        override fun ledgerClaimConfigured(): Boolean = true
        override fun ledgerClaim(onEvent: (SnLedgerClaimEvent) -> Unit) {
            claims += 1
            this.onEvent = onEvent
        }
        override fun gasKey(): SnGasKeyState? = key
        override suspend fun gasBalance(): Result<SnGasBalanceState> =
            Result.success(SnGasBalanceState("0", gasTao))
        override suspend fun ctfWins(): Result<List<SnCtfWinState>> {
            val started = winStarts++
            winGate?.await()
            winGateFor[started]?.await()
            val answer = if (started in winAnswerFor) winAnswerFor[started] else winAnswers[minOf(winReads, winAnswers.size - 1)]
            winReads += 1
            return answer?.let { Result.success(it) }
                ?: Result.failure(SnProtocolException("ctf_unavailable", "the capture the flag route answered 503"))
        }
    }

    /**
     * The flow's waits between the reads of the wins: each one is kept, and
     * the flow waits until the test moves the clock past it.
     */
    private class Clock {
        val waits = mutableListOf<Long>()
        private var tick = CompletableDeferred<Unit>()

        suspend fun sleep(ms: Long) {
            waits += ms
            tick.await()
        }

        /** Past the wait the flow is in, if any. */
        fun next() {
            val passed = tick
            tick = CompletableDeferred()
            passed.complete(Unit)
        }
    }

    private fun sourceWith(vararg accounts: SnLedgerAccountState) =
        FakeSource(Result.success(accounts.toList()), key = gasKey)

    private fun flowFor(
        source: FakeSource,
        hasWallet: Boolean = true,
        transferFloorRao: Long = 0,
        clock: Clock = Clock(),
    ): SnLedgerFlow =
        SnLedgerFlow(CoroutineScope(Dispatchers.Unconfined), { hasWallet }, { transferFloorRao }, clock::sleep).also { it.reset(source) }

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
            it.copy(status = SnLedgerClaimStatus.STILL_CLAIMABLE, message = "needs_gas: balance 0 below 0.0025 TAO")
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
    fun `a screen left during a claim and shown again shows it and gets its Done`() {
        // the flow is the activity's view model's: leaving Earnings closes the dialog, and a visit
        // touches none of the flow (onScreenResumed); a refresh while there (refreshAll) reads
        // the accounts and the wins again, which leaves the claim be too
        val source = sourceWith(pool1, ctf)
        // the refresh's read, the read at Done, and the read 30 s after it, which finds the win claimed
        source.winAnswers = listOf(listOf(win), listOf(win), emptyList())
        val clock = Clock()
        val flow = flowFor(source, clock = clock)
        flow.open()
        flow.claim()
        source.onEvent(SnLedgerClaimEvent.Sent(1, 42, txHash1))
        flow.close()
        // away from Earnings, the claim goes on
        source.onEvent(SnLedgerClaimEvent.Sent(ctfNoId, 3, txHash1))
        source.onEvent(SnLedgerClaimEvent.Confirmed(1, 42, txHash1, pool1.claimableRao))
        // back, and a refresh's reads; then the tile's Claim shows the claim in flight
        runBlocking {
            flow.refresh()
            flow.refreshWins()
        }
        assertEquals(listOf(win), flow.wins.value)
        flow.open()
        val inFlight = listOf(
            pending(pool1).copy(status = SnLedgerClaimStatus.CONFIRMED, txHash = txHash1),
            pending(ctf).copy(status = SnLedgerClaimStatus.SENT, txHash = txHash1),
        )
        assertEquals(SnLedgerClaimDialogState.Sending(inFlight), flow.dialog.value)
        // its last event and its Done land: the rows, the accounts read again, and the wins on
        // the apps' schedule
        source.onEvent(SnLedgerClaimEvent.Confirmed(ctfNoId, 3, txHash1, ctf.claimableRao))
        source.accounts = Result.success(listOf(pool1.copy(claimableRao = 0, paidRao = 5_120_000_000), ctf.copy(claimableRao = 0, paidRao = ctf.finalizedRao)))
        source.onEvent(SnLedgerClaimEvent.Done)
        assertEquals(
            SnLedgerClaimDialogState.Finished(
                listOf(inFlight[0], pending(ctf).copy(status = SnLedgerClaimStatus.CONFIRMED, txHash = txHash1)),
                pool1.claimableRao + ctf.claimableRao,
            ),
            flow.dialog.value
        )
        assertEquals(SnLedgerLoad.Loaded(source.accounts.getOrThrow()), flow.load.value)
        assertEquals(2, source.winReads)
        assertEquals(listOf(30_000L), clock.waits)
        clock.next()
        assertEquals(3, source.winReads)
        assertEquals(emptyList<SnCtfWinState>(), flow.wins.value)
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

    // ---- capture the flag

    @Test
    fun `one claim takes the pool's account and capture the flag's in one transaction`() {
        // CTF-3: the sdk sends both in one batch, Sent for each with the same transaction
        val source = sourceWith(pool1, ctf)
        val flow = flowFor(source)
        flow.open()
        val ready = flow.dialog.value as SnLedgerClaimDialogState.Ready
        assertEquals(listOf(pool1, ctf), ready.accounts)
        assertEquals(pool1.claimableRao + ctf.claimableRao, ready.totalRao)
        // its Claim reads Claim both
        assertTrue(snLedgerClaimsBoth(ready.accounts.map { it.pool }))
        flow.claim()
        assertEquals(1, source.claims)
        assertEquals(SnLedgerClaimDialogState.Sending(listOf(pending(pool1), pending(ctf))), flow.dialog.value)

        source.onEvent(SnLedgerClaimEvent.Sent(1, 42, txHash1))
        source.onEvent(SnLedgerClaimEvent.Sent(ctfNoId, 3, txHash1))
        source.onEvent(SnLedgerClaimEvent.Confirmed(1, 42, txHash1, pool1.claimableRao))
        source.onEvent(SnLedgerClaimEvent.Confirmed(ctfNoId, 3, txHash1, ctf.claimableRao))
        source.accounts = Result.success(
            listOf(pool1.copy(claimableRao = 0, paidRao = 5_120_000_000), ctf.copy(claimableRao = 0, paidRao = ctf.finalizedRao))
        )
        source.onEvent(SnLedgerClaimEvent.Done)
        val rows = listOf(
            pending(pool1).copy(status = SnLedgerClaimStatus.CONFIRMED, txHash = txHash1),
            pending(ctf).copy(status = SnLedgerClaimStatus.CONFIRMED, txHash = txHash1),
        )
        assertEquals(SnLedgerClaimDialogState.Finished(rows, pool1.claimableRao + ctf.claimableRao), flow.dialog.value)
        // each row keeps its pool's kind, which names it
        assertEquals(listOf(Sdk.SnLedgerPoolKindPool, Sdk.SnLedgerPoolKindCtf), rows.map { it.kind })
        assertEquals(SnLedgerLoad.Loaded(source.accounts.getOrThrow()), flow.load.value)
    }

    @Test
    fun `a pool that could not be read is unavailable while the others are claimed`() {
        // CTF-2: Failed(noId, 0, ledger_unavailable) for that pool, whose account is unknown
        val source = FakeSource(Result.success(listOf(pool1)), key = gasKey, unavailablePools = listOf(SnLedgerPool(ctfNoId)))
        val flow = flowFor(source)
        flow.open()
        assertEquals(
            SnLedgerClaimDialogState.Ready(listOf(pool1), pool1.claimableRao, gasKey, 0.124, listOf(SnLedgerPool(ctfNoId))),
            flow.dialog.value
        )
        flow.claim()
        // the claim keeps the pool the dialog listed as unavailable
        val unread = SnLedgerClaimProgress(ctfNoId, 0, 0, SnLedgerClaimStatus.POOL_UNAVAILABLE, null, null)
        assertEquals(SnLedgerClaimDialogState.Sending(listOf(pending(pool1), unread)), flow.dialog.value)
        source.onEvent(SnLedgerClaimEvent.Failed(ctfNoId, 0, "ledger_unavailable: 502"))
        source.onEvent(SnLedgerClaimEvent.Sent(1, 42, txHash1))
        source.onEvent(SnLedgerClaimEvent.Confirmed(1, 42, txHash1, pool1.claimableRao))
        source.onEvent(SnLedgerClaimEvent.Done)
        assertEquals(
            SnLedgerClaimDialogState.Finished(
                listOf(
                    pending(pool1).copy(status = SnLedgerClaimStatus.CONFIRMED, txHash = txHash1),
                    unread.copy(message = "ledger_unavailable: 502"),
                ),
                pool1.claimableRao,
            ),
            flow.dialog.value
        )
    }

    @Test
    fun `a claim that fails before the sdk reads keeps the unavailable pool's row`() {
        // CTF-2: a failure about no one account fails the rows not sent, and the pool the dialog
        // could not read stays unavailable rather than vanishing
        val rows = snLedgerClaimStart(listOf(pool1), listOf(SnLedgerPool(ctfNoId)))
        assertEquals(
            listOf(
                pending(pool1).copy(status = SnLedgerClaimStatus.FAILED, message = "chain_not_configured: vault2 address: not set"),
                SnLedgerClaimProgress(ctfNoId, 0, 0, SnLedgerClaimStatus.POOL_UNAVAILABLE, null, null),
            ),
            snLedgerClaimProgressAfter(rows, SnLedgerClaimEvent.Failed(0, 0, "chain_not_configured: vault2 address: not set"))
        )
    }

    @Test
    fun `a pool the dialog could not read but the sdk could takes its row`() {
        // the sdk reads the ledger again: an account of a pool unavailable at the dialog's read
        // replaces that pool's row, with no second row and no amount until vault2 names it
        val start = snLedgerClaimStart(listOf(pool1), listOf(SnLedgerPool(ctfNoId, Sdk.SnLedgerPoolKindCtf)))
        val sent = snLedgerClaimProgressAfter(start, SnLedgerClaimEvent.Sent(ctfNoId, 3, txHash1))
        val unknown = SnLedgerClaimProgress(ctfNoId, 3, 0, SnLedgerClaimStatus.SENT, txHash1, null, Sdk.SnLedgerPoolKindCtf)
        assertEquals(listOf(pending(pool1), unknown), sent)
        assertFalse(sent[1].amountKnown)
        assertEquals(
            listOf(pending(pool1), unknown.copy(status = SnLedgerClaimStatus.CONFIRMED, amountRao = ctf.claimableRao)),
            snLedgerClaimProgressAfter(sent, SnLedgerClaimEvent.Confirmed(ctfNoId, 3, txHash1, ctf.claimableRao))
        )
        assertTrue(pending(pool1).amountKnown)
    }

    @Test
    fun `a pool unreadable at claim time leaves its listed account unavailable, not failed`() {
        // the sdk reads the ledger again when it claims: the pool of an account the dialog listed may fail it
        val rows = listOf(pending(pool1), pending(ctf))
        assertEquals(
            listOf(pending(pool1), pending(ctf).copy(status = SnLedgerClaimStatus.POOL_UNAVAILABLE, message = "ledger_unavailable: timeout")),
            snLedgerClaimProgressAfter(rows, SnLedgerClaimEvent.Failed(ctfNoId, 0, "ledger_unavailable: timeout"))
        )
        // a failure about no one account still fails every row not sent
        assertEquals(
            rows.map { it.copy(status = SnLedgerClaimStatus.FAILED, message = "ledger_unavailable: timeout") },
            snLedgerClaimProgressAfter(rows, SnLedgerClaimEvent.Failed(0, 0, "ledger_unavailable: timeout"))
        )
    }

    @Test
    fun `an account under the transfer floor waits, which is no failure`() {
        // not_claimable with the runtime's AmountTooLow: Below the claim minimum
        val message = "not_claimable: AmountTooLow: the claim is below the runtime's transfer floor"
        assertTrue(snLedgerClaimUnderFloor(message))
        assertFalse(snLedgerClaimUnderFloor("not_claimable: the ledger lists this account in 0x1, not in the configured vault2 0x2"))
        assertFalse(snLedgerClaimUnderFloor("claim_failed: AmountTooLow"))
        assertEquals(
            listOf(pending(ctf).copy(status = SnLedgerClaimStatus.UNDER_FLOOR, message = message)),
            snLedgerClaimProgressAfter(listOf(pending(ctf)), SnLedgerClaimEvent.Failed(ctfNoId, 3, message))
        )
        // a pool's account beside capture the flag's waits the same way
        assertEquals(
            listOf(pending(pool1).copy(status = SnLedgerClaimStatus.UNDER_FLOOR, message = message), pending(ctf)),
            snLedgerClaimProgressAfter(listOf(pending(pool1), pending(ctf)), SnLedgerClaimEvent.Failed(1, 42, message))
        )
    }

    @Test
    fun `the runtime's refusal under its floor waits on any server`() {
        // CTF-6: whatever pools the read showed, a lone pool's account the runtime refuses under
        // its floor waits, as one beside capture the flag does; the relay's price was an estimate
        val message = "not_claimable: AmountTooLow: the claim is below the runtime's transfer floor"
        val source = sourceWith(pool1, ctf.copy(claimableRao = 0, paidRao = ctf.finalizedRao))
        val flow = flowFor(source)
        flow.open()
        flow.claim()
        source.onEvent(SnLedgerClaimEvent.Failed(1, 42, message))
        source.onEvent(SnLedgerClaimEvent.Done)
        assertEquals(
            SnLedgerClaimDialogState.Finished(listOf(pending(pool1).copy(status = SnLedgerClaimStatus.UNDER_FLOOR, message = message)), 0),
            flow.dialog.value
        )
    }

    @Test
    fun `a pool the sdk read without a word for it is no longer unavailable at done`() {
        // the dialog's read could not read capture the flag's pool; the sdk's could, and found
        // nothing there to claim, so it sends no event for it: the claim read it
        val source = FakeSource(Result.success(listOf(pool1)), key = gasKey, unavailablePools = listOf(SnLedgerPool(ctfNoId)))
        val flow = flowFor(source)
        flow.open()
        flow.claim()
        source.onEvent(SnLedgerClaimEvent.Sent(1, 42, txHash1))
        source.onEvent(SnLedgerClaimEvent.Confirmed(1, 42, txHash1, pool1.claimableRao))
        source.onEvent(SnLedgerClaimEvent.Done)
        assertEquals(
            SnLedgerClaimDialogState.Finished(
                listOf(pending(pool1).copy(status = SnLedgerClaimStatus.CONFIRMED, txHash = txHash1)),
                pool1.claimableRao,
            ),
            flow.dialog.value
        )
    }

    @Test
    fun `a claim stopped before the sdk's read keeps the pools it could not read`() {
        val source = FakeSource(Result.success(listOf(pool1)), key = gasKey, unavailablePools = listOf(SnLedgerPool(ctfNoId)))
        val flow = flowFor(source)
        flow.open()
        flow.claim()
        source.onEvent(SnLedgerClaimEvent.Failed(0, 0, "chain_rpc_unreachable: dial tcp"))
        source.onEvent(SnLedgerClaimEvent.Done)
        assertEquals(
            SnLedgerClaimDialogState.Finished(
                listOf(
                    pending(pool1).copy(status = SnLedgerClaimStatus.FAILED, message = "chain_rpc_unreachable: dial tcp"),
                    SnLedgerClaimProgress(ctfNoId, 0, 0, SnLedgerClaimStatus.POOL_UNAVAILABLE, null, null),
                ),
                0,
            ),
            flow.dialog.value
        )
    }

    @Test
    fun `the sdk's read answered once it says anything of an account or a pool`() {
        assertTrue(snLedgerClaimReadLedger(SnLedgerClaimEvent.Sent(1, 42, txHash1)))
        assertTrue(snLedgerClaimReadLedger(SnLedgerClaimEvent.Confirmed(1, 42, txHash1, 1)))
        assertTrue(snLedgerClaimReadLedger(SnLedgerClaimEvent.Failed(ctfNoId, 0, "ledger_unavailable: 502")))
        assertTrue(snLedgerClaimReadLedger(SnLedgerClaimEvent.Failed(1, 42, "proof_mismatch: x")))
        // nothing to claim anywhere: never said while a pool is unavailable
        assertTrue(snLedgerClaimReadLedger(SnLedgerClaimEvent.Failed(0, 0, "not_claimable: no settlement v2 account has anything to claim")))
        // stopped before the read
        for (message in listOf(
            "chain_not_configured: vault2 address: not set",
            "connect_wallet_first: connect a Bittensor wallet first",
            "ledger_unavailable: 502",
            "local_state_unavailable: x",
        )) {
            assertFalse(message, snLedgerClaimReadLedger(SnLedgerClaimEvent.Failed(0, 0, message)))
        }
        assertFalse(snLedgerClaimReadLedger(SnLedgerClaimEvent.Done))
        // only an unavailable row the read said nothing of goes
        val said = SnLedgerClaimProgress(2, 0, 0, SnLedgerClaimStatus.POOL_UNAVAILABLE, null, "ledger_unavailable: 502")
        val unsaid = SnLedgerClaimProgress(ctfNoId, 0, 0, SnLedgerClaimStatus.POOL_UNAVAILABLE, null, null)
        assertEquals(listOf(pending(pool1), said), snLedgerClaimSettled(listOf(pending(pool1), said, unsaid), ledgerRead = true))
        assertEquals(listOf(pending(pool1), said, unsaid), snLedgerClaimSettled(listOf(pending(pool1), said, unsaid), ledgerRead = false))
    }

    @Test
    fun `without capture the flag an account under the floor waits too`() {
        // CTF-6 (owner, 2026-11-11): sdk main already sends not_claimable AmountTooLow, and a
        // server without capture the flag shows it Below the claim minimum, not Failed
        val message = "not_claimable: AmountTooLow: the claim is below the runtime's transfer floor"
        assertEquals(
            listOf(pending(pool1).copy(status = SnLedgerClaimStatus.UNDER_FLOOR, message = message), pending(pool2)),
            snLedgerClaimProgressAfter(listOf(pending(pool1), pending(pool2)), SnLedgerClaimEvent.Failed(1, 42, message))
        )
        // any other not_claimable is still a failure
        val other = "not_claimable: the ledger lists this account in 0x1, not in the configured vault2 0x2"
        assertEquals(
            listOf(pending(pool1).copy(status = SnLedgerClaimStatus.FAILED, message = other)),
            snLedgerClaimProgressAfter(listOf(pending(pool1)), SnLedgerClaimEvent.Failed(1, 42, other))
        )
    }

    @Test
    fun `Claim and Claim both count what clears the relay's transfer floor`() {
        // CTF-6: an account under the floor is no claim's to take
        assertEquals(listOf(pool1, ctf), snLedgerClaimTakes(listOf(pool1, ctf), 0))
        assertTrue(snLedgerClaimsBoth(snLedgerClaimTakes(listOf(pool1, ctf), 0).map { it.pool }))
        assertEquals(listOf(pool1, ctf), snLedgerClaimTakes(listOf(pool1, ctf), ctf.claimableRao))
        // capture the flag's account under the floor: Claim, not Claim both
        val above = snLedgerClaimTakes(listOf(pool1, ctf), ctf.claimableRao + 1)
        assertEquals(listOf(pool1), above)
        assertFalse(snLedgerClaimsBoth(above.map { it.pool }))
        // nothing above the floor
        assertEquals(emptyList<SnLedgerAccountState>(), snLedgerClaimTakes(listOf(ctf), ctf.claimableRao + 1))
        // nothing to claim is never counted
        assertEquals(emptyList<SnLedgerAccountState>(), snLedgerClaimTakes(listOf(paidUp), 0))
        // two operator pools and no capture the flag: the floor applies all the same
        assertEquals(listOf(pool1), snLedgerClaimTakes(listOf(pool1, pool2), pool2.claimableRao + 1))
    }

    @Test
    fun `the dialog's total and gas are for what the claim moves`() {
        // CTF-6: the account under the floor is listed, and the total leaves it out
        val floor = ctf.claimableRao + 1
        val ready = snLedgerClaimDialog(SnLedgerLoad.Loaded(listOf(pool1, ctf)), gasKey, 0.124, floor)
        assertEquals(SnLedgerClaimDialogState.Ready(listOf(pool1, ctf), pool1.claimableRao, gasKey, 0.124, transferFloorRao = floor), ready)
        assertEquals(listOf(pool1), (ready as SnLedgerClaimDialogState.Ready).takes)
        // the gas top-up asks only for what can move
        assertEquals(
            SnLedgerClaimDialogState.NeedsGas(gasKey, 0.001, pool1.claimableRao),
            snLedgerClaimDialog(SnLedgerLoad.Loaded(listOf(pool1, ctf)), gasKey, 0.001, floor)
        )
        // with no price, the whole claimable, as before
        assertEquals(
            SnLedgerClaimDialogState.Ready(listOf(pool1, ctf), pool1.claimableRao + ctf.claimableRao, gasKey, 0.124),
            snLedgerClaimDialog(SnLedgerLoad.Loaded(listOf(pool1, ctf)), gasKey, 0.124, 0)
        )
    }

    @Test
    fun `with every account under the floor the dialog offers no claim and asks for no gas`() {
        // CTF-6: no claim the runtime would refuse, alone or beside another pool
        val small = pool1.copy(claimableRao = 48_025_660)
        for (accounts in listOf(listOf(small), listOf(small, ctf))) {
            val ready = snLedgerClaimDialog(SnLedgerLoad.Loaded(accounts), gasKey, 0.001, 100_000_000)
            assertEquals(SnLedgerClaimDialogState.Ready(accounts, 0, gasKey, 0.001, transferFloorRao = 100_000_000), ready)
            assertEquals(emptyList<SnLedgerAccountState>(), (ready as SnLedgerClaimDialogState.Ready).takes)
        }
        // and the flow sends nothing
        val source = sourceWith(small, ctf)
        val flow = flowFor(source, transferFloorRao = 100_000_000)
        flow.open()
        assertEquals(SnLedgerClaimDialogState.Ready(listOf(small, ctf), 0, gasKey, 0.124, transferFloorRao = 100_000_000), flow.dialog.value)
        flow.claim()
        assertEquals(0, source.claims)
    }

    @Test
    fun `a claim lists the account under the floor as the dialog did`() {
        // CTF-6: it starts Below the claim minimum, not Pending; the sdk's preflight still
        // decides, so a price that moved lets the sdk claim it after all
        val floor = ctf.claimableRao + 1
        val rows = snLedgerClaimStart(listOf(pool1, ctf), transferFloorRao = floor)
        val waiting = pending(ctf).copy(status = SnLedgerClaimStatus.UNDER_FLOOR)
        assertEquals(listOf(pending(pool1), waiting), rows)
        assertEquals(
            listOf(pending(pool1), waiting.copy(status = SnLedgerClaimStatus.SENT, txHash = txHash1)),
            snLedgerClaimProgressAfter(rows, SnLedgerClaimEvent.Sent(ctfNoId, 3, txHash1))
        )
        // the runtime's refusal keeps it waiting, now with the sdk's word
        val message = "not_claimable: AmountTooLow: the claim is below the runtime's transfer floor"
        assertEquals(
            listOf(pending(pool1), waiting.copy(message = message)),
            snLedgerClaimProgressAfter(rows, SnLedgerClaimEvent.Failed(ctfNoId, 3, message))
        )
        // its pool unreadable at the claim's read: unavailable, as a pending row would be
        assertEquals(
            listOf(pending(pool1), waiting.copy(status = SnLedgerClaimStatus.POOL_UNAVAILABLE, message = "ledger_unavailable: 502")),
            snLedgerClaimProgressAfter(rows, SnLedgerClaimEvent.Failed(ctfNoId, 0, "ledger_unavailable: 502"))
        )
        // the flow starts its rows the same way
        val source = sourceWith(pool1, ctf)
        val flow = flowFor(source, transferFloorRao = floor)
        flow.open()
        flow.claim()
        assertEquals(1, source.claims)
        assertEquals(SnLedgerClaimDialogState.Sending(rows), flow.dialog.value)
        source.onEvent(SnLedgerClaimEvent.Sent(1, 42, txHash1))
        source.onEvent(SnLedgerClaimEvent.Confirmed(1, 42, txHash1, pool1.claimableRao))
        source.onEvent(SnLedgerClaimEvent.Failed(ctfNoId, 3, message))
        source.onEvent(SnLedgerClaimEvent.Done)
        assertEquals(
            SnLedgerClaimDialogState.Finished(
                listOf(pending(pool1).copy(status = SnLedgerClaimStatus.CONFIRMED, txHash = txHash1), waiting.copy(message = message)),
                pool1.claimableRao,
            ),
            flow.dialog.value
        )
    }

    @Test
    fun `done with the accounts it could take short of gas is the top-up, whatever waits`() = runBlocking {
        val short = pending(pool1).copy(status = SnLedgerClaimStatus.STILL_CLAIMABLE, message = "needs_gas: balance 0")
        val floor = pending(ctf).copy(status = SnLedgerClaimStatus.UNDER_FLOOR, message = "not_claimable: AmountTooLow: x")
        val unavailable = SnLedgerClaimProgress(2, 0, 0, SnLedgerClaimStatus.POOL_UNAVAILABLE, null, "ledger_unavailable: x")
        assertEquals(
            SnLedgerClaimDialogState.NeedsGas(gasKey, 0.0004, pool1.claimableRao),
            snLedgerClaimFinished(listOf(short, floor, unavailable), gasKey) { 0.0004 }
        )
        // nothing the claim could take: the rows, not the top-up
        assertEquals(
            SnLedgerClaimDialogState.Finished(listOf(floor, unavailable), 0),
            snLedgerClaimFinished(listOf(floor, unavailable), gasKey) { error("no gas read") }
        )
    }

    @Test
    fun `the dialog lists the pools that could not be read beside the accounts`() {
        // CTF-2: the total leaves them out
        val unavailable = listOf(SnLedgerPool(ctfNoId))
        assertEquals(
            SnLedgerClaimDialogState.Ready(listOf(pool1), pool1.claimableRao, gasKey, 0.124, unavailable),
            snLedgerClaimDialog(SnLedgerLoad.Loaded(listOf(pool1), unavailable), gasKey, 0.124)
        )
        // nothing readable to claim: the dialog still names them, and asks for no gas
        assertEquals(
            SnLedgerClaimDialogState.Ready(emptyList(), 0, gasKey, 0.001, unavailable),
            snLedgerClaimDialog(SnLedgerLoad.Loaded(emptyList(), unavailable), gasKey, 0.001)
        )
    }

    @Test
    fun `a read keeps the pools that could not be read`() = runBlocking {
        val source = FakeSource(Result.success(listOf(pool1)), key = gasKey, unavailablePools = listOf(SnLedgerPool(ctfNoId)))
        val flow = flowFor(source)
        flow.refresh()
        assertEquals(SnLedgerLoad.Loaded(listOf(pool1), listOf(SnLedgerPool(ctfNoId))), flow.load.value)
    }

    // ---- the gas key's largest-first claim

    @Test
    fun `an account the gas key cannot pay for is still claimable, not failed`() {
        // CTF-7: the sdk says needs_gas before it sends anything, so the account keeps its balance
        val message = "needs_gas: this claim needs up to 0.0143 TAO of gas, the gas key 0xab has 0.0040; send TAO to 5F"
        val still = pending(ctf).copy(status = SnLedgerClaimStatus.STILL_CLAIMABLE, message = message)
        assertEquals(listOf(still), snLedgerClaimProgressAfter(listOf(pending(ctf)), SnLedgerClaimEvent.Failed(ctfNoId, 3, message)))
        // after a batch that did not attempt it, the claim alone it was owed was not sent either
        val sent = pending(ctf).copy(status = SnLedgerClaimStatus.SENT, txHash = txHash1)
        assertEquals(
            listOf(sent.copy(status = SnLedgerClaimStatus.STILL_CLAIMABLE, message = message)),
            snLedgerClaimProgressAfter(listOf(sent), SnLedgerClaimEvent.Failed(ctfNoId, 3, message))
        )
        // a needs_gas about no one account leaves every row not sent claimable too
        assertEquals(
            listOf(pending(pool1).copy(status = SnLedgerClaimStatus.STILL_CLAIMABLE, message = "needs_gas: x"), still),
            snLedgerClaimProgressAfter(listOf(pending(pool1), still), SnLedgerClaimEvent.Failed(0, 0, "needs_gas: x"))
        )
        // it reads Claimable, with the top-up's hint under it, and no claim's status
        assertEquals(R.string.sn_ledger_claimable, snLedgerClaimRowNoteRes(SnLedgerClaimStatus.STILL_CLAIMABLE))
        assertEquals(R.string.sn_ledger_pool_under_floor, snLedgerClaimRowNoteRes(SnLedgerClaimStatus.UNDER_FLOOR))
        assertEquals(R.string.sn_ledger_pool_unavailable, snLedgerClaimRowNoteRes(SnLedgerClaimStatus.POOL_UNAVAILABLE))
        for (status in listOf(SnLedgerClaimStatus.PENDING, SnLedgerClaimStatus.SENT, SnLedgerClaimStatus.CONFIRMED,
            SnLedgerClaimStatus.ALREADY_CLAIMED, SnLedgerClaimStatus.FAILED)) {
            assertNull(status.name, snLedgerClaimRowNoteRes(status))
        }
        assertTrue(snLedgerClaimNeedsGas(message))
        assertFalse(snLedgerClaimNeedsGas("claim_failed: needs_gas"))
        assertFalse(snLedgerClaimNeedsGas(null))
    }

    @Test
    fun `the largest account claimed alone leaves the other still claimable`() {
        // CTF-7: the gas key pays for one claim but not the batch: the sdk claims the largest,
        // the pool's account, alone, and capture the flag's waits for gas with its balance
        val source = sourceWith(pool1, ctf)
        val flow = flowFor(source)
        flow.open()
        flow.claim()
        val message = "needs_gas: this claim needs up to 0.0143 TAO of gas, the gas key 0xab has 0.0040; send TAO to 5F"
        source.onEvent(SnLedgerClaimEvent.Sent(1, 42, txHash1))
        source.onEvent(SnLedgerClaimEvent.Confirmed(1, 42, txHash1, pool1.claimableRao))
        source.onEvent(SnLedgerClaimEvent.Failed(ctfNoId, 3, message))
        source.accounts = Result.success(listOf(pool1.copy(claimableRao = 0, paidRao = 5_120_000_000), ctf))
        source.onEvent(SnLedgerClaimEvent.Done)
        assertEquals(
            SnLedgerClaimDialogState.Finished(
                listOf(
                    pending(pool1).copy(status = SnLedgerClaimStatus.CONFIRMED, txHash = txHash1),
                    pending(ctf).copy(status = SnLedgerClaimStatus.STILL_CLAIMABLE, message = message),
                ),
                pool1.claimableRao,
            ),
            flow.dialog.value
        )
        // the tile, read again, still offers capture the flag's account
        assertEquals(SnLedgerLoad.Loaded(source.accounts.getOrThrow()), flow.load.value)
        assertTrue(snLedgerTile(flow.load.value, claimConfigured = true)!!.claimEnabled)
    }

    // ---- capture the flag's wins after a claim: every app reads them at Done and again
    // about 30 s, 60 s and 120 s after it, when the claim paid an account

    // a provider slot won in Iceland, which capture the flag's account holds
    private val win = SnCtfWinState(Sdk.SnCtfRoleProvider, "is")

    /** Claims the pool's account and capture the flag's in one batch, both confirmed; Done is the test's. */
    private fun claimBothConfirmed(source: FakeSource, flow: SnLedgerFlow) {
        flow.open()
        flow.claim()
        source.onEvent(SnLedgerClaimEvent.Sent(1, 42, txHash1))
        source.onEvent(SnLedgerClaimEvent.Sent(ctfNoId, 3, txHash1))
        source.onEvent(SnLedgerClaimEvent.Confirmed(1, 42, txHash1, pool1.claimableRao))
        source.onEvent(SnLedgerClaimEvent.Confirmed(ctfNoId, 3, txHash1, ctf.claimableRao))
    }

    @Test
    fun `the wins a claim paid are read again until the server reads them claimed`() {
        // the sdk's Done comes once the claim is in a block, but the server says a win is claimed
        // only once vault2's claimed at the finalized head covers it: its first answers after the
        // claim still list the win, and the line stays until a later read
        assertEquals(listOf(30_000L, 60_000L, 120_000L), SN_CTF_WINS_READS_AFTER_CLAIM_MS)
        val source = sourceWith(pool1, ctf)
        source.winAnswers = listOf(listOf(win), listOf(win), listOf(win), emptyList())
        val clock = Clock()
        val flow = flowFor(source, clock = clock)
        // the page's read, before the claim
        runBlocking { flow.refreshWins() }
        assertEquals(listOf(win), flow.wins.value)
        claimBothConfirmed(source, flow)
        assertEquals(1, source.winReads)
        // at Done: still unclaimed at the finalized head
        source.onEvent(SnLedgerClaimEvent.Done)
        assertEquals(2, source.winReads)
        assertEquals(listOf(win), flow.wins.value)
        assertEquals(listOf(30_000L), clock.waits)
        // 30 s after Done: still unclaimed
        clock.next()
        assertEquals(3, source.winReads)
        assertEquals(listOf(win), flow.wins.value)
        // 60 s: the finalized head covers the claim, and the line goes
        clock.next()
        assertEquals(4, source.winReads)
        assertEquals(emptyList<SnCtfWinState>(), flow.wins.value)
        // 120 s: the last read
        clock.next()
        assertEquals(5, source.winReads)
        assertEquals(emptyList<SnCtfWinState>(), flow.wins.value)
        assertEquals(SN_CTF_WINS_READS_AFTER_CLAIM_MS, clock.waits.runningReduce { at, wait -> at + wait })
        // and none after it
        clock.next()
        assertEquals(5, source.winReads)
        assertEquals(3, clock.waits.size)
    }

    @Test
    fun `an account another claim paid first counts as paid too`() {
        // already_claimed: vault2 paid the same coldkey, maybe just now, so its wins may lag too
        val source = sourceWith(pool1, ctf)
        source.winAnswers = listOf(listOf(win), emptyList())
        val clock = Clock()
        val flow = flowFor(source, clock = clock)
        flow.open()
        flow.claim()
        source.onEvent(SnLedgerClaimEvent.Failed(ctfNoId, 3, "already_claimed: NothingToClaim: another claim paid this account first"))
        source.onEvent(SnLedgerClaimEvent.Failed(1, 42, "claim_failed: the claim transaction reverted"))
        source.onEvent(SnLedgerClaimEvent.Done)
        assertEquals(listOf(win), flow.wins.value)
        clock.next()
        assertEquals(emptyList<SnCtfWinState>(), flow.wins.value)
        assertEquals(2, source.winReads)
        // what counts as paid
        val row = pending(pool1)
        assertTrue(snLedgerClaimPaid(listOf(row.copy(status = SnLedgerClaimStatus.FAILED), row.copy(status = SnLedgerClaimStatus.CONFIRMED))))
        assertTrue(snLedgerClaimPaid(listOf(row.copy(status = SnLedgerClaimStatus.ALREADY_CLAIMED))))
        for (status in SnLedgerClaimStatus.values().toList() - SnLedgerClaimStatus.CONFIRMED - SnLedgerClaimStatus.ALREADY_CLAIMED) {
            assertFalse(status.name, snLedgerClaimPaid(listOf(row.copy(status = status))))
        }
        assertFalse(snLedgerClaimPaid(emptyList()))
    }

    @Test
    fun `a claim that paid nothing reads the wins once, at Done`() {
        val source = sourceWith(pool1, ctf)
        source.winAnswers = listOf(listOf(win))
        val clock = Clock()
        val flow = flowFor(source, clock = clock)
        flow.open()
        flow.claim()
        source.onEvent(SnLedgerClaimEvent.Failed(0, 0, "needs_gas: this claim needs up to 0.0143 TAO of gas"))
        source.onEvent(SnLedgerClaimEvent.Done)
        assertEquals(1, source.winReads)
        assertEquals(listOf(win), flow.wins.value)
        assertEquals(emptyList<Long>(), clock.waits)
    }

    @Test
    fun `a claim that paid nothing leaves the reads an earlier claim that paid is owed`() {
        // a second claim 10 s after one that paid, failing before it reaches any account
        val source = sourceWith(pool1, ctf)
        source.winAnswers = listOf(listOf(win), listOf(win), listOf(win), emptyList())
        val clock = Clock()
        val flow = flowFor(source, clock = clock)
        claimBothConfirmed(source, flow)
        source.onEvent(SnLedgerClaimEvent.Done)
        assertEquals(1, source.winReads)
        flow.open()
        flow.claim()
        source.onEvent(SnLedgerClaimEvent.Failed(0, 0, "chain_rpc_unreachable: dial tcp: i/o timeout"))
        source.onEvent(SnLedgerClaimEvent.Done)
        // its own read at Done, and no schedule of its own
        assertEquals(2, source.winReads)
        assertEquals(listOf(30_000L), clock.waits)
        // the first claim's reads at 30, 60 and 120 s go on: the line goes at 60 s
        clock.next()
        assertEquals(3, source.winReads)
        assertEquals(listOf(win), flow.wins.value)
        clock.next()
        assertEquals(4, source.winReads)
        assertEquals(emptyList<SnCtfWinState>(), flow.wins.value)
        clock.next()
        assertEquals(5, source.winReads)
        assertEquals(listOf(30_000L, 30_000L, 60_000L), clock.waits)
    }

    @Test
    fun `a slow read moves none of the reads after a claim`() {
        // the route's answer can take up to the sdk's 60 s timeout: the reads stay 30, 60 and
        // 120 s after Done, each on its own
        val source = sourceWith(pool1, ctf)
        source.winAnswers = listOf(listOf(win), emptyList())
        val clock = Clock()
        val flow = flowFor(source, clock = clock)
        claimBothConfirmed(source, flow)
        val gate = CompletableDeferred<Unit>()
        source.winGate = gate
        source.onEvent(SnLedgerClaimEvent.Done)
        // the read at Done is still out, and the wait for the read at 30 s has begun
        assertEquals(0, source.winReads)
        assertEquals(listOf(30_000L), clock.waits)
        clock.next()
        assertEquals(0, source.winReads)
        assertEquals(listOf(30_000L, 30_000L), clock.waits)
        // both answer, the later one finding the win claimed
        gate.complete(Unit)
        assertEquals(2, source.winReads)
        assertEquals(emptyList<SnCtfWinState>(), flow.wins.value)
        clock.next()
        clock.next()
        assertEquals(4, source.winReads)
        assertEquals(listOf(30_000L, 30_000L, 60_000L), clock.waits)
    }

    @Test
    fun `signing out or another device stops the reads after a claim`() {
        val source = sourceWith(pool1, ctf)
        source.winAnswers = listOf(listOf(win))
        val clock = Clock()
        val flow = flowFor(source, clock = clock)
        claimBothConfirmed(source, flow)
        source.onEvent(SnLedgerClaimEvent.Done)
        assertEquals(1, source.winReads)
        assertEquals(listOf(win), flow.wins.value)
        // signed out: no lines, and the departed device's wins are not read again
        flow.reset(NoProtocolSource)
        assertEquals(emptyList<SnCtfWinState>(), flow.wins.value)
        repeat(3) { clock.next() }
        assertEquals(1, source.winReads)
        assertEquals(emptyList<SnCtfWinState>(), flow.wins.value)
        // signed in again and claimed, then another device: the claiming device's wins are not
        // read again, and the other's are read only by its own refresh
        flow.reset(source)
        claimBothConfirmed(source, flow)
        source.onEvent(SnLedgerClaimEvent.Done)
        assertEquals(2, source.winReads)
        val other = sourceWith(pool2)
        flow.reset(other)
        repeat(3) { clock.next() }
        assertEquals(2, source.winReads)
        assertEquals(0, other.winReads)
        assertEquals(emptyList<SnCtfWinState>(), flow.wins.value)
    }

    @Test
    fun `a read of the wins that answers after the device changed draws no line`() {
        // the page's read, still out when the user signs out
        val source = sourceWith(pool1)
        source.winAnswers = listOf(listOf(win))
        val gate = CompletableDeferred<Unit>()
        source.winGate = gate
        val flow = flowFor(source)
        CoroutineScope(Dispatchers.Unconfined).launch { flow.refreshWins() }
        flow.reset(NoProtocolSource)
        gate.complete(Unit)
        assertEquals(1, source.winReads)
        assertEquals(emptyList<SnCtfWinState>(), flow.wins.value)
    }

    @Test
    fun `a claim's Done reads the wins of the device that claimed, the dialog closed or not, and the next Done starts over`() {
        val source = sourceWith(pool1, ctf)
        val clock = Clock()
        val flow = flowFor(source, clock = clock)
        claimBothConfirmed(source, flow)
        assertEquals(0, source.winReads)
        source.onEvent(SnLedgerClaimEvent.Done)
        assertEquals(1, source.winReads)
        assertTrue(flow.dialog.value is SnLedgerClaimDialogState.Finished)
        // a closed dialog's claim reads them too, and its Done starts the reads over: the first
        // claim's stop, and the second's run in full
        claimBothConfirmed(source, flow)
        flow.close()
        source.onEvent(SnLedgerClaimEvent.Done)
        assertEquals(2, source.winReads)
        clock.next()
        assertEquals(3, source.winReads)
        clock.next()
        clock.next()
        assertEquals(5, source.winReads)
        assertEquals(listOf(30_000L, 30_000L, 30_000L, 60_000L), clock.waits)
        // the last device's claim does not
        flow.open()
        flow.claim()
        flow.reset(sourceWith(pool2))
        source.onEvent(SnLedgerClaimEvent.Done)
        assertEquals(5, source.winReads)
    }

    // ---- a read of the wins that fails (every app, 2026-11-12): the lines of the last read that
    // answered stay; only an answer that no longer lists a win, a sign-out or another device
    // takes a line away

    @Test
    fun `a read of the wins that fails between two that answer leaves the lines as they were`() = runBlocking {
        val kenya = SnCtfWinState(Sdk.SnCtfRoleExtender, "ke")
        val source = sourceWith(pool1, ctf)
        source.winAnswers = listOf(listOf(win, kenya), null, listOf(win))
        val flow = flowFor(source)
        flow.refreshWins()
        assertEquals(listOf(win, kenya), flow.wins.value)
        // the route answered 503 (the sdk's ctf_unavailable): nothing changes
        flow.refreshWins()
        assertEquals(listOf(win, kenya), flow.wins.value)
        // the next answer no longer lists the Kenya slot, and its line goes
        flow.refreshWins()
        assertEquals(listOf(win), flow.wins.value)
        assertEquals(3, source.winReads)
    }

    @Test
    fun `the first read after signing in shows no line until it answers`() = runBlocking {
        val source = sourceWith(pool1, ctf)
        source.winAnswers = listOf(listOf(win))
        val flow = flowFor(source)
        flow.refreshWins()
        assertEquals(listOf(win), flow.wins.value)
        // signed out: the lines go, with no read
        flow.reset(NoProtocolSource)
        assertEquals(emptyList<SnCtfWinState>(), flow.wins.value)
        // signed in again, and the first read fails: still no line, until a read answers
        val next = sourceWith(pool1, ctf)
        next.winAnswers = listOf(null, listOf(win))
        flow.reset(next)
        flow.refreshWins()
        assertEquals(emptyList<SnCtfWinState>(), flow.wins.value)
        flow.refreshWins()
        assertEquals(listOf(win), flow.wins.value)
    }

    @Test
    fun `the reads after a claim that fail keep the lines until one answers`() {
        // the reads at Done and 30 s after it fail; at 60 s the route answers with the win
        // claimed, and its line goes
        val source = sourceWith(pool1, ctf)
        source.winAnswers = listOf(listOf(win), null, null, emptyList())
        val clock = Clock()
        val flow = flowFor(source, clock = clock)
        runBlocking { flow.refreshWins() }
        claimBothConfirmed(source, flow)
        source.onEvent(SnLedgerClaimEvent.Done)
        assertEquals(2, source.winReads)
        assertEquals(listOf(win), flow.wins.value)
        clock.next()
        assertEquals(3, source.winReads)
        assertEquals(listOf(win), flow.wins.value)
        clock.next()
        assertEquals(4, source.winReads)
        assertEquals(emptyList<SnCtfWinState>(), flow.wins.value)
    }

    @Test
    fun `overlapping reads of the wins show the newest good answer by the order they started`() {
        // reads overlap (a refresh's beside a claim's) and their answers land in any order
        val kenya = SnCtfWinState(Sdk.SnCtfRoleExtender, "ke")
        val source = sourceWith(pool1, ctf)
        val flow = flowFor(source)
        val scope = CoroutineScope(Dispatchers.Unconfined)
        // an older read's answer that lands after a newer one's never replaces it
        source.winGateFor[0] = CompletableDeferred()
        source.winAnswerFor[0] = listOf(win, kenya)
        source.winAnswerFor[1] = listOf(win)
        scope.launch { flow.refreshWins() }
        scope.launch { flow.refreshWins() }
        assertEquals(listOf(win), flow.wins.value)
        source.winGateFor.getValue(0).complete(Unit)
        assertEquals(listOf(win), flow.wins.value)
        // a newer read that fails never keeps an older read's good answer, still to land, from showing
        source.winGateFor[2] = CompletableDeferred()
        source.winAnswerFor[2] = emptyList()
        source.winAnswerFor[3] = null
        scope.launch { flow.refreshWins() }
        scope.launch { flow.refreshWins() }
        assertEquals(listOf(win), flow.wins.value)
        source.winGateFor.getValue(2).complete(Unit)
        assertEquals(emptyList<SnCtfWinState>(), flow.wins.value)
        // a read from before a sign-out or another device shows nowhere after it
        source.winGateFor[4] = CompletableDeferred()
        source.winAnswerFor[4] = listOf(kenya)
        scope.launch { flow.refreshWins() }
        flow.reset(source)
        source.winGateFor.getValue(4).complete(Unit)
        assertEquals(emptyList<SnCtfWinState>(), flow.wins.value)
        assertEquals(5, source.winReads)
    }

    @Test
    fun `a slow read at Done that still lists a paid win never brings its line back`() {
        // the read at Done waits on a slow route; the read 30 s after Done finds the win claimed,
        // and the read at Done's late answer, older, changes nothing
        val source = sourceWith(pool1, ctf)
        source.winAnswers = listOf(listOf(win))
        val clock = Clock()
        val flow = flowFor(source, clock = clock)
        runBlocking { flow.refreshWins() }
        assertEquals(listOf(win), flow.wins.value)
        claimBothConfirmed(source, flow)
        source.winGateFor[1] = CompletableDeferred()
        source.winAnswerFor[1] = listOf(win)
        source.winAnswerFor[2] = emptyList()
        source.onEvent(SnLedgerClaimEvent.Done)
        clock.next()
        assertEquals(emptyList<SnCtfWinState>(), flow.wins.value)
        source.winGateFor.getValue(1).complete(Unit)
        assertEquals(emptyList<SnCtfWinState>(), flow.wins.value)
        assertEquals(3, source.winReads)
    }

    @Test
    fun `the gas key paying for none of them is the top-up, whatever order the sdk tried`() = runBlocking {
        // CTF-7: the largest first, then the other: both short of gas is the top-up for both
        val rows = listOf(pending(pool1), pending(ctf))
        val message = "needs_gas: this claim needs up to 0.0143 TAO of gas"
        val after = snLedgerClaimProgressAfter(
            snLedgerClaimProgressAfter(rows, SnLedgerClaimEvent.Failed(1, 42, message)),
            SnLedgerClaimEvent.Failed(ctfNoId, 3, message),
        )
        assertEquals(
            SnLedgerClaimDialogState.NeedsGas(gasKey, 0.004, pool1.claimableRao + ctf.claimableRao),
            snLedgerClaimFinished(after, gasKey) { 0.004 }
        )
    }

    @Test
    fun `the top-up at Done counts no account the claim listed under the floor`() {
        // CTF-6, CTF-7: the dialog lists capture the flag's account under the floor and its total,
        // the claim's amount, leaves it out; the sdk tries it all the same, and the gas key can pay
        // for neither. The top-up's total leaves it out too, by the floor the claim started with
        val message = "needs_gas: this claim needs up to 0.0143 TAO of gas, the gas key 0xab has 0.0040; send TAO to 5F"
        val floor = 100_000_000L
        assertTrue(snLedgerUnderFloor(ctf.claimableRao, floor))
        assertFalse(snLedgerUnderFloor(pool1.claimableRao, floor))
        val rows = listOf(
            pending(pool1).copy(status = SnLedgerClaimStatus.STILL_CLAIMABLE, message = message),
            pending(ctf).copy(status = SnLedgerClaimStatus.STILL_CLAIMABLE, message = message),
        )
        assertEquals(
            SnLedgerClaimDialogState.NeedsGas(gasKey, 0.004, pool1.claimableRao),
            runBlocking { snLedgerClaimFinished(rows, gasKey, floor) { 0.004 } }
        )
        // without a floor every account counts, as before
        assertEquals(
            SnLedgerClaimDialogState.NeedsGas(gasKey, 0.004, pool1.claimableRao + ctf.claimableRao),
            runBlocking { snLedgerClaimFinished(rows, gasKey) { 0.004 } }
        )

        // through the flow, while the relay's floor moves after the claim starts
        var relayFloor = floor
        val source = sourceWith(pool1, ctf)
        val flow = SnLedgerFlow(CoroutineScope(Dispatchers.Unconfined), { true }, { relayFloor }, Clock()::sleep)
            .also { it.reset(source) }
        flow.open()
        val ready = flow.dialog.value as SnLedgerClaimDialogState.Ready
        assertEquals(pool1.claimableRao, ready.totalRao)
        flow.claim()
        assertEquals(
            SnLedgerClaimDialogState.Sending(listOf(pending(pool1), pending(ctf).copy(status = SnLedgerClaimStatus.UNDER_FLOOR))),
            flow.dialog.value
        )
        relayFloor = 0
        source.gasTao = 0.004
        source.onEvent(SnLedgerClaimEvent.Failed(1, 42, message))
        source.onEvent(SnLedgerClaimEvent.Failed(ctfNoId, 3, message))
        source.onEvent(SnLedgerClaimEvent.Done)
        assertEquals(SnLedgerClaimDialogState.NeedsGas(gasKey, 0.004, pool1.claimableRao), flow.dialog.value)
    }

    @Test
    fun `no top-up at Done when the only accounts short of gas are ones the claim could not take`() {
        // pool 1, above the floor, could not be read at the claim, or the runtime refused it under
        // its own floor; capture the flag's account, listed under the floor, was tried and found
        // short of gas. Nothing the claim could take is left to top up for: the rows say what each
        // is, never a top-up of 0
        val floor = 100_000_000L
        val message = "needs_gas: this claim needs up to 0.0143 TAO of gas, the gas key 0xab has 0.0040; send TAO to 5F"
        val short = pending(ctf).copy(status = SnLedgerClaimStatus.STILL_CLAIMABLE, message = message)
        for (first in listOf(
            pending(pool1).copy(status = SnLedgerClaimStatus.POOL_UNAVAILABLE, message = "ledger_unavailable: x"),
            pending(pool1).copy(status = SnLedgerClaimStatus.UNDER_FLOOR, message = "not_claimable: AmountTooLow: x"),
        )) {
            assertEquals(
                first.status.name,
                SnLedgerClaimDialogState.Finished(listOf(first, short), 0),
                runBlocking { snLedgerClaimFinished(listOf(first, short), gasKey, floor) { error("no gas read") } }
            )
        }
        // an account the claim paid, even one listed under the floor, is gas the key paid for:
        // the rows, with the one it could not pay for still claimable
        val paid = pending(ctf).copy(status = SnLedgerClaimStatus.CONFIRMED, txHash = txHash1)
        val stillShort = pending(pool1).copy(status = SnLedgerClaimStatus.STILL_CLAIMABLE, message = message)
        assertEquals(
            SnLedgerClaimDialogState.Finished(listOf(stillShort, paid), ctf.claimableRao),
            runBlocking { snLedgerClaimFinished(listOf(stillShort, paid), gasKey, floor) { error("no gas read") } }
        )
    }

    @Test
    fun `the top-up for an account of unknown amount has no figure to show`() {
        // capture the flag's pool could not be read when the dialog opened; the sdk's own read found
        // its account and the gas key could not pay for it, and the runtime refused the pool's
        // account under its floor: the top-up waits for an account whose amount the dialog never
        // read, so its total is 0, which NeedsGasContent shows as no figure (SnLedgerWiringTest)
        val message = "needs_gas: this claim needs up to 0.0143 TAO of gas"
        val start = snLedgerClaimStart(listOf(pool1), listOf(SnLedgerPool(ctfNoId)))
        val after = listOf(
            SnLedgerClaimEvent.Failed(1, 42, "not_claimable: AmountTooLow: x"),
            SnLedgerClaimEvent.Failed(ctfNoId, 3, message),
        ).fold(start) { rows, event -> snLedgerClaimProgressAfter(rows, event) }
        assertFalse(after.last().amountKnown)
        assertEquals(
            SnLedgerClaimDialogState.NeedsGas(gasKey, 0.004, 0),
            runBlocking { snLedgerClaimFinished(after, gasKey) { 0.004 } }
        )
    }
}
