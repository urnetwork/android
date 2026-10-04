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
 * signed for the add purpose, then /auth/add-auth with the proof. The proof
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
) {
    fun start() {
        setError(null)
        flow.open(BittensorWallets.PURPOSE_ADD)
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
                .onSuccess { flow.sessionReady(request, it) }
                .onFailure {
                    Log.i(TAG, "challenge: ${it.message}")
                    flow.sessionFailed(request)
                }
        }
    }
}
