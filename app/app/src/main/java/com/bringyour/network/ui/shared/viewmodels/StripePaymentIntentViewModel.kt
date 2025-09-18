package com.bringyour.network.ui.shared.viewmodels

import android.util.Log
import androidx.compose.ui.text.toUpperCase
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bringyour.network.DeviceManager
import com.bringyour.network.TAG
import com.bringyour.sdk.StripeCreatePaymentIntentArgs
import com.bringyour.sdk.StripeCreatePaymentIntentResult
import com.bringyour.sdk.StripePaymentIntentList
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.Locale.getDefault
import javax.inject.Inject

@HiltViewModel
class StripePaymentIntentViewModel @Inject constructor(
    deviceManager: DeviceManager,
): ViewModel() {

    private val _isCreatingIntents = MutableStateFlow<Boolean>(true)
    val isCreatingIntents: StateFlow<Boolean> = _isCreatingIntents.asStateFlow()

    val createPaymentIntent: (onSuccess: (StripeCreatePaymentIntentResult) -> Unit) -> Unit = { onSuccess ->

        val args = StripeCreatePaymentIntentArgs()
        deviceManager.device?.api?.createStripePaymentIntent(args) { result, err ->

            viewModelScope.launch {

                if (err != null) {
                    _isCreatingIntents.value = false
                    Log.i(TAG, "error creating stripe payment intent: ${err.message}")
                    return@launch
                }

                if (result.error != null) {
                    _isCreatingIntents.value = false
                    Log.i(TAG, "result error creating stripe payment intent: ${result.error}")
                    return@launch
                }

                Log.i(TAG, "create intent success: $result")
                onSuccess(result)
                _isCreatingIntents.value = false

            }

        }

    }

    val findClientSecret: (list: StripePaymentIntentList, subType: String) -> String? = { list, subType ->

        val n = list.len()
        var clientSecret: String? = null

        for (i in 0 until n) {

            val intent = list.get(i)
            if (intent.subscriptionType.uppercase(getDefault()) == subType.uppercase(getDefault())) {
                clientSecret = intent.clientSecret
            }

        }

        clientSecret

    }

}