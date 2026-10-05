package com.bringyour.network.ui.settings

import com.bringyour.network.ui.login.BITTENSOR_BLOCKCHAIN
import com.bringyour.network.ui.login.SSO_OAUTH_PURPOSE_ADD
import com.bringyour.network.ui.login.SsoOAuthAttempts
import com.bringyour.network.ui.login.SsoOAuthReturn
import com.bringyour.network.ui.login.SsoProvider
import com.bringyour.network.ui.login.ssoOAuthReturn
import com.bringyour.network.ui.wallet.BittensorProof
import com.bringyour.network.ui.wallet.BittensorProofRoute
import com.bringyour.network.ui.wallet.BittensorReturnAction
import com.bringyour.network.ui.wallet.BittensorWallets
import com.bringyour.network.ui.wallet.bittensorProofRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The add sign-in method sheet's options, the same on every app (ur.io
 * AddSignInSheet, apple AddAuthSheetMethods): Apple, Google, a wallet
 * (Solana or Bittensor), and an email or phone with a code. Apple and Google
 * are offered where the flavor's login offers them (BRINGYOUR_BUNDLE_SSO_GOOGLE):
 * every flavor now. The Play services flavors (play, solana_dapp, ethos_dapp)
 * add Google natively; the ungoogle (github) flavor adds Google in the browser
 * (launchGoogleOAuth), and every flavor adds Apple in the browser.
 */
enum class AddAuthMethod { APPLE, GOOGLE, WALLET, EMAIL }

/** The wallet option's chains, in the order ur.io lists them. */
enum class AddAuthWalletChain { SOLANA, BITTENSOR }

/** The methods the sheet offers, in picker order. */
fun addAuthMethods(ssoAvailable: Boolean): List<AddAuthMethod> =
    if (ssoAvailable) {
        listOf(AddAuthMethod.APPLE, AddAuthMethod.GOOGLE, AddAuthMethod.WALLET, AddAuthMethod.EMAIL)
    } else {
        listOf(AddAuthMethod.WALLET, AddAuthMethod.EMAIL)
    }

val addAuthWalletChains: List<AddAuthWalletChain> = listOf(AddAuthWalletChain.SOLANA, AddAuthWalletChain.BITTENSOR)

/** The wallet_auth of /auth/add-auth (the sdk WalletAuthArgs fields). */
data class AddWalletAuth(
    val blockchain: String,
    val publicKey: String,
    val message: String,
    val signature: String,
    // the Bittensor wallet the signature was pasted from (null: a wallet signed it),
    // named when the server refuses a signature from another account
    val manualWalletId: String? = null,
)

/**
 * A refused AddAuth: the message to show, and the server's code for it (null for
 * none). [BittensorWallets.SIGNATURE_MISMATCH] after a pasted Bittensor signature
 * has its own words (bittensorSignatureMismatchWallet).
 */
data class AddAuthRefusal(val message: String, val code: String? = null)

/**
 * The wallet_auth for a Bittensor proof signed to add the wallet, or null for
 * a proof signed for anything else: a sign-in or create proof is never added,
 * and an add proof never reaches /auth/login.
 */
fun bittensorAddWalletAuth(proof: BittensorProof): AddWalletAuth? {
    if (bittensorProofRoute(proof) != BittensorProofRoute.ADD_SIGN_IN) {
        return null
    }
    return AddWalletAuth(
        blockchain = BITTENSOR_BLOCKCHAIN,
        publicKey = proof.address,
        message = proof.message,
        signature = proof.signature,
    )
}

/** The auth_jwt pair of /auth/add-auth for an SSO identity token. */
data class AddSsoAuth(val authJwt: String, val authJwtType: String)

sealed class SsoAddOutcome {
    data class Add(val auth: AddSsoAuth) : SsoAddOutcome()
    // the provider or the callback reported an error, or the token is not this attempt's
    data class Failed(val error: String?) : SsoAddOutcome()
    // no add attempt is waiting for this state: not this sheet's
    object Stray : SsoAddOutcome()
}

/**
 * Checks a browser sign-in return against the provider's pending add attempt:
 * the state must name it (consumed here) and the token must carry its nonce.
 * `nonceOf` reads the token's nonce claim (the server verifies the signature).
 */
fun ssoAddOutcome(
    provider: SsoProvider,
    attempts: SsoOAuthAttempts,
    ssoReturn: SsoOAuthReturn,
    nonceOf: (String) -> String?,
): SsoAddOutcome {
    val pending = attempts.take(ssoReturn.state, SSO_OAUTH_PURPOSE_ADD) ?: return SsoAddOutcome.Stray
    val idToken = ssoReturn.idToken
    if (ssoReturn.error != null || idToken.isNullOrEmpty()) {
        return SsoAddOutcome.Failed(ssoReturn.error)
    }
    if (nonceOf(idToken) != pending.nonce) {
        return SsoAddOutcome.Failed(null)
    }
    return SsoAddOutcome.Add(AddSsoAuth(idToken, provider.authJwtType))
}

/** A browser sign-in return for an add attempt, with the provider it came back from. */
data class SsoAddReturn(val provider: SsoProvider, val ssoReturn: SsoOAuthReturn)

/**
 * Hands a browser sign-in return for an add attempt from the LoginActivity
 * (where every `ur://` link arrives) to the add sheet in the main activity.
 * Held for the process: the browser round trip ends in a new LoginActivity,
 * not in the sheet. A process restart drops it, and the user adds the sign-in
 * again.
 */
object SsoAddSignInReturns {
    private val _pending = MutableStateFlow<SsoAddReturn?>(null)
    val pending: StateFlow<SsoAddReturn?> = _pending.asStateFlow()

    fun deliver(addReturn: SsoAddReturn) {
        _pending.value = addReturn
    }

    /** The waiting return, taken once. */
    fun take(): SsoAddReturn? {
        val addReturn = _pending.value
        _pending.value = null
        return addReturn
    }
}

/** A WalletConnect bridge return for the add sheet's Bittensor wallet. */
sealed class BittensorAddReturn {
    data class Proven(val proof: BittensorProof) : BittensorAddReturn()
    // detail: the wallet's own text for wallet_error
    data class Failed(val code: String, val detail: String?) : BittensorAddReturn()
}

/**
 * The add sheet's share of a `ur://bittensor-sign-message` return: a proof or
 * refusal for an add-purpose session, else null (the login's, the Earnings
 * connect's, or a pre-helper return, handled as before).
 */
fun bittensorAddReturn(action: BittensorReturnAction): BittensorAddReturn? = when (action) {
    is BittensorReturnAction.Proven ->
        if (action.route == BittensorProofRoute.ADD_SIGN_IN) BittensorAddReturn.Proven(action.proof) else null
    is BittensorReturnAction.Failed ->
        if (action.purpose == BittensorWallets.PURPOSE_ADD) BittensorAddReturn.Failed(action.code, action.detail) else null
    BittensorReturnAction.Legacy -> null
}

/**
 * Hands a Bittensor bridge return for an add-purpose session from the
 * LoginActivity to the add sheet, like [SsoAddSignInReturns].
 */
object BittensorAddSignInReturns {
    private val _pending = MutableStateFlow<BittensorAddReturn?>(null)
    val pending: StateFlow<BittensorAddReturn?> = _pending.asStateFlow()

    fun deliver(addReturn: BittensorAddReturn) {
        _pending.value = addReturn
    }

    /** The waiting return, taken once. */
    fun take(): BittensorAddReturn? {
        val addReturn = _pending.value
        _pending.value = null
        return addReturn
    }
}

/**
 * Brings the main activity back over the browser tab after a return for the
 * add sheet was handed over. The existing main activity is kept (single top),
 * so the sheet that started the attempt is still there to take it.
 */
private fun returnToAddSheet(activity: android.app.Activity, mainActivity: Class<*>) {
    val intent = android.content.Intent(activity, mainActivity)
    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
    activity.startActivity(intent)
    activity.finish()
}

/**
 * Hands an add attempt's browser sign-in return (`ur://oauth/apple`, which
 * every flavor's LoginActivity receives, or `ur://oauth/google` on the github
 * flavor) to the add sheet.
 */
fun forwardSsoAddSignInReturn(
    activity: android.app.Activity,
    provider: SsoProvider,
    uri: android.net.Uri,
    mainActivity: Class<*>,
) {
    SsoAddSignInReturns.deliver(SsoAddReturn(provider, ssoOAuthReturn(uri)))
    returnToAddSheet(activity, mainActivity)
}

/** Hands an add-purpose Bittensor bridge return to the add sheet. */
fun forwardBittensorAddSignInReturn(activity: android.app.Activity, addReturn: BittensorAddReturn, mainActivity: Class<*>) {
    BittensorAddSignInReturns.deliver(addReturn)
    returnToAddSheet(activity, mainActivity)
}
