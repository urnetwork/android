package com.bringyour.network.acceptance

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bringyour.network.utils.SOLANA_PLAN_MONTHLY
import com.bringyour.network.utils.SOLANA_PLAN_YEARLY
import com.bringyour.network.utils.buildSolanaPaymentUrl
import com.bringyour.network.utils.createPaymentReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The `usdc-quote` acceptance case: the payment THIS APP builds, checked
 * against the price a server quoted.
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
 */
@RunWith(AndroidJUnit4::class)
class SolanaPayQuoteAcceptanceTest {

    /** A price a server might quote. Never a constant the client chose. */
    private val quotedAmountUsd = 39.99

    @Test
    fun theAppBuildsThePaymentTheServerQuoted() {
        val reference = createPaymentReference()
        val url = buildSolanaPaymentUrl(reference, quotedAmountUsd, SOLANA_PLAN_YEARLY)
        val problems = solanaPayQuoteProblems(
            SolanaPayRequest(
                reference = reference,
                amountUsd = quotedAmountUsd,
                plan = SOLANA_PLAN_YEARLY,
                url = url,
            ),
            quotedAmountUsd,
        )
        assertEquals("the app built a payment that disagrees with the quote: $problems", emptyList<String>(), problems)
    }

    @Test
    fun theMonthlyPlanIsSellableToo() {
        // The hardcoded-amount bug made the monthly plan unsellable entirely.
        val reference = createPaymentReference()
        val monthly = 5.00
        val problems = solanaPayQuoteProblems(
            SolanaPayRequest(
                reference = reference,
                amountUsd = monthly,
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
        val invented = 40.00
        val problems = solanaPayQuoteProblems(
            SolanaPayRequest(
                reference = reference,
                amountUsd = invented,
                plan = SOLANA_PLAN_YEARLY,
                url = buildSolanaPaymentUrl(reference, invented, SOLANA_PLAN_YEARLY),
            ),
            quotedAmountUsd,
        )
        assertTrue("a client-invented price must be caught", problems.isNotEmpty())
    }

    @Test
    fun thePaymentNamesTheMerchantAndUsdcOnSolana() {
        val reference = createPaymentReference()
        val url = parseSolanaPayUrl(buildSolanaPaymentUrl(reference, quotedAmountUsd, SOLANA_PLAN_YEARLY))
        assertNotEquals(null, url)
        assertEquals(SOLANA_MERCHANT_ADDRESS, url!!.recipient)
        // USDC is issued on a dozen chains. This is the only one the server
        // credits, and a transfer of any other token is unrecoverable.
        assertEquals(SOLANA_USDC_MINT, url.splTokenMint)
        assertEquals(reference, url.reference)
    }
}
