package com.bringyour.network.ui.shared.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bringyour.network.DeviceManager
import com.bringyour.network.ui.account.GuestAccount
import com.bringyour.network.ui.account.PurchaseRefusal
import com.bringyour.sdk.SolanaPaymentIntentArgs
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The Solana Pay hand-off: registers the payment intent and remembers the payment the
 * wallet was opened for. The pending payment is persisted (PendingSolanaPaymentStore,
 * UPGRADE.md N6) so the return-path check survives the system killing the app while
 * the wallet is in front.
 */
@HiltViewModel
class SolanaPaymentViewModel @Inject constructor(
    deviceManager: DeviceManager,
    @ApplicationContext context: Context,
): ViewModel() {

    private val store = PendingSolanaPaymentStore(
        object : PendingSolanaPaymentStore.Prefs {
            private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

            override fun getString(key: String): String? = prefs.getString(key, null)

            override fun putStrings(values: Map<String, String?>) {
                val edit = prefs.edit()
                values.forEach { (key, value) ->
                    if (value == null) edit.remove(key) else edit.putString(key, value)
                }
                edit.apply()
            }
        }
    )

    // the last intent the server quoted, so the opened payment persists with its plan
    @Volatile
    private var lastIntent: Triple<String, String, Double>? = null

    private val _pendingSolanaSubscriptionReference = MutableStateFlow<String?>(store.load()?.reference)
    val pendingSolanaSubscriptionReference: StateFlow<String?> = _pendingSolanaSubscriptionReference.asStateFlow()

    /** The persisted pending payment, if the check for it has not ended. */
    internal val pendingSolanaPayment: PendingSolanaPayment?
        get() = store.load()

    val setPendingSolanaSubscriptionReference: (String?) -> Unit = { reference ->
        if (reference == null) {
            store.clear()
        } else {
            val intent = lastIntent?.takeIf { it.first == reference }
            store.save(
                PendingSolanaPayment(
                    reference = reference,
                    plan = intent?.second ?: "",
                    amountUsd = intent?.third ?: 0.0,
                    createdAtMillis = System.currentTimeMillis(),
                )
            )
        }
        _pendingSolanaSubscriptionReference.value = reference
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
     *
     * `onError` says what the failure leads to: a legacy guest network is refused with
     * `guest_sign_in_required`, which opens the add-sign-in sheet
     * (GuestAccount.purchaseRefusal); everything else is a payment error.
     */
    val createSolanaPaymentIntent: (
        reference: String,
        plan: String,
        onSuccess: (amountUsd: Double) -> Unit,
        onError: (PurchaseRefusal) -> Unit
            ) -> Unit = { reference, plan, onSuccess, onError ->

                val args = SolanaPaymentIntentArgs()
                args.reference = reference
                args.plan = plan

                val api = deviceManager.device?.api
                if (api != null) {
                    api.createSolanaPaymentIntent(args) { result, err ->

                        viewModelScope.launch {

                            if (err != null || result == null) {
                                onError(PurchaseRefusal.PaymentError)
                                return@launch
                            }

                            if (result.error != null) {
                                onError(GuestAccount.purchaseRefusal(result.error.code))
                                return@launch
                            }

                            // A missing or zero quote is never sellable. The webhook's
                            // check is `amount >= quoted - tolerance`, so at zero it is
                            // satisfied by any payment at all, including none.
                            if (result.amountUsd <= 0.0) {
                                onError(PurchaseRefusal.PaymentError)
                                return@launch
                            }

                            lastIntent = Triple(reference, plan, result.amountUsd)
                            onSuccess(result.amountUsd)

                        }

                    }
                } else {
                    onError(PurchaseRefusal.PaymentError)
                }
    }

    companion object {
        private const val PREFS_NAME = "pending_solana_payment"
    }

}
