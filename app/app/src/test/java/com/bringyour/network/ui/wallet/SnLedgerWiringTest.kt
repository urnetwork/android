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
        // gomobile objects; an error draws no line
        val wins = between(sdk, "override suspend fun ctfWins()", "override suspend fun accountEpochs()")
        assertTrue(wins.contains("device.snCtfClient {"))
        assertTrue(wins.contains("snCtfWinsOf(result?.takeIf { it.error == null }?.unclaimedWins)"))
        assertTrue(wins.contains("runCatching {"))
        assertTrue(wins.contains("}.getOrDefault(emptyList())"))
        val each = between(sdk, "private fun snCtfWinsOf(", "override val available")
        assertTrue(each.contains("return (0 until list.len()).map { i ->"))
        assertTrue(each.contains("role = w.role,"))
        assertTrue(each.contains("country = w.country,"))
        // the latest win alone is no longer the line
        assertFalse(sdk.contains("latestWin"))
        // read with each refresh of Earnings, on its own: the route's answer, or its 60 s
        // timeout, holds up neither the claims after it nor the refresh's spinner
        val model = source("EarningsViewModel.kt")
        val refresh = between(model, "private suspend fun refreshAll(", "val retryWallet")
        assertTrue(refresh.contains("viewModelScope.launch { refreshCtfWins() }"))
        assertTrue(refresh.indexOf("viewModelScope.launch { refreshCtfWins() }") < refresh.indexOf("coroutineScope {"))
        assertEquals(1, Regex("refreshCtfWins\\(\\)").findAll(refresh).count())
        // dropped with the device
        assertTrue(between(model, "private fun setupDevice(", "private fun createProtocolSource(").contains("_ctfWins.value = emptyList()"))
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
        // and a claim's Done reads the wins again, on their own: it may have paid them
        assertTrue(flow.contains("onClaimDone = { viewModelScope.launch { refreshCtfWins() } },"))
        val ledger = source("SnLedger.kt")
        assertTrue(ledger.contains("_dialog.value = snLedgerClaimDialog(_load.value, s.gasKey(), gasTao, transferFloorRao())"))
        assertTrue(ledger.contains("if (ready.takes.isEmpty() || claiming != null) {"))
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
    fun `the accounts are read with the v1 claims, and v1 reads alone`() {
        val model = source("EarningsViewModel.kt")
        assertTrue(model.contains("ledgerFlow.reset(source)"))
        assertTrue(model.contains("launch { ledgerFlow.refresh() }"))
        // wallet read, retry, connect and the wallet listener read both
        assertEquals(4, Regex("refreshSettlements\\(\\)\\n").findAll(model).count())
        // the v1 claim dialog and its Done read only the v1 claims
        assertTrue(between(model, "private suspend fun loadClaimDialog()", "fun claimAll()").contains("refreshClaims()"))
        assertTrue(between(model, "ClaimEvent.Done -> {", "// ---- seeker").contains("refreshClaims()"))
    }
}
