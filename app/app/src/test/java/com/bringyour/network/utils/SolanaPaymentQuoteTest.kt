package com.bringyour.network.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The quote the app pays from a Solana Pay intent result: the server's amount and
 * where to pay it. The merchant is a fixture key (sha256 of a fixed phrase), not a
 * wallet; the mint is USDC on Solana mainnet.
 */
class SolanaPaymentQuoteTest {

    // the receiver the server rotated to
    private val rotatedMerchant = "Fi3zUczEY3MPrXMk4GTVgxXth1Mnv1ndknPPFFnLScXs"
    private val usdcMint = "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v"

    @Test
    fun theQuoteCarriesTheMerchantTheServerNamed() {
        // a rotation on the server reaches the payment with the next quote
        assertEquals(
            SolanaPaymentQuote(amountUsd = 40.004317, recipient = rotatedMerchant, splTokenMint = usdcMint),
            solanaPaymentQuote(40.004317, rotatedMerchant, usdcMint),
        )
    }

    @Test
    fun aQuoteThatDoesNotSayWhereToPayIsNotPaid() {
        // a server that predates the fields sends neither, and there is no
        // address built into the app to fall back to
        assertNull(solanaPaymentQuote(40.0, null, null))
        assertNull(solanaPaymentQuote(40.0, "", ""))
        assertNull(solanaPaymentQuote(40.0, rotatedMerchant, ""))
        assertNull(solanaPaymentQuote(40.0, "", usdcMint))
        assertNull(solanaPaymentQuote(40.0, "not a solana address", usdcMint))
    }

    @Test
    fun aNonPositiveAmountIsNotPaid() {
        // the webhook's check is `amount >= quoted - tolerance`, so a zero quote
        // is satisfied by any payment at all
        assertNull(solanaPaymentQuote(0.0, rotatedMerchant, usdcMint))
        assertNull(solanaPaymentQuote(-1.0, rotatedMerchant, usdcMint))
        assertNull(solanaPaymentQuote(Double.NaN, rotatedMerchant, usdcMint))
    }
}
