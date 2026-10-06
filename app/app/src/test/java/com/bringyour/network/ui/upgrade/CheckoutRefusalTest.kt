package com.bringyour.network.ui.upgrade

import com.bringyour.network.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The plan purchase in the F-Droid and dapp builds (StripeSheetRequest, then
 * the ur.io pay page or Stripe's PaymentSheet) showed every refusal of
 * `POST /subscription/stripe/payment-sheet` as "Your payment could not be
 * completed." with the server's English message under it. The server now
 * sends a stable code with each refusal (its PurchaseErrorCode* values), and
 * the purchase words the codes the way ur.io does.
 */
class CheckoutRefusalTest {

    private val screenLine = "Your payment could not be completed."

    // a string resource read as its id, so each check sees which line was picked
    private val text: (Int) -> String = { id -> "string resource $id" }

    private fun shown(failure: StripeSheetFailure): String =
        CheckoutRefusal.message(failure, screenLine, text)

    // the server's exact refusals (controller.StripePaymentSheet)
    private val alreadySubscribed = StripeSheetFailure.Refused(
        "already_subscribed",
        "You already have an active Pro subscription, so no new payment was made. Manage your subscription from your account.",
    )
    private val planUnavailable = StripeSheetFailure.Refused("plan_unavailable", "That plan is not available.")

    @Test
    fun aCodeWithALineReadsThatLineAlone() {
        assertEquals("string resource ${R.string.site_payment_error_already_subscribed}", shown(alreadySubscribed))
        assertEquals("string resource ${R.string.site_payment_error_plan_unavailable}", shown(planUnavailable))
    }

    @Test
    fun aClientDefectOrAStartToTryAgainReadsTheScreenLine() {
        assertEquals(screenLine, shown(StripeSheetFailure.Refused("invalid_request", "Unknown plan.")))
        assertEquals(
            screenLine,
            shown(StripeSheetFailure.Refused("start_failed", "Could not start the payment. Please try again.")),
        )
    }

    @Test
    fun anUnknownCodeKeepsTheServerWordsUnderTheScreenLine() {
        assertEquals(
            "$screenLine\nA refusal this app has no line for.",
            shown(StripeSheetFailure.Refused("refusal_from_a_newer_server", "A refusal this app has no line for.")),
        )
        // an older server sends no code
        assertEquals(
            "$screenLine\nThat plan is not available.",
            shown(StripeSheetFailure.Refused("", "That plan is not available.")),
        )
        // codes compare exactly, like guest_sign_in_required
        assertEquals(
            "$screenLine\nThat plan is not available.",
            shown(StripeSheetFailure.Refused("PLAN_UNAVAILABLE", "That plan is not available.")),
        )
        // a refusal with no words reads the screen line alone
        assertEquals(screenLine, shown(StripeSheetFailure.Refused("refusal_from_a_newer_server", "")))
    }

    @Test
    fun noAnswerReadsAsBefore() {
        assertEquals("$screenLine\nnetwork", shown(StripeSheetFailure.NoAnswer("network")))
        assertEquals(
            "$screenLine\nPost \"https://api.example/subscription/stripe/payment-sheet\": i/o timeout",
            shown(StripeSheetFailure.NoAnswer("Post \"https://api.example/subscription/stripe/payment-sheet\": i/o timeout")),
        )
    }

    @Test
    fun theLinesAreTheStoreKeysUrIoUses() {
        assertEquals(R.string.site_payment_error_already_subscribed, CheckoutRefusal.lineFor(CheckoutRefusal.ALREADY_SUBSCRIBED))
        assertEquals(R.string.site_payment_error_plan_unavailable, CheckoutRefusal.lineFor(CheckoutRefusal.PLAN_UNAVAILABLE))
        assertNull(CheckoutRefusal.lineFor(CheckoutRefusal.INVALID_REQUEST))
        assertNull(CheckoutRefusal.lineFor(CheckoutRefusal.START_FAILED))
        assertNull(CheckoutRefusal.lineFor("guest_sign_in_required"))
        assertNull(CheckoutRefusal.lineFor(""))
    }

    @Test
    fun theCodesAreTheServers() {
        assertEquals("already_subscribed", CheckoutRefusal.ALREADY_SUBSCRIBED)
        assertEquals("plan_unavailable", CheckoutRefusal.PLAN_UNAVAILABLE)
        assertEquals("invalid_request", CheckoutRefusal.INVALID_REQUEST)
        assertEquals("start_failed", CheckoutRefusal.START_FAILED)
    }
}
