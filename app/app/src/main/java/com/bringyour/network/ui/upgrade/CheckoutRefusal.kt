package com.bringyour.network.ui.upgrade

import androidx.annotation.StringRes
import com.bringyour.network.R

/**
 * Why the server did not prepare an inline Stripe purchase
 * (`POST /subscription/stripe/payment-sheet`, StripeSheetRequest).
 */
sealed interface StripeSheetFailure {
    /**
     * The server refused: its code (one of the server's PurchaseErrorCode*
     * values, empty from an older server) and its words.
     */
    data class Refused(val code: String, val words: String) : StripeSheetFailure

    /** No answer: the sdk's error text. */
    data class NoAnswer(val words: String) : StripeSheetFailure
}

/**
 * The line the plan purchase shows for a failed StripeSheetRequest, worded
 * the way ur.io's payment screens word the same refusals
 * (src/lib/paymentFailure.js):
 * - a code with a line of its own reads that translated line alone;
 * - `invalid_request` (a client defect) and `start_failed` (a start to try
 *   again) read the screen's own line, with nothing under it;
 * - any other code, or none from an older server, reads the screen's own line
 *   with the server's words under it;
 * - no answer reads the screen's own line with the sdk's error text under it,
 *   as it did before the codes.
 * `guest_sign_in_required` never gets here: it opens the add-sign-in sheet
 * (GuestAccount.purchaseRefusal).
 */
object CheckoutRefusal {

    // the server's PurchaseErrorCode* values the payment sheet refuses with,
    // beside guest_sign_in_required
    const val ALREADY_SUBSCRIBED = "already_subscribed"
    const val PLAN_UNAVAILABLE = "plan_unavailable"
    const val INVALID_REQUEST = "invalid_request"
    const val START_FAILED = "start_failed"

    /**
     * The translated line for a refusal code, or null when the screen's own
     * line words it. Codes compare exactly, like the guest code.
     */
    @StringRes
    fun lineFor(code: String): Int? = when (code) {
        ALREADY_SUBSCRIBED -> R.string.site_payment_error_already_subscribed
        PLAN_UNAVAILABLE -> R.string.site_payment_error_plan_unavailable
        else -> null
    }

    /**
     * The error text for [failure]. [screenLine] is the screen's own line
     * (payment_not_completed), and [text] reads a string resource.
     */
    fun message(failure: StripeSheetFailure, screenLine: String, text: (Int) -> String): String =
        when (failure) {
            is StripeSheetFailure.Refused -> {
                val line = lineFor(failure.code)
                when {
                    line != null -> text(line)
                    failure.code == INVALID_REQUEST || failure.code == START_FAILED -> screenLine
                    else -> withWords(screenLine, failure.words)
                }
            }
            is StripeSheetFailure.NoAnswer -> withWords(screenLine, failure.words)
        }

    private fun withWords(screenLine: String, words: String): String =
        listOf(screenLine, words).filter { it.isNotEmpty() }.joinToString("\n")
}
