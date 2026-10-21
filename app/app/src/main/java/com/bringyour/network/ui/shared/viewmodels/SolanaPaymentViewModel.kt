package com.bringyour.network.ui.shared.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * The Solana Pay payment an earlier version of the app opened the wallet for
 * (UPGRADE.md N6), until its return-path check ends. USDC is now sold through
 * the crypto invoice on the flavors that sell outside a store (src/cryptoPay,
 * CryptoPayViewModel), which keeps its own saved invoice, so nothing records a
 * new payment here: one recorded before the update is still checked when the
 * app comes back (MainNavHost), and the sign-out clears it.
 */
@HiltViewModel
class SolanaPaymentViewModel @Inject constructor(
    @ApplicationContext context: Context,
): ViewModel() {

    private val store = pendingSolanaPaymentStore(context)

    private val _pendingSolanaSubscriptionReference = MutableStateFlow<String?>(store.load()?.reference)
    val pendingSolanaSubscriptionReference: StateFlow<String?> = _pendingSolanaSubscriptionReference.asStateFlow()

    /** The persisted pending payment, if the check for it has not ended. */
    internal val pendingSolanaPayment: PendingSolanaPayment?
        get() = store.load()

    /** The check ended, confirmed or timed out: the payment is no longer pending. */
    fun clearPendingSolanaPayment() {
        store.clear()
        _pendingSolanaSubscriptionReference.value = null
    }
}

private const val PENDING_SOLANA_PAYMENT_PREFS = "pending_solana_payment"

/**
 * The pending Solana payment in its preferences, for the view model and for
 * the sign-out, which clears it: the payment belongs to the network that
 * signed out.
 */
internal fun pendingSolanaPaymentStore(context: Context): PendingSolanaPaymentStore =
    PendingSolanaPaymentStore(
        object : PendingSolanaPaymentStore.Prefs {
            private val prefs = context.getSharedPreferences(PENDING_SOLANA_PAYMENT_PREFS, Context.MODE_PRIVATE)

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
