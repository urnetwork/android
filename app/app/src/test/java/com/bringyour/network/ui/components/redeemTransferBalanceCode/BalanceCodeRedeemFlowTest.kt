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

    private data class Answer(val credited: Boolean, val errorMessage: String?, val byteCount: Long = 0L)

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
            addedByteCount = { it.byteCount },
        )

        var redeemed: RedeemedBalanceCode? = null

        /** The failure, or null when the redeem credited (then `redeemed` is set). */
        fun result(secret: String): RedeemBalanceCodeFailure? {
            var out: RedeemBalanceCodeFailure? = null
            var calls = 0
            flow.run(
                secret,
                onRedeemed = { redeemed = it; calls += 1 },
                onFailure = { out = it; calls += 1 },
            )
            check(calls == 1)
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
        assertEquals(RedeemedBalanceCode(addedByteCount = 0L), run.redeemed)
        assertEquals(0, run.listFetches)
    }

    /**
     * The defect: a credited data code reached the UI as a bare success, so the app
     * confirmed it with the Pro upgrade overlay and a Pro confirmation poll. The
     * success must carry the data the server's answer says the code added.
     */
    @Test
    fun creditedRedeemReportsTheDataTheCodeAdded() {
        val fiveGib = 5L * 1024 * 1024 * 1024
        val run = Run(
            Answer(credited = true, errorMessage = null, byteCount = fiveGib),
            transportError = false,
            redeemedCodes = null,
            classify = ::classify,
        )
        assertNull(run.result(secret))
        assertEquals(RedeemedBalanceCode(addedByteCount = fiveGib), run.redeemed)
    }

    @Test
    fun alreadyRedeemedIsNotReportedAsANewCredit() {
        val run = Run(null, transportError = true, redeemedCodes = listOf(secret), classify = ::classify)
        assertEquals(RedeemBalanceCodeFailure.AlreadyRedeemed, run.result(secret))
        assertNull(run.redeemed)
    }

    @Test
    fun redeemedMessageStatesTheDataAddedAndNoPlan() {
        val message = balanceCodeRedeemedMessage(
            RedeemedBalanceCode(addedByteCount = 5L * 1024 * 1024 * 1024),
            "Balance code redeemed.",
            { amount -> "$amount of data added to your balance." },
            formatBytes = { "${it / (1024L * 1024 * 1024)} GiB" },
        )
        assertEquals("Balance code redeemed. 5 GiB of data added to your balance.", message)
    }

    @Test
    fun redeemedMessageWithoutAByteCountConfirmsTheRedeem() {
        val message = balanceCodeRedeemedMessage(
            RedeemedBalanceCode(addedByteCount = 0L),
            "Balance code redeemed.",
            { amount -> "$amount of data added to your balance." },
        )
        assertEquals("Balance code redeemed.", message)
    }

    @Test
    fun sdkOutcomesMapToTheUiFailures() {
        assertNull(redeemFailureForOutcome("redeemed"))
        assertEquals(RedeemBalanceCodeFailure.AlreadyRedeemed, redeemFailureForOutcome("already_redeemed"))
        assertEquals(RedeemBalanceCodeFailure.Invalid, redeemFailureForOutcome("invalid"))
        assertEquals(RedeemBalanceCodeFailure.Transport, redeemFailureForOutcome("unknown"))
    }
}
