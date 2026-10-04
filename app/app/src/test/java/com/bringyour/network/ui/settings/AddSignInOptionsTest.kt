package com.bringyour.network.ui.settings

import com.bringyour.network.ui.login.APPLE_OAUTH_PURPOSE_ADD
import com.bringyour.network.ui.login.APPLE_OAUTH_PURPOSE_LOGIN
import com.bringyour.network.ui.login.AppleOAuthAttempts
import com.bringyour.network.ui.login.AppleOAuthReturnRoute
import com.bringyour.network.ui.login.AppleOAuthStore
import com.bringyour.network.ui.login.PendingAppleOAuth
import com.bringyour.network.ui.login.appleOAuthReturnRoute
import com.bringyour.network.ui.wallet.BittensorBridgeReturns
import com.bringyour.network.ui.wallet.BittensorProof
import com.bringyour.network.ui.wallet.BittensorProofFlow
import com.bringyour.network.ui.wallet.BittensorProofOutcome
import com.bringyour.network.ui.wallet.BittensorProofRoute
import com.bringyour.network.ui.wallet.BittensorProofSession
import com.bringyour.network.ui.wallet.BittensorReturnAction
import com.bringyour.network.ui.wallet.BittensorWallets
import com.bringyour.network.ui.wallet.bittensorReturnAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The add sign-in method sheet offers the same options on every app (ur.io
 * AddSignInSheet, apple AddAuthSheetMethods): Apple, Google, a Solana or
 * Bittensor wallet, and an email or phone. Android offered Google, a Solana
 * wallet and email only. Adding must never sign in: an Apple return started
 * by the sheet is not taken by the login, and a Bittensor proof signed to add
 * is never a sign-in proof. Stores, clocks and tokens are injected.
 */
class AddSignInOptionsTest {

    private class MemoryStore : AppleOAuthStore {
        var pending: PendingAppleOAuth? = null
        override fun load(): PendingAppleOAuth? = pending
        override fun save(pending: PendingAppleOAuth) {
            this.pending = pending
        }
        override fun clear() {
            pending = null
        }
    }

    private class Tokens {
        var next = 0
        fun token(): String {
            next += 1
            return "token-$next"
        }
    }

    private fun attempts(store: MemoryStore, nowMillis: Long = 5_000L): AppleOAuthAttempts {
        val tokens = Tokens()
        return AppleOAuthAttempts(store, { nowMillis }, tokens::token)
    }

    private val alice = "5GrwvaEF5zXb26Fz9rcQpDWS57CtERHpNehXCPcNoHGKutQY"
    private val message = "Sign in to URnetwork\nChallenge: abc\nTimestamp: 1757340000"
    private val signature = "0x" + "ab".repeat(64)

    private fun proof(purpose: String) = BittensorProof(BittensorWallets.TALISMAN, purpose, alice, message, signature)

    @Test
    fun ssoFlavorsOfferEveryMethodInTheUrIoOrder() {
        assertEquals(
            listOf(AddAuthMethod.APPLE, AddAuthMethod.GOOGLE, AddAuthMethod.WALLET, AddAuthMethod.EMAIL),
            addAuthMethods(ssoAvailable = true),
        )
        // the ungoogle (github) flavor's login has no Apple or Google either
        assertEquals(listOf(AddAuthMethod.WALLET, AddAuthMethod.EMAIL), addAuthMethods(ssoAvailable = false))
    }

    @Test
    fun theWalletOptionOffersSolanaAndBittensor() {
        assertEquals(listOf(AddAuthWalletChain.SOLANA, AddAuthWalletChain.BITTENSOR), addAuthWalletChains)
    }

    @Test
    fun aBittensorProofSignedToAddIsAddedAsATaoWallet() {
        assertEquals("add", BittensorWallets.PURPOSE_ADD)
        val auth = bittensorAddWalletAuth(proof(BittensorWallets.PURPOSE_ADD))
        assertEquals(AddWalletAuth(blockchain = "TAO", publicKey = alice, message = message, signature = signature), auth)
    }

    @Test
    fun aSignInOrConnectProofIsNeverAdded() {
        for (purpose in listOf(BittensorWallets.PURPOSE_LOGIN, BittensorWallets.PURPOSE_CREATE, BittensorWallets.PURPOSE_CONNECT)) {
            assertNull(purpose, bittensorAddWalletAuth(proof(purpose)))
        }
    }

    @Test
    fun anAddStartedAppleReturnIsNotTakenByTheLogin() {
        val store = MemoryStore()
        val attempts = attempts(store)
        val pending = attempts.begin(APPLE_OAUTH_PURPOSE_ADD)

        assertEquals(AppleOAuthReturnRoute.ADD_SIGN_IN, appleOAuthReturnRoute(attempts, pending.state))
        // the login refuses it and leaves it for the sheet
        assertNull(attempts.take(pending.state, APPLE_OAUTH_PURPOSE_LOGIN))
        assertNotNull(store.pending)

        val outcome = appleAddOutcome(attempts, AppleOAuthReturn(pending.state, "apple-id-token", null)) { pending.nonce }
        assertEquals(AppleAddOutcome.Add(AddSsoAuth("apple-id-token", "apple")), outcome)
        // consumed once
        assertNull(store.pending)
        assertEquals(
            AppleAddOutcome.Stray,
            appleAddOutcome(attempts, AppleOAuthReturn(pending.state, "apple-id-token", null)) { pending.nonce },
        )
    }

    @Test
    fun aLoginAppleReturnIsNotTakenByTheSheet() {
        val store = MemoryStore()
        val attempts = attempts(store)
        val pending = attempts.begin(APPLE_OAUTH_PURPOSE_LOGIN)

        assertEquals(AppleOAuthReturnRoute.LOGIN, appleOAuthReturnRoute(attempts, pending.state))
        assertEquals(
            AppleAddOutcome.Stray,
            appleAddOutcome(attempts, AppleOAuthReturn(pending.state, "apple-id-token", null)) { pending.nonce },
        )
        // still the login's
        assertEquals(pending.nonce, attempts.take(pending.state, APPLE_OAUTH_PURPOSE_LOGIN)?.nonce)
    }

    @Test
    fun anAppleAddReturnMustCarryTheAttemptsNonce() {
        val store = MemoryStore()
        val attempts = attempts(store)
        val pending = attempts.begin(APPLE_OAUTH_PURPOSE_ADD)
        assertEquals(
            AppleAddOutcome.Failed(null),
            appleAddOutcome(attempts, AppleOAuthReturn(pending.state, "other-token", null)) { "other-nonce" },
        )

        val cancelled = attempts.begin(APPLE_OAUTH_PURPOSE_ADD)
        assertEquals(
            AppleAddOutcome.Failed("user_cancelled_authorize"),
            appleAddOutcome(attempts, AppleOAuthReturn(cancelled.state, null, "user_cancelled_authorize")) { cancelled.nonce },
        )
    }

    @Test
    fun anUnknownStateRoutesToTheLoginWhichRefusesIt() {
        val store = MemoryStore()
        val attempts = attempts(store)
        attempts.begin(APPLE_OAUTH_PURPOSE_ADD)
        assertEquals(AppleOAuthReturnRoute.LOGIN, appleOAuthReturnRoute(attempts, "forged-state"))
        assertEquals(AppleOAuthReturnRoute.LOGIN, appleOAuthReturnRoute(attempts, null))
    }

    @Test
    fun appleAddsWithoutACodeAndNeverVerifies() {
        assertFalse(AddedSignInMethod.APPLE.needsVerification)
        val calls = mutableListOf<String>()
        val flow = AddSignInFlow(object : AddSignInSession<String> {
            override fun addAuth(args: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
                calls += "addAuth:$args"
                onSuccess()
            }
            override fun sendCode(userAuth: String, done: (Boolean, com.bringyour.network.ui.login.VerifySendError?) -> Unit) {
                calls += "sendCode"
            }
            // authVerify returns a jwt; it is the only call that could replace the session's
            override fun verifyCode(userAuth: String, code: String, done: (String?) -> Unit) {
                calls += "verifyCode"
            }
        }) { 0L }
        var added = 0
        flow.add(AddedSignInMethod.APPLE, "apple-args", "", { added += 1 }, {})
        assertEquals(1, added)
        assertEquals(AddSignInStep.ADDED, flow.step)
        assertEquals(listOf("addAuth:apple-args"), calls)
        assertTrue(calls.none { it == "verifyCode" })
    }

    // a WalletConnect bridge session with one scripted return
    private class BridgeSession(override val purpose: String, private val outcome: BittensorProofOutcome) : BittensorProofSession {
        override val walletId: String = BittensorWallets.WALLET_CONNECT
        override val message: String = "Sign in to URnetwork\nChallenge: abc\nTimestamp: 1757340000"
        override val transport: String = BittensorWallets.TRANSPORT_BROWSER_BRIDGE
        override fun handleSignature(address: String, signature: String, nowMillis: Long): BittensorProofOutcome =
            BittensorProofOutcome.Refused("wrong_transport")
        override fun handleBridgeReturn(uri: String, nowMillis: Long): BittensorProofOutcome = outcome
    }

    @Test
    fun aWalletConnectAddReturnGoesToTheSheetNotTheLogin() {
        val bridgeReturns = BittensorBridgeReturns()
        bridgeReturns.begin(BridgeSession(BittensorWallets.PURPOSE_ADD, BittensorProofOutcome.Proven(proof(BittensorWallets.PURPOSE_ADD))))
        val action = bittensorReturnAction("ur://bittensor-sign-message?purpose=add", 0L, bridgeReturns)
        assertEquals(BittensorReturnAction.Proven(BittensorProofRoute.ADD_SIGN_IN, proof(BittensorWallets.PURPOSE_ADD)), action)
        assertEquals(BittensorAddReturn.Proven(proof(BittensorWallets.PURPOSE_ADD)), bittensorAddReturn(action))

        val refused = BittensorBridgeReturns()
        refused.begin(BridgeSession(BittensorWallets.PURPOSE_ADD, BittensorProofOutcome.Refused(BittensorWallets.ERROR_WALLET, "Rejected")))
        assertEquals(
            BittensorAddReturn.Failed(BittensorWallets.ERROR_WALLET, "Rejected"),
            bittensorAddReturn(bittensorReturnAction("ur://bittensor-sign-message?purpose=add", 0L, refused)),
        )
    }

    @Test
    fun aSignInOrConnectReturnIsNotTheSheets() {
        for (purpose in listOf(BittensorWallets.PURPOSE_LOGIN, BittensorWallets.PURPOSE_CREATE, BittensorWallets.PURPOSE_CONNECT)) {
            val bridgeReturns = BittensorBridgeReturns()
            bridgeReturns.begin(BridgeSession(purpose, BittensorProofOutcome.Proven(proof(purpose))))
            assertNull(purpose, bittensorAddReturn(bittensorReturnAction("ur://bittensor-sign-message", 0L, bridgeReturns)))
        }
        assertNull(bittensorAddReturn(BittensorReturnAction.Legacy))
    }

    @Test
    fun theSheetAddsAReturnedProofAsATaoWallet() {
        val added = mutableListOf<AddWalletAuth>()
        var error: String? = "stale"
        val controller = BittensorAddSignInController(
            flow = BittensorProofFlow(BittensorBridgeReturns()) { 0L },
            scope = CoroutineScope(Dispatchers.Unconfined),
            api = { null },
            setError = { error = it },
            defaultError = { "default" },
            addWalletAuth = { added += it },
            refusalError = { code, detail -> "$code:$detail" },
        )
        controller.handleReturn(BittensorAddReturn.Proven(proof(BittensorWallets.PURPOSE_ADD)))
        assertEquals(listOf(AddWalletAuth("TAO", alice, message, signature)), added)
        assertNull(error)

        // a proof for anything but adding is never added
        controller.handleReturn(BittensorAddReturn.Proven(proof(BittensorWallets.PURPOSE_LOGIN)))
        assertEquals(1, added.size)
        assertEquals("default", error)

        controller.handleReturn(BittensorAddReturn.Failed(BittensorWallets.ERROR_WALLET, "Rejected"))
        assertEquals("wallet_error:Rejected", error)
        assertEquals(1, added.size)
    }
}
