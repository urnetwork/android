package com.bringyour.network.ui.upgrade

import com.bringyour.network.ui.shared.enums.PlanType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow

/**
 * The price strings (mmm/onboarding/PLAN.md "PRICE PRESENTATION"), built with a
 * pure twin of the SDK's equivalent rule: ceiling to the minor unit, saving
 * rounded down, suppressed under one major unit.
 */
class PlanPresentationTest {

    private val rule: (Double, Double, Int) -> EquivalentResult = { yearly, monthly, digits ->
        val scale = 10.0.pow(digits)
        val yearlyMinor = Math.round(yearly * scale)
        val monthlyMinor = Math.round(monthly * scale)
        val equivalentMinor = ceil(yearlyMinor / 12.0).toLong()
        val saving = if (0 < monthlyMinor) floor(100.0 * (12 * monthlyMinor - yearlyMinor) / (12.0 * monthlyMinor)).toInt().coerceAtLeast(0) else 0
        EquivalentResult(equivalentMinor / scale, scale <= equivalentMinor, saving)
    }

    private val standard = TierInput("standard", 40.0, 5.0, "USD")
    private val regional = TierInput("regional", 4.0, 0.5, "USD")

    @Test
    fun standardTierPrintsTheHeadlineTheCeilingEquivalentAndTheSaving() {
        val p = PlanPresentations.build(standard, null, StorePrice(39.99, "USD"), StorePrice(4.99, "USD"), Locale.US, rule)
        assertEquals("$39.99", p.yearlyPrice)
        assertEquals("$4.99", p.monthlyPrice)
        // 39.99 / 12 = 3.3325 -> rounded up, never understated
        assertEquals("$3.34", p.monthlyEquivalent)
        assertEquals(33, p.savingPercent)
        assertNull(p.offer)
        assertFalse(p.isRegional)
    }

    @Test
    fun regionalTierNeverPrintsAPerMonthLine() {
        val p = PlanPresentations.build(regional, null, locale = Locale.US, equivalent = rule)
        assertEquals("$4.00", p.yearlyPrice)
        assertEquals("$0.50", p.monthlyPrice)
        assertNull(p.monthlyEquivalent)
        assertTrue(p.isRegional)
    }

    @Test
    fun theWelcomeOfferScalesToTheStoresCurrencyByTheTiersRatio() {
        val offer = OfferInput(firstYearUsd = 30.0, percentOff = 25, monthsFree = 3, expiresAtMillis = 1_800_000_000_000L, playOfferTag = "onboarding25")
        // the store prints 39.99 for the 40.00 tier: the offer follows the store's figure
        val p = PlanPresentations.build(standard, offer, StorePrice(39.99, "USD"), StorePrice(4.99, "USD"), Locale.US, rule)
        val o = p.offer
        assertNotNull(o)
        assertEquals("$29.99", o!!.firstYearPrice)
        assertEquals("$39.99", o.regularYearPrice)
        assertEquals(25, o.percentOff)
        assertEquals(3, o.monthsFree)
        assertEquals("onboarding25", o.playOfferTag)
    }

    @Test
    fun theOfferOnTheRegionalTierIsThreeDollars() {
        val offer = OfferInput(firstYearUsd = 3.0, percentOff = 25, monthsFree = 3, expiresAtMillis = 1L)
        val p = PlanPresentations.build(regional, offer, locale = Locale.US, equivalent = rule)
        assertEquals("$3.00", p.offer?.firstYearPrice)
        assertEquals("$4.00", p.offer?.regularYearPrice)
    }

    @Test
    fun zeroDecimalCurrenciesKeepWholeAmounts() {
        val p = PlanPresentations.build(standard, null, StorePrice(5900.0, "JPY"), StorePrice(750.0, "JPY"), Locale.JAPAN, rule)
        assertTrue(p.yearlyPrice, p.yearlyPrice.contains("5,900") || p.yearlyPrice.contains("5900"))
        // 5900 / 12 = 491.67 -> 492
        assertTrue(p.monthlyEquivalent!!, p.monthlyEquivalent!!.contains("492"))
    }

    @Test
    fun theFallbackIsTheStandardUsdTierWithNoOffer() {
        val p = PlanPresentations.build(null as TierInput?, null, locale = Locale.US, equivalent = rule)
        assertEquals("$40.00", p.yearlyPrice)
        assertEquals("$5.00", p.monthlyPrice)
        assertEquals("$3.34", p.monthlyEquivalent)
        assertNull(p.offer)
    }

    private val playWinback = WinbackInput(percentOff = 50, expiresAtMillis = 1_800_000_000_000L, store = "play", playOfferTag = "winback-monthly-50")
    private val stripeWinback = WinbackInput(percentOff = 50, expiresAtMillis = 1_800_000_000_000L, store = "stripe")

    private fun withWinback(winback: WinbackInput, winbackStore: String, tier: TierInput = standard) =
        PlanPresentations.build(tier, null, locale = Locale.US, equivalent = rule, winback = winback, winbackStore = winbackStore)

    @Test
    fun thePlayWinbackOfferPrintsPlaysOwnFirstMonthPrice() {
        val p = PlanPresentations.build(
            standard, null, StorePrice(39.99, "USD"), StorePrice(4.99, "USD"), Locale.US, rule,
            winback = playWinback, winbackStore = "play", storeWinbackFirstMonth = StorePrice(2.49, "USD"),
        )
        val w = p.winback
        assertNotNull(w)
        assertEquals("$2.49", w!!.firstMonthPrice)
        assertEquals("$4.99", w.regularMonthPrice)
        assertEquals(50, w.percentOff)
        assertEquals(1_800_000_000_000L, w.expiresAtMillis)
        assertEquals("winback-monthly-50", w.playOfferTag)
        assertEquals(2.49, p.firstChargeAmount(PlanType.MONTHLY), 0.0)
        // nothing on the yearly plan
        assertNull(p.offer)
        assertEquals(39.99, p.firstChargeAmount(PlanType.YEARLY), 0.0)
    }

    @Test
    fun thePlayWinbackOfferShowsNothingWhilePlayDoesNotListItsTag() {
        val p = PlanPresentations.build(
            standard, null, StorePrice(39.99, "USD"), StorePrice(4.99, "USD"), Locale.US, rule,
            winback = playWinback, winbackStore = "play", storeWinbackFirstMonth = null,
        )
        assertNull(p.winback)
        assertEquals(4.99, p.firstChargeAmount(PlanType.MONTHLY), 0.0)
    }

    @Test
    fun theStripeWinbackOfferIsTheMonthlyPriceAtItsDiscount() {
        val w = withWinback(stripeWinback, "stripe").winback
        assertNotNull(w)
        assertEquals("$2.50", w!!.firstMonthPrice)
        assertEquals("$5.00", w.regularMonthPrice)
        assertEquals(2.5, w.firstMonthAmount, 0.0)
        // Stripe applies it at checkout: no offer to pick in the app
        assertEquals("", w.playOfferTag)
        // half of the regional tier's $0.50
        assertEquals("$0.25", withWinback(stripeWinback, "stripe", regional).winback?.firstMonthPrice)
    }

    @Test
    fun aWinbackOfferForAnotherStoreOrPlanShowsNothing() {
        // a Stripe offer on the Play app, a Play offer on a Stripe flavor, an App Store offer anywhere
        assertNull(withWinback(stripeWinback, "play").winback)
        assertNull(withWinback(playWinback, "stripe").winback)
        assertNull(withWinback(stripeWinback.copy(store = "apple"), "stripe").winback)
        assertNull(withWinback(stripeWinback, "").winback)
        // only the monthly plan, and only an offer that takes something off
        assertNull(withWinback(stripeWinback.copy(plan = "yearly"), "stripe").winback)
        assertNull(withWinback(stripeWinback.copy(percentOff = 0), "stripe").winback)
    }

    @Test
    fun theWinbackOfferAndTheWelcomeOfferEachKeepTheirOwnPlan() {
        val offer = OfferInput(firstYearUsd = 30.0, percentOff = 25, monthsFree = 3, expiresAtMillis = 1_800_000_000_000L)
        val p = PlanPresentations.build(standard, offer, locale = Locale.US, equivalent = rule, winback = stripeWinback, winbackStore = "stripe")
        assertEquals(30.0, p.firstChargeAmount(PlanType.YEARLY), 0.0)
        assertEquals(2.5, p.firstChargeAmount(PlanType.MONTHLY), 0.0)
    }

    @Test
    fun theDiscountRoundsHalfUpToTheMinorUnitAsTheServerDoes() {
        // 2.495 -> 2.50: never below what the checkout charges
        assertEquals(2.5, PlanPresentations.discountedAmount(4.99, 50, 2), 0.0)
        assertEquals(2.5, PlanPresentations.discountedAmount(5.0, 50, 2), 0.0)
        assertEquals(0.25, PlanPresentations.discountedAmount(0.5, 50, 2), 0.0)
        assertEquals(376.0, PlanPresentations.discountedAmount(751.0, 50, 0), 0.0)
    }
}
