package com.bringyour.network.ui.wallet

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Settlement v2 as wired into the app, read from the sources: the SDK's
 * classes need the native library, which these JVM tests do not have, and
 * the screen needs a device. The behavior is SnLedgerTileTest's and
 * SnLedgerClaimTest's.
 */
class SnLedgerWiringTest {

    private fun source(name: String): String =
        File("src/main/java/com/bringyour/network/ui/wallet/$name").readText()

    // the text between two markers, failing when either is missing
    private fun between(source: String, start: String, end: String): String {
        val from = source.indexOf(start)
        assertTrue("missing: $start", 0 <= from)
        val to = source.indexOf(end, from + start.length)
        assertTrue("missing after $start: $end", 0 <= to)
        return source.substring(from, to)
    }

    @Test
    fun `the sdk source reads the accounts and passes every claim event on`() {
        // UX-2, UX-3: SnLedgerAccounts; an SnError (ledger_unavailable) is a failure, never an empty list
        val sdk = source("SdkProtocolSource.kt")
        val accounts = between(sdk, "override suspend fun ledgerAccounts()", "override fun ledgerClaimConfigured()")
        assertTrue(accounts.contains("ensureChainSettings()"))
        assertTrue(accounts.contains("device.snLedgerAccounts {"))
        assertTrue(accounts.contains("result.error != null -> continuation.resume(Result.failure(result.error.toException()!!))"))
        // and the kind each pool is named by (capture the flag's, sdk SnLedgerPoolKind*)
        for (field in listOf("noId", "index", "finalizedRao", "claimableRao", "paidRao", "pending", "pendingRao", "kind")) {
            assertTrue(field, accounts.contains("$field = a.$field,"))
        }
        // a partial answer's pools that could not be read, beside the accounts, never dropped
        assertTrue(accounts.contains("unavailablePools = snLedgerUnavailablePoolsOf(result.unavailablePools),"))
        val pools = between(sdk, "private fun snLedgerUnavailablePoolsOf(", "override val available")
        assertTrue(pools.contains("noId = p.noId,"))
        assertTrue(pools.contains("kind = p.kind,"))
        // UX-5: Vault2Address configured
        assertTrue(sdk.contains("override fun ledgerClaimConfigured(): Boolean = !device.snChainSettings?.vault2Address.isNullOrBlank()"))
        // UX-4: SnLedgerClaim, each callback as its event
        val claim = between(sdk, "override fun ledgerClaim(", "override suspend fun accountEpochs()")
        assertTrue(claim.contains("device.snLedgerClaim(object : SnLedgerClaimCallback {"))
        assertTrue(claim.contains("onEvent(SnLedgerClaimEvent.Sent(noId, index, txHash ?: \"\"))"))
        assertTrue(claim.contains("onEvent(SnLedgerClaimEvent.Confirmed(noId, index, txHash ?: \"\", amountRao))"))
        assertTrue(claim.contains("onEvent(SnLedgerClaimEvent.Failed(noId, index, message ?: \"\"))"))
        assertTrue(claim.contains("onEvent(SnLedgerClaimEvent.Done)"))
    }

    @Test
    fun `the v2 tile sits under the v1 unclaimed tile, which is unchanged`() {
        // UX-1
        val screen = source("EarningsScreen.kt")
        val walletBlock = between(screen, "if (wallet != null) {\n                    Spacer", "when (top200) {")
        val unclaimed = walletBlock.indexOf("UnclaimedTile(")
        val ledger = walletBlock.indexOf("SnLedgerTileBlock(")
        assertTrue(0 <= unclaimed && unclaimed < ledger)
        val tile = between(walletBlock, "SnLedgerTileBlock(", "\n                    )")
        for (argument in listOf("tile = it,", "onClaim = onOpenLedgerClaim,", "formatAlpha = formatAlpha,")) {
            assertTrue(argument, tile.contains(argument))
        }
        // the device wallet's ledger, or for a network with no wallet here what the claim relay
        // says is claimable (BTEASY, sdk SnClaimStatus)
        assertTrue(screen.contains("ledgerTile = snLedgerTileWithRelay(wallet != null, snLedgerTile(ledgerLoad, ledgerClaimConfigured), claimStatus),"))
        assertTrue(screen.contains("onOpenLedgerClaim = { earningsViewModel.ledgerFlow.open() },"))
        val dialog = between(screen, "SnLedgerClaimDialog(", "BittensorProofSheets(")
        assertTrue(dialog.contains("onClaim = { earningsViewModel.ledgerFlow.claim() },"))
        assertTrue(dialog.contains("onRetry = { earningsViewModel.ledgerFlow.retry() },"))
        assertTrue(dialog.contains("explorerTxUrl = earningsViewModel::explorerTxUrl,"))
        // the v1 dialog still claims through SnClaim
        assertTrue(between(screen, "    ClaimDialog(", "SnLedgerClaimDialog(").contains("onClaim = { earningsViewModel.claimAll() },"))
    }

    @Test
    fun `the relay's pools, its floor and the capture the flag wins are read from the sdk`() {
        val sdk = source("SdkProtocolSource.kt")
        // each relay account's kind names its row; the pools it could not read and its
        // transfer floor in α (Below the claim minimum)
        val status = between(sdk, "private fun snClaimStatusOf(", "private fun snLedgerUnavailablePoolsOf(")
        assertTrue(status.contains("transferFloorRao = result.transferFloorRao,"))
        assertTrue(status.contains("unavailablePools = snLedgerUnavailablePoolsOf(result.unavailablePools),"))
        assertTrue(status.contains("kind = a.kind,"))
        // GET /sn/ctf/client's unclaimed wins, every one in the sdk's order, copied off the
        // gomobile objects; an error, or an answer that cannot be read, is a failure, which keeps
        // the lines drawn (every app, 2026-11-12), never an empty list. With them the client the
        // answer is for, "" for the network's: a network's answer beats a client's
        val wins = between(sdk, "override suspend fun ctfWins()", "override suspend fun accountEpochs()")
        assertTrue(wins.contains("override suspend fun ctfWins(): Result<SnCtfWinsAnswer> ="))
        assertTrue(wins.contains("device.snCtfClient { result, err ->"))
        assertTrue(wins.contains("val wins: Result<SnCtfWinsAnswer> = runCatching {"))
        assertTrue(wins.contains("err != null -> throw err"))
        assertTrue(wins.contains("result == null -> throw IllegalStateException("))
        assertTrue(wins.contains("result.error != null -> throw result.error.toException()!!"))
        assertTrue(wins.contains("else -> SnCtfWinsAnswer(snCtfWinsOf(result.unclaimedWins), result.clientId.orEmpty())"))
        assertFalse(wins.contains("getOrDefault"))
        val each = between(sdk, "private fun snCtfWinsOf(", "override val available")
        assertTrue(each.contains("if (list == null) {\n                throw IllegalStateException("))
        assertTrue(each.contains("return (0 until list.len()).map { i ->"))
        assertTrue(each.contains("role = w.role,"))
        assertTrue(each.contains("country = w.country,"))
        // the flow shows the newest read's good answer, by the order the reads started, a
        // network's over a client's, and keeps the lines through a failure (SnCtfWinsLines,
        // SnLedgerClaimTest)
        val read = between(source("SnLedger.kt"), "suspend fun refreshWins() {", "\n    }\n")
        assertTrue(read.contains("val read = winsLines.start()\n        val answer = s.ctfWins()"))
        assertTrue(read.contains("if (winsLines.apply(read, answer.getOrNull())) {\n            _wins.value = winsLines.wins"))
        // the latest win alone is no longer the line
        assertFalse(sdk.contains("latestWin"))
        // read with each refresh of Earnings, on its own: the route's answer, or its 60 s
        // timeout, holds up neither the claims after it nor the refresh's spinner
        val model = source("EarningsViewModel.kt")
        val refresh = between(model, "private suspend fun refreshAll(", "val retryWallet")
        assertTrue(refresh.contains("viewModelScope.launch { ledgerFlow.refreshWins() }"))
        assertTrue(refresh.indexOf("viewModelScope.launch { ledgerFlow.refreshWins() }") < refresh.indexOf("coroutineScope {"))
        assertEquals(1, Regex("refreshWins\\(\\)").findAll(refresh).count())
        // the lines are the ledger flow's, dropped with the device (ledgerFlow.reset, which
        // SnLedgerClaimTest covers) and read again after a claim on the apps' schedule
        assertTrue(model.contains("val ctfWins: StateFlow<List<SnCtfWinState>> = ledgerFlow.wins"))
        assertTrue(between(model, "private fun setupDevice(", "private fun createProtocolSource(").contains("ledgerFlow.reset(source, device)"))
        assertFalse(model.contains("_ctfWins"))
        // the tile shows them, and the claim dialog names its rows from the same read as the tile
        val screen = source("EarningsScreen.kt")
        assertTrue(screen.contains("val ctfWins by earningsViewModel.ctfWins.collectAsState()"))
        assertTrue(between(screen, "SnLedgerTileBlock(", "\n                    )").contains("ctfWins = ctfWins,"))
        val dialog = between(screen, "SnLedgerClaimDialog(", "BittensorProofSheets(")
        assertTrue(dialog.contains("pools = snLedgerPools(ledgerLoad),"))
        // the claim offers what the relay's floor lets move, read when the dialog opens
        assertFalse(dialog.contains("transferFloorRao"))
        val flow = between(model, "val ledgerFlow = SnLedgerFlow(", "\n    )")
        assertTrue(flow.contains("transferFloorRao = { _claimStatus.value?.takeIf { !it.unavailable }?.transferFloorRao ?: 0 },"))
        // and a claim's Done reads the wins again, on their own and on the apps' schedule, through
        // the flows of the session there at their times: it may have paid them. The schedule is
        // the session's holder's (the app's), each read the flow's, in its view model's scope
        assertTrue(flow.contains("scope = viewModelScope,"))
        assertFalse(flow.contains("sleep"))
        val ledger = source("SnLedger.kt")
        val done = between(ledger, "claim = null\n        val settled = snLedgerClaimSettled(of.rows, of.read)", "\n    }\n")
        assertTrue(done.contains("listeners(of.session).forEach { it.claimDone(settled, of.transferFloorRao) }"))
        assertTrue(done.contains("readWinsAfterClaim(of.session, snLedgerClaimPaid(settled))"))
        assertTrue(ledger.contains("private val sleep: suspend (Long) -> Unit = { delay(it) },"))
        assertTrue(ledger.contains("override fun readWins() {\n            scope.launch { refreshWins() }"))
        assertTrue(ledger.contains("_dialog.value = snLedgerClaimDialog(_load.value, s.gasKey(), gasTao, transferFloorRao())"))
        assertTrue(ledger.contains("if (ready.takes.isEmpty() || claims.inFlight(owner) != null) {"))
    }

    @Test
    fun `the no-wallet tile says a lone total is under the floor`() {
        // Below the claim minimum under a total that is one account's, with no row to say it
        val tile = source("SnLedgerTile.kt")
        val relayTotal = between(tile, "tile.relayClaimableRao != null -> {", "tile.accounts == null -> LedgerSpinner()")
        val total = relayTotal.indexOf("formatAlpha(tile.relayClaimableRao),")
        val line = relayTotal.indexOf("if (relay != null && snClaimRelayTileTotalUnderFloor(relay)) {")
        assertTrue(0 <= total && total < line)
        assertTrue(relayTotal.contains("SnClaimRelayPill(stringResource(id = R.string.sn_ledger_pool_under_floor))"))
    }

    @Test
    fun `the tile draws a line for each unclaimed win`() {
        // owner, 2026-11-11: every unclaimed win, newest first, each in the store's words
        val tile = source("SnLedgerTile.kt")
        val block = between(tile, "fun SnLedgerTileBlock(", "fun snLedgerPoolNameText(")
        assertTrue(block.contains("ctfWins: List<SnCtfWinState> = emptyList(),"))
        assertTrue(block.contains("val wonLines = snCtfWonLines(ctfWins)"))
        assertTrue(block.contains("wonLines.forEachIndexed { i, (res, country) ->"))
        assertTrue(block.contains("stringResource(id = res, localizedCountryName(country)),"))
    }

    @Test
    fun `the tile and the dialogs name each pool by its kind`() {
        // the store's sn_ctf_pool and sn_ledger_provider_pool, Pool {no_id} otherwise
        val tile = source("SnLedgerTile.kt")
        val names = between(tile, "fun snLedgerPoolNameText(", "private fun LedgerAmountRow(")
        assertTrue(names.contains("is SnLedgerPoolName.Pool -> stringResource(id = R.string.sn_ledger_pool, name.noId)"))
        assertTrue(names.contains("SnLedgerPoolName.ProviderPool -> stringResource(id = R.string.sn_ledger_provider_pool)"))
        assertTrue(names.contains("SnLedgerPoolName.CaptureTheFlag -> stringResource(id = R.string.sn_ctf_pool)"))
        // no row names a pool by its number alone any more
        assertEquals(1, Regex("R\\.string\\.sn_ledger_pool,").findAll(tile).count())
        val relay = source("ClaimRelayDialog.kt")
        assertEquals(0, Regex("R\\.string\\.sn_ledger_pool,").findAll(relay).count())
        assertEquals(2, Regex("snLedgerPoolNameText\\(snLedgerPoolName\\(").findAll(relay).count())
        // Claim both on the tile and in the dialog; the dialog's Claim names and takes what
        // can move (Ready.takes), and with nothing that can move offers only Close
        assertTrue(tile.contains("stringResource(id = if (tile.claimBoth) R.string.sn_ledger_claim_both else R.string.claim)"))
        assertTrue(tile.contains("primary = if (snLedgerClaimsBoth(takes.map { it.pool })) {"))
        assertTrue(tile.contains("val takes = state.takes"))
        assertTrue(tile.contains("stringResource(id = R.string.claim_amount_button, formatAlpha(state.totalRao))"))
        val ready = between(tile, "private fun LedgerReadyContent(", "private fun LedgerAccountRows(")
        val closeOnly = between(ready, "if (takes.isEmpty()) {", "return@Column")
        assertTrue(closeOnly.contains("DialogButtons(primary = stringResource(id = R.string.close), onPrimary = onDismiss)"))
        assertFalse(closeOnly.contains("onClaim"))
        // a row the claim did not move says what it is, and a still claimable one the top-up
        val progress = between(tile, "private fun LedgerProgressContent(", "@Preview")
        assertTrue(progress.contains("snLedgerClaimRowNoteRes(p.status)?.let { res ->"))
        assertTrue(progress.contains("if (p.status == SnLedgerClaimStatus.STILL_CLAIMABLE) {"))
        assertTrue(progress.contains("stringResource(id = R.string.add_tao_for_gas),"))
        // the relay's rows take its floor for every pool, alone or not
        val sheet = between(relay, "fun ClaimRelayDialog(", "BittensorFundsDisclaimerText(disclaimer = fundsDisclaimer, boxed = true)")
        assertTrue(sheet.contains("transferFloorRao = status.transferFloorRao,"))
        assertTrue(between(relay, "internal fun SnClaimRelayTilePools(", "fun ClaimRelayDialog(").contains("transferFloorRao = status.transferFloorRao,"))
        // no total where nothing could be read: the unavailable rows say it
        assertTrue(sheet.contains("if (snClaimRelayTotalKnown(status)) {"))
        assertTrue(tile.contains("if (relay == null || snClaimRelayTotalKnown(relay)) {"))
        // a progress row shows an amount only when it has one
        assertTrue(between(tile, "private fun LedgerProgressContent(", "@Preview").contains("if (p.amountKnown) {"))
    }

    @Test
    fun `the v2 claim lives as long as the session, not the activity or the screen`() {
        // the claim in flight, its rows, its events, its Done and the reads after it are the
        // process's holder's, a Hilt singleton on the process's own scope, which the earnings view
        // model of each flavor's activity hands its flow: an activity that ends and comes back
        // keeps the claim (SnLedgerClaimTest: an activity that ends during a claim)
        for (flavor in listOf("google", "ungoogle", "solana_dapp", "ethos_dapp")) {
            val activity = File("src/$flavor/java/com/bringyour/network/MainActivity.kt").readText()
            assertTrue(flavor, activity.contains("private val earningsViewModel: EarningsViewModel by viewModels()"))
        }
        val ledger = source("SnLedger.kt")
        assertTrue(ledger.contains("@Singleton\nclass SnClaimHolder internal constructor("))
        assertTrue(ledger.contains("@Inject\n    constructor() : this(CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate))"))
        val model = source("EarningsViewModel.kt")
        assertTrue(between(model, "class EarningsViewModel @Inject constructor(", ") : ViewModel() {").contains("private val ledgerClaims: SnClaimHolder,"))
        val flow = between(model, "val ledgerFlow = SnLedgerFlow(", "\n    )")
        assertTrue(flow.contains("scope = viewModelScope,"))
        assertTrue(flow.contains("claims = ledgerClaims,"))
        // the flow hears no more once its view model ends
        assertTrue(ledger.contains("scope.coroutineContext.job.invokeOnCompletion {\n            detach?.invoke()"))
        // a visit reads again; only another device or a sign-out (setupDevice) resets the flow,
        // for the device, the session: the view model of a new activity finds its claim again
        assertFalse(between(model, "fun onScreenResumed() {", "\n    }").contains("ledgerFlow"))
        assertEquals(1, Regex("ledgerFlow\\.reset\\(").findAll(model).count())
        assertTrue(between(model, "private fun setupDevice(", "private fun createProtocolSource(").contains("ledgerFlow.reset(source, device)"))
        // the claim's events count only while it is the claim in flight: never after its Done,
        // nor once a flow of another session (a sign-out, another device) has attached
        val event = between(ledger, "private fun onEvent(of: Claim, event: SnLedgerClaimEvent) {", "\n    }\n")
        assertTrue(event.contains("if (claim !== of) {\n            return\n        }"))
        val attach = between(ledger, "fun attach(listener: Listener, session: Any?): () -> Unit {", "\n    }\n")
        assertTrue(attach.contains("if (claim?.session !== session) {\n            claim = null"))
        assertTrue(attach.contains("if (winsSession !== session) {\n            winsAfterClaim?.cancel()"))
        // the dialog shows the claim before the sdk's first event, which may come at once, and
        // the holder keeps it before it asks the sdk
        val claim = between(ledger, "    fun claim() {", "\n    }\n")
        assertTrue(claim.indexOf("_dialog.value = SnLedgerClaimDialogState.Sending(progress, snLedgerClaimMovingRao(progress, floorRao))") <
            claim.indexOf("claims.start(owner, source, progress, floorRao)"))
        val start = between(ledger, "    fun start(session: Any,", "\n    }\n")
        assertTrue(start.indexOf("claim = started") < start.indexOf("source.ledgerClaim { event ->"))
    }

    @Test
    fun `the v2 claim dialog shows no figure for nothing it can move, and never Claim 0`() {
        // as the other apps' sheets: the dialog's figure only while an account can move (above
        // the floor), or as a true zero with nothing at all; with nothing that can move, the rows
        // say why and there is no Claim, only Close; Claim names what moves, never 0
        // (SnLedgerClaimTest: the dialog's total is what the claim moves)
        val tile = source("SnLedgerTile.kt")
        val ready = between(tile, "private fun LedgerReadyContent(", "private fun LedgerAccountRows(")
        assertTrue(ready.contains("if (takes.isNotEmpty() || (state.accounts.isEmpty() && state.unavailablePools.isEmpty())) {\n            Text(\n                formatAlpha(state.totalRao),"))
        val closeOnly = between(ready, "if (takes.isEmpty()) {", "return@Column")
        assertFalse(closeOnly.contains("claim_amount_button"))
        assertTrue(ready.contains("stringResource(id = R.string.claim_amount_button, formatAlpha(state.totalRao))"))
        // while a claim runs, what it moves over its rows (every app, 2026-11-12), never a zero;
        // after it, the rows and Claimed's total once confirmed (SnLedgerClaimTest: the figure
        // during a claim)
        val progress = between(tile, "private fun LedgerProgressContent(", "@Preview")
        assertTrue(progress.contains("if (0 < movingRao) {\n            Text(\n                formatAlpha(movingRao),"))
        assertEquals(1, Regex("formatAlpha\\(movingRao\\)").findAll(progress).count())
        val dialog = between(tile, "fun SnLedgerClaimDialog(", "private fun LedgerReadyContent(")
        assertTrue(between(dialog, "is SnLedgerClaimDialogState.Sending -> LedgerProgressContent(", "explorerTxUrl = explorerTxUrl")
            .contains("movingRao = state.movingRao,"))
        assertTrue(between(dialog, "is SnLedgerClaimDialogState.Finished -> LedgerProgressContent(", "explorerTxUrl = explorerTxUrl")
            .contains("movingRao = 0,"))
        assertFalse(progress.contains("claim_amount_button"))
        assertFalse(progress.contains("R.string.unclaimed"))
        // the top-up names no figure for accounts of unknown amount, never a zero
        val gas = between(source("ClaimDialog.kt"), "internal fun NeedsGasContent(", "DialogButtons(")
        assertTrue(gas.contains("if (0 < state.totalRao) {\n            Row("))
        assertEquals(1, Regex("R\\.string\\.unclaimed").findAll(gas).count())
    }

    @Test
    fun `the accounts are read with the v1 claims, and v1 reads alone`() {
        val model = source("EarningsViewModel.kt")
        assertTrue(model.contains("ledgerFlow.reset(source, device)"))
        assertTrue(model.contains("launch { ledgerFlow.refresh() }"))
        // wallet read, retry, connect and the wallet listener read both
        assertEquals(4, Regex("refreshSettlements\\(\\)\\n").findAll(model).count())
        // the v1 claim dialog and its Done read only the v1 claims
        assertTrue(between(model, "private suspend fun loadClaimDialog()", "fun claimAll()").contains("refreshClaims()"))
        assertTrue(between(model, "private suspend fun finishEpochClaim(", "// ---- seeker").contains("refreshClaims()"))
    }

    @Test
    fun `the v1 claim is the session's too, not its dialog's`() {
        // its rows, its events and its Done are the process's holder's (SnLedgerClaimTest: a v1
        // claim's rows and its Done outlive its dialog and its view model)
        val model = source("EarningsViewModel.kt")
        val open = between(model, "    fun openClaimDialog() {", "\n    }\n")
        assertTrue(open.contains("ledgerClaims.epochClaimInFlight(byDevice)?.let {\n            _claimDialog.value = ClaimDialogState.Sending(it)\n            return"))
        assertTrue(open.indexOf("ledgerClaims.epochClaimInFlight(byDevice)") < open.indexOf("ClaimDialogState.NoWallet"))
        assertTrue(between(model, "    fun retryClaimDialog() {", "\n    }\n").contains("ledgerClaims.epochClaimInFlight(byDevice) != null"))
        val claimAll = between(model, "    fun claimAll() {", "\n    }\n")
        assertTrue(claimAll.contains("if (ready.claims.isEmpty() || ledgerClaims.epochClaimInFlight(device) != null) {"))
        val sending = claimAll.indexOf("_claimDialog.value = ClaimDialogState.Sending(progress)")
        assertTrue(0 <= sending && sending < claimAll.indexOf("ledgerClaims.startEpochClaim(device, source, ready.claims.map { it.epoch }, progress)"))
        // the dialog no longer keeps the claim: nothing asks the sdk but the holder
        assertFalse(model.contains("source.claim("))
        assertFalse(model.contains("onClaimEvent"))
        // the view model hears the session's v1 claim while it lives, its dialog open or not
        val ear = between(model, "private val epochClaimListener = object : SnClaimHolder.Listener {", "\n    }\n")
        assertTrue(ear.contains("if (_claimDialog.value is ClaimDialogState.Sending) {\n                _claimDialog.value = ClaimDialogState.Sending(rows)"))
        assertTrue(ear.contains("viewModelScope.launch { finishEpochClaim(settled) }"))
        val setup = between(model, "private fun setupDevice(", "private fun createProtocolSource(")
        assertTrue(setup.contains("detachEpochClaim?.invoke()\n        detachEpochClaim = ledgerClaims.attach(epochClaimListener, device)"))
        assertTrue(between(model, "override fun onCleared() {", "\n    }\n").contains("detachEpochClaim?.invoke()"))
        // its Done: the dialog's top-up or rows only while the dialog shows the claim, and the
        // v1 claims read again whatever the dialog
        val finish = between(model, "private suspend fun finishEpochClaim(", "\n    }\n")
        assertEquals(2, Regex("_claimDialog\\.value is ClaimDialogState\\.Sending").findAll(finish).count())
        assertFalse(finish.contains("as? ClaimDialogState.Sending ?: return"))
        assertTrue(finish.trimEnd().endsWith("refreshClaims()"))
        // and the holder is the same that keeps the v2 claim (SnLedger.kt)
        val ledger = source("SnLedger.kt")
        val start = between(ledger, "    fun startEpochClaim(", "\n    }\n")
        assertTrue(start.indexOf("epochClaim = started") < start.indexOf("source.claim(epochs) { event ->"))
        assertTrue(between(ledger, "private fun onEpochEvent(of: EpochClaim, event: ClaimEvent) {", "\n    }\n")
            .contains("if (epochClaim !== of) {\n            return\n        }"))
        assertTrue(between(ledger, "fun attach(listener: Listener, session: Any?): () -> Unit {", "\n    }\n")
            .contains("if (epochClaim?.session !== session) {\n            epochClaim = null"))
        // its dialog's figure while it runs: what it moves, never a zero (every app, 2026-11-12)
        val progress = between(source("ClaimDialog.kt"), "private fun ProgressContent(", "\n}\n")
        assertTrue(progress.contains("val movingRao = progress.sumOf { it.amountRao }\n        if (!finished && 0 < movingRao) {\n            Text(\n                formatAlpha(movingRao),"))
    }
}
