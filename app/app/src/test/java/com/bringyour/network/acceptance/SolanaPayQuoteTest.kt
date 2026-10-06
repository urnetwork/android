package com.bringyour.network.acceptance

import com.bringyour.network.utils.SolanaPaymentQuote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules the instrumented Solana Pay case enforces, tested on the JVM where
 * they can actually be run. Each case here corresponds to a bug that took real
 * money and delivered nothing.
 *
 * The addresses are fixture keys (sha256 of a fixed phrase), not wallets; the
 * mint is USDC on Solana mainnet.
 */
class SolanaPayQuoteTest {

    private val reference = "9LyJFGoWExmu98m1wn2LACRfhwQtmKUE8Bv2hwkCktBR"
    private val merchant = "835ygSFyB6b9Ghz5yXjv8QiiKDrzHA8rJzoVPChGJYmX"
    // the receiver the server rotated to
    private val rotatedMerchant = "Fi3zUczEY3MPrXMk4GTVgxXth1Mnv1ndknPPFFnLScXs"
    private val usdcMint = "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v"

    private fun quote(
        amountUsd: Double = 40.0,
        recipient: String = merchant,
        splTokenMint: String = usdcMint,
    ) = SolanaPaymentQuote(amountUsd = amountUsd, recipient = recipient, splTokenMint = splTokenMint)

    private fun request(
        reference: String = this.reference,
        amountUsd: Double = 40.0,
        plan: String = "yearly",
        url: String = payUrl(reference, "40"),
    ) = SolanaPayRequest(reference = reference, amountUsd = amountUsd, plan = plan, url = url)

    private fun payUrl(reference: String, amount: String, recipient: String = merchant) =
        "solana:$recipient?amount=$amount&spl-token=$usdcMint" +
            "&reference=$reference&label=URnetwork&message=UR%20Pro"

    @Test
    fun aWellFormedRequestHasNoProblems() {
        assertEquals(emptyList<String>(), solanaPayQuoteProblems(request(), quote()))
    }

    @Test
    fun aHexUuidReferenceIsRefused() {
        // The shipped bug: Solana Pay requires a base58 32-byte pubkey, and a
        // hex uuid is neither, so the webhook could never match the payment.
        val uuid = "0f9a6d2c8b7e4f1aa3c5d7e9f1b3c5d7"
        val problems = solanaPayQuoteProblems(
            request(reference = uuid, url = payUrl(uuid, "40")), quote(),
        )
        assertTrue(problems.any { it.contains("base58") })
    }

    @Test
    fun aMissingPlanIsRefused() {
        // The shipped bug: without the plan the server answers "Unknown plan."
        // and every Solana upgrade failed before the wallet opened.
        val problems = solanaPayQuoteProblems(request(plan = ""), quote())
        assertTrue(problems.any { it.contains("plan") })
    }

    @Test
    fun aHardcodedAmountIsRefusedWhenTheServerQuotesSomethingElse() {
        // The shipped bug: the client hardcoded 40 while the server quoted the
        // welcome-offer price.
        val problems = solanaPayQuoteProblems(request(amountUsd = 40.0), quote(amountUsd = 9.99))
        assertTrue(problems.any { it.contains("40.0") && it.contains("9.99") })
    }

    @Test
    fun aWrongMerchantIsRefused() {
        val url = "solana:SomeOtherMerchantAddress?amount=40&spl-token=$usdcMint&reference=$reference"
        val problems = solanaPayQuoteProblems(request(url = url), quote())
        assertTrue(problems.any { it.contains("merchant") })
    }

    @Test
    fun aMerchantTheServerNoLongerQuotesIsRefused() {
        // The server rotated its receiver: a client that still pays the address
        // it knew pays an address the server may no longer watch.
        val problems = solanaPayQuoteProblems(request(), quote(recipient = rotatedMerchant))
        assertTrue(problems.any { it.contains("merchant") && it.contains(rotatedMerchant) })
    }

    @Test
    fun theMerchantTheServerRotatedToIsPaid() {
        val rotated = request(url = payUrl(reference, "40", recipient = rotatedMerchant))
        assertEquals(emptyList<String>(), solanaPayQuoteProblems(rotated, quote(recipient = rotatedMerchant)))
    }

    @Test
    fun aWrongMintIsRefused() {
        val url = "solana:$merchant?amount=40&spl-token=NotUsdcMint&reference=$reference"
        val problems = solanaPayQuoteProblems(request(url = url), quote())
        assertTrue(problems.any { it.contains("mint the server quoted") })
    }

    @Test
    fun aUrlCarryingADifferentReferenceIsRefused() {
        val other = "9gNyzB68hKQP1AaKGVcEEye4B25LoUwGP3ZCzHBY43v3"
        val problems = solanaPayQuoteProblems(request(url = payUrl(other, "40")), quote())
        assertTrue(problems.any { it.contains("different reference") })
    }

    @Test
    fun aUrlAmountThatDisagreesWithTheQuoteIsRefused() {
        val problems = solanaPayQuoteProblems(request(url = payUrl(reference, "41")), quote())
        assertTrue(problems.any { it.contains("41.0") })
    }

    @Test
    fun aNonSolanaUrlIsRefused() {
        val problems = solanaPayQuoteProblems(request(url = "https://example.com/pay"), quote())
        assertTrue(problems.any { it.contains("not a solana: payment url") })
    }

    @Test
    fun aNonPositiveQuoteIsRefused() {
        val problems = solanaPayQuoteProblems(
            request(amountUsd = 0.0, url = payUrl(reference, "0")), quote(amountUsd = 0.0),
        )
        assertTrue(problems.any { it.contains("non-positive") })
    }

    @Test
    fun aCentOfRoundingIsTolerated() {
        // The server itself allows a one-cent tolerance, so the client must not
        // be failed for the same rounding.
        assertEquals(
            emptyList<String>(),
            solanaPayQuoteProblems(
                request(amountUsd = 39.99, url = payUrl(reference, "39.99")), quote(amountUsd = 39.991),
            ),
        )
    }

    @Test
    fun parsingReadsEveryFieldTheWalletNeeds() {
        val url = parseSolanaPayUrl(payUrl(reference, "40"))!!
        assertEquals(merchant, url.recipient)
        assertEquals("40", url.amount)
        assertEquals(usdcMint, url.splTokenMint)
        assertEquals(reference, url.reference)
    }

    @Test
    fun parsingRejectsWhatIsNotAPaymentUrl() {
        assertNull(parseSolanaPayUrl(""))
        assertNull(parseSolanaPayUrl("solana:"))
        assertNull(parseSolanaPayUrl("https://site.example"))
        assertNull(parseSolanaPayUrl("solana"))
    }

    @Test
    fun parsingDecodesPercentEscapes() {
        val url = parseSolanaPayUrl("solana:$merchant?amount=1&message=UR%20Pro%20%E2%80%94%20Yearly")
        assertEquals("1", url!!.amount)
    }

    @Test
    fun aReferenceIsThirtyTwoBytes() {
        assertTrue(isSolanaPayReference(reference))
        assertTrue(isSolanaPayReference(rotatedMerchant))
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
