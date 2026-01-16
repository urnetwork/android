package com.bringyour.network.ui.components.redeemTransferBalanceCode

import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bringyour.network.DeviceManager
import com.bringyour.network.JwtManager
import com.bringyour.sdk.RedeemBalanceCodeArgs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

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
            onError: () -> Unit
            ) -> Unit = { onSuccess, onError ->

        if (!_isLoading.value && _codeIsValid.value) {

            _isLoading.value = true

            val args = RedeemBalanceCodeArgs()
            args.secret = code.text
            deviceManager.device?.api?.redeemBalanceCode(args) { result, error ->

                viewModelScope.launch {
                    if (error != null) {
                        _isLoading.value = false
                        onError()
                        return@launch
                    }

                    if (result.error != null) {
                        _isLoading.value = false
                        onError()
                        return@launch
                    }

                    onSuccess()
                    _isLoading.value = false

                }

            }

        }

    }

}
