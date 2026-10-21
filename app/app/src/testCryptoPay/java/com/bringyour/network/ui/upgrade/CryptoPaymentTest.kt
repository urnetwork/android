package com.bringyour.network.ui.upgrade

import com.bringyour.network.analytics.ClientEvents
import com.bringyour.network.ui.shared.enums.PlanType
import com.bringyour.sdk.Sdk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The payment method row's rules (mmm/ZCASH-CONTRACT.md §6.1) on the flavors
 * that sell outside a store: which methods show and in what order, which one
 * is chosen, what a coin buys and what it greys out, and where a refused start
 * goes. Plain values; the sdk's PaymentMethodAllowsPlan is a twin here.
 */
class CryptoPaymentTest {

    private val card = Sdk.PaymentMethodCard
    private val usdc = Sdk.CryptoAssetUsdcSolana
    private val zec = Sdk.CryptoAssetZec
    private val xmr = Sdk.CryptoAssetXmr

    // the sdk's PaymentMethodAllowsPlan: false only for a coin with monthly
    private val allowsPlan: (String, String) -> Boolean = { method, plan ->
        !(method != Sdk.PaymentMethodCard && plan == Sdk.PlanMonthly)
    }

    @Test
    fun theRowIsCardThenTheListedCoinsInOneOrder() {
        assertEquals(listOf(card, usdc, zec, xmr), CryptoPayment.rowMethods(listOf(card, xmr, zec, usdc)))
        assertEquals(listOf(card, xmr), CryptoPayment.rowMethods(listOf(card, xmr)))
        // the card leads even when a list leaves it out
        assertEquals(listOf(card, zec), CryptoPayment.rowMethods(listOf(zec)))
        // a coin this app has no label for is not shown blank
        assertEquals(listOf(card, usdc), CryptoPayment.rowMethods(listOf(card, "btc", usdc)))
        assertEquals("card", card)
        assertEquals(listOf("card", "usdc_solana", "zec", "xmr"), CryptoPayment.METHOD_ORDER)
    }

    @Test
    fun withNoCoinListedThereIsNoRow() {
        assertFalse(CryptoPayment.showsRow(listOf(card)))
        assertFalse(CryptoPayment.showsRow(emptyList()))
        assertTrue(CryptoPayment.showsRow(listOf(card, xmr)))
        assertTrue(CryptoPayment.showsRow(CryptoPayment.rowMethods(listOf(card, usdc))))
    }

    @Test
    fun theRememberedMethodIsChosenWhileTheRowListsIt() {
        val all = listOf(card, usdc, zec, xmr)
        assertEquals(xmr, CryptoPayment.chosenMethod(xmr, all))
        assertEquals(usdc, CryptoPayment.chosenMethod(usdc, all))
        assertEquals(card, CryptoPayment.chosenMethod(card, all))
    }

    @Test
    fun aRememberedCoinTheServerNoLongerListsFallsBackToTheCard() {
        assertEquals(card, CryptoPayment.chosenMethod(zec, listOf(card, usdc, xmr)))
        // no coin listed at all
        assertEquals(card, CryptoPayment.chosenMethod(xmr, listOf(card)))
        // nothing remembered yet (the sdk answers "")
        assertEquals(card, CryptoPayment.chosenMethod("", listOf(card, xmr)))
        assertEquals(card, CryptoPayment.chosenMethod(null, listOf(card, xmr)))
        // a value the sdk would never keep
        assertEquals(card, CryptoPayment.chosenMethod("paypal", listOf(card, xmr)))
    }

    @Test
    fun aCoinBuysTheWelcomeOfferWhileItIsActiveElseTheYearlyPlan() {
        assertEquals(Sdk.PlanYearlyOnboarding, CryptoPayment.coinPlan(offerActive = true))
        assertEquals(Sdk.PlanYearly, CryptoPayment.coinPlan(offerActive = false))
        assertEquals("yearly_onboarding", CryptoPayment.coinPlan(offerActive = true))
        assertEquals("yearly", CryptoPayment.coinPlan(offerActive = false))
    }

    @Test
    fun pickingACoinMovesMonthlyToYearly() {
        for (coin in listOf(usdc, zec, xmr)) {
            assertEquals(PlanType.YEARLY, CryptoPayment.planFor(coin, PlanType.MONTHLY, allowsPlan))
            assertEquals(PlanType.YEARLY, CryptoPayment.planFor(coin, PlanType.YEARLY, allowsPlan))
        }
    }

    @Test
    fun theCardKeepsMonthly() {
        assertEquals(PlanType.MONTHLY, CryptoPayment.planFor(card, PlanType.MONTHLY, allowsPlan))
        assertEquals(PlanType.YEARLY, CryptoPayment.planFor(card, PlanType.YEARLY, allowsPlan))
    }

    @Test
    fun anUnavailableCoinIsSaidUnderTheRow() {
        assertEquals(CryptoStartRefusal.Unavailable, CryptoPayment.startRefusal("asset_unavailable", "Monero can't be quoted right now."))
        assertEquals("asset_unavailable", Sdk.PurchaseErrorCodeAssetUnavailable)
    }

    @Test
    fun aGuestNetworkAddsASignInMethodFirst() {
        assertEquals(CryptoStartRefusal.AddSignInMethod, CryptoPayment.startRefusal("guest_sign_in_required", "Add a sign-in method first."))
    }

    @Test
    fun everyOtherRefusalIsTheSurfacesErrorLine() {
        for (code in listOf("rate_limited", "offer_unavailable", "start_failed", "plan_unavailable", "invalid_request", "item_unavailable")) {
            assertEquals(CryptoStartRefusal.Refused(code, "words"), CryptoPayment.startRefusal(code, "words"))
        }
        // a transport failure has no code, only the transport's words
        assertEquals(CryptoStartRefusal.Refused("", "i/o timeout"), CryptoPayment.startRefusal("", "i/o timeout"))
        // codes compare exactly
        assertEquals(CryptoStartRefusal.Refused("ASSET_UNAVAILABLE", ""), CryptoPayment.startRefusal("ASSET_UNAVAILABLE", ""))
    }

    @Test
    fun theEventsNameTheCoinsStore() {
        assertEquals(Sdk.EventStoreSolana, CryptoPayment.eventStore(usdc))
        assertEquals(Sdk.EventStoreZcash, CryptoPayment.eventStore(zec))
        assertEquals(Sdk.EventStoreMonero, CryptoPayment.eventStore(xmr))
        assertNull(CryptoPayment.eventStore(card))
        assertEquals("solana", CryptoPayment.eventStore(usdc))
        assertEquals("zcash", CryptoPayment.eventStore(zec))
        assertEquals("monero", CryptoPayment.eventStore(xmr))

        assertEquals(ClientEvents.PRODUCT_SOLANA_PRO_YEARLY, CryptoPayment.eventProduct(usdc))
        assertEquals("pro_yearly_zec", CryptoPayment.eventProduct(zec))
        assertEquals("pro_yearly_xmr", CryptoPayment.eventProduct(xmr))
        assertNull(CryptoPayment.eventProduct(card))
    }

    // the server's event schema knows yearly and monthly only (EventPlans)
    @Test
    fun theEventsNameTheWelcomeOfferAsTheYearlyPlan() {
        assertEquals("yearly", CryptoPayment.eventPlan(Sdk.PlanYearlyOnboarding))
        assertEquals("yearly", CryptoPayment.eventPlan(Sdk.PlanYearly))
        assertEquals("monthly", CryptoPayment.eventPlan(Sdk.PlanMonthly))
    }

    @Test
    fun theCountdownIsMinutesAndSeconds() {
        assertEquals("30:00", CryptoPayment.countdown(1800))
        assertEquals("29:59", CryptoPayment.countdown(1799))
        assertEquals("1:00", CryptoPayment.countdown(60))
        assertEquals("0:59", CryptoPayment.countdown(59))
        assertEquals("0:05", CryptoPayment.countdown(5))
        assertEquals("0:00", CryptoPayment.countdown(0))
        assertEquals("0:00", CryptoPayment.countdown(-3))
        // a USDC quote holds an hour
        assertEquals("60:00", CryptoPayment.countdown(3600))
    }

    @Test
    fun anExactAmountIsMoreThanZeroOrNot() {
        assertTrue(CryptoPayment.isPositive("0.000001"))
        assertTrue(CryptoPayment.isPositive("0.012345"))
        assertTrue(CryptoPayment.isPositive(" 1 "))
        assertFalse(CryptoPayment.isPositive("0"))
        assertFalse(CryptoPayment.isPositive("0.000000"))
        assertFalse(CryptoPayment.isPositive(""))
        assertFalse(CryptoPayment.isPositive("-0.1"))
        assertFalse(CryptoPayment.isPositive("not an amount"))
    }

    @Test
    fun theSdksTimesAreRfc3339() {
        assertEquals(1_792_521_000_000L, CryptoPayment.timeMillis("2026-10-20T18:30:00Z"))
        assertEquals(1_792_521_000_000L, CryptoPayment.timeMillis("2026-10-20T20:30:00+02:00"))
        assertEquals(1_792_521_000_123L, CryptoPayment.timeMillis("2026-10-20T18:30:00.123456789Z"))
        assertNull(CryptoPayment.timeMillis(""))
        assertNull(CryptoPayment.timeMillis(null))
        assertNull(CryptoPayment.timeMillis("yesterday"))
    }
}
