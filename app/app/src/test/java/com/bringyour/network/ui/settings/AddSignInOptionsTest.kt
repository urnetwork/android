package com.bringyour.network.ui.settings

import com.bringyour.network.ui.login.APPLE_OAUTH_PURPOSE_ADD
import com.bringyour.network.ui.login.APPLE_OAUTH_PURPOSE_LOGIN
import com.bringyour.network.ui.login.AppleOAuthAttempts
import com.bringyour.network.ui.login.AppleOAuthReturnRoute
import com.bringyour.network.ui.login.AppleOAuthStore
import com.bringyour.network.ui.login.PendingAppleOAuth
import com.bringyour.network.ui.login.appleOAuthReturnRoute
import com.bringyour.network.ui.wallet.BittensorProof
import com.bringyour.network.ui.wallet.BittensorWallets
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
}
