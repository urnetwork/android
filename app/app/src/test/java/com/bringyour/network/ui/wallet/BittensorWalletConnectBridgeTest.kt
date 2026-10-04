package com.bringyour.network.ui.wallet

import com.bringyour.network.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The WalletConnect wallet (UPGRADE.md 4.6, restored 2026-10-04): the chooser
 * entry, the browser-bridge session waiting for its return, and how a
 * ur://bittensor-sign-message return is routed. The session is a fake with
 * scripted bridge outcomes; the SDK's own return checks are covered in
 * sdk/bittensor_wallet_test.go. No clock, network or browser.
 */
class BittensorWalletConnectBridgeTest {

    private val alice = "5GrwvaEF5zXb26Fz9rcQpDWS57CtERHpNehXCPcNoHGKutQY"
    private val message = "Sign in to URnetwork\nChallenge: abc\nTimestamp: 1757340000"
    private val signature = "0x" + "ab".repeat(64)
    private val bridgeUrl = "https://ur.io/bittensor-connect?wallet=walletconnect&wc_project_id=app-project"

    private class BridgeSession(
        override val walletId: String,
        override val purpose: String,
        override val message: String,
        private val url: String?,
    ) : BittensorProofSession {
        override val transport: String = BittensorWallets.TRANSPORT_BROWSER_BRIDGE
        val outcomes = ArrayDeque<BittensorProofOutcome>()
        val returns = mutableListOf<Pair<String, Long>>()

        override fun handleSignature(address: String, signature: String, nowMillis: Long): BittensorProofOutcome =
            BittensorProofOutcome.Refused("wrong_transport")

        override fun bridgeUrl(): String? = url

        override fun handleBridgeReturn(uri: String, nowMillis: Long): BittensorProofOutcome {
            returns.add(uri to nowMillis)
            return outcomes.removeFirst()
        }
    }

    private fun proof(purpose: String) = BittensorProof(BittensorWallets.WALLET_CONNECT, purpose, alice, message, signature)

    @Test
    fun `walletconnect is the third chooser entry with its wallets named`() {
        assertEquals("walletconnect", BittensorWallets.walletIds[2])
        assertEquals(R.string.bittensor_walletconnect_hint, BittensorWallets.subtitleRes(BittensorWallets.WALLET_CONNECT))
        assertEquals(R.string.enter_address_manually, BittensorWallets.subtitleRes(BittensorWallets.TAO_COM))
        assertNull(BittensorWallets.subtitleRes(BittensorWallets.TALISMAN))
    }

    @Test
    fun `choosing walletconnect opens the bridge page and waits for its return`() {
        val returns = BittensorBridgeReturns()
        val flow = BittensorProofFlow(returns) { 1000L }
        flow.open(BittensorWallets.PURPOSE_LOGIN)
        val request = flow.choose(BittensorWallets.WALLET_CONNECT)!!
        assertEquals(BittensorProofRequest("walletconnect", "login", null), request)

        val session = BridgeSession("walletconnect", "login", message, bridgeUrl)
        assertEquals(bridgeUrl, flow.sessionReady(request, session))
        assertEquals(BittensorProofStage.AwaitingBrowser("walletconnect"), flow.stage.value)
        assertTrue(returns.waiting)

        // back on screen before the return: still waiting
        flow.onResumed()
        assertEquals(BittensorProofStage.AwaitingBrowser("walletconnect"), flow.stage.value)

        // the return arrives (in LoginActivity) and is accepted
        session.outcomes.add(BittensorProofOutcome.Proven(proof("login")))
        val action = bittensorReturnAction("ur://bittensor-sign-message?x", 2000L, returns)
        assertEquals(BittensorReturnAction.Proven(BittensorProofRoute.LOGIN, proof("login")), action)
        assertEquals(listOf("ur://bittensor-sign-message?x" to 2000L), session.returns)
        assertFalse(returns.waiting)

        // back on screen after the return: nothing left to wait for
        flow.onResumed()
        assertEquals(BittensorProofStage.Hidden, flow.stage.value)
    }

    @Test
    fun `a return for another flow or wallet is ignored and the session keeps waiting`() {
        val returns = BittensorBridgeReturns()
        val session = BridgeSession("walletconnect", "connect", message, bridgeUrl)
        returns.begin(session)
        for (code in listOf("not_bittensor_return", "purpose_mismatch", "unsupported_wallet", "not_awaiting_wallet")) {
            session.outcomes.add(BittensorProofOutcome.Refused(code))
            assertEquals(code, BittensorReturnAction.Legacy, bittensorReturnAction("ur://bittensor-sign-message?y", 1L, returns))
            assertTrue(code, returns.waiting)
        }
        // then its own return routes to the Earnings connect
        session.outcomes.add(BittensorProofOutcome.Proven(proof("connect")))
        assertEquals(
            BittensorReturnAction.Proven(BittensorProofRoute.CONNECT_WALLET, proof("connect")),
            bittensorReturnAction("ur://bittensor-sign-message?z", 2L, returns),
        )
    }

    @Test
    fun `a refused return ends the wait with the flow's purpose and the wallet's text`() {
        val returns = BittensorBridgeReturns()
        val session = BridgeSession("walletconnect", "create", message, bridgeUrl)
        returns.begin(session)
        session.outcomes.add(BittensorProofOutcome.Refused("wallet_error", "User rejected."))
        assertEquals(
            BittensorReturnAction.Failed("create", "wallet_error", "User rejected."),
            bittensorReturnAction("ur://bittensor-sign-message?e", 3L, returns),
        )
        assertFalse(returns.waiting)
        // nothing waiting: a later return is the pre-helper handling's
        assertEquals(BittensorReturnAction.Legacy, bittensorReturnAction("ur://bittensor-sign-message?e", 4L, returns))
        assertEquals(R.string.bittensor_error_challenge_expired, BittensorWallets.errorRes("challenge_expired"))
    }

    @Test
    fun `cancelling or a browser that cannot open drops the waiting session`() {
        val returns = BittensorBridgeReturns()
        val flow = BittensorProofFlow(returns) { 1000L }
        flow.open(BittensorWallets.PURPOSE_CONNECT, alice)
        val request = flow.choose(BittensorWallets.WALLET_CONNECT)!!
        assertEquals(alice, request.expectedAddress)
        flow.sessionReady(request, BridgeSession("walletconnect", "connect", message, bridgeUrl))
        flow.dismiss()
        assertFalse(returns.waiting)
        assertEquals(BittensorProofStage.Hidden, flow.stage.value)

        flow.open(BittensorWallets.PURPOSE_CONNECT)
        val again = flow.choose(BittensorWallets.WALLET_CONNECT)!!
        flow.sessionReady(again, BridgeSession("walletconnect", "connect", message, bridgeUrl))
        flow.browserFailed()
        assertFalse(returns.waiting)
        assertEquals(BittensorProofStage.Choosing(R.string.login_error), flow.stage.value)

        // a session without a bridge url never waits
        flow.open(BittensorWallets.PURPOSE_CONNECT)
        val third = flow.choose(BittensorWallets.WALLET_CONNECT)!!
        assertNull(flow.sessionReady(third, BridgeSession("walletconnect", "connect", message, null)))
        assertFalse(returns.waiting)
    }
}
