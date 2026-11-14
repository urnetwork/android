package com.bringyour.network.ui.components.redeemTransferBalanceCode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.Locale
import java.util.TimeZone

/**
 * UPGRADE.md N7: the server answers "Unknown balance code." for a code this network
 * already redeemed, so the UI must classify against the network's redeemed-code list
 * instead of the server message. The classifier here mirrors the SDK's
 * ClassifyBalanceCodeRedeem contract (sdk payment_catalog.go) over fake types.
 *
 * GIFTOFPRIVACY.md: a Pro gift code's answer carries `pro` (a year of Pro, maybe
 * queued after a paid period) beside a 0-byte transfer balance, and the
 * confirmation says the year instead of data.
 */
class BalanceCodeRedeemFlowTest {

    // the redeem answer's `pro`
    private data class Pro(val startMillis: Long?, val endMillis: Long?, val queued: Boolean)

    private data class Answer(
        val credited: Boolean,
        val errorMessage: String?,
        val byteCount: Long = 0L,
        val pro: Pro? = null,
    )

    private val secret = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"

    private val fiveGib = 5L * 1024 * 1024 * 1024
    private val yearMillis = 365L * 24 * 60 * 60 * 1000
    // an immediate gift year starts at the redeem
    private val redeemMillis = Instant.parse("2026-11-13T12:00:00Z").toEpochMilli()
    // a queued one starts when the current paid period ends
    private val paidPeriodEndMillis = Instant.parse("2027-11-13T12:00:00Z").toEpochMilli()

    // the English strings (values/strings.xml)
    private val redeemedText = "Balance code redeemed."
    private val dataAddedText = { amount: String -> "$amount of data added to your balance." }
    private val proRedeemedText = "A year of Pro is yours."
    private val proQueuedText = { startDate: String ->
        "Your gift year of Pro starts on $startDate, after your current Pro period."
    }

    // a code on the network's redeemed list: its secret as stored, and its kind ("data" or "pro_gift")
    private data class Listed(val secret: String, val kind: String = "data", val endMillis: Long? = null)

    // the sdk's classifier matches the list on the trimmed secret, case aside
    private fun classify(result: Answer?, redeemedCodes: List<Listed>?, secret: String): String {
        if (result != null && result.credited) {
            return "redeemed"
        }
        if (redeemedCodes != null && redeemedCodes.any { it.secret.equals(secret.trim(), ignoreCase = true) }) {
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
        private val redeemedCodes: List<Listed>?,
        classify: (Answer?, List<Listed>?, String) -> String,
    ) {
        var listFetches = 0
        // the codes the redeem sent to the server
        val sentSecrets = mutableListOf<String>()
        val flow = BalanceCodeRedeemFlow<Answer, List<Listed>>(
            redeem = { sent, callback ->
                sentSecrets.add(sent)
                if (transportError) callback(null, Exception("connection reset")) else callback(answer, null)
            },
            fetchRedeemedCodes = { callback ->
                listFetches += 1
                callback(redeemedCodes)
            },
            classify = classify,
            addedByteCount = { it.byteCount },
            proGrant = { answer ->
                answer.pro?.let { RedeemedProGrant(it.startMillis, it.endMillis, it.queued) }
            },
            // as the view model reads the sdk's list
            findListed = { codes, typed ->
                codes.firstOrNull { balanceCodeMatches(typed, it.secret) }
                    ?.let { ListedBalanceCode(proGift = it.kind == "pro_gift", endTimeMillis = it.endMillis) }
            },
            answeredAlreadyRedeemed = { isAlreadyRedeemedAnswer(it.errorMessage) },
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
            redeemedCodes = listOf(Listed(secret)),
            classify = ::classify,
        )
        assertEquals(RedeemBalanceCodeFailure.AlreadyRedeemed, run.result(secret))
    }

    @Test
    fun transportFailureAfterACommittedRedeemIsAlreadyRedeemed() {
        val run = Run(null, transportError = true, redeemedCodes = listOf(Listed(secret)), classify = ::classify)
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
        val run = Run(null, transportError = true, redeemedCodes = listOf(Listed(secret)), classify = ::classify)
        assertEquals(RedeemBalanceCodeFailure.AlreadyRedeemed, run.result(secret))
        assertNull(run.redeemed)
    }

    @Test
    fun redeemedMessageStatesTheDataAddedAndNoPlan() {
        val message = balanceCodeRedeemedMessage(
            RedeemedBalanceCode(addedByteCount = 5L * 1024 * 1024 * 1024),
            redeemedText,
            dataAddedText,
            proRedeemedText,
            proQueuedText,
            formatBytes = { "${it / (1024L * 1024 * 1024)} GiB" },
        )
        assertEquals("Balance code redeemed. 5 GiB of data added to your balance.", message)
    }

    @Test
    fun redeemedMessageWithoutAByteCountConfirmsTheRedeem() {
        val message = balanceCodeRedeemedMessage(
            RedeemedBalanceCode(addedByteCount = 0L),
            redeemedText,
            dataAddedText,
            proRedeemedText,
            proQueuedText,
        )
        assertEquals("Balance code redeemed.", message)
    }

    // A gift code: its answer carries `pro` and a transfer balance of 0 bytes.

    @Test
    fun giftRedeemReportsAYearOfProStartingNow() {
        val run = Run(
            Answer(
                credited = true,
                errorMessage = null,
                byteCount = 0L,
                pro = Pro(redeemMillis, redeemMillis + yearMillis, queued = false),
            ),
            transportError = false,
            redeemedCodes = null,
            classify = ::classify,
        )
        assertNull(run.result(secret))
        assertEquals(
            RedeemedBalanceCode(
                addedByteCount = 0L,
                pro = RedeemedProGrant(redeemMillis, redeemMillis + yearMillis, queued = false),
            ),
            run.redeemed,
        )
        assertEquals(0, run.listFetches)
    }

    @Test
    fun giftRedeemMessageSaysAYearOfProIsYours() {
        val message = balanceCodeRedeemedMessage(
            RedeemedBalanceCode(0L, RedeemedProGrant(redeemMillis, redeemMillis + yearMillis, queued = false)),
            redeemedText,
            dataAddedText,
            proRedeemedText,
            proQueuedText,
            formatBytes = { error("a gift's confirmation names no data") },
            formatDate = { error("a year that starts now needs no date") },
        )
        assertEquals("A year of Pro is yours.", message)
    }

    @Test
    fun queuedGiftRedeemReportsTheYearAfterThePaidPeriod() {
        val run = Run(
            Answer(
                credited = true,
                errorMessage = null,
                byteCount = 0L,
                pro = Pro(paidPeriodEndMillis, paidPeriodEndMillis + yearMillis, queued = true),
            ),
            transportError = false,
            redeemedCodes = null,
            classify = ::classify,
        )
        assertNull(run.result(secret))
        assertEquals(
            RedeemedBalanceCode(
                addedByteCount = 0L,
                pro = RedeemedProGrant(paidPeriodEndMillis, paidPeriodEndMillis + yearMillis, queued = true),
            ),
            run.redeemed,
        )
        assertEquals(0, run.listFetches)
    }

    @Test
    fun queuedGiftRedeemMessageSaysTheDayTheYearStarts() {
        var formattedMillis: Long? = null
        val message = balanceCodeRedeemedMessage(
            RedeemedBalanceCode(0L, RedeemedProGrant(paidPeriodEndMillis, paidPeriodEndMillis + yearMillis, queued = true)),
            redeemedText,
            dataAddedText,
            proRedeemedText,
            proQueuedText,
            formatBytes = { error("a gift's confirmation names no data") },
            formatDate = { millis -> formattedMillis = millis; "Nov 13, 2027" },
        )
        assertEquals("Your gift year of Pro starts on Nov 13, 2027, after your current Pro period.", message)
        // the day the gift year starts, not the day it ends
        assertEquals(paidPeriodEndMillis, formattedMillis)
    }

    /** The app's date: the device's locale and zone, medium length, with the year. */
    @Test
    fun queuedGiftRedeemMessageDatesTheStartForTheDevice() {
        val locale = Locale.getDefault()
        val zone = TimeZone.getDefault()
        try {
            Locale.setDefault(Locale.US)
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val message = balanceCodeRedeemedMessage(
                RedeemedBalanceCode(0L, RedeemedProGrant(paidPeriodEndMillis, paidPeriodEndMillis + yearMillis, queued = true)),
                redeemedText,
                dataAddedText,
                proRedeemedText,
                proQueuedText,
            )
            assertEquals("Your gift year of Pro starts on Nov 13, 2027, after your current Pro period.", message)
        } finally {
            Locale.setDefault(locale)
            TimeZone.setDefault(zone)
        }
    }

    @Test
    fun queuedGiftWithoutAStartTimeSaysAYearOfProIsYours() {
        val message = balanceCodeRedeemedMessage(
            RedeemedBalanceCode(0L, RedeemedProGrant(startTimeMillis = null, endTimeMillis = null, queued = true)),
            redeemedText,
            dataAddedText,
            proRedeemedText,
            proQueuedText,
        )
        assertEquals("A year of Pro is yours.", message)
    }

    // A data code is unchanged: no `pro`, and the data confirmation.

    @Test
    fun dataCodeRedeemReportsNoPro() {
        val run = Run(
            Answer(credited = true, errorMessage = null, byteCount = fiveGib, pro = null),
            transportError = false,
            redeemedCodes = null,
            classify = ::classify,
        )
        assertNull(run.result(secret))
        assertNull(run.redeemed?.pro)
        val message = balanceCodeRedeemedMessage(
            run.redeemed!!,
            redeemedText,
            dataAddedText,
            proRedeemedText,
            proQueuedText,
            formatBytes = { "${it / (1024L * 1024 * 1024)} GiB" },
            formatDate = { error("a data code names no date") },
        )
        assertEquals("Balance code redeemed. 5 GiB of data added to your balance.", message)
    }

    // A lost answer: the redeem committed, its answer never arrived, and the
    // retry answers "already redeemed". The network's own list decides.

    private val giftEndMillis = redeemMillis + yearMillis

    // the server's refusal of a gift code that is already redeemed
    private val alreadyRedeemedAnswer = Answer(credited = false, errorMessage = "Balance code already redeemed.")

    @Test
    fun lostGiftAnswerWithTheGiftOnTheListIsARedeem() {
        // the redeem's own answer is lost: no answer at all
        val run = Run(
            null,
            transportError = true,
            redeemedCodes = listOf(Listed(secret, kind = "pro_gift", endMillis = giftEndMillis)),
            classify = ::classify,
        )
        assertNull(run.result(secret))
        assertEquals(
            RedeemedBalanceCode(
                addedByteCount = 0L,
                pro = RedeemedProGrant(startTimeMillis = null, endTimeMillis = giftEndMillis, queued = false),
            ),
            run.redeemed,
        )
    }

    @Test
    fun alreadyRedeemedGiftOnTheListIsARedeemWithTheGiftMessage() {
        // typed as the card prints it: lower case, in groups, with a hyphen
        val typed = "abcd efgh-ijkl mnop qrst uvwx yz"
        val run = Run(
            alreadyRedeemedAnswer,
            transportError = false,
            redeemedCodes = listOf(Listed("ZZZZZZZZZZZZZZZZZZZZZZZZZZ"), Listed(secret, kind = "pro_gift", endMillis = giftEndMillis)),
            classify = ::classify,
        )
        assertNull(run.result(typed))
        assertEquals(1, run.listFetches)
        val message = balanceCodeRedeemedMessage(
            run.redeemed!!,
            redeemedText,
            dataAddedText,
            proRedeemedText,
            proQueuedText,
            formatBytes = { error("a gift's confirmation names no data") },
            formatDate = { error("the list does not say when the year starts") },
        )
        assertEquals("A year of Pro is yours.", message)
    }

    @Test
    fun alreadyRedeemedAnswerForACodeNotOnTheListKeepsTheAlreadyRedeemedMessage() {
        // another network redeemed it
        val run = Run(alreadyRedeemedAnswer, transportError = false, redeemedCodes = emptyList(), classify = ::classify)
        assertEquals(RedeemBalanceCodeFailure.AlreadyRedeemed, run.result(secret))
        assertNull(run.redeemed)
    }

    @Test
    fun alreadyRedeemedAnswerWithoutTheListKeepsTheAlreadyRedeemedMessage() {
        val run = Run(alreadyRedeemedAnswer, transportError = false, redeemedCodes = null, classify = ::classify)
        assertEquals(RedeemBalanceCodeFailure.AlreadyRedeemed, run.result(secret))
        assertNull(run.redeemed)
    }

    @Test
    fun dataCodeOnTheListHoweverTypedStaysAlreadyRedeemed() {
        // UPGRADE.md N7: a data code credited earlier is never reported as a new credit
        val run = Run(
            Answer(credited = false, errorMessage = "Unknown balance code."),
            transportError = false,
            redeemedCodes = listOf(Listed(secret, kind = "data")),
            classify = ::classify,
        )
        assertEquals(RedeemBalanceCodeFailure.AlreadyRedeemed, run.result("abcd-efgh-ijkl-mnop-qrst-uvwx-yz"))
        assertNull(run.redeemed)
    }

    @Test
    fun aTypedCodeMatchesItsListedCodeHoweverItWasTyped() {
        for (typed in listOf(
            secret,
            "  abcdefghijklmnopqrstuvwxyz ",
            "ABCD EFGH IJKL MNOP QRST UVWX YZ",
            "abcd-efgh-ijkl-mnop-qrst-uvwx-yz",
            "ABCD.EFGH.IJKL.MNOP.QRST.UVWX.YZ",
            // an en dash and a no-break space, as a pasted card can carry
            "ABCD–EFGH IJKLMNOPQRSTUVWXYZ",
        )) {
            assertTrue(typed, balanceCodeMatches(typed, secret))
        }
        // 0, 1 and 8 read as the O, I and B a card's face makes them look like
        assertTrue(balanceCodeMatches("0PQR 1STU 8VWX", "OPQRISTUBVWX"))
        // another code, or nothing, is no match
        assertFalse(balanceCodeMatches(secret.replace('Z', 'Y'), secret))
        assertFalse(balanceCodeMatches("", ""))
        assertFalse(balanceCodeMatches(secret, ""))
        assertEquals("ABCDEFGHIJKLMNOPQRSTUVWXYZ", normalizedBalanceCode("abcd efgh-ijkl.mnop qrst uvwx yz"))
    }

    @Test
    fun onlyAnAlreadyRedeemedRefusalReadsAsAlreadyRedeemed() {
        assertTrue(isAlreadyRedeemedAnswer("Balance code already redeemed."))
        assertFalse(isAlreadyRedeemedAnswer("Unknown balance code."))
        assertFalse(isAlreadyRedeemedAnswer("Balance code expired."))
        assertFalse(isAlreadyRedeemedAnswer("Balance code was refunded."))
        assertFalse(isAlreadyRedeemedAnswer(null))
    }

    @Test
    fun theRedeemSendsTheCodeAsTyped() {
        // the server tries it as given, then normalized; only the list match normalizes here
        val typed = "abcd efgh-ijkl mnop qrst uvwx yz"
        val run = Run(Answer(credited = true, errorMessage = null), transportError = false, redeemedCodes = null, classify = ::classify)
        assertNull(run.result(typed))
        assertEquals(listOf(typed), run.sentSecrets)
    }

    @Test
    fun sdkOutcomesMapToTheUiFailures() {
        assertNull(redeemFailureForOutcome("redeemed"))
        assertEquals(RedeemBalanceCodeFailure.AlreadyRedeemed, redeemFailureForOutcome("already_redeemed"))
        assertEquals(RedeemBalanceCodeFailure.Invalid, redeemFailureForOutcome("invalid"))
        assertEquals(RedeemBalanceCodeFailure.Transport, redeemFailureForOutcome("unknown"))
    }
}
