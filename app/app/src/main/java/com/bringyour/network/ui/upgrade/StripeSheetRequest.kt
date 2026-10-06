package com.bringyour.network.ui.upgrade

import com.bringyour.network.ui.account.GuestAccount
import com.bringyour.network.ui.account.PurchaseRefusal
import com.bringyour.network.ui.shared.enums.PlanType
import com.bringyour.sdk.Api
import com.bringyour.sdk.Sdk
import com.bringyour.sdk.StripePaymentSheetArgs
import com.bringyour.sdk.StripePaymentSheetResult

/**
 * Asks the server to prepare an inline Stripe purchase of Pro
 * (`POST /subscription/stripe/payment-sheet`): the customer, its ephemeral
 * key, and the intent to confirm -- a SetupIntent for the yearly plan (the
 * card is saved, the 14-day trial starts, the welcome coupon is applied when
 * the caller's offer is active) or a PaymentIntent for the monthly plan (no
 * trial). The result's amounts tell the sheet what the first period costs.
 * A refusal says what it leads to: a legacy guest network is refused with
 * `guest_sign_in_required`, which opens the add-sign-in sheet
 * (GuestAccount.purchaseRefusal) instead of the error. Any other failure comes
 * back as a StripeSheetFailure, which CheckoutRefusal words.
 */
object StripeSheetRequest {

    // the Stripe API version the Android PaymentSheet needs for its ephemeral key
    const val STRIPE_VERSION = "2024-06-20"

    fun request(
        api: Api,
        plan: PlanType,
        storefrontCountry: String?,
        callback: (StripePaymentSheetResult?, StripeSheetFailure?, PurchaseRefusal) -> Unit,
    ) {
        val args = StripePaymentSheetArgs()
        args.plan = if (plan == PlanType.YEARLY) Sdk.PlanYearly else Sdk.PlanMonthly
        args.storefrontCountry = storefrontCountry ?: ""
        args.stripeVersion = STRIPE_VERSION
        api.stripePaymentSheet(args) { result, err ->
            when {
                err != null -> callback(null, StripeSheetFailure.NoAnswer(err.message ?: "network"), PurchaseRefusal.PaymentError)
                result == null -> callback(null, StripeSheetFailure.NoAnswer("empty"), PurchaseRefusal.PaymentError)
                result.error != null -> callback(
                    null,
                    StripeSheetFailure.Refused(result.error.code ?: "", result.error.message ?: ""),
                    GuestAccount.purchaseRefusal(result.error.code),
                )
                else -> callback(result, null, PurchaseRefusal.PaymentError)
            }
        }
    }
}
