package com.bringyour.network.ui.wallet

import com.bringyour.network.R
import com.bringyour.sdk.Sdk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
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
        // the v1 claims asked for (SnClaim), and the last one's callback
        var epochClaims = 0
        var onEpochEvent: (ClaimEvent) -> Unit = {}
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
        // the client read n's answer is for, by the order the reads started: one client's
        // answer (the sdk's fallback to the client token, or a server before the network's
        // answer); unset, the network's ("")
        val winClientFor = mutableMapOf<Int, String>()
        // the ledger reads by the order they started, from 0: read n with a gate in
        // ledgerGateFor answers what the ledger held as it started, once the gate opens
        var ledgerStarts = 0
        val ledgerGateFor = mutableMapOf<Int, CompletableDeferred<Unit>>()
        // when set, the next gas balance read waits for it
        var gasGate: CompletableDeferred<Unit>? = null

        override val available: Boolean = true
        override suspend fun ledgerAccounts(): Result<SnLedgerAccounts> {
            val started = ledgerStarts++
            ledgerGateFor[started]?.let { gate ->
                // the ledger answered as the read started; the answer is on its way
                val answer = accounts.map { SnLedgerAccounts(it, unavailablePools) }
                gate.await()
                reads += 1
                return answer
            }
            readGate?.await()
            reads += 1
            return accounts.map { SnLedgerAccounts(it, unavailablePools) }
        }
        override fun ledgerClaimConfigured(): Boolean = true
        override fun claim(epochs: List<Long>, onEvent: (ClaimEvent) -> Unit) {
            epochClaims += 1
            onEpochEvent = onEvent
        }
        override fun ledgerClaim(onEvent: (SnLedgerClaimEvent) -> Unit) {
            claims += 1
            this.onEvent = onEvent
        }
        override fun gasKey(): SnGasKeyState? = key
        override suspend fun gasBalance(): Result<SnGasBalanceState> {
            val tao = gasTao
            gasGate?.let { gate ->
                gasGate = null
                gate.await()
            }
            return Result.success(SnGasBalanceState("0", tao))
        }
        override suspend fun ctfWins(): Result<SnCtfWinsAnswer> {
            val started = winStarts++
            winGate?.await()
            winGateFor[started]?.await()
            val answer = if (started in winAnswerFor) winAnswerFor[started] else winAnswers[minOf(winReads, winAnswers.size - 1)]
            winReads += 1
            return answer?.let { Result.success(SnCtfWinsAnswer(it, winClientFor[started] ?: "")) }
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
        CoroutineScope(Dispatchers.Unconfined).let { scope ->
            SnLedgerFlow(scope, { hasWallet }, { transferFloorRao }, SnClaimHolder(scope, clock::sleep)).also { it.reset(source) }
        }

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
        // with what it moves, as its Claim named it
        val moving = pool1.claimableRao + pool2.claimableRao
        assertEquals(SnLedgerClaimDialogState.Sending(listOf(pending(pool1), pending(pool2)), moving), flow.dialog.value)

        source.onEvent(SnLedgerClaimEvent.Sent(1, 42, txHash1))
        source.onEvent(SnLedgerClaimEvent.Confirmed(1, 42, txHash1, 4_120_000_000))
        source.onEvent(SnLedgerClaimEvent.Sent(2, 7, txHash2))
        source.onEvent(SnLedgerClaimEvent.Failed(2, 7, "claim_failed: TooManyStakingHotkeys"))
        val rows = listOf(
            pending(pool1).copy(status = SnLedgerClaimStatus.CONFIRMED, txHash = txHash1),
            pending(pool2).copy(status = SnLedgerClaimStatus.FAILED, txHash = txHash2, message = "claim_failed: TooManyStakingHotkeys"),
        )
        assertEquals(SnLedgerClaimDialogState.Sending(rows, moving), flow.dialog.value)

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
            SnLedgerClaimDialogState.Sending(listOf(pending(pool1).copy(status = SnLedgerClaimStatus.SENT, txHash = txHash1)), pool1.claimableRao),
            flow.dialog.value
        )
        assertEquals(readsBefore, source.reads)
        flow.claim()
        flow.retry()
        assertEquals(1, source.claims)
    }

    @Test
    fun `a screen left during a claim and shown again shows it and gets its Done`() {
        // leaving Earnings closes the dialog, and a visit touches none of the flow
        // (onScreenResumed), nor the claim, the session's; a refresh while there (refreshAll)
        // reads the accounts and the wins again, which leaves the claim be too
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
        assertEquals(SnLedgerClaimDialogState.Sending(inFlight, pool1.claimableRao + ctf.claimableRao), flow.dialog.value)
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
        assertEquals(SnLedgerClaimDialogState.Sending(listOf(pending(pool1), pending(ctf)), pool1.claimableRao + ctf.claimableRao), flow.dialog.value)

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
        // the figure leaves out the pool that could not be read
        assertEquals(SnLedgerClaimDialogState.Sending(listOf(pending(pool1), unread), pool1.claimableRao), flow.dialog.value)
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
        // the figure leaves out the account under the floor
        assertEquals(SnLedgerClaimDialogState.Sending(rows, pool1.claimableRao), flow.dialog.value)
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
        // a read from before a sign-out or another device shows nowhere after it: here a sign-in
        // on the same source, which is another session
        source.winGateFor[4] = CompletableDeferred()
        source.winAnswerFor[4] = listOf(kenya)
        scope.launch { flow.refreshWins() }
        flow.reset(source, Any())
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
        val flow = CoroutineScope(Dispatchers.Unconfined).let { scope ->
            SnLedgerFlow(scope, { true }, { relayFloor }, SnClaimHolder(scope, Clock()::sleep)).also { it.reset(source) }
        }
        flow.open()
        val ready = flow.dialog.value as SnLedgerClaimDialogState.Ready
        assertEquals(pool1.claimableRao, ready.totalRao)
        flow.claim()
        assertEquals(
            SnLedgerClaimDialogState.Sending(listOf(pending(pool1), pending(ctf).copy(status = SnLedgerClaimStatus.UNDER_FLOOR)), pool1.claimableRao),
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
        // an account listed under the floor that came to anything but short of gas is the
        // claim's to show beside the one it could not pay for: paid (by this claim, or already
        // by another, as the relay's Claim now), or failed in the sdk's preflight
        val stillShort = pending(pool1).copy(status = SnLedgerClaimStatus.STILL_CLAIMABLE, message = message)
        for (other in listOf(
            pending(ctf).copy(status = SnLedgerClaimStatus.CONFIRMED, txHash = txHash1),
            pending(ctf).copy(status = SnLedgerClaimStatus.ALREADY_CLAIMED, message = "already_claimed: NothingToClaim: x"),
            pending(ctf).copy(status = SnLedgerClaimStatus.FAILED, message = "chain_rpc_error: x"),
        )) {
            val confirmedRao = if (other.status == SnLedgerClaimStatus.CONFIRMED) ctf.claimableRao else 0
            assertEquals(
                other.status.name,
                SnLedgerClaimDialogState.Finished(listOf(stillShort, other), confirmedRao),
                runBlocking { snLedgerClaimFinished(listOf(stillShort, other), gasKey, floor) { error("no gas read") } }
            )
        }
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

    // ---- a network's answer beats a client's (every app, 2026-11-12): the sdk reads with the
    // client token when the network's read fails, and a server before the network's answer
    // answers the client token alone; such an answer lists one client's wins

    @Test
    fun `the won lines take the newest good answer, and a network's over a client's`() {
        val kenya = SnCtfWinState(Sdk.SnCtfRoleExtender, "ke")
        val network = { wins: List<SnCtfWinState> -> SnCtfWinsAnswer(wins) }
        val client = { wins: List<SnCtfWinState> -> SnCtfWinsAnswer(wins, "client-a") }
        assertTrue(network(emptyList()).network)
        assertFalse(client(emptyList()).network)
        val lines = SnCtfWinsLines()
        val r1 = lines.start()
        val r2 = lines.start()
        val r3 = lines.start()
        // a client's answer shows while no network's has, and an older client's then does not
        assertTrue(lines.apply(r2, client(listOf(win))))
        assertEquals(listOf(win), lines.wins)
        assertFalse(lines.apply(r1, client(listOf(kenya))))
        // a network's answer replaces it, though its read started first
        assertTrue(lines.apply(r1, network(listOf(win, kenya))))
        assertEquals(listOf(win, kenya), lines.wins)
        // from then on a client's answer is a read that failed, newer or not
        assertFalse(lines.apply(r3, client(emptyList())))
        assertFalse(lines.apply(r3, null))
        assertEquals(listOf(win, kenya), lines.wins)
        // a newer network's answer replaces it, and an older one after it does not
        val r4 = lines.start()
        val r5 = lines.start()
        assertTrue(lines.apply(r5, network(listOf(kenya))))
        assertFalse(lines.apply(r4, network(emptyList())))
        assertEquals(listOf(kenya), lines.wins)
        // a reset: no lines, no answer of a read from before it, and a client's answer shows
        // again until a network's does
        val r6 = lines.start()
        lines.reset()
        assertEquals(emptyList<SnCtfWinState>(), lines.wins)
        assertFalse(lines.apply(r6, network(listOf(win))))
        assertTrue(lines.apply(lines.start(), client(listOf(win))))
        assertEquals(listOf(win), lines.wins)
        assertTrue(lines.apply(lines.start(), network(emptyList())))
        assertFalse(lines.apply(lines.start(), client(listOf(kenya))))
        assertEquals(emptyList<SnCtfWinState>(), lines.wins)
    }

    @Test
    fun `a client's answer after a network's leaves the network's lines alone`() = runBlocking {
        val kenya = SnCtfWinState(Sdk.SnCtfRoleExtender, "ke")
        val source = sourceWith(pool1, ctf)
        // the network's answer, with two of its clients' wins; then two reads the sdk answered
        // with this client's token, this client's win alone, then none; then the network's again
        source.winAnswers = listOf(listOf(win, kenya), listOf(win), emptyList(), listOf(kenya))
        source.winClientFor[1] = "client-a"
        source.winClientFor[2] = "client-a"
        val flow = flowFor(source)
        flow.refreshWins()
        assertEquals(listOf(win, kenya), flow.wins.value)
        flow.refreshWins()
        assertEquals(listOf(win, kenya), flow.wins.value)
        flow.refreshWins()
        assertEquals(listOf(win, kenya), flow.wins.value)
        // the network's next answer replaces its last
        flow.refreshWins()
        assertEquals(listOf(kenya), flow.wins.value)
        assertEquals(4, source.winReads)
    }

    @Test
    fun `a client's answer alone, as from a server before the network's answer, shows its lines`() = runBlocking {
        val kenya = SnCtfWinState(Sdk.SnCtfRoleExtender, "ke")
        val source = sourceWith(pool1, ctf)
        source.winAnswers = listOf(listOf(win, kenya), null, listOf(win))
        source.winClientFor[0] = "client-a"
        source.winClientFor[2] = "client-a"
        val flow = flowFor(source)
        flow.refreshWins()
        assertEquals(listOf(win, kenya), flow.wins.value)
        // a read that fails keeps them, and the client's next answer replaces them
        flow.refreshWins()
        assertEquals(listOf(win, kenya), flow.wins.value)
        flow.refreshWins()
        assertEquals(listOf(win), flow.wins.value)
    }

    @Test
    fun `a network's answer replaces a client's whichever read started first, and a sign-in starts over`() {
        val kenya = SnCtfWinState(Sdk.SnCtfRoleExtender, "ke")
        val source = sourceWith(pool1, ctf)
        val flow = flowFor(source)
        val scope = CoroutineScope(Dispatchers.Unconfined)
        // the network's read is slow; a newer read fell back to the client token and answers first
        source.winGateFor[0] = CompletableDeferred()
        source.winAnswerFor[0] = listOf(win, kenya)
        source.winAnswerFor[1] = listOf(win)
        source.winClientFor[1] = "client-a"
        scope.launch { flow.refreshWins() }
        scope.launch { flow.refreshWins() }
        assertEquals(listOf(win), flow.wins.value)
        // the network's answer lands, with every client's wins
        source.winGateFor.getValue(0).complete(Unit)
        assertEquals(listOf(win, kenya), flow.wins.value)
        // signed out and in again: the first answer, a client's, shows until a network's does
        flow.reset(NoProtocolSource)
        flow.reset(source)
        source.winAnswerFor[2] = listOf(kenya)
        source.winClientFor[2] = "client-a"
        runBlocking { flow.refreshWins() }
        assertEquals(listOf(kenya), flow.wins.value)
    }

    // ---- a claim in flight is the signed-in session's (every app, 2026-11-12): the activity can
    // end while the process lives (Back from the root on Android 8 to 11, a swipe-away while the
    // VPN service keeps the process), and its view model with it

    @Test
    fun `an activity that ends during a claim and comes back keeps the claim and gets its Done`() {
        val clock = Clock()
        val claims = SnClaimHolder(CoroutineScope(Dispatchers.Unconfined), clock::sleep)
        val device = Any()
        val source = sourceWith(pool1, ctf)
        val firstScope = CoroutineScope(Dispatchers.Unconfined)
        val first = SnLedgerFlow(firstScope, { true }, { 0 }, claims).also { it.reset(source, device) }
        first.open()
        first.claim()
        assertEquals(1, source.claims)
        source.onEvent(SnLedgerClaimEvent.Sent(1, 42, txHash1))
        // the activity ends, its view model with it; the claim goes on
        firstScope.cancel()
        source.onEvent(SnLedgerClaimEvent.Sent(ctfNoId, 3, txHash1))
        source.onEvent(SnLedgerClaimEvent.Confirmed(1, 42, txHash1, pool1.claimableRao))
        // the activity comes back: a new view model, a new source on the same device, and its
        // refresh's reads, which still list the win (the read at Done too; the read 30 s after it
        // finds it claimed)
        val next = sourceWith(pool1, ctf)
        next.winAnswers = listOf(listOf(win), listOf(win), emptyList())
        val second = SnLedgerFlow(CoroutineScope(Dispatchers.Unconfined), { true }, { 0 }, claims).also { it.reset(next, device) }
        runBlocking {
            second.refresh()
            second.refreshWins()
        }
        assertEquals(listOf(win), second.wins.value)
        // its Claim shows the claim in flight, with what it moves, and starts no other
        val inFlight = listOf(
            pending(pool1).copy(status = SnLedgerClaimStatus.CONFIRMED, txHash = txHash1),
            pending(ctf).copy(status = SnLedgerClaimStatus.SENT, txHash = txHash1),
        )
        val moving = pool1.claimableRao + ctf.claimableRao
        // the tile's Claim opens it whatever the new read says (here, still something to claim)
        assertTrue(second.claimInFlight.value)
        second.open()
        assertEquals(SnLedgerClaimDialogState.Sending(inFlight, moving), second.dialog.value)
        second.claim()
        second.retry()
        second.close()
        second.open()
        assertEquals(SnLedgerClaimDialogState.Sending(inFlight, moving), second.dialog.value)
        // the holder itself starts no second claim of the session
        assertFalse(claims.start(device, next, snLedgerClaimStart(listOf(pool1, ctf)), 0))
        assertEquals(1, source.claims)
        assertEquals(0, next.claims)
        // its last event and its Done land in the new view model: the rows, the accounts read
        // again, and the wins at Done and on the apps' schedule
        source.onEvent(SnLedgerClaimEvent.Confirmed(ctfNoId, 3, txHash1, ctf.claimableRao))
        next.accounts = Result.success(
            listOf(pool1.copy(claimableRao = 0, paidRao = 5_120_000_000), ctf.copy(claimableRao = 0, paidRao = ctf.finalizedRao))
        )
        val readsBefore = next.reads
        source.onEvent(SnLedgerClaimEvent.Done)
        assertEquals(
            SnLedgerClaimDialogState.Finished(
                listOf(inFlight[0], pending(ctf).copy(status = SnLedgerClaimStatus.CONFIRMED, txHash = txHash1)),
                moving,
            ),
            second.dialog.value
        )
        assertEquals(readsBefore + 1, next.reads)
        assertEquals(SnLedgerLoad.Loaded(next.accounts.getOrThrow()), second.load.value)
        assertFalse(second.claimInFlight.value)
        assertEquals(2, next.winReads)
        assertEquals(listOf(30_000L), clock.waits)
        clock.next()
        assertEquals(3, next.winReads)
        assertEquals(emptyList<SnCtfWinState>(), second.wins.value)
        // the ended view model heard nothing after its end and read nothing
        assertEquals(0, source.winReads)
        assertTrue(first.dialog.value is SnLedgerClaimDialogState.Sending)
        // and the next claim is a claim of its own
        next.accounts = Result.success(listOf(pool2))
        second.open()
        second.claim()
        assertEquals(1, next.claims)
    }

    @Test
    fun `a claim done while no activity is there is over, and the reads after it reach the next one`() {
        val clock = Clock()
        val claims = SnClaimHolder(CoroutineScope(Dispatchers.Unconfined), clock::sleep)
        val device = Any()
        val source = sourceWith(pool1, ctf)
        val firstScope = CoroutineScope(Dispatchers.Unconfined)
        val first = SnLedgerFlow(firstScope, { true }, { 0 }, claims).also { it.reset(source, device) }
        claimBothConfirmed(source, first)
        firstScope.cancel()
        // its Done while no activity is there: the claim is over, and the reads at Done and 30 s
        // after it find no one to read for
        source.onEvent(SnLedgerClaimEvent.Done)
        assertNull(claims.inFlight(device))
        clock.next()
        assertEquals(0, source.winReads)
        // an activity comes back 40 s after Done: its refresh reads the wins, still listing the
        // win the claim paid, and the reads 60 and 120 s after Done are its own
        val next = sourceWith(pool1.copy(claimableRao = 0, paidRao = 5_120_000_000), ctf.copy(claimableRao = 0, paidRao = ctf.finalizedRao))
        next.winAnswers = listOf(listOf(win), emptyList())
        val second = SnLedgerFlow(CoroutineScope(Dispatchers.Unconfined), { true }, { 0 }, claims).also { it.reset(next, device) }
        runBlocking { second.refreshWins() }
        assertEquals(listOf(win), second.wins.value)
        clock.next()
        assertEquals(2, next.winReads)
        assertEquals(emptyList<SnCtfWinState>(), second.wins.value)
        clock.next()
        assertEquals(3, next.winReads)
        assertEquals(listOf(30_000L, 30_000L, 60_000L), clock.waits)
        // its Claim opens on the accounts as read now: no claim is in flight
        second.open()
        assertEquals(SnLedgerClaimDialogState.Ready(emptyList(), 0, gasKey, 0.124), second.dialog.value)
    }

    @Test
    fun `a sign-out ends the claim of an activity that is gone`() {
        val clock = Clock()
        val claims = SnClaimHolder(CoroutineScope(Dispatchers.Unconfined), clock::sleep)
        val device = Any()
        val source = sourceWith(pool1, ctf)
        val firstScope = CoroutineScope(Dispatchers.Unconfined)
        val first = SnLedgerFlow(firstScope, { true }, { 0 }, claims).also { it.reset(source, device) }
        first.open()
        first.claim()
        firstScope.cancel()
        // the next activity finds no one signed in, then another account's device
        val second = SnLedgerFlow(CoroutineScope(Dispatchers.Unconfined), { true }, { 0 }, claims)
        second.reset(NoProtocolSource, null)
        assertNull(claims.inFlight(device))
        assertFalse(second.claimInFlight.value)
        val next = sourceWith(pool2)
        val other = Any()
        second.reset(next, other)
        // the departed claim's events count for nothing; the next account claims afresh
        source.onEvent(SnLedgerClaimEvent.Sent(1, 42, txHash1))
        second.open()
        assertEquals(SnLedgerClaimDialogState.Ready(listOf(pool2), pool2.claimableRao, gasKey, 0.124), second.dialog.value)
        second.claim()
        assertEquals(1, next.claims)
        // and the departed claim's late Done ends nothing of the next account's claim, nor reads
        // its wins
        source.onEvent(SnLedgerClaimEvent.Done)
        assertTrue(claims.inFlight(other) != null)
        assertEquals(SnLedgerClaimDialogState.Sending(listOf(pending(pool2)), pool2.claimableRao), second.dialog.value)
        assertEquals(0, next.winReads)
        assertEquals(emptyList<Long>(), clock.waits)
    }

    // ---- the figure while a claim runs (every app, 2026-11-12): what the claim is moving

    @Test
    fun `the figure during a claim is what it moves, never an account under the floor or unknown`() {
        val floor = 100_000_000L
        assertTrue(snLedgerUnderFloor(ctf.claimableRao, floor))
        val start = snLedgerClaimStart(listOf(pool1, ctf, pool2), listOf(SnLedgerPool(5)), floor)
        assertEquals(pool1.claimableRao + pool2.claimableRao, snLedgerClaimMovingRao(start, floor))
        // without a floor the small account moves too
        assertEquals(
            pool1.claimableRao + ctf.claimableRao + pool2.claimableRao,
            snLedgerClaimMovingRao(snLedgerClaimStart(listOf(pool1, ctf, pool2)), 0)
        )
        // an account of no known amount is no part of it, nor a pool that could not be read
        assertEquals(0, snLedgerClaimMovingRao(listOf(SnLedgerClaimProgress(9, 1, 0, SnLedgerClaimStatus.PENDING, null, null)), 0))
        assertEquals(0, snLedgerClaimMovingRao(snLedgerClaimStart(emptyList(), listOf(SnLedgerPool(5))), 0))
        // nothing it moves: no figure, never a zero
        assertEquals(0, snLedgerClaimMovingRao(snLedgerClaimStart(listOf(ctf), emptyList(), floor), floor))
        // through the flow: the claim shows what its Claim named while it runs, whatever its
        // events say of an account, and a dialog opened again shows it too
        val source = sourceWith(pool1, ctf)
        val flow = flowFor(source, transferFloorRao = floor)
        flow.open()
        val ready = flow.dialog.value as SnLedgerClaimDialogState.Ready
        flow.claim()
        assertEquals(ready.totalRao, (flow.dialog.value as SnLedgerClaimDialogState.Sending).movingRao)
        assertEquals(pool1.claimableRao, ready.totalRao)
        source.onEvent(SnLedgerClaimEvent.Sent(1, 42, txHash1))
        source.onEvent(SnLedgerClaimEvent.Sent(ctfNoId, 3, txHash1))
        source.onEvent(SnLedgerClaimEvent.Failed(1, 42, "claim_failed: the claim transaction reverted"))
        assertEquals(pool1.claimableRao, (flow.dialog.value as SnLedgerClaimDialogState.Sending).movingRao)
        flow.close()
        flow.open()
        assertEquals(pool1.claimableRao, (flow.dialog.value as SnLedgerClaimDialogState.Sending).movingRao)
        source.onEvent(SnLedgerClaimEvent.Done)
        assertTrue(flow.dialog.value is SnLedgerClaimDialogState.Finished)
    }

    // ---- the v1 claim is the session's too (every app, 2026-11-12): its dialog, dismissed and
    // opened again, and its view model, ended with its activity, lose none of it

    /** A view model's ear for the v1 claim, as EarningsViewModel's epochClaimListener. */
    private class EpochClaimEar : SnClaimHolder.Listener {
        val moved = mutableListOf<List<ClaimProgress>>()
        val done = mutableListOf<List<ClaimProgress>>()

        override fun epochClaimMoved(rows: List<ClaimProgress>) {
            moved += rows
        }

        override fun epochClaimDone(settled: List<ClaimProgress>) {
            done += settled
        }
    }

    @Test
    fun `a v1 claim's rows and its Done outlive its dialog and its view model`() {
        val claims = SnClaimHolder(CoroutineScope(Dispatchers.Unconfined))
        val device = Any()
        val source = sourceWith(pool1)
        val first = EpochClaimEar()
        val detachFirst = claims.attach(first, device)
        val rows = listOf(
            ClaimProgress(1_218, 3_241_000_000, ClaimProgressStatus.PENDING, null, null),
            ClaimProgress(1_217, 2_905_500_000, ClaimProgressStatus.PENDING, null, null),
        )
        assertTrue(claims.startEpochClaim(device, source, listOf(1_218, 1_217), rows))
        assertEquals(1, source.epochClaims)
        // a dialog opened again shows it, and starts no second claim while it is in flight
        assertEquals(rows, claims.epochClaimInFlight(device))
        assertFalse(claims.startEpochClaim(device, source, listOf(1_218), rows))
        assertEquals(1, source.epochClaims)
        source.onEpochEvent(ClaimEvent.Sent(1_218, txHash1))
        val sent = listOf(rows[0].copy(status = ClaimProgressStatus.SENT, txHash = txHash1), rows[1])
        assertEquals(listOf(sent), first.moved)
        assertEquals(sent, claims.epochClaimInFlight(device))
        // the activity ends, its view model with it; the claim goes on
        detachFirst()
        val message = "needs_gas: this claim needs up to 0.0149 TAO of gas"
        source.onEpochEvent(ClaimEvent.Confirmed(1_218, txHash1, 3_241_000_000))
        source.onEpochEvent(ClaimEvent.Failed(1_217, message))
        // a new activity's view model, of the same session, finds it in flight and hears its Done
        val second = EpochClaimEar()
        claims.attach(second, device)
        val inFlight = listOf(
            rows[0].copy(status = ClaimProgressStatus.CONFIRMED, txHash = txHash1),
            rows[1].copy(status = ClaimProgressStatus.FAILED, message = message),
        )
        assertEquals(inFlight, claims.epochClaimInFlight(device))
        source.onEpochEvent(ClaimEvent.Done)
        assertEquals(listOf(inFlight), second.done)
        assertNull(claims.epochClaimInFlight(device))
        // the ended view model heard nothing after its end
        assertEquals(1, first.moved.size)
        assertTrue(first.done.isEmpty())
        // the next claim is a claim of its own
        assertTrue(claims.startEpochClaim(device, source, listOf(1_217), listOf(rows[1])))
        assertEquals(2, source.epochClaims)
    }

    @Test
    fun `a sign-out or another device ends the v1 claim in flight`() {
        val claims = SnClaimHolder(CoroutineScope(Dispatchers.Unconfined))
        val device = Any()
        val source = sourceWith(pool1)
        val rows = listOf(ClaimProgress(1_218, 3_241_000_000, ClaimProgressStatus.PENDING, null, null))
        claims.startEpochClaim(device, source, listOf(1_218), rows)
        // the next view model finds no one signed in: the claim ends, and its events count for nothing
        val ear = EpochClaimEar()
        claims.attach(ear, null)
        assertNull(claims.epochClaimInFlight(device))
        claims.attach(ear, device)
        source.onEpochEvent(ClaimEvent.Sent(1_218, txHash1))
        source.onEpochEvent(ClaimEvent.Done)
        assertTrue(ear.moved.isEmpty() && ear.done.isEmpty())
        // another device's claim is its own, and its ear hears it; a late Done of the departed
        // claim ends nothing of it
        val departed = sourceWith(pool1)
        claims.startEpochClaim(device, departed, listOf(1_218), rows)
        val other = Any()
        claims.attach(ear, other)
        assertTrue(claims.startEpochClaim(other, source, listOf(1_218), rows))
        departed.onEpochEvent(ClaimEvent.Done)
        assertEquals(rows, claims.epochClaimInFlight(other))
        assertTrue(ear.done.isEmpty())
        source.onEpochEvent(ClaimEvent.Done)
        assertEquals(listOf(claimProgressSettled(rows)), ear.done)
    }

    // ---- one claim at a time: the gas key sends one claim at a time, so neither kind starts while
    // the other is in flight and each dialog's Claim waits for it, as on Apple, Windows and Linux

    @Test
    fun `a v1 and a v2 claim of the session never run at once`() {
        val claims = SnClaimHolder(CoroutineScope(Dispatchers.Unconfined))
        val device = Any()
        val source = sourceWith(pool1, ctf)
        val rows = listOf(ClaimProgress(1_218, 3_241_000_000, ClaimProgressStatus.PENDING, null, null))
        val ledgerRows = snLedgerClaimStart(listOf(pool1, ctf))
        assertFalse(claims.claiming(device))
        // a v2 claim in flight holds the v1 claim until its Done
        assertTrue(claims.start(device, source, ledgerRows, 0))
        assertTrue(claims.claiming(device))
        assertFalse(claims.startEpochClaim(device, source, listOf(1_218), rows))
        source.onEvent(SnLedgerClaimEvent.Sent(1, 42, txHash1))
        assertFalse(claims.startEpochClaim(device, source, listOf(1_218), rows))
        assertEquals(0, source.epochClaims)
        assertNull(claims.epochClaimInFlight(device))
        source.onEvent(SnLedgerClaimEvent.Done)
        assertFalse(claims.claiming(device))
        // a v1 claim in flight holds the v2 claim until its Done
        assertTrue(claims.startEpochClaim(device, source, listOf(1_218), rows))
        assertTrue(claims.claiming(device))
        assertFalse(claims.start(device, source, ledgerRows, 0))
        source.onEpochEvent(ClaimEvent.Sent(1_218, txHash2))
        assertFalse(claims.start(device, source, ledgerRows, 0))
        assertEquals(1, source.claims)
        assertNull(claims.inFlight(device))
        source.onEpochEvent(ClaimEvent.Done)
        assertFalse(claims.claiming(device))
        assertTrue(claims.start(device, source, ledgerRows, 0))
        assertEquals(2, source.claims)
        // no one signed in, or another session, has nothing in flight
        assertFalse(claims.claiming(null))
        assertFalse(claims.claiming(Any()))
    }

    @Test
    fun `the v2 dialog's Claim starts nothing while the session's v1 claim is in flight`() {
        val claims = SnClaimHolder(CoroutineScope(Dispatchers.Unconfined))
        val device = Any()
        val source = sourceWith(pool1, ctf)
        val flow = SnLedgerFlow(CoroutineScope(Dispatchers.Unconfined), { true }, { 0 }, claims).also { it.reset(source, device) }
        val rows = listOf(ClaimProgress(1_218, 3_241_000_000, ClaimProgressStatus.PENDING, null, null))
        assertTrue(claims.startEpochClaim(device, source, listOf(1_218), rows))
        // the tile's Claim opens the dialog on the accounts as read: no v2 claim is in flight
        flow.open()
        val ready = flow.dialog.value
        assertTrue(ready is SnLedgerClaimDialogState.Ready)
        // its Claim waits: the dialog stays as it is, and nothing is asked of the sdk
        flow.claim()
        assertEquals(ready, flow.dialog.value)
        assertEquals(0, source.claims)
        assertFalse(flow.claimInFlight.value)
        assertNull(claims.inFlight(device))
        // the v1 claim's Done frees it: the same Claim claims
        source.onEpochEvent(ClaimEvent.Done)
        flow.claim()
        assertEquals(1, source.claims)
        assertEquals(
            SnLedgerClaimDialogState.Sending(snLedgerClaimStart(listOf(pool1, ctf)), pool1.claimableRao + ctf.claimableRao),
            flow.dialog.value
        )
        assertTrue(flow.claimInFlight.value)
        // and the v1 claim waits for it in turn
        assertFalse(claims.startEpochClaim(device, source, listOf(1_218), rows))
        assertEquals(1, source.epochClaims)
    }

    @Test
    fun `a v1 claim's rows follow its events, and an epoch never reported on fails at Done`() {
        val row = ClaimProgress(1_218, 3_241_000_000, ClaimProgressStatus.PENDING, null, null)
        val other = ClaimProgress(1_217, 2_905_500_000, ClaimProgressStatus.PENDING, null, null)
        val rows = listOf(row, other)
        assertEquals(
            listOf(row.copy(status = ClaimProgressStatus.SENT, txHash = txHash1), other),
            claimProgressAfter(rows, ClaimEvent.Sent(1_218, txHash1))
        )
        // the vault's amount once confirmed
        assertEquals(
            listOf(row.copy(status = ClaimProgressStatus.CONFIRMED, txHash = txHash1, amountRao = 3_240_999_999), other),
            claimProgressAfter(rows, ClaimEvent.Confirmed(1_218, txHash1, 3_240_999_999))
        )
        assertEquals(
            listOf(row, other.copy(status = ClaimProgressStatus.FAILED, message = "claims_for_epoch_expired: ClaimExpired")),
            claimProgressAfter(rows, ClaimEvent.Failed(1_217, "claims_for_epoch_expired: ClaimExpired"))
        )
        assertEquals(rows, claimProgressAfter(rows, ClaimEvent.Done))
        val sent = row.copy(status = ClaimProgressStatus.SENT, txHash = txHash1)
        assertEquals(listOf(sent, other.copy(status = ClaimProgressStatus.FAILED)), claimProgressSettled(listOf(sent, other)))
    }

    @Test
    fun `the tile's Claim opens a claim in flight whatever the read says`() {
        // the read during the claim finds nothing left to claim: Claim still opens the claim's
        // progress, as on Linux, Apple and Windows, and starts no other
        val source = sourceWith(pool1, ctf)
        val flow = flowFor(source)
        assertFalse(flow.claimInFlight.value)
        flow.open()
        flow.claim()
        assertTrue(flow.claimInFlight.value)
        source.onEvent(SnLedgerClaimEvent.Sent(1, 42, txHash1))
        source.accounts = Result.success(listOf(pool1.copy(claimableRao = 0, paidRao = 5_120_000_000), ctf.copy(claimableRao = 0, paidRao = ctf.finalizedRao)))
        runBlocking { flow.refresh() }
        val read = snLedgerTile(flow.load.value, claimConfigured = true)!!
        assertFalse(read.claimEnabled)
        assertTrue(read.withClaimInFlight(flow.claimInFlight.value).claimEnabled)
        flow.close()
        flow.open()
        assertTrue(flow.dialog.value is SnLedgerClaimDialogState.Sending)
        // at its Done Claim follows the read again
        source.onEvent(SnLedgerClaimEvent.Done)
        assertFalse(flow.claimInFlight.value)
        assertFalse(read.withClaimInFlight(flow.claimInFlight.value).claimEnabled)
        // with no Claim to offer (before vault2's address) nothing changes
        val unconfigured = snLedgerTile(SnLedgerLoad.Loaded(listOf(pool1)), claimConfigured = false)!!
        assertEquals(unconfigured, unconfigured.withClaimInFlight(true))
        // a sign-out: no claim in flight
        flow.open()
        flow.reset(NoProtocolSource)
        assertFalse(flow.claimInFlight.value)
    }

    @Test
    fun `an activity that comes back during the sign-in keeps the session's won lines`() {
        // the lines are the session's: a view model of a new activity finds them as the last good
        // read left them, a network's answer shown before still beats a client's, and a read that
        // fails keeps them (every app, 2026-11-12)
        val kenya = SnCtfWinState(Sdk.SnCtfRoleExtender, "ke")
        val claims = SnClaimHolder(CoroutineScope(Dispatchers.Unconfined))
        val device = Any()
        val source = sourceWith(pool1, ctf)
        source.winAnswers = listOf(listOf(win, kenya))
        val firstScope = CoroutineScope(Dispatchers.Unconfined)
        val first = SnLedgerFlow(firstScope, { true }, { 0 }, claims).also { it.reset(source, device) }
        runBlocking { first.refreshWins() }
        assertEquals(listOf(win, kenya), first.wins.value)
        firstScope.cancel()
        // the new activity's view model: the lines are there before it reads
        val next = sourceWith(pool1, ctf)
        next.winAnswers = listOf(listOf(win), null, emptyList())
        next.winClientFor[0] = "client-a"
        val second = SnLedgerFlow(CoroutineScope(Dispatchers.Unconfined), { true }, { 0 }, claims).also { it.reset(next, device) }
        assertEquals(listOf(win, kenya), second.wins.value)
        // its first read fell back to the client token: the network's lines stay
        runBlocking { second.refreshWins() }
        assertEquals(listOf(win, kenya), second.wins.value)
        // a read that fails keeps them, and the network's next answer replaces them
        runBlocking { second.refreshWins() }
        assertEquals(listOf(win, kenya), second.wins.value)
        runBlocking { second.refreshWins() }
        assertEquals(emptyList<SnCtfWinState>(), second.wins.value)
        // a sign-out drops them, and a read from before it shows nowhere after it, though the
        // next sign-in reads through the same source
        val gate = CompletableDeferred<Unit>()
        next.winGate = gate
        next.winAnswerFor[3] = listOf(kenya)
        CoroutineScope(Dispatchers.Unconfined).launch { second.refreshWins() }
        second.reset(NoProtocolSource, null)
        assertEquals(emptyList<SnCtfWinState>(), second.wins.value)
        second.reset(next, Any())
        gate.complete(Unit)
        assertEquals(4, next.winReads)
        assertEquals(emptyList<SnCtfWinState>(), second.wins.value)
    }

    // ---- the reads by the order they started (every app, 2026-11-12)

    /** pool1 once the claim paid it */
    private val pool1Paid = pool1.copy(claimableRao = 0, paidRao = 5_120_000_000)

    @Test
    fun `a read from before a claim's Done that lands after the read at Done puts back nothing it paid`() {
        val source = sourceWith(pool1)
        val flow = flowFor(source)
        flow.open()
        flow.claim()
        // a refresh during the claim, slow to answer: the ledger as it was before the claim
        val before = CompletableDeferred<Unit>()
        source.ledgerGateFor[source.ledgerStarts] = before
        CoroutineScope(Dispatchers.Unconfined).launch { flow.refresh() }
        source.onEvent(SnLedgerClaimEvent.Sent(1, 42, txHash1))
        source.onEvent(SnLedgerClaimEvent.Confirmed(1, 42, txHash1, pool1.claimableRao))
        source.accounts = Result.success(listOf(pool1Paid))
        source.onEvent(SnLedgerClaimEvent.Done)
        // the read at Done: the account paid, and no Claim on the tile
        assertEquals(SnLedgerLoad.Loaded(listOf(pool1Paid)), flow.load.value)
        // the read from before the Done lands after it: nothing it paid comes back
        before.complete(Unit)
        assertEquals(SnLedgerLoad.Loaded(listOf(pool1Paid)), flow.load.value)
        assertFalse(snLedgerTile(flow.load.value, flow.claimConfigured.value)!!.claimEnabled)
        // nor in a dialog opened now
        flow.close()
        flow.open()
        assertEquals(SnLedgerClaimDialogState.Ready(emptyList(), 0, gasKey, 0.124), flow.dialog.value)
        flow.claim()
        assertEquals(1, source.claims)
    }

    @Test
    fun `the dialog's read is never replaced by a read from before the Done that lands while its gas is read`() {
        // the dialog reads the accounts and the gas side by side and draws once both answered
        val source = sourceWith(pool1)
        val flow = flowFor(source)
        flow.open()
        flow.claim()
        val before = CompletableDeferred<Unit>()
        source.ledgerGateFor[source.ledgerStarts] = before
        CoroutineScope(Dispatchers.Unconfined).launch { flow.refresh() }
        source.onEvent(SnLedgerClaimEvent.Confirmed(1, 42, txHash1, pool1.claimableRao))
        source.accounts = Result.success(listOf(pool1Paid))
        source.onEvent(SnLedgerClaimEvent.Done)
        flow.close()
        // opened again: its own read answers at once, its gas read is slow
        val gas = CompletableDeferred<Unit>()
        source.gasGate = gas
        flow.open()
        assertEquals(SnLedgerClaimDialogState.Loading, flow.dialog.value)
        // the read from before the Done lands while the gas is still read
        before.complete(Unit)
        gas.complete(Unit)
        assertEquals(SnLedgerClaimDialogState.Ready(emptyList(), 0, gasKey, 0.124), flow.dialog.value)
        flow.claim()
        assertEquals(1, source.claims)
    }

    @Test
    fun `a dialog drawn before another activity's claim ended reads again and offers nothing it paid`() {
        // two activities of the session (a notification opens a second MainActivity over the first):
        // the one below has its dialog drawn, the one on top claims
        val claims = SnClaimHolder(CoroutineScope(Dispatchers.Unconfined))
        val device = Any()
        val source = sourceWith(pool1)
        val below = SnLedgerFlow(CoroutineScope(Dispatchers.Unconfined), { true }, { 0 }, claims).also { it.reset(source, device) }
        below.open()
        assertEquals(SnLedgerClaimDialogState.Ready(listOf(pool1), pool1.claimableRao, gasKey, 0.124), below.dialog.value)
        val top = SnLedgerFlow(CoroutineScope(Dispatchers.Unconfined), { true }, { 0 }, claims).also { it.reset(source, device) }
        top.open()
        top.claim()
        assertEquals(1, source.claims)
        source.onEvent(SnLedgerClaimEvent.Confirmed(1, 42, txHash1, pool1.claimableRao))
        source.accounts = Result.success(listOf(pool1Paid))
        source.onEvent(SnLedgerClaimEvent.Done)
        // the dialog below read again: nothing left to claim, and its Claim starts nothing
        assertEquals(SnLedgerClaimDialogState.Ready(emptyList(), 0, gasKey, 0.124), below.dialog.value)
        below.claim()
        assertEquals(1, source.claims)
        // the one on top shows the claim's end
        assertTrue(top.dialog.value is SnLedgerClaimDialogState.Finished)
    }

    @Test
    fun `a dialog whose read was out when another activity's claim ended waits for the read after it`() {
        val claims = SnClaimHolder(CoroutineScope(Dispatchers.Unconfined))
        val device = Any()
        val source = sourceWith(pool1)
        val below = SnLedgerFlow(CoroutineScope(Dispatchers.Unconfined), { true }, { 0 }, claims).also { it.reset(source, device) }
        // its dialog opens on a slow read: the ledger before the claim
        val before = CompletableDeferred<Unit>()
        source.ledgerGateFor[source.ledgerStarts] = before
        below.open()
        assertEquals(SnLedgerClaimDialogState.Loading, below.dialog.value)
        val top = SnLedgerFlow(CoroutineScope(Dispatchers.Unconfined), { true }, { 0 }, claims).also { it.reset(source, device) }
        top.open()
        top.claim()
        source.onEvent(SnLedgerClaimEvent.Confirmed(1, 42, txHash1, pool1.claimableRao))
        source.accounts = Result.success(listOf(pool1Paid))
        // the reads the Done starts are slow too: below, its dialog's again and the one after
        // every claim; on top, the one after its claim
        val after = CompletableDeferred<Unit>()
        for (i in 0 until 3) {
            source.ledgerGateFor[source.ledgerStarts + i] = after
        }
        source.onEvent(SnLedgerClaimEvent.Done)
        assertEquals(SnLedgerClaimDialogState.Loading, below.dialog.value)
        // the read from before the Done lands first: no Claim from it
        before.complete(Unit)
        assertEquals(SnLedgerClaimDialogState.Loading, below.dialog.value)
        // the read after it answers
        after.complete(Unit)
        assertEquals(SnLedgerClaimDialogState.Ready(emptyList(), 0, gasKey, 0.124), below.dialog.value)
        assertEquals(SnLedgerLoad.Loaded(listOf(pool1Paid)), below.load.value)
        below.claim()
        assertEquals(1, source.claims)
    }

    @Test
    fun `the newest read decides the ledger, a failed one unavailable, whatever order they answer in`() {
        // the ledger's rule: the newest read decides, a failed one unavailable
        val source = sourceWith(pool1)
        val flow = flowFor(source)
        val first = CompletableDeferred<Unit>()
        source.ledgerGateFor[source.ledgerStarts] = first
        CoroutineScope(Dispatchers.Unconfined).launch { flow.refresh() }
        source.accounts = Result.failure(SnProtocolException("ledger_unavailable", "refused the ledger answer"))
        runBlocking { flow.refresh() }
        assertEquals(SnLedgerLoad.Unavailable, flow.load.value)
        // the older read's good answer lands after the newer read's failure: the newer decides
        first.complete(Unit)
        assertEquals(SnLedgerLoad.Unavailable, flow.load.value)
        // and the other way round: an older read's failure after a newer good answer
        val failed = CompletableDeferred<Unit>()
        source.ledgerGateFor[source.ledgerStarts] = failed
        CoroutineScope(Dispatchers.Unconfined).launch { flow.refresh() }
        source.accounts = Result.success(listOf(pool1))
        runBlocking { flow.refresh() }
        failed.complete(Unit)
        assertEquals(SnLedgerLoad.Loaded(listOf(pool1)), flow.load.value)
    }

    @Test
    fun `the reads' order, as the v1 claims read and the gas read take it`() {
        // EarningsViewModel.refreshClaims numbers each read as it starts and shows an answer only
        // through the order; a failed read keeps what shows (SnLedgerWiringTest pins the wiring:
        // the view model needs a device, which these JVM tests do not have)
        val order = SnReadOrder()
        // the dialog's own read, with no claim done yet
        val opened = order.start()
        assertTrue(order.sinceDone(opened))
        assertTrue(order.apply(opened))
        // a refresh during a claim, then the claim's Done and the read at Done
        val during = order.start()
        order.done()
        assertFalse(order.sinceDone(during))
        val atDone = order.start()
        assertTrue(order.sinceDone(atDone))
        // the read at Done answers first; the one from before lands after it: no epoch the claim
        // paid comes back, and its failure would say nothing either
        assertTrue(order.apply(atDone))
        assertFalse(order.applies(during))
        assertFalse(order.apply(during))
        // a read that fails says so while no read that started after it answered, and applies
        // nothing: an older read's good answer still to land shows
        val older = order.start()
        val newer = order.start()
        assertTrue(order.applies(newer))
        assertTrue(order.apply(older))
        assertTrue(order.applies(newer))
        assertTrue(order.apply(newer))
        assertFalse(order.applies(older))
        // another device or a sign-out: no read from before it shows
        val out = order.start()
        order.reset()
        assertFalse(order.applies(out))
        assertFalse(order.apply(out))
        val next = order.start()
        assertTrue(order.apply(next))
        // a Done marks only the reads out when it came
        order.done()
        assertFalse(order.sinceDone(next))
        assertTrue(order.sinceDone(order.start()))
    }
}
