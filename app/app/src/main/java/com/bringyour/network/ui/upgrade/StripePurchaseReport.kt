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
 * without waiting for the next background poll. Nothing is sent without an api or
 * an id (a server that answers no subscription id): Stripe's webhook and the
 * server's reconcile credit those, as before.
 */
object StripePurchaseReport {

    fun report(api: Api?, subscriptionId: String?, onCredited: () -> Unit) {
        val id = subscriptionId?.trim().orEmpty()
        if (api == null || id.isEmpty()) {
            Log.i(TAG, "card purchase not reported: ${if (api == null) "no api" else "no subscription id"}")
            return
        }
        val args = VerifyStripeCheckoutArgs()
        args.subscriptionId = id
        api.reportStripePurchase(args, VerifyStripeCheckoutCallback { result, err ->
            // a Go thread: the refresh goes to the main thread
            when {
                err != null -> Log.i(TAG, "card purchase report: no answer: ${err.message}")
                result == null -> Log.i(TAG, "card purchase report: empty answer")
                else -> {
                    Log.i(TAG, "card purchase report: ${result.status}")
                    if (result.status == Sdk.PurchaseReportStatusCredited ||
                        result.status == Sdk.PurchaseReportStatusAlreadyCredited
                    ) {
                        Handler(Looper.getMainLooper()).post { onCredited() }
                    }
                }
            }
        })
    }
}
