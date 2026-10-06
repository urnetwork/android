package com.bringyour.network.acceptance

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bringyour.network.utils.SOLANA_PLAN_MONTHLY
import com.bringyour.network.utils.SOLANA_PLAN_YEARLY
import com.bringyour.network.utils.SolanaPaymentQuote
import com.bringyour.network.utils.buildSolanaPaymentUrl
import com.bringyour.network.utils.createPaymentReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The `usdc-quote` acceptance case: the payment this app builds, checked
 * against what a server quoted, the price and where to pay it.
 *
 * It runs on a device because the url is built by the gomobile SDK, which is
 * the point -- the rules are shared with every other platform in
 * sdk/solana_pay.go, and this proves the Android binding of them rather than a
 * reimplementation. It spends nothing and contacts nothing: the quote is
 * injected, exactly as a server response would arrive.
 *
 * On the flavors that sell through Solana Pay (github/fdroid via webPay,
 * solana_dapp and ethos_dapp via stripeSheet) this is the request the wallet
 * receives. On play there is no Solana surface, but the url builder is shared
 * code in src/main, so the case still guards it.
 *
 * The merchant addresses are fixture keys (sha256 of a fixed phrase), not
 * wallets; the mint is USDC on Solana mainnet.
 */
@RunWith(AndroidJUnit4::class)
class SolanaPayQuoteAcceptanceTest {

    /** What a server might quote. Never constants the client chose. */
    private val quote = SolanaPaymentQuote(
        amountUsd = 39.99,
        recipient = "835ygSFyB6b9Ghz5yXjv8QiiKDrzHA8rJzoVPChGJYmX",
        splTokenMint = "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v",
    )

    /** The receiver the server rotated to. */
    private val rotatedMerchant = "Fi3zUczEY3MPrXMk4GTVgxXth1Mnv1ndknPPFFnLScXs"

    @Test
    fun theAppBuildsThePaymentTheServerQuoted() {
        val reference = createPaymentReference()
        val url = buildSolanaPaymentUrl(reference, quote, SOLANA_PLAN_YEARLY)
        val problems = solanaPayQuoteProblems(
            SolanaPayRequest(
                reference = reference,
                amountUsd = quote.amountUsd,
                plan = SOLANA_PLAN_YEARLY,
                url = url,
            ),
            quote,
        )
        assertEquals("the app built a payment that disagrees with the quote: $problems", emptyList<String>(), problems)
    }

    @Test
    fun theMonthlyPlanIsSellableToo() {
        // The hardcoded-amount bug made the monthly plan unsellable entirely.
        val reference = createPaymentReference()
        val monthly = quote.copy(amountUsd = 5.00)
        val problems = solanaPayQuoteProblems(
            SolanaPayRequest(
                reference = reference,
                amountUsd = monthly.amountUsd,
                plan = SOLANA_PLAN_MONTHLY,
                url = buildSolanaPaymentUrl(reference, monthly, SOLANA_PLAN_MONTHLY),
            ),
            monthly,
        )
        assertEquals(emptyList<String>(), problems)
    }

    @Test
    fun everyReferenceIsAFreshBase58PublicKey() {
        // The shipped bug was a hex uuid here, which the indexer could never
        // surface, so the payment was unmatchable and the money was lost.
        val seen = mutableSetOf<String>()
        repeat(16) {
            val reference = createPaymentReference()
            assertTrue("reference $reference is not a base58 32-byte pubkey", isSolanaPayReference(reference))
            assertTrue("references must not repeat", seen.add(reference))
        }
    }

    @Test
    fun aPriceTheClientInventedIsCaught() {
        // The guard itself has to work, or the case above passes vacuously.
        val reference = createPaymentReference()
        val invented = quote.copy(amountUsd = 40.00)
        val problems = solanaPayQuoteProblems(
            SolanaPayRequest(
                reference = reference,
                amountUsd = invented.amountUsd,
                plan = SOLANA_PLAN_YEARLY,
                url = buildSolanaPaymentUrl(reference, invented, SOLANA_PLAN_YEARLY),
            ),
            quote,
        )
        assertTrue("a client-invented price must be caught", problems.isNotEmpty())
    }

    @Test
    fun thePaymentPaysTheMerchantAndMintTheServerQuoted() {
        // After a rotation on the server the quote names a new receiver, and
        // that is the address the wallet is asked to pay. USDC is issued on a
        // dozen chains; the quoted mint is the one the server credits.
        val reference = createPaymentReference()
        val rotated = quote.copy(recipient = rotatedMerchant)
        val url = parseSolanaPayUrl(buildSolanaPaymentUrl(reference, rotated, SOLANA_PLAN_YEARLY))
        assertNotEquals(null, url)
        assertEquals(rotatedMerchant, url!!.recipient)
        assertEquals(rotated.splTokenMint, url.splTokenMint)
        assertEquals(reference, url.reference)
    }

    @Test
    fun aQuoteWithoutAMerchantBuildsNoPayment() {
        // The sdk refuses rather than pay an address nobody quoted.
        val reference = createPaymentReference()
        assertThrows(Exception::class.java) {
            buildSolanaPaymentUrl(reference, quote.copy(recipient = ""), SOLANA_PLAN_YEARLY)
        }
    }
}
