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

    /**
     * Register the intent the customer is about to pay against, and hand back the price
     * the SERVER quoted.
     *
     * `plan` is required. The server derives the price from pro.yml keyed by plan and
     * answers "Unknown plan." for an empty one -- this used to send only the reference,
     * so every Solana upgrade failed here before the wallet ever opened.
     *
     * `onSuccess` receives the quoted amount in USD. Build the payment url from THAT and
     * never from a constant: the webhook checks the arriving payment against this same
     * number, so a client-side price is how a customer pays and gets nothing.
     */
    val createSolanaPaymentIntent: (
        reference: String,
        plan: String,
        onSuccess: (amountUsd: Double) -> Unit,
        onError: () -> Unit
            ) -> Unit = { reference, plan, onSuccess, onError ->

                val args = SolanaPaymentIntentArgs()
                args.reference = reference
                args.plan = plan

                val api = deviceManager.device?.api
                if (api != null) {
                    api.createSolanaPaymentIntent(args) { result, err ->

                        viewModelScope.launch {

                            if (err != null || result == null) {
                                onError()
                                return@launch
                            }

                            if (result.error != null) {
                                onError()
                                return@launch
                            }

                            // A missing or zero quote is never sellable. The webhook's
                            // check is `amount >= quoted - tolerance`, so at zero it is
                            // satisfied by any payment at all, including none.
                            if (result.amountUsd <= 0.0) {
                                onError()
                                return@launch
                            }

                            onSuccess(result.amountUsd)

                        }

                    }
                } else {
                    onError()
                }
    }

}