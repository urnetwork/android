package com.bringyour.network.ui.upgrade

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.bringyour.network.TAG
import com.bringyour.sdk.Api
import com.bringyour.sdk.Sdk
import com.bringyour.sdk.VerifyStripeCheckoutArgs
import com.bringyour.sdk.VerifyStripeCheckoutCallback

/**
 * Tells the server a card purchase finished (support inbox 7247), for both card
 * purchasers: the inline payment sheet (src/stripeSheet) and the ur.io pay page
 * (src/webPay). The plan used to wait for Stripe's webhook alone, so a late webhook
 * left a paying customer on Free. Now, as the purchase completes, the subscription
 * id the server answered when it prepared the purchase
 * (StripeSheetRequest, StripePaymentSheetResult.subscriptionId) goes to the sdk's
 * Api.reportStripePurchase, which asks again on a pending answer and calls back
 * once with the last answer.
 *
 * The purchase's balance confirmation (SubscriptionBalanceViewModel.confirmPurchase)
 * still decides what the screens show. A credited answer also runs [onCredited] on
 * the main thread, so a credit that lands after the confirmation gave up shows
 * without waiting for the next background poll. A refused answer runs [onRefused]
 * on the main thread ([outcome]): a yearly trial whose card failed its CVC check
 * gets no Pro, and the balance never says so, so the app ends the confirmation
 * itself and shows why. Nothing is sent without an api or an id (a server that
 * answers no subscription id): Stripe's webhook and the server's reconcile credit
 * those, as before.
 */
object StripePurchaseReport {

    fun report(
        api: Api?,
        subscriptionId: String?,
        onCredited: () -> Unit,
        onRefused: (StripePurchaseRefusal) -> Unit,
    ) {
        val id = subscriptionId?.trim().orEmpty()
        if (api == null || id.isEmpty()) {
            Log.i(TAG, "card purchase not reported: ${if (api == null) "no api" else "no subscription id"}")
            return
        }
        val args = VerifyStripeCheckoutArgs()
        args.subscriptionId = id
        api.reportStripePurchase(args, VerifyStripeCheckoutCallback { result, err ->
            // a Go thread: the refresh and the refusal go to the main thread
            when {
                err != null -> Log.i(TAG, "card purchase report: no answer: ${err.message}")
                result == null -> Log.i(TAG, "card purchase report: empty answer")
                else -> {
                    val refusal = result.error?.let { StripePurchaseRefusal(it.code ?: "", it.message ?: "") }
                    Log.i(TAG, "card purchase report: ${result.status}${refusal?.let { " ${it.errorClass}" } ?: ""}")
                    when (val outcome = outcome(result.status, refusal)) {
                        is Outcome.Refused -> Handler(Looper.getMainLooper()).post { onRefused(outcome.refusal) }
                        Outcome.Credited -> Handler(Looper.getMainLooper()).post { onCredited() }
                        Outcome.None -> Unit
                    }
                }
            }
        })
    }

    /** What a report's answer asks of the app. */
    sealed interface Outcome {
        data class Refused(val refusal: StripePurchaseRefusal) : Outcome
        data object Credited : Outcome
        /** Nothing: the confirmation goes on, as before (pending, or invalid without a reason). */
        data object None : Outcome
    }

    /**
     * An answer that carries an error (VerifyStorePurchaseResult.error) is the
     * server's refusal of the purchase, whatever its status: the server sends it
     * with invalid, every later report of the purchase answers the same, and the
     * sdk stops asking at the first answer that carries one. Every app reads it
     * so. Otherwise a credited or already credited status is the credit.
     */
    fun outcome(status: String, refusal: StripePurchaseRefusal?): Outcome = when {
        refusal != null -> Outcome.Refused(refusal)
        status == Sdk.PurchaseReportStatusCredited || status == Sdk.PurchaseReportStatusAlreadyCredited -> Outcome.Credited
        else -> Outcome.None
    }
}

/**
 * A card purchase the server refused when it was reported: its code (one of the
 * server's PurchaseErrorCode* values, [CheckoutRefusal.INCORRECT_CVC] for a yearly
 * trial whose card failed its CVC check, empty from a refusal without one) and its
 * English words, which a code this app has no line for reads
 * ([CheckoutRefusal.refusedMessage]).
 */
data class StripePurchaseRefusal(val code: String, val words: String) {
    /** The purchase.failed event's error class: the code, else "refused". */
    val errorClass: String
        get() = code.ifEmpty { "refused" }
}
