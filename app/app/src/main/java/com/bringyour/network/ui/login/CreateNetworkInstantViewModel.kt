package com.bringyour.network.ui.login

import com.bringyour.network.ui.connection_region.CantReachPrompt
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bringyour.network.NetworkSpaceManagerProvider
import com.bringyour.network.LoginRequest
import com.bringyour.network.ui.components.referral.ReferralCodeInputController
import com.bringyour.network.ui.components.referral.apiReferralCodeChecker
import com.bringyour.sdk.NetworkCreateArgs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

/**
 * Owns the instant (seedphrase-backed) account creation flow.
 *
 * The account and its seedphrase exist server side the moment networkCreate
 * returns, and the phrase is only ever returned once, so both the request and
 * its result live here rather than in composition state: a rotation or a back
 * press must not be able to lose the only copy. The session is persisted
 * before the phrase is published. If another sign-in superseded this request,
 * the one-time phrase is still published for recovery without replacing it.
 */
@HiltViewModel
class CreateNetworkInstantViewModel @Inject constructor(
    private val networkSpaceManagerProvider: NetworkSpaceManagerProvider,
): ViewModel() {

    private val _inProgress = MutableStateFlow(false)
    val inProgress: StateFlow<Boolean> = _inProgress

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _seedphrase = MutableStateFlow<String?>(null)
    val seedphrase: StateFlow<String?> = _seedphrase
    var loginRequest: LoginRequest? = null
        private set

    /**
     * Referral code entry. Instant accounts can be referred too -- the server
     * links the referral on any create path.
     */
    val referralInput = ReferralCodeInputController(
        viewModelScope,
        apiReferralCodeChecker { networkSpaceManagerProvider.getNetworkSpace()?.api },
    )

    fun validateReferralCode(onComplete: (Boolean) -> Unit) {
        referralInput.check(onComplete)
    }

    /**
     * Pre-fills the code a referral link or the Play install referrer named,
     * and checks it, as the create-network screens do. A code the user has
     * already entered is kept.
     */
    fun seedReferralCode(code: String?) {
        val seed = referralCodeSeed(code, referralInput.code.text) ?: return
        referralInput.setCode(TextFieldValue(seed))
        validateReferralCode {}
    }

    fun createNetwork(
        request: LoginRequest,
        termsAgreed: Boolean,
        productUpdates: Boolean = true,
        appLogin: (
            request: LoginRequest,
            byJwt: String,
            newNetwork: Boolean,
            completion: (Boolean) -> Unit,
        ) -> Unit,
    ) {
        if (_inProgress.value || _seedphrase.value != null) {
            return
        }

        val api = request.api
        loginRequest = request

        _inProgress.value = true
        _error.value = null

        val args = NetworkCreateArgs()
        // Main still validates this field. Newer servers generate their own
        // instant-account name and safely ignore this compatibility fallback.
        args.networkName = "guest-${UUID.randomUUID()}"
        // no guestMode: the server dropped guest_mode from network create with
        // the seedphrase path and ignores it; this is a seedphrase account
        args.terms = termsAgreed
        // the sign-up page's "Periodic product updates" line; false here keeps the preference on
        args.productUpdatesOptOut = !productUpdates
        // no userAuth, userName, password or walletAuth -- the server reads that
        // as the seedphrase path and returns a generated phrase with the network

        referralInput.createCode?.let { code ->
            args.referralCode = code
        }

        val completed = AtomicBoolean(false)
        api.networkCreate(args) { result, err ->
            if (!completed.compareAndSet(false, true)) return@networkCreate
            val createdSeedphrase = if (err == null && result?.error == null) {
                result?.seedphrase?.takeIf(String::isNotEmpty)
            } else {
                null
            }
            val byJwt = if (createdSeedphrase != null) {
                result?.network?.byJwt?.takeIf(String::isNotEmpty)
            } else {
                null
            }
            if (byJwt != null) {
                // Persist before handing the one-time phrase back to UI-owned
                // state. This callback must remain effective after onCleared.
                appLogin(request, byJwt, true) { persisted ->
                    _inProgress.value = false
                    // The account exists even if a newer login superseded this
                    // one. Keep its one-time recovery phrase available.
                    _seedphrase.value = createdSeedphrase
                    if (!persisted) _error.value = "Sign-in changed. Keep this recovery phrase to access the created account."
                }
                return@networkCreate
            }
            viewModelScope.launch {
                _inProgress.value = false

                if (err != null) {
                    CantReachPrompt.shared.report(err)
                    _error.value = err.message ?: "Unable to connect. Please try again."
                    return@launch
                }
                if (result?.error != null) {
                    _error.value = result.error.message ?: "Failed to create account"
                    return@launch
                }

                _error.value = "Failed to create account"
            }
        }
    }
}

/**
 * The code to pre-fill the referral field with: an incoming non-blank code,
 * unless the field already holds one.
 */
internal fun referralCodeSeed(incoming: String?, current: String): String? {
    val code = incoming?.trim().orEmpty()
    return code.takeIf { it.isNotEmpty() && current.isEmpty() }
}
