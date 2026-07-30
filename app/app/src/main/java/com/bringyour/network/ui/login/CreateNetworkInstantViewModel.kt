package com.bringyour.network.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bringyour.network.NetworkSpaceManagerProvider
import com.bringyour.sdk.NetworkCreateArgs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Owns the instant (seedphrase-backed) account creation flow.
 *
 * The account and its seedphrase exist server side the moment networkCreate
 * returns, and the phrase is only ever returned once, so both the request and
 * its result live here rather than in composition state: a rotation or a back
 * press must not be able to lose the only copy. The session is persisted
 * before the phrase is published, so a created account is always reachable
 * again even if this screen dies.
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

    fun createNetwork(termsAgreed: Boolean, appLogin: (String) -> Unit) {
        if (_inProgress.value || _seedphrase.value != null) {
            return
        }

        val api = networkSpaceManagerProvider.getNetworkSpace()?.api ?: run {
            _error.value = "Unable to connect. Please try again."
            return
        }

        _inProgress.value = true
        _error.value = null

        val args = NetworkCreateArgs()
        args.terms = termsAgreed
        // no userAuth, userName, password or walletAuth -- the server reads that
        // as the seedphrase path and returns a generated phrase with the network

        api.networkCreate(args) { result, err ->
            viewModelScope.launch {
                _inProgress.value = false

                if (err != null) {
                    _error.value = err.message ?: "Unable to connect. Please try again."
                    return@launch
                }
                if (result?.error != null) {
                    _error.value = result.error.message ?: "Failed to create account"
                    return@launch
                }

                // gomobile binds Go strings as non-null Java strings ("" when the
                // server omits them), so these need emptiness checks, not null ones
                val createdSeedphrase = result?.seedphrase
                val byJwt = result?.network?.byJwt
                if (createdSeedphrase.isNullOrEmpty() || byJwt.isNullOrEmpty()) {
                    _error.value = "Failed to create account"
                    return@launch
                }

                appLogin(byJwt)
                _seedphrase.value = createdSeedphrase
            }
        }
    }
}
