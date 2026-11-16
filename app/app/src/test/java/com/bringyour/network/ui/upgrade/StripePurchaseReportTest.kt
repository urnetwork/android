package com.bringyour.network.ui.upgrade

import com.bringyour.sdk.Sdk
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The trial card check (server, 2026-11-16): a yearly trial whose card failed its
 * CVC check is refused when the app reports the purchase
 * (`POST /subscription/verify-stripe-checkout`), and the balance never says so.
 * The report's answer is the only place the app learns of it, so an answer that
 * carries an error is the refusal the app shows and ends its confirmation on.
 */
class StripePurchaseReportTest {

    // the server's exact refusal (controller.stripeCardCheckRefusalError)
    private val cvc = StripePurchaseRefusal(
        "incorrect_cvc",
        "Your card's security code (CVC) didn't match. Check the card details and try again.",
    )

    @Test
    fun theServersRefusalIsARefusal() {
        assertEquals(StripePurchaseReport.Outcome.Refused(cvc), StripePurchaseReport.outcome("invalid", cvc))
        // a code from a newer server is a refusal too: its words read under the screen line
        val newer = StripePurchaseRefusal("refusal_from_a_newer_server", "A refusal this app has no line for.")
        assertEquals(StripePurchaseReport.Outcome.Refused(newer), StripePurchaseReport.outcome("invalid", newer))
    }

    // the sdk stops asking at the first answer that carries an error, so no later
    // answer could end the confirmation; every app reads it as the refusal
    @Test
    fun anErrorIsARefusalWhateverTheStatusSays() {
        for (status in listOf("pending", "wrong_network", "credited", "already_credited", "")) {
            assertEquals(status, StripePurchaseReport.Outcome.Refused(cvc), StripePurchaseReport.outcome(status, cvc))
        }
    }

    @Test
    fun withoutAnErrorTheStatusDecides() {
        assertEquals(StripePurchaseReport.Outcome.Credited, StripePurchaseReport.outcome("credited", null))
        assertEquals(StripePurchaseReport.Outcome.Credited, StripePurchaseReport.outcome("already_credited", null))
        // an invalid answer with no reason (an id Stripe does not know) is no refusal
        for (status in listOf("invalid", "pending", "wrong_network", "")) {
            assertEquals(status, StripePurchaseReport.Outcome.None, StripePurchaseReport.outcome(status, null))
        }
    }

    @Test
    fun theFailedEventNamesTheCode() {
        assertEquals("incorrect_cvc", cvc.errorClass)
        assertEquals("refused", StripePurchaseRefusal("", "words").errorClass)
    }

    @Test
    fun theWireValuesAreTheSdks() {
        assertEquals("invalid", Sdk.PurchaseReportStatusInvalid)
        assertEquals("credited", Sdk.PurchaseReportStatusCredited)
        assertEquals("already_credited", Sdk.PurchaseReportStatusAlreadyCredited)
        assertEquals(Sdk.PurchaseErrorCodeIncorrectCvc, CheckoutRefusal.INCORRECT_CVC)
    }
}
