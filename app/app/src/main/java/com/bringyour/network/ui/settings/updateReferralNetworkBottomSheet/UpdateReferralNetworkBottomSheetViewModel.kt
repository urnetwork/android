package com.bringyour.network.ui.settings.updateReferralNetworkBottomSheet

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bringyour.network.DeviceManager
import com.bringyour.network.TAG
import com.bringyour.sdk.SetNetworkReferralArgs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UpdateReferralNetworkBottomSheetViewModel @Inject constructor(
    private val deviceManager: DeviceManager,
): ViewModel() {

    private val _isUpdatingReferralNetwork = MutableStateFlow(false)
    val isUpdatingReferralNetwork: StateFlow<Boolean> = _isUpdatingReferralNetwork

    private val _referralCode = mutableStateOf(TextFieldValue(""))
    val referralCode: TextFieldValue get() = _referralCode.value
    val setReferralCode: (TextFieldValue) -> Unit = { _referralCode.value = it }

    var codeInputSupportingText by mutableStateOf("")

    val updateReferralNetwork: (() -> Unit, (String) -> Unit) -> Unit = { onSuccess, onFailure ->

        if (!_isUpdatingReferralNetwork.value) {

            _isUpdatingReferralNetwork.value = true
            codeInputSupportingText = ""

            val args = SetNetworkReferralArgs()
            args.referralCode = _referralCode.value.text

            deviceManager.device?.api?.setNetworkReferral(args) { result, error ->

                if (error != null) {
                    Log.i(TAG, "error setting network referral: ${error.message}")
                    viewModelScope.launch {
                        onFailure("Error setting referral network, please try again later.")
                        codeInputSupportingText = "Error setting referral network, please try again later."
                        _isUpdatingReferralNetwork.value = false
                    }
                    return@setNetworkReferral
                }

                if (result.error != null) {
                    Log.i(TAG, "result error setting network referral: ${result.error.message}")
                    viewModelScope.launch {
                        onFailure(result.error.message)
                        codeInputSupportingText = "Invalid referral code"
                        _isUpdatingReferralNetwork.value = false
                    }
                    return@setNetworkReferral
                }

                viewModelScope.launch {
                    onSuccess()
                    _isUpdatingReferralNetwork.value = false
                }

            }

        }

    }

}
