package com.bringyour.network.ui.wallet

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The payout consent as wired into the app, read from the sources: the SDK's
 * classes need the native library, which these JVM tests do not have, and
 * the screen and the activities need a device. The behavior is
 * SnConsentFlowTest's and SnConsentTileTest's. C4 is CONSENT.md §4, C7 its
 * amendment A1, C9 its final generated names, C10 amendment A3 (the review).
 */
class SnConsentWiringTest {

    private fun source(path: String): String = File("src/$path").readText()

    private fun wallet(name: String): String = source("main/java/com/bringyour/network/ui/wallet/$name")

    // the brace-matched body of the block `start` opens (it ends in '{')
    private fun blockAfter(source: String, start: String): String {
        val from = source.indexOf(start)
        assertTrue("missing: $start", 0 <= from)
        var i = from + start.length
        var depth = 1
        while (0 < depth) {
            when (source[i]) {
                '{' -> depth++
                '}' -> depth--
            }
            i++
        }
        return source.substring(from + start.length, i - 1)
    }

    // the text between two markers, failing when either is missing
    private fun between(source: String, start: String, end: String): String {
        val from = source.indexOf(start)
        assertTrue("missing: $start", 0 <= from)
        val to = source.indexOf(end, from + start.length)
        assertTrue("missing after $start: $end", 0 <= to)
        return source.substring(from, to)
    }

    @Test
    fun `the session signs only the server's challenge for the coldkey`() {
        // C7, C9: a consent session; the challenge for the coldkey; the session takes the result verbatim
        val sheets = wallet("BittensorProofSheets.kt")
        val start = between(sheets, "suspend fun startBittensorConsentSession(", "suspend fun startBittensorBridgeProof(")
        assertTrue(start.contains("Sdk.newBittensorWalletSession(\n            walletId,\n            BittensorWallets.PLATFORM,\n            Sdk.BittensorWalletPurposeConsent,\n            BittensorWallets.REDIRECT_LINK,\n        )"))
        assertTrue(start.contains("session.setWalletConnectProjectId(walletConnectProjectId)"))
        assertTrue(start.contains("api.snNetworkConsentChallenge(coldkeySs58) { result, err ->"))
        assertTrue(start.contains("err != null"))
        assertTrue(start.contains("session.setNetworkConsentChallenge(result, nowMillis())"))
        assertTrue(start.contains("continuation.resume(Result.success(SdkBittensorProofSession(session)))"))
        // C10, probe N1: the wait follows the SDK session's own state
        assertTrue(sheets.contains("override val waitingForReturn: Boolean get() = session.state() == Sdk.BittensorWalletStateAwaitingWallet"))
        // no other message reaches the session: no login challenge, no message of the app's
        assertFalse(start.contains("setChallenge("))
        assertFalse(start.contains("authWalletChallenge"))
        // the app's mirror of the purpose routes the session's return
        assertEquals("consent", BittensorWallets.PURPOSE_CONSENT)
    }

    @Test
    fun `the sdk source reads the status and submits the signed consent`() {
        // C4, C7, C9: SnNetworkConsentStatus; SnNetworkConsentSubmit, its result's code a failure
        val sdk = wallet("SdkProtocolSource.kt")
        val status = between(sdk, "override suspend fun consentStatus()", "override suspend fun consentSession(")
        assertTrue(status.contains("api.snNetworkConsentStatus { result, err ->"))
        assertTrue(status.contains("err != null -> continuation.resume(Result.failure(err))"))
        assertTrue(status.contains("SnConsentStatusState(result.active, result.coldkeySs58)"))
        val session = between(sdk, "override suspend fun consentSession(", "override suspend fun consentSubmit(")
        assertTrue(session.contains("return startBittensorConsentSession(api, walletId, coldkeySs58)"))
        // C10, probe F2: Sign is offered only with the network credential
        assertTrue(sdk.contains("override fun hasNetworkCredential(): Boolean = device.api?.hasNetworkCredential() == true"))
        val submit = between(sdk, "override suspend fun consentSubmit(", "override suspend fun accountEpochs()")
        assertTrue(submit.contains("api.snNetworkConsentSubmit(coldkeySs58, message, signature) { result, err ->"))
        assertTrue(submit.contains("err != null -> continuation.resume(Result.failure(err))"))
        assertTrue(submit.contains("Result.failure(SnProtocolException(result.error.code.ifBlank { null }, result.error.message))"))
        assertTrue(submit.contains("else -> continuation.resume(Result.success(Unit))"))
    }

    @Test
    fun `the consent block sits after the v2 tile, with or without a wallet entry, with its own steps`() {
        // C4: after the v2 tile and claim; C10, probe F1: not behind the legacy wallet, since a
        // network without a GET /sn/wallet entry is the one the consent pays
        val screen = wallet("EarningsScreen.kt")
        val content = screen.substring(screen.indexOf("fun EarningsScreenContent("))
        // the protocol layer's block, the one holding UnclaimedTile
        val gateAt = content.indexOf("if (wallet != null) {\n                    Spacer(modifier = Modifier.height(16.dp))\n                    UnclaimedTile(")
        assertTrue(0 <= gateAt)
        val gated = blockAfter(content.substring(gateAt), "if (wallet != null) {")
        assertTrue(gated.contains("SnLedgerTileBlock("))
        assertFalse(gated.contains("SnConsentBlock("))
        val ledger = content.indexOf("SnLedgerTileBlock(tile = it, onClaim = onOpenLedgerClaim, formatAlpha = formatAlpha)")
        val consent = content.indexOf("SnConsentBlock(")
        assertTrue(0 <= ledger && ledger < consent && consent < content.indexOf("head?.let"))
        // hidden only without the protocol (signed out)
        assertTrue(screen.contains("consentTile = if (earningsViewModel.protocolAvailable) snConsentTile(consentState, consentStage) else null,"))
        assertTrue(screen.contains("onSignConsent = { earningsViewModel.consentFlow.start() },"))
        assertTrue(screen.contains("onRetryConsent = { earningsViewModel.consentFlow.retry() },"))
        val steps = between(screen, "flow = earningsViewModel.consentFlow.proofFlow,", "if (earningsViewModel.isPresentedManualSheet) {")
        assertTrue(steps.contains("onChoose = { walletId -> earningsViewModel.consentFlow.chooseWallet(walletId) },"))
        assertTrue(steps.contains("onSubmit = earningsViewModel.consentFlow::submit,"))
        assertTrue(steps.contains("consentState.coldkeyEntry?.let { entry ->\n        SnConsentColdkeySheet("))
        assertTrue(steps.contains("onAddressChange = { earningsViewModel.consentFlow.updateColdkey(it) },"))
        assertTrue(steps.contains("earningsViewModel.consentFlow.continueWithColdkey { url -> launchBittensorBridge(bridgeContext, url) }"))
        assertTrue(steps.contains("onDismiss = { earningsViewModel.consentFlow.dismissColdkey() },"))
        // the WalletConnect return, taken once
        assertTrue(steps.contains("val consentReturn by SnConsentReturns.pending.collectAsState()"))
        assertTrue(steps.contains("SnConsentReturns.take()?.let { earningsViewModel.consentFlow.handleReturn(it) }"))
        // the block's Sign and Change start the same steps
        val block = between(wallet("SnConsentTile.kt"), "fun SnConsentBlock(", "fun SnConsentColdkeySheet(")
        assertEquals(2, Regex(Regex.escape("onClick = onSign,")).findAll(block).count())
        // the coldkey entry is never prefilled
        assertTrue(between(wallet("SnConsent.kt"), "fun chooseWallet(walletId: String) {", "fun updateColdkey(")
            .contains("coldkeyEntry = SnConsentColdkeyEntry(walletId)"))
    }

    @Test
    fun `the consent is read on every refresh, with or without a wallet entry, and dropped with the device`() {
        // C10, probe F1: refreshAll reads it beside the wallet, not behind it as the settlements are
        val model = wallet("EarningsViewModel.kt")
        val refreshAll = blockAfter(model, "private suspend fun refreshAll(userInitiated: Boolean) {")
        val parallel = blockAfter(refreshAll, "coroutineScope {")
        assertTrue(parallel.contains("launch { consentFlow.refresh() }"))
        assertFalse(blockAfter(model, "private suspend fun refreshSettlements() = coroutineScope {").contains("consentFlow"))
        assertTrue(model.contains("consentFlow.reset(source)"))
        assertTrue(between(model, "fun onScreenResumed() {", "fun continueAfterLooksNew()").contains("consentFlow.onResumed()"))
    }

    @Test
    fun `every flavor's login activity forwards a consent return before the login takes it`() {
        // C4: the WalletConnect bridge returns to the LoginActivity; a consent session's verdict goes to Earnings
        for (flavor in listOf("google", "ungoogle", "solana_dapp", "ethos_dapp")) {
            val login = source("$flavor/java/com/bringyour/network/LoginActivity.kt")
            val returns = between(login, "val action = bittensorReturnAction(u.toString(), System.currentTimeMillis())", "bittensorSignMessageLogin(bridgeUri)")
            val add = returns.indexOf("forwardBittensorAddSignInReturn(this, addReturn, MainActivity::class.java)")
            // C10, probe F3: no text of the return reaches the consent
            val consent = returns.indexOf("val consentReturn = snConsentReturn(action)\n")
            val forward = returns.indexOf("if (consentReturn != null && app.device != null) {")
            val forwarded = returns.indexOf("forwardSnConsentReturn(this, consentReturn, MainActivity::class.java)\n                        return")
            val legacy = returns.indexOf("val bridgeUri = when (action) {")
            assertTrue(flavor, 0 <= add && add < consent && consent < forward && forward < forwarded && forwarded < legacy)
        }
    }

    @Test
    fun `a sign out drops the consent return waiting for the screen`() {
        // C4: nothing one network signed reaches the next network's screen
        val clear = between(
            source("main/java/com/bringyour/network/SignedOutNetworkState.kt"),
            "internal fun clearSignedOutNetworkState(",
            "\n}\n",
        )
        assertTrue(clear.contains("SnConsentReturns.take()"))
    }
}
