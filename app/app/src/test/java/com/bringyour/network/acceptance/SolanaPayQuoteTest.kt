package com.bringyour.network.acceptance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules the instrumented Solana Pay case enforces, tested on the JVM where
 * they can actually be run. Each case here corresponds to a bug that took real
 * money and delivered nothing.
 */
class SolanaPayQuoteTest {

    private val reference = "4Fj9RCwJqHLdLNK28DwWHunHqWapxKbbzeYZLmreSYCM"

    private fun request(
        reference: String = this.reference,
        amountUsd: Double = 40.0,
        plan: String = "yearly",
        url: String = payUrl(reference, "40"),
    ) = SolanaPayRequest(reference = reference, amountUsd = amountUsd, plan = plan, url = url)

    private fun payUrl(reference: String, amount: String) =
        "solana:$SOLANA_MERCHANT_ADDRESS?amount=$amount&spl-token=$SOLANA_USDC_MINT" +
            "&reference=$reference&label=URnetwork&message=UR%20Pro"

    @Test
    fun aWellFormedRequestHasNoProblems() {
        assertEquals(emptyList<String>(), solanaPayQuoteProblems(request(), 40.0))
    }

    @Test
    fun aHexUuidReferenceIsRefused() {
        // The shipped bug: Solana Pay requires a base58 32-byte pubkey, and a
        // hex uuid is neither, so the webhook could never match the payment.
        val uuid = "0f9a6d2c8b7e4f1aa3c5d7e9f1b3c5d7"
        val problems = solanaPayQuoteProblems(
            request(reference = uuid, url = payUrl(uuid, "40")), 40.0,
        )
        assertTrue(problems.any { it.contains("base58") })
    }

    @Test
    fun aMissingPlanIsRefused() {
        // The shipped bug: without the plan the server answers "Unknown plan."
        // and every Solana upgrade failed before the wallet opened.
        val problems = solanaPayQuoteProblems(request(plan = ""), 40.0)
        assertTrue(problems.any { it.contains("plan") })
    }

    @Test
    fun aHardcodedAmountIsRefusedWhenTheServerQuotesSomethingElse() {
        // The shipped bug: the client hardcoded 40 while the server quoted the
        // welcome-offer price.
        val problems = solanaPayQuoteProblems(request(amountUsd = 40.0), 9.99)
        assertTrue(problems.any { it.contains("40.0") && it.contains("9.99") })
    }

    @Test
    fun aWrongMerchantIsRefused() {
        val url = "solana:SomeOtherMerchantAddress?amount=40&spl-token=$SOLANA_USDC_MINT&reference=$reference"
        val problems = solanaPayQuoteProblems(request(url = url), 40.0)
        assertTrue(problems.any { it.contains("merchant") })
    }

    @Test
    fun aWrongMintIsRefused() {
        val url = "solana:$SOLANA_MERCHANT_ADDRESS?amount=40&spl-token=NotUsdcMint&reference=$reference"
        val problems = solanaPayQuoteProblems(request(url = url), 40.0)
        assertTrue(problems.any { it.contains("USDC on Solana") })
    }

    @Test
    fun aUrlCarryingADifferentReferenceIsRefused() {
        val other = "74UNdYRpvakSABaYHSZMQNaXBVtA6eY9Nt8chcqocKe7"
        val problems = solanaPayQuoteProblems(request(url = payUrl(other, "40")), 40.0)
        assertTrue(problems.any { it.contains("different reference") })
    }

    @Test
    fun aUrlAmountThatDisagreesWithTheQuoteIsRefused() {
        val problems = solanaPayQuoteProblems(request(url = payUrl(reference, "41")), 40.0)
        assertTrue(problems.any { it.contains("41.0") })
    }

    @Test
    fun aNonSolanaUrlIsRefused() {
        val problems = solanaPayQuoteProblems(request(url = "https://example.com/pay"), 40.0)
        assertTrue(problems.any { it.contains("not a solana: payment url") })
    }

    @Test
    fun aNonPositiveQuoteIsRefused() {
        val problems = solanaPayQuoteProblems(request(amountUsd = 0.0, url = payUrl(reference, "0")), 0.0)
        assertTrue(problems.any { it.contains("non-positive") })
    }

    @Test
    fun aCentOfRoundingIsTolerated() {
        // The server itself allows a one-cent tolerance, so the client must not
        // be failed for the same rounding.
        assertEquals(
            emptyList<String>(),
            solanaPayQuoteProblems(
                request(amountUsd = 39.99, url = payUrl(reference, "39.99")), 39.991,
            ),
        )
    }

    @Test
    fun parsingReadsEveryFieldTheWalletNeeds() {
        val url = parseSolanaPayUrl(payUrl(reference, "40"))!!
        assertEquals(SOLANA_MERCHANT_ADDRESS, url.recipient)
        assertEquals("40", url.amount)
        assertEquals(SOLANA_USDC_MINT, url.splTokenMint)
        assertEquals(reference, url.reference)
    }

    @Test
    fun parsingRejectsWhatIsNotAPaymentUrl() {
        assertNull(parseSolanaPayUrl(""))
        assertNull(parseSolanaPayUrl("solana:"))
        assertNull(parseSolanaPayUrl("https://ur.io"))
        assertNull(parseSolanaPayUrl("solana"))
    }

    @Test
    fun parsingDecodesPercentEscapes() {
        val url = parseSolanaPayUrl("solana:$SOLANA_MERCHANT_ADDRESS?amount=1&message=UR%20Pro%20%E2%80%94%20Yearly")
        assertEquals("1", url!!.amount)
    }

    @Test
    fun aReferenceIsThirtyTwoBytes() {
        assertTrue(isSolanaPayReference(reference))
        assertTrue(isSolanaPayReference("74UNdYRpvakSABaYHSZMQNaXBVtA6eY9Nt8chcqocKe7"))
        // too short, not base58, and empty
        assertFalse(isSolanaPayReference("abc"))
        assertFalse(isSolanaPayReference("0OIl+/"))
        assertFalse(isSolanaPayReference(""))
    }

    @Test
    fun base58DecodesKnownVectors() {
        // The alphabet excludes 0, O, I and l, so those are not base58 at all.
        assertNull(decodeBase58("0"))
        assertNull(decodeBase58("O"))
        assertNull(decodeBase58("I"))
        assertNull(decodeBase58("l"))
        // A leading '1' is a leading zero byte.
        assertEquals(1, decodeBase58("1")!!.size)
        assertEquals(0, decodeBase58("1")!![0].toInt())
        assertEquals(32, decodeBase58(reference)!!.size)
    }
}
