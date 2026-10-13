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
        for (field in listOf("noId", "index", "finalizedRao", "claimableRao", "paidRao", "pending", "pendingRao")) {
            assertTrue(field, accounts.contains("$field = a.$field,"))
        }
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
        val walletBlock = between(screen, "if (wallet != null) {\n                    Spacer", "head?.let")
        val unclaimed = walletBlock.indexOf("UnclaimedTile(")
        val ledger = walletBlock.indexOf("SnLedgerTileBlock(tile = it, onClaim = onOpenLedgerClaim, formatAlpha = formatAlpha)")
        assertTrue(0 <= unclaimed && unclaimed < ledger)
        assertTrue(screen.contains("ledgerTile = snLedgerTile(ledgerLoad, ledgerClaimConfigured),"))
        assertTrue(screen.contains("onOpenLedgerClaim = { earningsViewModel.ledgerFlow.open() },"))
        val dialog = between(screen, "SnLedgerClaimDialog(", "BittensorProofSheets(")
        assertTrue(dialog.contains("onClaim = { earningsViewModel.ledgerFlow.claim() },"))
        assertTrue(dialog.contains("onRetry = { earningsViewModel.ledgerFlow.retry() },"))
        assertTrue(dialog.contains("explorerTxUrl = earningsViewModel::explorerTxUrl,"))
        // the v1 dialog still claims through SnClaim
        assertTrue(between(screen, "    ClaimDialog(", "SnLedgerClaimDialog(").contains("onClaim = { earningsViewModel.claimAll() },"))
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
