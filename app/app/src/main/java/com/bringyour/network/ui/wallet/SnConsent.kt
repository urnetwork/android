package com.bringyour.network.ui.wallet

import android.app.Activity
import android.content.Intent
import androidx.annotation.StringRes
import com.bringyour.network.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/*
 * The network's payout consent (CONSENT.md), beside the settlement v2 tile.
 * The network owner signs once with a Bittensor coldkey, and every provider
 * client of the network earns to it; without a consent an app-only provider
 * is not paid. The SDK chooses the range and requests the challenge
 * (SnNetworkConsentChallenge: a consent always starts next epoch), submits the
 * signed consent (SnNetworkConsentSubmit) and reads the newest one
 * (SnNetworkConsentStatus). The steps match every app's: choose the wallet
 * in the chooser sign-in uses, enter the coldkey the consent will name, take
 * the challenge for it, and sign the server's exact message in an SDK session
 * of the consent purpose.
 */

/** The network's newest payout consent, as the SDK's SnNetworkConsentStatus. */
data class SnConsentStatusState(
    // a consent whose through epoch has not passed: in force, or starting next epoch
    val active: Boolean,
    val coldkeySs58: String,
)

/** The consent after a read. */
sealed class SnConsentLoad {
    object Loading : SnConsentLoad()

    /** the status could not be read */
    object Unavailable : SnConsentLoad()

    data class Loaded(val status: SnConsentStatusState) : SnConsentLoad()
}

/** Why the last attempt ended without a consent. */
sealed class SnConsentOutcome {
    /** the challenge's 300 s passed before the signed consent arrived (sn_consent_expired) */
    object Expired : SnConsentOutcome()

    /**
     * a signature that is not the coldkey's over the challenge: the server's
     * signature_mismatch, or a proof of another message or coldkey than the
     * challenge's, never submitted (sn_consent_failed)
     */
    object SignatureMismatch : SnConsentOutcome()

    /** the session is not the network owner's (sn_consent_owner_only) */
    object OwnerOnly : SnConsentOutcome()

    /** anything else, shown as a failed wallet connect is: the detail */
    data class Failed(val detail: String?) : SnConsentOutcome()
}

/** The coldkey the consent will name, entered after the wallet is chosen. Never prefilled. */
data class SnConsentColdkeyEntry(
    val walletId: String,
    val address: String = "",
    // not a Bittensor (ss58) address: nothing was requested for it
    val invalid: Boolean = false,
)

/** The flow's state beside its wallet sheets. */
data class SnConsentState(
    val load: SnConsentLoad = SnConsentLoad.Loading,
    // the coldkey entry, between the wallet chooser and the challenge
    val coldkeyEntry: SnConsentColdkeyEntry? = null,
    // the signed consent is being submitted and its status read again
    val submitting: Boolean = false,
    // the last attempt's refusal, until the next attempt
    val outcome: SnConsentOutcome? = null,
)

/** What the payout consent block shows. */
data class SnConsentTile(
    val loading: Boolean,
    // "Try again" in place of the status
    val unavailable: Boolean,
    // sn_consent_active with this coldkey, Change and its note; null: sn_consent_none with Sign
    val activeColdkeySs58: String?,
    // sn_consent_signing: from the challenge until the consent is submitted
    val signing: Boolean,
    val outcome: SnConsentOutcome?,
    // Sign or Change
    val actionEnabled: Boolean,
)

/**
 * The block for the flow's state and its wallet sheets' stage. An active
 * consent may start only next epoch (a new one always does); either way it
 * shows its coldkey with Change and the note that a change takes effect from
 * the next epoch. The owner-only refusal holds until the next session: no
 * other session of this one can sign.
 */
fun snConsentTile(state: SnConsentState, stage: BittensorProofStage): SnConsentTile {
    val signing = state.submitting ||
        stage is BittensorProofStage.Loading ||
        stage is BittensorProofStage.Signing ||
        stage is BittensorProofStage.AwaitingBrowser
    val status = (state.load as? SnConsentLoad.Loaded)?.status
    return SnConsentTile(
        loading = state.load is SnConsentLoad.Loading,
        unavailable = state.load is SnConsentLoad.Unavailable,
        activeColdkeySs58 = status?.takeIf { it.active }?.coldkeySs58,
        signing = signing,
        outcome = state.outcome.takeIf { !signing },
        actionEnabled = status != null && !signing && state.outcome != SnConsentOutcome.OwnerOnly,
    )
}

/** The pinned line for a refusal; null for [SnConsentOutcome.Failed], which shows its detail. */
@StringRes
fun snConsentOutcomeRes(outcome: SnConsentOutcome): Int? = when (outcome) {
    SnConsentOutcome.Expired -> R.string.sn_consent_expired
    SnConsentOutcome.SignatureMismatch -> R.string.sn_consent_failed
    SnConsentOutcome.OwnerOnly -> R.string.sn_consent_owner_only
    is SnConsentOutcome.Failed -> null
}

/**
 * An SDK refusal by its code: SnSetWalletResult.Error.Code
 * ([SnProtocolException]), else the "code: message" prefix of the error.
 */
fun snConsentOutcome(error: Throwable): SnConsentOutcome =
    when ((error as? SnProtocolException)?.code ?: EarningsViewModel.claimFailureCode(error.message)) {
        EarningsViewModel.SN_CODE_NETWORK_OWNER_ONLY -> SnConsentOutcome.OwnerOnly
        EarningsViewModel.SN_CODE_CHALLENGE_EXPIRED -> SnConsentOutcome.Expired
        EarningsViewModel.SN_CODE_SIGNATURE_MISMATCH -> SnConsentOutcome.SignatureMismatch
        else -> SnConsentOutcome.Failed(error.message)
    }

// ---- the WalletConnect bridge return

/** A WalletConnect bridge return for the payout consent. */
sealed class SnConsentReturn {
    data class Proven(val proof: BittensorProof) : SnConsentReturn()

    // message: the refusal in this app's words (bittensorRefusalMessage)
    data class Failed(val code: String, val message: String) : SnConsentReturn()
}

/**
 * The consent's share of a `ur://bittensor-sign-message` return: the proof
 * or refusal of a consent session, else null (another flow's, or a
 * pre-helper return, handled as before).
 */
fun snConsentReturn(
    action: BittensorReturnAction,
    refusalMessage: (BittensorReturnAction.Failed) -> String,
): SnConsentReturn? = when (action) {
    is BittensorReturnAction.Proven ->
        if (action.route == BittensorProofRoute.CONSENT) SnConsentReturn.Proven(action.proof) else null
    is BittensorReturnAction.Failed ->
        if (action.purpose == BittensorWallets.PURPOSE_CONSENT) {
            SnConsentReturn.Failed(action.code, refusalMessage(action))
        } else {
            null
        }
    BittensorReturnAction.Legacy -> null
}

/**
 * Hands a consent return from the LoginActivity (where the bridge returns)
 * to the Earnings screen, which takes it once. Held for the process, like
 * the add sheet's returns.
 */
object SnConsentReturns {
    private val _pending = MutableStateFlow<SnConsentReturn?>(null)
    val pending: StateFlow<SnConsentReturn?> = _pending.asStateFlow()

    fun deliver(consentReturn: SnConsentReturn) {
        _pending.value = consentReturn
    }

    /** The waiting return, taken once. */
    fun take(): SnConsentReturn? {
        val consentReturn = _pending.value
        _pending.value = null
        return consentReturn
    }
}

/**
 * Hands the return over and brings the main activity back over the browser
 * tab. The existing main activity is kept (single top), so the Earnings
 * screen that opened the bridge is still there to take it.
 */
fun forwardSnConsentReturn(activity: Activity, consentReturn: SnConsentReturn, mainActivity: Class<*>) {
    SnConsentReturns.deliver(consentReturn)
    val intent = Intent(activity, mainActivity)
    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    activity.startActivity(intent)
    activity.finish()
}

// ---- the flow

/**
 * The payout consent block and its wallet sheets. The earnings view model
 * owns one and points it at the device's protocol source, like [SnLedgerFlow].
 * Sign and Change wallet run the same steps: the sign-in wallet chooser; the
 * coldkey the consent will name, entered and checked locally before any
 * request; a fresh challenge for it in a consent session of the chosen
 * wallet; the wallet's signature over the server's message on the manual
 * sheet or the WalletConnect bridge page; then the submission and the status
 * read again. Only the challenge out for signature, signed by the coldkey it
 * names, is ever submitted.
 */
class SnConsentFlow(
    private val scope: CoroutineScope,
    // the wallet chooser and the manual sheet, as sign-in shows them, for consent sessions
    val proofFlow: BittensorProofFlow = BittensorProofFlow(nowMillis = System::currentTimeMillis),
) {
    private var source: EarningsProtocolSource = NoProtocolSource

    private val _state = MutableStateFlow(SnConsentState())
    val state: StateFlow<SnConsentState> = _state.asStateFlow()

    /** The challenge out for signature: the coldkey and the server's exact message. */
    private data class Challenge(val coldkeySs58: String, val message: String)

    private var awaiting: Challenge? = null

    /** A new device (or none): drop what the last one read and signed. */
    fun reset(source: EarningsProtocolSource) {
        this.source = source
        proofFlow.dismiss()
        awaiting = null
        _state.value = SnConsentState()
    }

    /** Reads the network's newest consent. */
    suspend fun refresh() {
        val s = source
        if (!s.available) {
            _state.update { it.copy(load = SnConsentLoad.Loading) }
            return
        }
        val load = s.consentStatus().fold(
            onSuccess = { SnConsentLoad.Loaded(it) },
            onFailure = { SnConsentLoad.Unavailable },
        )
        if (s === source) {
            _state.update { it.copy(load = load) }
        }
    }

    /** Try again after an unavailable read. */
    fun retry() {
        _state.update { it.copy(load = SnConsentLoad.Loading) }
        scope.launch { refresh() }
    }

    /** Sign with wallet, or Change wallet: the wallet chooser. */
    fun start() {
        if (_state.value.submitting) {
            return
        }
        awaiting = null
        _state.update { it.copy(coldkeyEntry = null, outcome = null) }
        proofFlow.open(BittensorWallets.PURPOSE_CONSENT)
    }

    /** The chooser's wallet; next, the coldkey the consent will name. */
    fun chooseWallet(walletId: String) {
        if (proofFlow.stage.value !is BittensorProofStage.Choosing || walletId !in BittensorWallets.walletIds) {
            return
        }
        proofFlow.dismiss()
        _state.update { it.copy(coldkeyEntry = SnConsentColdkeyEntry(walletId)) }
    }

    fun updateColdkey(address: String) {
        _state.update { s -> s.copy(coldkeyEntry = s.coldkeyEntry?.copy(address = address, invalid = false)) }
    }

    fun dismissColdkey() {
        _state.update { it.copy(coldkeyEntry = null) }
    }

    /**
     * The entered coldkey, checked locally (validateSs58) before any request,
     * then a consent session of the chosen wallet over a fresh challenge for
     * it. A WalletConnect session opens its bridge page with `openUrl`; its
     * return comes back through [SnConsentReturns]. A challenge that arrives
     * after its attempt was left is stale: never shown, never signed. A
     * challenge refused for another reason than the owner or the clock returns
     * to the chooser with its error, as any challenge the chooser could not get.
     */
    fun continueWithColdkey(openUrl: (String) -> Boolean = { false }) {
        val entry = _state.value.coldkeyEntry ?: return
        val coldkeySs58 = entry.address.trim()
        val s = source
        if (!s.validateSs58(coldkeySs58)) {
            _state.update { it.copy(coldkeyEntry = entry.copy(invalid = true)) }
            return
        }
        _state.update { it.copy(coldkeyEntry = null) }
        val request = proofFlow.openForWallet(entry.walletId, BittensorWallets.PURPOSE_CONSENT, coldkeySs58) ?: return
        scope.launch {
            val started = s.consentSession(entry.walletId, coldkeySs58)
            if (s !== source || proofFlow.stage.value != BittensorProofStage.Loading(request)) {
                return@launch
            }
            started.fold(
                onSuccess = { session ->
                    awaiting = Challenge(coldkeySs58, session.message)
                    proofFlow.sessionReady(request, session)?.let { url ->
                        if (!openUrl(url)) {
                            proofFlow.browserFailed()
                        }
                    }
                },
                onFailure = { e ->
                    when (val outcome = snConsentOutcome(e)) {
                        is SnConsentOutcome.Failed -> proofFlow.sessionFailed(request)
                        else -> {
                            proofFlow.dismiss()
                            _state.update { it.copy(outcome = outcome) }
                        }
                    }
                },
            )
        }
    }

    /**
     * The manual sheet's Continue: the session checks the pasted answer. A
     * challenge past its expiry is spent, so the sheet closes on it.
     */
    fun submit() {
        val proof = proofFlow.submit()
        if (proof != null) {
            submitProof(proof)
            return
        }
        if ((proofFlow.stage.value as? BittensorProofStage.Signing)?.errorCode == BittensorWallets.ERROR_EXPIRED) {
            proofFlow.dismiss()
            fail(SnConsentOutcome.Expired)
        }
    }

    /** A WalletConnect bridge return, checked by the waiting session in the LoginActivity. */
    fun handleReturn(consentReturn: SnConsentReturn) {
        proofFlow.dismiss()
        when (consentReturn) {
            is SnConsentReturn.Proven -> submitProof(consentReturn.proof)
            is SnConsentReturn.Failed -> fail(
                if (consentReturn.code == BittensorWallets.ERROR_EXPIRED) {
                    SnConsentOutcome.Expired
                } else {
                    SnConsentOutcome.Failed(consentReturn.message)
                }
            )
        }
    }

    /** Back on screen (from the browser). */
    fun onResumed() {
        proofFlow.onResumed()
    }

    private fun fail(outcome: SnConsentOutcome) {
        awaiting = null
        _state.update { it.copy(outcome = outcome) }
    }

    private fun submitProof(proof: BittensorProof) {
        val challenge = awaiting
        if (challenge == null ||
            proof.purpose != BittensorWallets.PURPOSE_CONSENT ||
            proof.address != challenge.coldkeySs58 ||
            proof.message != challenge.message
        ) {
            fail(SnConsentOutcome.SignatureMismatch)
            return
        }
        awaiting = null
        val s = source
        _state.update { it.copy(submitting = true, outcome = null) }
        scope.launch {
            val submitted = s.consentSubmit(proof.address, proof.message, proof.signature)
            if (s !== source) {
                return@launch
            }
            if (submitted.isSuccess) {
                // the new consent, which starts next epoch
                refresh()
            }
            if (s === source) {
                _state.update { it.copy(submitting = false, outcome = submitted.exceptionOrNull()?.let(::snConsentOutcome)) }
            }
        }
    }
}
