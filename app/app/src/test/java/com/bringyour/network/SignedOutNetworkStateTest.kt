package com.bringyour.network

import com.bringyour.network.ui.login.PendingSsoOAuth
import com.bringyour.network.ui.login.SSO_OAUTH_PURPOSE_ADD
import com.bringyour.network.ui.login.SSO_OAUTH_PURPOSE_LOGIN
import com.bringyour.network.ui.login.SsoOAuthAttempts
import com.bringyour.network.ui.login.SsoOAuthReturn
import com.bringyour.network.ui.login.SsoOAuthReturnRoute
import com.bringyour.network.ui.login.SsoOAuthStore
import com.bringyour.network.ui.login.SsoProvider
import com.bringyour.network.ui.login.ssoOAuthReturnRoute
import com.bringyour.network.ui.settings.BittensorAddReturn
import com.bringyour.network.ui.settings.BittensorAddSignInReturns
import com.bringyour.network.ui.settings.SsoAddOutcome
import com.bringyour.network.ui.settings.SsoAddReturn
import com.bringyour.network.ui.settings.SsoAddSignInReturns
import com.bringyour.network.ui.settings.ssoAddOutcome
import com.bringyour.network.ui.shared.viewmodels.PendingSolanaPayment
import com.bringyour.network.ui.shared.viewmodels.PendingSolanaPaymentStore
import com.bringyour.network.ui.wallet.BittensorBridgeReturn
import com.bringyour.network.ui.wallet.BittensorBridgeReturns
import com.bringyour.network.ui.wallet.BittensorProofOutcome
import com.bringyour.network.ui.wallet.BittensorProofSession
import com.bringyour.network.ui.wallet.BittensorWallets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Each network starts fresh (owner decision 2026-10-05): a sign-out clears
 * what the network signing out left outside the sdk's local state, so a late
 * browser return, a waiting wallet session or a pending payment of network A
 * never reaches network B signed in next. Stores and sessions are in memory;
 * no clock, network or browser.
 */
class SignedOutNetworkStateTest {

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

    private class MemoryPrefs : PendingSolanaPaymentStore.Prefs {
        val values = mutableMapOf<String, String>()
        override fun getString(key: String): String? = values[key]
        override fun putStrings(values: Map<String, String?>) {
            values.forEach { (key, value) ->
                if (value == null) this.values.remove(key) else this.values[key] = value
            }
        }
    }

    private class WaitingBridgeSession(override val purpose: String) : BittensorProofSession {
        override val walletId: String = BittensorWallets.WALLET_CONNECT
        override val message: String = "Sign in to URnetwork\nChallenge: synthetic"
        override val transport: String = BittensorWallets.TRANSPORT_BROWSER_BRIDGE
        override fun handleSignature(address: String, signature: String, nowMillis: Long): BittensorProofOutcome =
            BittensorProofOutcome.Refused("wrong_transport")
    }

    private fun attempts(store: MemoryStore): SsoOAuthAttempts {
        var next = 0
        return SsoOAuthAttempts(store, { 5_000L }) {
            next += 1
            "token-$next"
        }
    }

    private fun clear(
        ssoAttempts: List<SsoOAuthAttempts>,
        payments: PendingSolanaPaymentStore,
        bridgeReturns: BittensorBridgeReturns,
    ) = clearSignedOutNetworkState(ssoAttempts, payments, bridgeReturns)

    // network A started adding a sign-in method in the browser and signed out
    // before the return came back: the return no longer names an add attempt,
    // so it goes to the login, which refuses it, and the add sheet of network
    // B, signed in next, finds nothing to add
    @Test
    fun aSignOutEndsTheBrowserAttemptsTheNetworkStarted() {
        val appleStore = MemoryStore()
        val googleStore = MemoryStore()
        val apple = attempts(appleStore)
        val google = attempts(googleStore)
        val add = apple.begin(SSO_OAUTH_PURPOSE_ADD)
        val login = google.begin(SSO_OAUTH_PURPOSE_LOGIN)
        assertEquals(SsoOAuthReturnRoute.ADD_SIGN_IN, ssoOAuthReturnRoute(apple, add.state))

        clear(listOf(apple, google), PendingSolanaPaymentStore(MemoryPrefs()), BittensorBridgeReturns())

        assertNull(appleStore.pending)
        assertNull(googleStore.pending)
        assertNull(google.purposeOf(login.state))
        assertEquals(SsoOAuthReturnRoute.LOGIN, ssoOAuthReturnRoute(apple, add.state))
        val lateReturn = SsoOAuthReturn(state = add.state, idToken = "synthetic.id.token", error = null)
        assertEquals(SsoAddOutcome.Stray, ssoAddOutcome(SsoProvider.APPLE, apple, lateReturn) { add.nonce })
    }

    // returns already handed to the add sheet, and the Bittensor wallet session
    // still waiting for its bridge (an add or a payout wallet connect), go too
    @Test
    fun aSignOutDropsTheReturnsAndTheWalletSessionWaitingForTheNetwork() {
        SsoAddSignInReturns.deliver(
            SsoAddReturn(SsoProvider.GOOGLE, SsoOAuthReturn(state = "state-a", idToken = "synthetic", error = null))
        )
        BittensorAddSignInReturns.deliver(BittensorAddReturn.Failed(code = "synthetic", detail = null))
        val bridgeReturns = BittensorBridgeReturns()
        bridgeReturns.begin(WaitingBridgeSession(BittensorWallets.PURPOSE_CONNECT))
        assertTrue(bridgeReturns.waiting)

        clear(listOf(), PendingSolanaPaymentStore(MemoryPrefs()), bridgeReturns)

        assertNull(SsoAddSignInReturns.take())
        assertNull(BittensorAddSignInReturns.take())
        assertFalse(bridgeReturns.waiting)
        assertEquals(
            BittensorBridgeReturn.Ignored,
            bridgeReturns.take("ur://bittensor-sign-message?state=late", 6_000L),
        )
    }

    // a Solana payment network A left waiting for its check is not checked, or
    // announced, under network B
    @Test
    fun aSignOutDropsThePendingPayment() {
        val prefs = MemoryPrefs()
        val payments = PendingSolanaPaymentStore(prefs) { 10_000L }
        payments.save(PendingSolanaPayment(reference = "reference-a", plan = "monthly", amountUsd = 5.0, createdAtMillis = 9_000L))
        assertEquals("reference-a", payments.load()?.reference)

        clear(listOf(), payments, BittensorBridgeReturns())

        assertNull(payments.load())
        assertTrue(prefs.values.isEmpty())
    }

    // every sign-out path (the account menu, the account switch, a deleted
    // account, the sdk's auth logout) runs logoutInternal, which clears this
    // ahead of the sdk's local state logout, and resets the plan the start
    // connect gate reads
    @Test
    fun logoutClearsTheNetworksStateBeforeTheLocalLogout() {
        val source = java.io.File("src/main/java/com/bringyour/network/MainApplication.kt").readText()
        val start = source.indexOf("private fun logoutInternal() {")
        assertTrue(start >= 0)
        val body = source.substring(start, source.indexOf("\n    }\n", start))
        val needles = listOf(
            "stop()",
            "uiIsPro = false",
            "uiPollingSubscriptionBalance = false",
            "clearSignedOutNetworkState(",
            "SsoProvider.entries.map { ssoOAuthAttempts(this, it) }",
            "pendingSolanaPaymentStore(this)",
            "BittensorBridgeReturns.shared",
            "asyncLocalState?.localState?.logout()",
            "api?.byJwt = null",
        )
        var from = 0
        for (needle in needles) {
            val at = body.indexOf(needle, from)
            assertTrue("logoutInternal does not run `$needle` in order", at >= 0)
            from = at + needle.length
        }
    }
}
