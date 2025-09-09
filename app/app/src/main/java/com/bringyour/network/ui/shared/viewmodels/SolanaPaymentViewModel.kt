package com.bringyour.network.ui.shared.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bringyour.network.DeviceManager
import com.bringyour.network.TAG
import com.bringyour.sdk.SolanaPaymentIntentArgs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SolanaPaymentViewModel @Inject constructor(
    deviceManager: DeviceManager,
): ViewModel() {

    private val _pendingSolanaSubscriptionReference = MutableStateFlow<String?>(null)
    val pendingSolanaSubscriptionReference: StateFlow<String?> = _pendingSolanaSubscriptionReference.asStateFlow()

    val setPendingSolanaSubscriptionReference: (String?) -> Unit = {
        _pendingSolanaSubscriptionReference.value = it
    }

    val createSolanaPaymentIntent: (
        reference: String,
        onSuccess: () -> Unit,
        onError: () -> Unit
            ) -> Unit = { reference, onSuccess, onError ->

                val args = SolanaPaymentIntentArgs()
                args.reference = reference

                deviceManager.device?.api?.createSolanaPaymentIntent(args) { result, err ->

                    viewModelScope.launch {

                        if (err != null || result == null) {
                            onError()
                            return@launch
                        }

                        if (result.error != null) {
                            onError()
                            return@launch
                        }

                        onSuccess()

                    }

                }
    }

}