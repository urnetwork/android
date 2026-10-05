package com.bringyour.network.ui.settings

import android.util.Log
import com.bringyour.network.ui.wallet.BittensorProofFlow
import com.bringyour.network.ui.wallet.BittensorProofRequest
import com.bringyour.network.ui.wallet.BittensorWallets
import com.bringyour.network.ui.wallet.startBittensorProofSession
import com.bringyour.sdk.Api
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val TAG = "BittensorAddSignIn"

/**
 * The add sheet's Bittensor wallet: the same chooser and proof as "Sign in
 * with Bittensor" ([BittensorProofFlow]), on a fresh /auth/wallet-challenge
 * signed for the add purpose, then /auth/add-auth with the proof. A
 * WalletConnect wallet signs on the bridge page, and its return reaches the
 * sheet through [BittensorAddSignInReturns]. The proof
 * never reaches /auth/login, so adding a wallet cannot sign in as it, and the
 * session's jwt is left as it is.
 *
 * Callbacks run on the main thread.
 */
class BittensorAddSignInController(
    val flow: BittensorProofFlow,
    private val scope: CoroutineScope,
    private val api: () -> Api?,
    private val setError: (String?) -> Unit,
    private val defaultError: () -> String,
    private val addWalletAuth: (AddWalletAuth) -> Unit,
    // opens a browser-bridge page (WalletConnect); false when no browser opened
    private val openUrl: (String) -> Boolean = { false },
    // the message for a refusal (BittensorWallets.refusalText)
    private val refusalError: (BittensorAddReturn.Failed) -> String = { defaultError() },
) {
    fun start() {
        setError(null)
        flow.open(BittensorWallets.PURPOSE_ADD)
    }

    /** Back on the sheet (from the browser). */
    fun onResumed() {
        flow.onResumed()
    }

    /** A WalletConnect bridge return for this sheet (BittensorAddSignInReturns). */
    fun handleReturn(addReturn: BittensorAddReturn) {
        flow.dismiss()
        when (addReturn) {
            is BittensorAddReturn.Proven -> {
                val walletAuth = bittensorAddWalletAuth(addReturn.proof)
                if (walletAuth == null) {
                    setError(defaultError())
                    return
                }
                setError(null)
                addWalletAuth(walletAuth)
            }
            is BittensorAddReturn.Failed -> setError(refusalError(addReturn))
        }
    }

    fun choose(walletId: String) {
        flow.choose(walletId)?.let { startSession(it) }
    }

    fun submit() {
        val proof = flow.submit() ?: return
        val walletAuth = bittensorAddWalletAuth(proof)
        if (walletAuth == null) {
            setError(defaultError())
            return
        }
        addWalletAuth(walletAuth)
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
                    // a WalletConnect session continues on the bridge page; its
                    // return comes back through BittensorAddSignInReturns
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
}
