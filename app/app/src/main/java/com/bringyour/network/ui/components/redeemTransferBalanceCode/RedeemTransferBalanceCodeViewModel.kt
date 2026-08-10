package com.bringyour.network.ui.components.redeemTransferBalanceCode

import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bringyour.network.DeviceManager
import com.bringyour.sdk.RedeemBalanceCodeArgs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Why a redeem failed. These MUST stay distinguishable: a transport failure can
 * arrive after the server already committed the redeem, so reporting it as "bad
 * code" tells the user their (consumed) code is invalid. The server's `result.error`
 * payload, by contrast, is an authoritative rejection.
 */
sealed class RedeemBalanceCodeFailure {
    /** No server answer (network failure, device/api not up). The redeem may have committed. */
    object Transport : RedeemBalanceCodeFailure()

    /** Server answered: this code was already redeemed. */
    object AlreadyRedeemed : RedeemBalanceCodeFailure()

    /** Server answered: unknown/invalid code. */
    object Invalid : RedeemBalanceCodeFailure()
}

@HiltViewModel
class RedeemTransferBalanceCodeViewModel @Inject constructor(
    deviceManager: DeviceManager
): ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _code = MutableStateFlow(TextFieldValue(""))
    val code: TextFieldValue get() = _code.value

    val onTextChanged: (newCode: TextFieldValue) -> Unit = {
        _code.value = it
        _codeIsValid.value = it.text.length == 26
    }

    private val _codeIsValid = MutableStateFlow(false)
    val codeIsValid: StateFlow<Boolean> = _codeIsValid.asStateFlow()

    val redeem: (
            onSuccess: () -> Unit,
            onError: (RedeemBalanceCodeFailure) -> Unit
            ) -> Unit = { onSuccess, onError ->

        if (!_isLoading.value && _codeIsValid.value) {

            _isLoading.value = true

            val args = RedeemBalanceCodeArgs()
            args.secret = code.text
            val api = deviceManager.device?.api
            if (api != null) {
                api.redeemBalanceCode(args) { result, error ->

                    viewModelScope.launch {
                        if (error != null) {
                            // no server answer -- the redeem may have committed
                            // server-side. Never report this as a bad code.
                            _isLoading.value = false
                            onError(RedeemBalanceCodeFailure.Transport)
                            return@launch
                        }

                        result.error?.let { resultError ->
                            // authoritative server rejection. The server today says
                            // "Unknown balance code." for both unknown and
                            // already-redeemed; classify by message so an explicit
                            // already-redeemed answer surfaces as such
                            _isLoading.value = false
                            val failure = if (
                                resultError.message?.contains("already", ignoreCase = true) == true ||
                                resultError.message?.contains("redeemed", ignoreCase = true) == true
                            ) {
                                RedeemBalanceCodeFailure.AlreadyRedeemed
                            } else {
                                RedeemBalanceCodeFailure.Invalid
                            }
                            onError(failure)
                            return@launch
                        }

                        onSuccess()
                        _isLoading.value = false

                    }

                }
            } else {
                _isLoading.value = false
                onError(RedeemBalanceCodeFailure.Transport)
            }

        }

    }

}
