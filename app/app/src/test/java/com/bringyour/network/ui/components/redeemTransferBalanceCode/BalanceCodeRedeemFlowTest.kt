package com.bringyour.network.ui.components.redeemTransferBalanceCode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * UPGRADE.md N7: the server answers "Unknown balance code." for a code this network
 * already redeemed, so the UI must classify against the network's redeemed-code list
 * instead of the server message. The classifier here mirrors the SDK's
 * ClassifyBalanceCodeRedeem contract (sdk payment_catalog.go) over fake types.
 */
class BalanceCodeRedeemFlowTest {

    private data class Answer(val credited: Boolean, val errorMessage: String?)

    private val secret = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"

    private fun classify(result: Answer?, redeemedCodes: List<String>?, secret: String): String {
        if (result != null && result.credited) {
            return "redeemed"
        }
        if (redeemedCodes != null && redeemedCodes.any { it.equals(secret.trim(), ignoreCase = true) }) {
            return "already_redeemed"
        }
        if (result?.errorMessage != null) {
            return "invalid"
        }
        return "unknown"
    }

    private class Run(
        answer: Answer?,
        transportError: Boolean,
        private val redeemedCodes: List<String>?,
        classify: (Answer?, List<String>?, String) -> String,
    ) {
        var listFetches = 0
        val flow = BalanceCodeRedeemFlow<Answer, List<String>>(
            redeem = { _, callback ->
                if (transportError) callback(null, Exception("connection reset")) else callback(answer, null)
            },
            fetchRedeemedCodes = { callback ->
                listFetches += 1
                callback(redeemedCodes)
            },
            classify = classify,
        )

        fun result(secret: String): RedeemBalanceCodeFailure? {
            var out: RedeemBalanceCodeFailure? = null
            var called = false
            flow.run(secret) { out = it; called = true }
            check(called)
            return out
        }
    }

    @Test
    fun unknownCodeAnswerForACodeThisNetworkRedeemedIsAlreadyRedeemed() {
        // the server's actual payload for an already-redeemed code
        val run = Run(
            Answer(credited = false, errorMessage = "Unknown balance code."),
            transportError = false,
            redeemedCodes = listOf(secret),
            classify = ::classify,
        )
        assertEquals(RedeemBalanceCodeFailure.AlreadyRedeemed, run.result(secret))
    }

    @Test
    fun transportFailureAfterACommittedRedeemIsAlreadyRedeemed() {
        val run = Run(null, transportError = true, redeemedCodes = listOf(secret), classify = ::classify)
        assertEquals(RedeemBalanceCodeFailure.AlreadyRedeemed, run.result(secret))
    }

    @Test
    fun transportFailureWithoutTheCodeInTheListStaysTransport() {
        val run = Run(null, transportError = true, redeemedCodes = emptyList(), classify = ::classify)
        assertEquals(RedeemBalanceCodeFailure.Transport, run.result(secret))
    }

    @Test
    fun unknownCodeNotInTheListIsInvalid() {
        val run = Run(
            Answer(credited = false, errorMessage = "Unknown balance code."),
            transportError = false,
            redeemedCodes = emptyList(),
            classify = ::classify,
        )
        assertEquals(RedeemBalanceCodeFailure.Invalid, run.result(secret))
    }

    @Test
    fun listUnavailableFallsBackToTheServerAnswer() {
        val run = Run(
            Answer(credited = false, errorMessage = "Unknown balance code."),
            transportError = false,
            redeemedCodes = null,
            classify = ::classify,
        )
        assertEquals(RedeemBalanceCodeFailure.Invalid, run.result(secret))
    }

    @Test
    fun creditedRedeemSucceedsWithoutFetchingTheList() {
        val run = Run(Answer(credited = true, errorMessage = null), transportError = false, redeemedCodes = null, classify = ::classify)
        assertNull(run.result(secret))
        assertEquals(0, run.listFetches)
    }

    @Test
    fun sdkOutcomesMapToTheUiFailures() {
        assertNull(redeemFailureForOutcome("redeemed"))
        assertEquals(RedeemBalanceCodeFailure.AlreadyRedeemed, redeemFailureForOutcome("already_redeemed"))
        assertEquals(RedeemBalanceCodeFailure.Invalid, redeemFailureForOutcome("invalid"))
        assertEquals(RedeemBalanceCodeFailure.Transport, redeemFailureForOutcome("unknown"))
    }
}
