package com.bringyour.network.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The delete-account decision. The server refuses a deletion by answering
 * normally with an error in the result; the app used to treat any answer
 * without a transport error as a deletion and logged the user out of an
 * account that still existed.
 */
class DeleteAccountOutcomeTest {

    @Test
    fun aResultErrorIsNotADeletion() {
        val outcome = DeleteAccountOutcome.of(
            exception = null,
            resultPresent = true,
            resultHasError = true,
            resultErrorMessage = "Could not cancel your Google Play subscription. Please try again.",
        )
        assertEquals(
            DeleteAccountOutcome.Failed("Could not cancel your Google Play subscription. Please try again."),
            outcome,
        )
    }

    @Test
    fun aResultErrorWithoutMessageFallsBackToTheGenericText() {
        val outcome = DeleteAccountOutcome.of(
            exception = null,
            resultPresent = true,
            resultHasError = true,
            resultErrorMessage = "  ",
        )
        assertEquals(DeleteAccountOutcome.Failed(null), outcome)
        assertEquals(
            "Sorry, there was an error deleting your account.",
            DeleteAccountOutcome.failureMessage("Sorry, there was an error deleting your account.", outcome as DeleteAccountOutcome.Failed),
        )
    }

    @Test
    fun theServerReasonFollowsTheGenericText() {
        assertEquals(
            "Sorry, there was an error deleting your account.\nFailed to unsubscribe Stripe",
            DeleteAccountOutcome.failureMessage(
                "Sorry, there was an error deleting your account.",
                DeleteAccountOutcome.Failed("Failed to unsubscribe Stripe"),
            ),
        )
    }

    @Test
    fun aMissingResultIsNotADeletion() {
        assertEquals(
            DeleteAccountOutcome.Failed(null),
            DeleteAccountOutcome.of(exception = null, resultPresent = false, resultHasError = false, resultErrorMessage = null),
        )
    }

    @Test
    fun aTransportErrorIsNotADeletion() {
        assertEquals(
            DeleteAccountOutcome.Failed(null),
            DeleteAccountOutcome.of(exception = Exception("synthetic transport error"), resultPresent = false, resultHasError = false, resultErrorMessage = null),
        )
    }

    @Test
    fun aResultWithoutErrorIsADeletion() {
        assertEquals(
            DeleteAccountOutcome.Deleted,
            DeleteAccountOutcome.of(exception = null, resultPresent = true, resultHasError = false, resultErrorMessage = null),
        )
    }
}
