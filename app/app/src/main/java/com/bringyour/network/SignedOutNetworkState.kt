package com.bringyour.network

import com.bringyour.network.ui.login.MicrosoftSignInStore
import com.bringyour.network.ui.login.SsoOAuthAttempts
import com.bringyour.network.ui.settings.BittensorAddSignInReturns
import com.bringyour.network.ui.settings.MicrosoftAddSignInReturns
import com.bringyour.network.ui.settings.SsoAddSignInReturns
import com.bringyour.network.ui.shared.viewmodels.PendingSolanaPaymentStore
import com.bringyour.network.ui.wallet.BittensorBridgeReturns
import com.bringyour.network.ui.wallet.SnConsentReturns

/**
 * Clears what the network signing out left outside the sdk's local state
 * (owner decision 2026-10-05: "logout should not cross contaminate other
 * networks. Each network should start fresh"). The sdk's localState.logout()
 * clears the rest: the credentials, the instance, the device identity and the
 * network-scoped stores.
 *
 * - The browser sign-in attempts the app started (Apple and Google, and the
 *   sdk's Microsoft attempt). An add attempt outlives the process in
 *   preferences, and its late return would add the sign-in method to the next
 *   network signed in.
 * - The add sheet's returns waiting to be taken (sso, Microsoft and
 *   Bittensor), for the same reason.
 * - The payout consent's Bittensor return waiting for the Earnings screen: a
 *   consent the network that left signed.
 * - The Bittensor wallet session waiting for its bridge return: an add, or a
 *   payout wallet connect, for the network that left.
 * - The Solana payment waiting for its check: a payment of the network that
 *   left, whose notices the next network must not see.
 */
internal fun clearSignedOutNetworkState(
    ssoAttempts: List<SsoOAuthAttempts>,
    microsoftSignIn: MicrosoftSignInStore,
    pendingSolanaPayment: PendingSolanaPaymentStore,
    bittensorBridgeReturns: BittensorBridgeReturns,
) {
    ssoAttempts.forEach { it.clear() }
    microsoftSignIn.clear()
    SsoAddSignInReturns.take()
    MicrosoftAddSignInReturns.take()
    BittensorAddSignInReturns.take()
    SnConsentReturns.take()
    bittensorBridgeReturns.cancel()
    pendingSolanaPayment.clear()
}
