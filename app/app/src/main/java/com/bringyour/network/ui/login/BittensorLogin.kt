package com.bringyour.network.ui.login

import android.util.Log
import com.bringyour.network.ui.wallet.BittensorProof
import com.bringyour.network.ui.wallet.BittensorProofFlow
import com.bringyour.network.ui.wallet.BittensorProofRequest
import com.bringyour.network.ui.wallet.BittensorProofRoute
import com.bringyour.network.ui.wallet.BittensorWallets
import com.bringyour.network.ui.wallet.bittensorProofRoute
import com.bringyour.network.ui.wallet.bittensorSignatureMismatchWallet
import com.bringyour.network.ui.wallet.startBittensorProofSession
import com.bringyour.sdk.Api
import com.bringyour.sdk.AuthLoginArgs
import com.bringyour.sdk.WalletAuthArgs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val TAG = "BittensorLogin"

/** The wallet_auth blockchain for Bittensor (sdk TAO). */
const val BITTENSOR_BLOCKCHAIN = "TAO"

/**
 * The create-network bundle for a create-purpose proof pasted on the manual sheet: it
 * names the wallet for a refusal of a signature from another account.
 */
fun bittensorCreateBundle(proof: BittensorProof): WalletCreateBundle = WalletCreateBundle(
    blockchain = BITTENSOR_BLOCKCHAIN,
    publicKey = proof.address,
    signedMessage = proof.message,
    signature = proof.signature,
    manualWalletId = proof.walletId,
)

/** What a /auth/login answer to a Bittensor proof leads to. */
sealed class BittensorLoginNext {
    data class SignedIn(val networkJwt: String) : BittensorLoginNext()
    // the wallet has no network yet: sign a fresh challenge, bound to it, to create one
    data class CreateNetwork(val walletId: String, val address: String) : BittensorLoginNext()
    data class Failed(val message: String?) : BittensorLoginNext()
    // the pasted signature is not from the entered address: sign again in this wallet
    // (bittensor_error_signature_mismatch)
    data class SignatureMismatch(val walletId: String) : BittensorLoginNext()
}

/**
 * The next step after /auth/login answered a proof pasted on the manual sheet.
 * `errorCode` is the result error's code: the server's signature_mismatch names the
 * wallet the proof was pasted from.
 */
fun bittensorLoginNext(
    proof: BittensorProof,
    networkJwt: String?,
    unlinkedWallet: Boolean,
    errorMessage: String?,
    errorCode: String? = null,
): BittensorLoginNext = when {
    bittensorSignatureMismatchWallet(errorCode, proof.walletId) != null ->
        BittensorLoginNext.SignatureMismatch(proof.walletId)
    errorMessage != null -> BittensorLoginNext.Failed(errorMessage)
    !networkJwt.isNullOrEmpty() -> BittensorLoginNext.SignedIn(networkJwt)
    unlinkedWallet -> BittensorLoginNext.CreateNetwork(proof.walletId, proof.address)
    else -> BittensorLoginNext.Failed(null)
}

/**
 * "Sign in with Bittensor" on every flavor's login screen: the wallet
 * chooser and manual proof ([BittensorProofFlow]), then /auth/login with the
 * proof. A wallet with no network signs a second, address-bound challenge
 * (purpose create) and continues to the create-network screen.
 *
 * Callbacks run on the main thread except `onNetworkJwt`, which runs on the
 * api callback thread (as the other wallet logins do).
 */
class BittensorLoginController(
    val flow: BittensorProofFlow,
    private val scope: CoroutineScope,
    private val api: () -> Api?,
    private val setLoginError: (String?) -> Unit,
    private val setInProgress: (Boolean) -> Unit,
    private val defaultError: () -> String,
    private val onNetworkJwt: (String) -> Unit,
    private val onCreateNetwork: (WalletCreateBundle) -> Unit,
    // opens a browser-bridge page (WalletConnect); false when no browser opened
    private val openUrl: (String) -> Boolean = { false },
    // the line for a signature pasted from this wallet that is not from the entered
    // address (bittensorSignatureMismatchText)
    private val signatureMismatchText: (walletId: String) -> String = { defaultError() },
) {
    fun start() {
        setLoginError(null)
        flow.open(BittensorWallets.PURPOSE_LOGIN)
    }

    /** Back on the login screen (from the browser). */
    fun onResumed() {
        flow.onResumed()
    }

    fun choose(walletId: String) {
        flow.choose(walletId)?.let { startSession(it) }
    }

    fun submit() {
        val proof = flow.submit() ?: return
        when (bittensorProofRoute(proof)) {
            BittensorProofRoute.LOGIN -> login(proof)
            BittensorProofRoute.CREATE_NETWORK -> onCreateNetwork(bittensorCreateBundle(proof))
            else -> setLoginError(defaultError())
        }
    }

    private fun startSession(request: BittensorProofRequest) {
        scope.launch {
            val api = api()
            if (api == null) {
                flow.sessionFailed(request)
                return@launch
            }
            startBittensorProofSession(api, request)
                .onSuccess { session ->
                    flow.sessionReady(request, session)?.let { url ->
                        if (!openUrl(url)) {
                            flow.browserFailed()
                        }
                    }
                }
                .onFailure {
                    Log.i(TAG, "challenge: ${it.message}")
                    flow.sessionFailed(request)
                }
        }
    }

    private fun login(proof: BittensorProof) {
        val api = api()
        if (api == null) {
            setLoginError(defaultError())
            return
        }
        setInProgress(true)
        val walletAuth = WalletAuthArgs()
        walletAuth.blockchain = BITTENSOR_BLOCKCHAIN
        walletAuth.publicKey = proof.address
        walletAuth.message = proof.message
        walletAuth.signature = proof.signature
        val args = AuthLoginArgs()
        args.walletAuth = walletAuth
        // a signature from another account than the address comes back as
        // result.error.code (a 401 error otherwise)
        args.resultErrors = true
        api.authLogin(args) { result, err ->
            val next = bittensorLoginNext(
                proof = proof,
                networkJwt = result?.network?.byJwt,
                unlinkedWallet = result?.walletAuth != null,
                errorMessage = err?.message ?: result?.error?.message,
                errorCode = result?.error?.code,
            )
            if (next is BittensorLoginNext.SignedIn) {
                onNetworkJwt(next.networkJwt)
                return@authLogin
            }
            scope.launch {
                setInProgress(false)
                when (next) {
                    is BittensorLoginNext.CreateNetwork -> {
                        flow.openForWallet(next.walletId, BittensorWallets.PURPOSE_CREATE, next.address)
                            ?.let { startSession(it) }
                    }
                    is BittensorLoginNext.Failed -> setLoginError(next.message ?: defaultError())
                    is BittensorLoginNext.SignatureMismatch -> setLoginError(signatureMismatchText(next.walletId))
                    else -> {}
                }
            }
        }
    }
}
