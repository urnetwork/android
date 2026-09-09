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
}
