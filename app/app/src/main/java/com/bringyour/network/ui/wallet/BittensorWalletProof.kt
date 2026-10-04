package com.bringyour.network.ui.wallet

import androidx.annotation.StringRes
import com.bringyour.network.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The app side of a Bittensor coldkey proof: pick a wallet, show the
 * server-issued challenge, take the pasted address and signature, and hand a
 * proof to the flow that asked (sign-in, the create-network second
 * signature, the Earnings coldkey connect, or adding the wallet as a sign-in
 * method).
 *
 * The protocol lives in the SDK (`sdk/bittensor_wallet.go`,
 * `BittensorWalletSession`): what is signed, and whether an answer is
 * acceptable. Here is only the screen state around it, behind
 * [BittensorProofSession] so the state machine runs in plain JVM tests (the
 * gomobile classes need the native library). [SdkBittensorProofSession]
 * adapts the SDK session.
 *
 * Android uses manual entry for both supported wallets: neither Talisman nor
 * TAO.com documents a mobile deep link or WalletConnect interface, so the
 * user signs the shown message in their wallet and pastes the result.
 *
 * Not safe for concurrent use: call from the main thread.
 */
object BittensorWallets {
    // mirror sdk BittensorWalletTalisman / BittensorWalletTaoCom
    const val TALISMAN = "talisman"
    const val TAO_COM = "taocom"
    // mirror sdk BittensorWalletPlatformAndroid
    const val PLATFORM = "android"

    // mirror sdk BittensorWalletPurpose*
    const val PURPOSE_LOGIN = "login"
    const val PURPOSE_CREATE = "create"
    const val PURPOSE_CONNECT = "connect"
    // add the wallet as a sign-in method to the signed-in network (/auth/add-auth)
    const val PURPOSE_ADD = "add"

    // the app's registered return link (unused by the manual transport, kept
    // so a session built here matches what the bridge would return to)
    const val REDIRECT_LINK = "ur://bittensor-sign-message"

    /** The supported wallets, in display order (sdk BittensorWalletIdList). */
    val walletIds: List<String> = listOf(TALISMAN, TAO_COM)

    // mirror sdk BittensorWalletError* codes
    const val ERROR_WALLET = "wallet_error"
    const val ERROR_NO_CHALLENGE = "no_challenge"
    const val ERROR_INVALID_CHALLENGE = "invalid_challenge"
    const val ERROR_EXPIRED = "challenge_expired"
    const val ERROR_MESSAGE_MISMATCH = "message_mismatch"
    const val ERROR_INVALID_ADDRESS = "invalid_ss58_address"
    const val ERROR_ADDRESS_MISMATCH = "address_mismatch"
    const val ERROR_INVALID_SIGNATURE = "invalid_signature"

    /** The message for a session refusal code. */
    @StringRes
    fun errorRes(code: String): Int = when (code) {
        ERROR_INVALID_SIGNATURE -> R.string.bittensor_error_invalid_signature
        ERROR_EXPIRED -> R.string.bittensor_error_challenge_expired
        ERROR_MESSAGE_MISMATCH -> R.string.bittensor_error_message_mismatch
        ERROR_ADDRESS_MISMATCH -> R.string.earnings_wallet_mismatch
        ERROR_INVALID_ADDRESS -> R.string.invalid_ss58_address
        else -> R.string.login_error
    }
}

/** A signed challenge, ready for /auth/login, /auth/network-create or POST /sn/wallet. */
data class BittensorProof(
    val walletId: String,
    val purpose: String,
    val address: String,
    val message: String,
    val signature: String,
)

sealed class BittensorProofOutcome {
    data class Proven(val proof: BittensorProof) : BittensorProofOutcome()
    data class Refused(val code: String) : BittensorProofOutcome()
}

/** One challenge: the SDK session, or a fake in tests. */
interface BittensorProofSession {
    val walletId: String
    val purpose: String
    // the exact message to sign
    val message: String
    fun handleSignature(address: String, signature: String, nowMillis: Long): BittensorProofOutcome
}

/** A session to start: the wallet, why, and the address the challenge is bound to. */
data class BittensorProofRequest(
    val walletId: String,
    val purpose: String,
    val expectedAddress: String?,
)

sealed class BittensorProofStage {
    object Hidden : BittensorProofStage()
    data class Choosing(@StringRes val errorRes: Int? = null) : BittensorProofStage()
    data class Loading(val request: BittensorProofRequest) : BittensorProofStage()
    data class Signing(
        val session: BittensorProofSession,
        val address: String,
        val signature: String,
        @StringRes val errorRes: Int? = null,
    ) : BittensorProofStage()
}

/**
 * The chooser + manual proof sheets. [open] shows the chooser; [choose]
 * returns the session to start (the caller fetches the challenge); then
 * [sessionReady] / [sessionFailed]; then [submit] returns the proof once the
 * session accepts the pasted answer. [openForWallet] skips the chooser (the
 * create-network second signature reuses the wallet that signed in).
 */
class BittensorProofFlow(
    private val nowMillis: () -> Long,
) {
    private val _stage = MutableStateFlow<BittensorProofStage>(BittensorProofStage.Hidden)
    val stage: StateFlow<BittensorProofStage> = _stage.asStateFlow()

    private var purpose: String = BittensorWallets.PURPOSE_LOGIN
    private var expectedAddress: String? = null

    fun open(purpose: String, expectedAddress: String? = null) {
        this.purpose = purpose
        this.expectedAddress = expectedAddress?.trim()?.takeIf { it.isNotEmpty() }
        _stage.value = BittensorProofStage.Choosing()
    }

    /** The session to start for the chosen wallet, or null when the chooser is not up. */
    fun choose(walletId: String): BittensorProofRequest? {
        if (_stage.value !is BittensorProofStage.Choosing || walletId !in BittensorWallets.walletIds) {
            return null
        }
        val request = BittensorProofRequest(walletId, purpose, expectedAddress)
        _stage.value = BittensorProofStage.Loading(request)
        return request
    }

    fun openForWallet(walletId: String, purpose: String, expectedAddress: String?): BittensorProofRequest? {
        open(purpose, expectedAddress)
        return choose(walletId)
    }

    /** The challenge arrived; a stale session (the user moved on) is dropped. */
    fun sessionReady(request: BittensorProofRequest, session: BittensorProofSession) {
        val s = _stage.value
        if (s !is BittensorProofStage.Loading || s.request != request) {
            return
        }
        _stage.value = BittensorProofStage.Signing(
            session = session,
            address = request.expectedAddress ?: "",
            signature = "",
        )
    }

    fun sessionFailed(request: BittensorProofRequest) {
        val s = _stage.value
        if (s !is BittensorProofStage.Loading || s.request != request) {
            return
        }
        _stage.value = BittensorProofStage.Choosing(R.string.login_error)
    }

    fun updateAddress(address: String) {
        val s = _stage.value as? BittensorProofStage.Signing ?: return
        _stage.value = s.copy(address = address, errorRes = null)
    }

    fun updateSignature(signature: String) {
        val s = _stage.value as? BittensorProofStage.Signing ?: return
        _stage.value = s.copy(signature = signature, errorRes = null)
    }

    /** The proof when the session accepts the answer (the sheets close); else null with the reason shown. */
    fun submit(): BittensorProof? {
        val s = _stage.value as? BittensorProofStage.Signing ?: return null
        return when (val outcome = s.session.handleSignature(s.address, s.signature, nowMillis())) {
            is BittensorProofOutcome.Proven -> {
                _stage.value = BittensorProofStage.Hidden
                outcome.proof
            }
            is BittensorProofOutcome.Refused -> {
                _stage.value = s.copy(errorRes = BittensorWallets.errorRes(outcome.code))
                null
            }
        }
    }

    fun dismiss() {
        _stage.value = BittensorProofStage.Hidden
    }
}

/** Where a proof goes next. */
enum class BittensorProofRoute { LOGIN, CREATE_NETWORK, CONNECT_WALLET, ADD_SIGN_IN }

fun bittensorProofRoute(proof: BittensorProof): BittensorProofRoute? = when (proof.purpose) {
    BittensorWallets.PURPOSE_LOGIN -> BittensorProofRoute.LOGIN
    BittensorWallets.PURPOSE_CREATE -> BittensorProofRoute.CREATE_NETWORK
    BittensorWallets.PURPOSE_CONNECT -> BittensorProofRoute.CONNECT_WALLET
    BittensorWallets.PURPOSE_ADD -> BittensorProofRoute.ADD_SIGN_IN
    else -> null
}
