package com.bringyour.network.ui.settings

import com.bringyour.network.ui.login.APPLE_OAUTH_PURPOSE_ADD
import com.bringyour.network.ui.login.AUTH_JWT_TYPE_APPLE
import com.bringyour.network.ui.login.AppleOAuthAttempts
import com.bringyour.network.ui.login.BITTENSOR_BLOCKCHAIN
import com.bringyour.network.ui.wallet.BittensorProof
import com.bringyour.network.ui.wallet.BittensorProofRoute
import com.bringyour.network.ui.wallet.bittensorProofRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The add sign-in method sheet's options, the same on every app (ur.io
 * AddSignInSheet, apple AddAuthSheetMethods): Apple, Google, a wallet
 * (Solana or Bittensor), and an email or phone with a code. Apple and Google
 * are offered where the flavor's login offers them: the Google SSO flavors
 * (play, solana_dapp, ethos_dapp) sign in with both, and the ungoogle
 * (github) flavor with neither.
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
)

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

/** An Apple return (`ur://oauth/apple?state&id_token|error`) for the add sheet. */
data class AppleOAuthReturn(
    val state: String?,
    val idToken: String?,
    val error: String?,
)

sealed class AppleAddOutcome {
    data class Add(val auth: AddSsoAuth) : AppleAddOutcome()
    // Apple or the callback reported an error, or the token is not this attempt's
    data class Failed(val error: String?) : AppleAddOutcome()
    // no add attempt is waiting for this state: not this sheet's
    object Stray : AppleAddOutcome()
}

/**
 * Checks an Apple return against the pending add attempt: the state must
 * name it (consumed here) and the token must carry its nonce. `nonceOf`
 * reads the token's nonce claim (the server verifies the signature).
 */
fun appleAddOutcome(
    attempts: AppleOAuthAttempts,
    appleReturn: AppleOAuthReturn,
    nonceOf: (String) -> String?,
): AppleAddOutcome {
    val pending = attempts.take(appleReturn.state, APPLE_OAUTH_PURPOSE_ADD) ?: return AppleAddOutcome.Stray
    val idToken = appleReturn.idToken
    if (appleReturn.error != null || idToken.isNullOrEmpty()) {
        return AppleAddOutcome.Failed(appleReturn.error)
    }
    if (nonceOf(idToken) != pending.nonce) {
        return AppleAddOutcome.Failed(null)
    }
    return AppleAddOutcome.Add(AddSsoAuth(idToken, AUTH_JWT_TYPE_APPLE))
}

/**
 * Hands an Apple return for an add attempt from the LoginActivity (where every
 * `ur://` link arrives) to the add sheet in the main activity. Held for the
 * process: the browser round trip ends in a new LoginActivity, not in the
 * sheet. A process restart drops it, and the user adds Apple again.
 */
object AppleAddSignInReturns {
    private val _pending = MutableStateFlow<AppleOAuthReturn?>(null)
    val pending: StateFlow<AppleOAuthReturn?> = _pending.asStateFlow()

    fun deliver(appleReturn: AppleOAuthReturn) {
        _pending.value = appleReturn
    }

    /** The waiting return, taken once. */
    fun take(): AppleOAuthReturn? {
        val appleReturn = _pending.value
        _pending.value = null
        return appleReturn
    }
}

/**
 * Hands an add attempt's Apple return (`ur://oauth/apple`, which every flavor's
 * LoginActivity receives) to the add sheet and brings the main activity back
 * over the browser tab. The existing main activity is kept (single top), so
 * the sheet that started the attempt is still there to take it.
 */
fun forwardAppleAddSignInReturn(activity: android.app.Activity, uri: android.net.Uri, mainActivity: Class<*>) {
    AppleAddSignInReturns.deliver(
        AppleOAuthReturn(
            state = uri.getQueryParameter("state"),
            idToken = uri.getQueryParameter("id_token"),
            error = uri.getQueryParameter("error"),
        )
    )
    val intent = android.content.Intent(activity, mainActivity)
    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
    activity.startActivity(intent)
    activity.finish()
}
