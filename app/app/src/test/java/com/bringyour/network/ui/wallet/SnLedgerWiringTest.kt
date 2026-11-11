package com.bringyour.network.ui.wallet

import java.io.File
import org.junit.Assert.assertEquals
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
    fun `the relay's pools, its floor and the device's capture the flag win are read from the sdk`() {
        val sdk = source("SdkProtocolSource.kt")
        // each relay account's kind names its row; the pools it could not read and its
        // transfer floor in α (Below the claim minimum)
        val status = between(sdk, "private fun snClaimStatusOf(", "private fun snLedgerUnavailablePoolsOf(")
        assertTrue(status.contains("transferFloorRao = result.transferFloorRao,"))
        assertTrue(status.contains("unavailablePools = snLedgerUnavailablePoolsOf(result.unavailablePools),"))
        assertTrue(status.contains("kind = a.kind,"))
        // GET /sn/ctf/client's latest win, copied off the gomobile objects; an error draws no line
        val win = between(sdk, "override suspend fun ctfWin()", "override suspend fun accountEpochs()")
        assertTrue(win.contains("device.snCtfClient {"))
        assertTrue(win.contains("result?.takeIf { it.error == null }?.latestWin?.let { SnCtfWinState(it.role, it.country) }"))
        assertTrue(win.contains("runCatching {"))
        // read with the rest of Earnings, dropped with the device
        val model = source("EarningsViewModel.kt")
        assertTrue(between(model, "private suspend fun refreshAll(", "val retryWallet").contains("launch { refreshCtfWin() }"))
        assertTrue(between(model, "private fun setupDevice(", "private fun createProtocolSource(").contains("_ctfWin.value = null"))
        // the tile shows it, and the claim dialog names its rows from the same read as the tile
        val screen = source("EarningsScreen.kt")
        assertTrue(screen.contains("val ctfWin by earningsViewModel.ctfWin.collectAsState()"))
        assertTrue(between(screen, "SnLedgerTileBlock(", "\n                    )").contains("ctfWin = ctfWin,"))
        val dialog = between(screen, "SnLedgerClaimDialog(", "BittensorProofSheets(")
        assertTrue(dialog.contains("pools = snLedgerPools(ledgerLoad),"))
        // and marks what waits under the transfer floor as the tile does
        assertTrue(dialog.contains("transferFloorRao = claimStatus?.takeIf { !it.unavailable }?.transferFloorRao ?: 0,"))
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
        // Claim both on the tile and in the dialog
        assertTrue(tile.contains("stringResource(id = if (tile.claimBoth) R.string.sn_ledger_claim_both else R.string.claim)"))
        assertTrue(tile.contains("primary = if (snLedgerClaimsBoth(takes.map { it.pool })) {"))
        assertTrue(tile.contains("val takes = snLedgerClaimTakes(state.accounts, transferFloorRao)"))
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
