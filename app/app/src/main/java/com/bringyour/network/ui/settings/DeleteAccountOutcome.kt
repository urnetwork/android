package com.bringyour.network.ui.settings

/**
 * What a delete-account call did, decided from the SDK callback.
 *
 * The server refuses a deletion by answering normally with an error in the
 * result (a failed Stripe or Google Play cancellation, an App Store
 * subscription that still renews), not with a transport error. The account
 * then still exists, so the user must stay signed in and see why. Only a
 * result with no error and no transport error is a deletion.
 */
sealed interface DeleteAccountOutcome {

    data object Deleted : DeleteAccountOutcome

    /**
     * The account was not deleted. [serverMessage] is the server's reason,
     * or null when it gave none (transport error, empty result).
     */
    data class Failed(val serverMessage: String?) : DeleteAccountOutcome

    companion object {

        /**
         * @param resultPresent whether the callback returned a result
         * @param resultHasError whether that result carries an error
         * @param resultErrorMessage the result error's message, if any
         */
        fun of(
            exception: Exception?,
            resultPresent: Boolean,
            resultHasError: Boolean,
            resultErrorMessage: String?,
        ): DeleteAccountOutcome {
            if (exception != null || !resultPresent) {
                return Failed(null)
            }
            if (resultHasError) {
                return Failed(resultErrorMessage?.trim()?.takeIf { it.isNotEmpty() })
            }
            return Deleted
        }

        /**
         * The text shown for a failed deletion: the generic error, then the
         * server's reason on its own line when there is one.
         */
        fun failureMessage(generic: String, outcome: Failed): String =
            outcome.serverMessage?.let { "$generic\n$it" } ?: generic
    }
}
