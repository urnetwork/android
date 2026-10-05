package com.bringyour.network.ui.settings

import com.bringyour.network.BuildConfig
import com.bringyour.network.ui.login.GOOGLE_OAUTH_WEB_CLIENT_ID
import com.bringyour.network.ui.login.SSO_OAUTH_PURPOSE_ADD
import com.bringyour.network.ui.login.SSO_OAUTH_PURPOSE_LOGIN
import com.bringyour.network.ui.login.SsoOAuthAttempts
import com.bringyour.network.ui.login.SsoOAuthReturnRoute
import com.bringyour.network.ui.login.SsoOAuthStore
import com.bringyour.network.ui.login.PendingSsoOAuth
import com.bringyour.network.ui.login.SsoLoginOutcome
import com.bringyour.network.ui.login.SsoOAuthReturn
import com.bringyour.network.ui.login.SsoProvider
import com.bringyour.network.ui.login.googleOAuthAuthorizeUrl
import com.bringyour.network.ui.login.loginSsoProviders
import com.bringyour.network.ui.login.ssoLoginOutcome
import com.bringyour.network.ui.login.ssoOAuthReturnProvider
import com.bringyour.network.ui.login.ssoOAuthReturnRoute
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
 * wallet and email only, and the github build (no Play services) neither
 * Apple nor Google, on its login or in the sheet; it now signs in with both
 * through the browser. Adding must never sign in: an Apple or Google return
 * started by the sheet is not taken by the login, and a Bittensor proof
 * signed to add is never a sign-in proof. Stores, clocks and tokens are
 * injected.
 */
class AddSignInOptionsTest {

    private class MemoryStore : SsoOAuthStore {
        var pending: PendingSsoOAuth? = null
        override fun load(): PendingSsoOAuth? = pending
        override fun save(pending: PendingSsoOAuth) {
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

    private fun attempts(store: MemoryStore, nowMillis: Long = 5_000L): SsoOAuthAttempts {
        val tokens = Tokens()
        return SsoOAuthAttempts(store, { nowMillis }, tokens::token)
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
        // a build that offered neither on its login
        assertEquals(listOf(AddAuthMethod.WALLET, AddAuthMethod.EMAIL), addAuthMethods(ssoAvailable = false))
    }

    // runs per flavor (testGithubDebugUnitTest is the build without Play
    // services): the sheet is given this build's flag, exactly as Settings and
    // the guest conversion give it
    @Test
    fun thisBuildsAddSheetOffersAppleAndGoogleInTheUrIoOrder() {
        assertEquals(
            listOf(AddAuthMethod.APPLE, AddAuthMethod.GOOGLE, AddAuthMethod.WALLET, AddAuthMethod.EMAIL),
            addAuthMethods(BuildConfig.BRINGYOUR_BUNDLE_SSO_GOOGLE),
        )
    }

    @Test
    fun thisBuildsLoginLeadsWithGoogleThenApple() {
        // the play flavor's order; the github login lays out its stack from this
        assertEquals(listOf(SsoProvider.GOOGLE, SsoProvider.APPLE), loginSsoProviders(BuildConfig.BRINGYOUR_BUNDLE_SSO_GOOGLE))
        assertEquals(listOf<SsoProvider>(), loginSsoProviders(false))
    }

    @Test
    fun googleSignsInThroughTheApiCallbackWithTheWebClient() {
        val url = googleOAuthAuthorizeUrl("https://api.example/", "state-1", "nonce-1")
        assertTrue(url, url.startsWith("https://accounts.google.com/o/oauth2/v2/auth?"))
        // the web client the server accepts as an audience (no Android client, no Play services)
        assertTrue(url, url.contains("client_id=$GOOGLE_OAUTH_WEB_CLIENT_ID&"))
        assertTrue(url, url.contains("redirect_uri=https%3A%2F%2Fapi.example%2Fauth%2Fgoogle%2Fcallback&"))
        // the code flow: the callback exchanges the code for the identity token
        assertTrue(url, url.contains("&response_type=code&"))
        assertTrue(url, url.contains("&scope=openid%20email%20profile&"))
        assertTrue(url, url.contains("&state=state-1&"))
        assertTrue(url, url.contains("&nonce=nonce-1&"))
    }

    @Test
    fun theCallbackReturnsAreToldApartByPath() {
        assertEquals(SsoProvider.GOOGLE, ssoOAuthReturnProvider("ur", "oauth", "/google"))
        assertEquals(SsoProvider.APPLE, ssoOAuthReturnProvider("ur", "oauth", "/apple"))
        assertNull(ssoOAuthReturnProvider("ur", "oauth", "/other"))
        assertNull(ssoOAuthReturnProvider("ur", "bittensor-sign-message", "/google"))
        assertNull(ssoOAuthReturnProvider("https", "oauth", "/google"))
    }

    @Test
    fun anAddStartedGoogleReturnIsAddedAndNeverSignsIn() {
        val store = MemoryStore()
        val attempts = attempts(store)
        val pending = attempts.begin(SSO_OAUTH_PURPOSE_ADD)
        val googleReturn = SsoOAuthReturn(pending.state, "google-id-token", null)

        assertEquals(SsoOAuthReturnRoute.ADD_SIGN_IN, ssoOAuthReturnRoute(attempts, pending.state))
        // the login refuses it and leaves it for the sheet
        assertEquals(SsoLoginOutcome.NoAttempt, ssoLoginOutcome(SsoProvider.GOOGLE, attempts, googleReturn) { pending.nonce })
        assertNotNull(store.pending)

        assertEquals(
            SsoAddOutcome.Add(AddSsoAuth("google-id-token", "google")),
            ssoAddOutcome(SsoProvider.GOOGLE, attempts, googleReturn) { pending.nonce },
        )
        assertNull(store.pending)
    }

    @Test
    fun aLoginGoogleReturnSignsInAndIsNotTheSheets() {
        val store = MemoryStore()
        val attempts = attempts(store)
        val pending = attempts.begin(SSO_OAUTH_PURPOSE_LOGIN)
        val googleReturn = SsoOAuthReturn(pending.state, "google-id-token", null)

        assertEquals(SsoOAuthReturnRoute.LOGIN, ssoOAuthReturnRoute(attempts, pending.state))
        assertEquals(SsoAddOutcome.Stray, ssoAddOutcome(SsoProvider.GOOGLE, attempts, googleReturn) { pending.nonce })
        assertEquals(
            SsoLoginOutcome.SignIn("google-id-token", "google"),
            ssoLoginOutcome(SsoProvider.GOOGLE, attempts, googleReturn) { pending.nonce },
        )
        // consumed once: a replayed return signs no one in
        assertEquals(SsoLoginOutcome.NoAttempt, ssoLoginOutcome(SsoProvider.GOOGLE, attempts, googleReturn) { pending.nonce })
    }

    @Test
    fun aGoogleLoginReturnMustCarryTheAttemptsNonce() {
        val store = MemoryStore()
        val attempts = attempts(store)
        val pending = attempts.begin(SSO_OAUTH_PURPOSE_LOGIN)
        assertEquals(
            SsoLoginOutcome.Failed(null),
            ssoLoginOutcome(SsoProvider.GOOGLE, attempts, SsoOAuthReturn(pending.state, "other-token", null)) { "other-nonce" },
        )
        val cancelled = attempts.begin(SSO_OAUTH_PURPOSE_LOGIN)
        assertEquals(
            SsoLoginOutcome.Failed("access_denied"),
            ssoLoginOutcome(SsoProvider.GOOGLE, attempts, SsoOAuthReturn(cancelled.state, null, "access_denied")) { cancelled.nonce },
        )
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
        val pending = attempts.begin(SSO_OAUTH_PURPOSE_ADD)

        assertEquals(SsoOAuthReturnRoute.ADD_SIGN_IN, ssoOAuthReturnRoute(attempts, pending.state))
        // the login refuses it and leaves it for the sheet
        assertNull(attempts.take(pending.state, SSO_OAUTH_PURPOSE_LOGIN))
        assertNotNull(store.pending)

        val outcome = ssoAddOutcome(SsoProvider.APPLE, attempts, SsoOAuthReturn(pending.state, "apple-id-token", null)) { pending.nonce }
        assertEquals(SsoAddOutcome.Add(AddSsoAuth("apple-id-token", "apple")), outcome)
        // consumed once
        assertNull(store.pending)
        assertEquals(
            SsoAddOutcome.Stray,
            ssoAddOutcome(SsoProvider.APPLE, attempts, SsoOAuthReturn(pending.state, "apple-id-token", null)) { pending.nonce },
        )
    }

    @Test
    fun aLoginAppleReturnIsNotTakenByTheSheet() {
        val store = MemoryStore()
        val attempts = attempts(store)
        val pending = attempts.begin(SSO_OAUTH_PURPOSE_LOGIN)

        assertEquals(SsoOAuthReturnRoute.LOGIN, ssoOAuthReturnRoute(attempts, pending.state))
        assertEquals(
            SsoAddOutcome.Stray,
            ssoAddOutcome(SsoProvider.APPLE, attempts, SsoOAuthReturn(pending.state, "apple-id-token", null)) { pending.nonce },
        )
        // still the login's
        assertEquals(pending.nonce, attempts.take(pending.state, SSO_OAUTH_PURPOSE_LOGIN)?.nonce)
    }

    @Test
    fun anAppleAddReturnMustCarryTheAttemptsNonce() {
        val store = MemoryStore()
        val attempts = attempts(store)
        val pending = attempts.begin(SSO_OAUTH_PURPOSE_ADD)
        assertEquals(
            SsoAddOutcome.Failed(null),
            ssoAddOutcome(SsoProvider.APPLE, attempts, SsoOAuthReturn(pending.state, "other-token", null)) { "other-nonce" },
        )

        val cancelled = attempts.begin(SSO_OAUTH_PURPOSE_ADD)
        assertEquals(
            SsoAddOutcome.Failed("user_cancelled_authorize"),
            ssoAddOutcome(SsoProvider.APPLE, attempts, SsoOAuthReturn(cancelled.state, null, "user_cancelled_authorize")) { cancelled.nonce },
        )
    }

    @Test
    fun anUnknownStateRoutesToTheLoginWhichRefusesIt() {
        val store = MemoryStore()
        val attempts = attempts(store)
        attempts.begin(SSO_OAUTH_PURPOSE_ADD)
        assertEquals(SsoOAuthReturnRoute.LOGIN, ssoOAuthReturnRoute(attempts, "forged-state"))
        assertEquals(SsoOAuthReturnRoute.LOGIN, ssoOAuthReturnRoute(attempts, null))
    }

    @Test
    fun appleAddsWithoutACodeAndNeverVerifies() {
        assertFalse(AddedSignInMethod.APPLE.needsVerification)
        val calls = mutableListOf<String>()
        val flow = AddSignInFlow(object : AddSignInSession<String> {
            override fun addAuth(args: String, onSuccess: () -> Unit, onError: (AddAuthRefusal) -> Unit) {
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
