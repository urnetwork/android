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
 * signature, or the Earnings coldkey connect).
 *
 * The protocol lives in the SDK (`sdk/bittensor_wallet.go`,
 * `BittensorWalletSession`): what is signed, and whether an answer is
 * acceptable. Here is only the screen state around it, behind
 * [BittensorProofSession] so the state machine runs in plain JVM tests (the
 * gomobile classes need the native library). [SdkBittensorProofSession]
 * adapts the SDK session.
 *
 * Talisman and TAO.com use manual entry on Android: neither documents a
 * mobile deep link, so the user signs the shown message in their wallet and
 * pastes the result. WalletConnect (Nova, Nightly and other substrate
 * wallets) uses the browser bridge: ur.io/bittensor-connect pairs with the
 * wallet app and returns on ur://bittensor-sign-message, where
 * [BittensorBridgeReturns] hands the return to the waiting session.
 *
 * Not safe for concurrent use: call from the main thread.
 */
object BittensorWallets {
    // mirror sdk BittensorWalletTalisman / BittensorWalletTaoCom / BittensorWalletWalletConnect
    const val TALISMAN = "talisman"
    const val TAO_COM = "taocom"
    const val WALLET_CONNECT = "walletconnect"

    // mirror sdk BittensorWalletTransport*
    const val TRANSPORT_MANUAL = "manual"
    const val TRANSPORT_BROWSER_BRIDGE = "browser_bridge"
    // mirror sdk BittensorWalletPlatformAndroid
    const val PLATFORM = "android"

    // mirror sdk BittensorWalletPurpose*
    const val PURPOSE_LOGIN = "login"
    const val PURPOSE_CREATE = "create"
    const val PURPOSE_CONNECT = "connect"

    // the app's registered return link: the bridge page returns here
    const val REDIRECT_LINK = "ur://bittensor-sign-message"

    /** The supported wallets, in display order (sdk BittensorWalletIdList). */
    val walletIds: List<String> = listOf(TALISMAN, TAO_COM, WALLET_CONNECT)

    /** The chooser line under a wallet's name, or null. */
    @StringRes
    fun subtitleRes(walletId: String): Int? = when (walletId) {
        TAO_COM -> R.string.enter_address_manually
        WALLET_CONNECT -> R.string.bittensor_walletconnect_hint
        else -> null
    }

    // mirror sdk BittensorWalletError* codes
    const val ERROR_WALLET = "wallet_error"
    const val ERROR_NO_CHALLENGE = "no_challenge"
    const val ERROR_INVALID_CHALLENGE = "invalid_challenge"
    const val ERROR_EXPIRED = "challenge_expired"
    const val ERROR_MESSAGE_MISMATCH = "message_mismatch"
    const val ERROR_INVALID_ADDRESS = "invalid_ss58_address"
    const val ERROR_ADDRESS_MISMATCH = "address_mismatch"
    const val ERROR_INVALID_SIGNATURE = "invalid_signature"
    const val ERROR_NOT_RETURN = "not_bittensor_return"
    const val ERROR_PURPOSE_MISMATCH = "purpose_mismatch"
    const val ERROR_UNSUPPORTED_WALLET = "unsupported_wallet"
    const val ERROR_NOT_AWAITING = "not_awaiting_wallet"

    // a bridge return these codes refuse belongs to another flow (or none):
    // the waiting session ignores it and keeps waiting
    val foreignReturnCodes = setOf(ERROR_NOT_RETURN, ERROR_PURPOSE_MISMATCH, ERROR_UNSUPPORTED_WALLET, ERROR_NOT_AWAITING)

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
    // detail: the wallet's own text for wallet_error
    data class Refused(val code: String, val detail: String? = null) : BittensorProofOutcome()
}

/** One challenge: the SDK session, or a fake in tests. */
interface BittensorProofSession {
    val walletId: String
    val purpose: String
    // the exact message to sign
    val message: String
    // sdk BittensorWalletTransport*
    val transport: String get() = BittensorWallets.TRANSPORT_MANUAL
    fun handleSignature(address: String, signature: String, nowMillis: Long): BittensorProofOutcome
    /** The bridge page to open (browser_bridge only). */
    fun bridgeUrl(): String? = null
    fun handleBridgeReturn(uri: String, nowMillis: Long): BittensorProofOutcome =
        BittensorProofOutcome.Refused(BittensorWallets.ERROR_NOT_RETURN)
}

/** A bridge return handed to the waiting session. */
sealed class BittensorBridgeReturn {
    // no waiting session, or the return belongs to another flow
    object Ignored : BittensorBridgeReturn()
    data class Proven(val proof: BittensorProof) : BittensorBridgeReturn()
    data class Refused(val purpose: String, val code: String, val detail: String?) : BittensorBridgeReturn()
}

/**
 * The browser-bridge session waiting for its return. The return arrives in a
 * new LoginActivity (ur://bittensor-sign-message), not in the screen that
 * opened the page, so the waiting session is held here for the process.
 * A process restart drops it: the return is then ignored and the user starts
 * again (the challenge is single use and short lived anyway).
 */
class BittensorBridgeReturns {
    private var pending: BittensorProofSession? = null

    val waiting: Boolean get() = pending != null

    fun begin(session: BittensorProofSession) {
        pending = session
    }

    fun cancel() {
        pending = null
    }

    fun take(uri: String, nowMillis: Long): BittensorBridgeReturn {
        val session = pending ?: return BittensorBridgeReturn.Ignored
        return when (val outcome = session.handleBridgeReturn(uri, nowMillis)) {
            is BittensorProofOutcome.Proven -> {
                pending = null
                BittensorBridgeReturn.Proven(outcome.proof)
            }
            is BittensorProofOutcome.Refused -> {
                if (outcome.code in BittensorWallets.foreignReturnCodes) {
                    BittensorBridgeReturn.Ignored
                } else {
                    pending = null
                    BittensorBridgeReturn.Refused(session.purpose, outcome.code, outcome.detail)
                }
            }
        }
    }

    companion object {
        val shared = BittensorBridgeReturns()
    }
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
    // the bridge page is open in the browser; the return comes back through LoginActivity
    data class AwaitingBrowser(val walletId: String) : BittensorProofStage()
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
    private val bridgeReturns: BittensorBridgeReturns = BittensorBridgeReturns.shared,
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

    /**
     * The challenge arrived; a stale session (the user moved on) is dropped.
     * Returns the bridge page to open for a browser-bridge session (it now
     * waits in [BittensorBridgeReturns]), else null.
     */
    fun sessionReady(request: BittensorProofRequest, session: BittensorProofSession): String? {
        val s = _stage.value
        if (s !is BittensorProofStage.Loading || s.request != request) {
            return null
        }
        if (session.transport == BittensorWallets.TRANSPORT_BROWSER_BRIDGE) {
            val url = session.bridgeUrl()
            if (url == null) {
                _stage.value = BittensorProofStage.Choosing(R.string.login_error)
                return null
            }
            bridgeReturns.begin(session)
            _stage.value = BittensorProofStage.AwaitingBrowser(session.walletId)
            return url
        }
        _stage.value = BittensorProofStage.Signing(
            session = session,
            address = request.expectedAddress ?: "",
            signature = "",
        )
        return null
    }

    /** The browser could not be opened. */
    fun browserFailed() {
        if (_stage.value !is BittensorProofStage.AwaitingBrowser) {
            return
        }
        bridgeReturns.cancel()
        _stage.value = BittensorProofStage.Choosing(R.string.login_error)
    }

    /** Back on screen: a bridge that already returned (or was dropped) is done waiting. */
    fun onResumed() {
        if (_stage.value is BittensorProofStage.AwaitingBrowser && !bridgeReturns.waiting) {
            _stage.value = BittensorProofStage.Hidden
        }
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
        if (_stage.value is BittensorProofStage.AwaitingBrowser) {
            bridgeReturns.cancel()
        }
        _stage.value = BittensorProofStage.Hidden
    }
}

/** Where a proof goes next. */
enum class BittensorProofRoute { LOGIN, CREATE_NETWORK, CONNECT_WALLET }

fun bittensorProofRoute(proof: BittensorProof): BittensorProofRoute? = when (proof.purpose) {
    BittensorWallets.PURPOSE_LOGIN -> BittensorProofRoute.LOGIN
    BittensorWallets.PURPOSE_CREATE -> BittensorProofRoute.CREATE_NETWORK
    BittensorWallets.PURPOSE_CONNECT -> BittensorProofRoute.CONNECT_WALLET
    else -> null
}

/** What LoginActivity does with a ur://bittensor-sign-message return. */
sealed class BittensorReturnAction {
    // no waiting session: the pre-helper return handling
    object Legacy : BittensorReturnAction()
    data class Proven(val route: BittensorProofRoute, val proof: BittensorProof) : BittensorReturnAction()
    data class Failed(val purpose: String, val code: String, val detail: String?) : BittensorReturnAction()
}

fun bittensorReturnAction(
    uri: String,
    nowMillis: Long,
    bridgeReturns: BittensorBridgeReturns = BittensorBridgeReturns.shared,
): BittensorReturnAction = when (val r = bridgeReturns.take(uri, nowMillis)) {
    BittensorBridgeReturn.Ignored -> BittensorReturnAction.Legacy
    is BittensorBridgeReturn.Refused -> BittensorReturnAction.Failed(r.purpose, r.code, r.detail)
    is BittensorBridgeReturn.Proven -> bittensorProofRoute(r.proof)
        ?.let { BittensorReturnAction.Proven(it, r.proof) }
        ?: BittensorReturnAction.Failed(r.proof.purpose, BittensorWallets.ERROR_PURPOSE_MISMATCH, null)
}
