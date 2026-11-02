package com.bringyour.network.ui.upgrade

import com.bringyour.network.ui.shared.enums.PlanType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlanOffersTest {

    private val base = PlanOffer(index = 0, periodDays = 365, freeDays = 0)
    private val trial = PlanOffer(index = 1, periodDays = 365, freeDays = 14)
    private val welcome = PlanOffer(index = 2, periodDays = 365, freeDays = 14, tags = listOf("onboarding25"))
    private val monthly = PlanOffer(index = 3, periodDays = 30, freeDays = 0)
    private val monthlyBase = PlanOffer(index = 4, periodDays = 30, freeDays = 0, basePlan = true)
    private val winback = PlanOffer(index = 5, periodDays = 30, freeDays = 0, tags = listOf("winback-monthly-50"))

    @Test
    fun yearlyPrefersTheWelcomeOfferTagWhileTheOfferIsActive() {
        val offers = listOf(base, trial, welcome, monthly)
        assertEquals(welcome, PlanOffers.yearly(offers, preferTag = "onboarding25"))
        assertEquals(welcome, PlanOffers.forPlan(offers, PlanType.YEARLY, preferTag = "onboarding25"))
    }

    @Test
    fun yearlyFallsBackToTheTrialOfferWithoutATagOrWhenPlayDoesNotListIt() {
        assertEquals(trial, PlanOffers.yearly(listOf(base, trial, welcome, monthly)))
        assertEquals(trial, PlanOffers.yearly(listOf(base, trial, monthly), preferTag = "onboarding25"))
        assertEquals(trial, PlanOffers.yearly(listOf(base, trial, monthly), preferTag = ""))
    }

    @Test
    fun yearlyFallsBackToTheBasePlanWithoutATrial() {
        assertEquals(base, PlanOffers.yearly(listOf(base, monthly)))
        assertEquals(0, PlanOffers.trialDays(listOf(base, monthly)))
    }

    @Test
    fun monthlyNeverTakesTheYearlyOffersAndTheTagNeverAppliesToIt() {
        val offers = listOf(base, trial, welcome, monthly)
        assertEquals(monthly, PlanOffers.forPlan(offers, PlanType.MONTHLY, preferTag = "onboarding25"))
        assertNull(PlanOffers.monthly(listOf(base, trial, welcome)))
    }

    @Test
    fun monthlyPrefersTheWinbackOfferTagWhileTheOfferIsActive() {
        val offers = listOf(base, trial, welcome, winback, monthlyBase)
        assertEquals(winback, PlanOffers.monthly(offers, preferTag = "winback-monthly-50"))
        assertEquals(winback, PlanOffers.forPlan(offers, PlanType.MONTHLY, preferTag = "winback-monthly-50"))
    }

    @Test
    fun monthlyNeverBuysTheWinbackOfferWithoutItsTagEvenWhenPlayListsItFirst() {
        // Play lists a developer-determined offer to every account
        val offers = listOf(winback, monthlyBase)
        assertEquals(monthlyBase, PlanOffers.monthly(offers))
        assertEquals(monthlyBase, PlanOffers.monthly(offers, preferTag = ""))
        assertEquals(monthlyBase, PlanOffers.forPlan(offers, PlanType.MONTHLY, preferTag = "onboarding25"))
        // the tagged offer alone is nothing to sell without its tag
        assertNull(PlanOffers.monthly(listOf(winback)))
    }

    @Test
    fun monthlyFallsBackToTheBasePlanWhenPlayDoesNotListTheTag() {
        assertEquals(monthlyBase, PlanOffers.monthly(listOf(base, trial, monthlyBase), preferTag = "winback-monthly-50"))
        assertEquals(monthlyBase, PlanOffers.forPlan(listOf(base, trial, monthlyBase), PlanType.MONTHLY, preferTag = "winback-monthly-50"))
    }

    @Test
    fun yearlyNeverBuysTheWelcomeOfferWithoutItsTag() {
        // an account Play lists no trial offer to (it had one before) still
        // sees the developer-determined welcome offer, possibly first
        assertEquals(base, PlanOffers.yearly(listOf(welcome, base)))
        val welcomeWithoutTrial = PlanOffer(index = 2, periodDays = 365, freeDays = 0, tags = listOf("onboarding25"))
        assertEquals(base, PlanOffers.yearly(listOf(welcomeWithoutTrial, base)))
        assertEquals(0, PlanOffers.trialDays(listOf(welcome, base)))
        // the win-back tag never reaches the yearly plan
        assertEquals(trial, PlanOffers.forPlan(listOf(welcome, trial, base), PlanType.YEARLY, preferTag = "winback-monthly-50"))
    }

    @Test
    fun aBasePlansTagsAreNotItsOffersOwn() {
        assertEquals(listOf("winback-monthly-50"), PlanOffers.ownTags(listOf("monthly", "winback-monthly-50"), setOf("monthly")))
        assertEquals(emptyList<String>(), PlanOffers.ownTags(listOf("monthly"), setOf("monthly")))
        assertEquals(listOf("onboarding25"), PlanOffers.ownTags(listOf("onboarding25"), emptySet()))
        // a tagged base plan is still the base plan
        val taggedBase = PlanOffer(index = 0, periodDays = 30, freeDays = 0, tags = listOf("monthly"), basePlan = true)
        assertEquals(taggedBase, PlanOffers.monthly(listOf(winback, taggedBase)))
    }
}
