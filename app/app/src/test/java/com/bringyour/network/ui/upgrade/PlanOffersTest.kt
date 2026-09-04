package com.bringyour.network.ui.upgrade

import com.bringyour.network.ui.shared.enums.PlanType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlanOffersTest {

    private val monthly = PlanOffer(index = 0, periodDays = 30, freeDays = 0)
    private val yearlyPlain = PlanOffer(index = 1, periodDays = 365, freeDays = 0)
    private val yearlyTrial = PlanOffer(index = 2, periodDays = 365, freeDays = 14)

    @Test
    fun monthlyOnlyStoreHasNoYearlyAndNoTrial() {
        val offers = listOf(monthly)
        assertNull(PlanOffers.yearly(offers))
        assertEquals(0, PlanOffers.trialDays(offers))
        // the yearly plan cannot be bought: the purchase reports that instead of selling monthly
        assertNull(PlanOffers.forPlan(offers, PlanType.YEARLY))
        assertEquals(monthly, PlanOffers.forPlan(offers, PlanType.MONTHLY))
    }

    @Test
    fun yearlyWithoutAFreePhasePromisesNoTrial() {
        val offers = listOf(monthly, yearlyPlain)
        assertEquals(yearlyPlain, PlanOffers.yearly(offers))
        assertEquals(0, PlanOffers.trialDays(offers))
        assertEquals(yearlyPlain, PlanOffers.forPlan(offers, PlanType.YEARLY))
    }

    @Test
    fun theTrialOfferWinsOverThePlainBasePlanWhateverTheOrder() {
        // the base plan is listed first, the trial offer after it
        val offers = listOf(monthly, yearlyPlain, yearlyTrial)
        assertEquals(yearlyTrial, PlanOffers.yearly(offers))
        assertEquals(14, PlanOffers.trialDays(offers))
        assertEquals(yearlyTrial, PlanOffers.forPlan(offers, PlanType.YEARLY))

        // and the other way round
        val reversed = listOf(yearlyTrial.copy(index = 0), yearlyPlain.copy(index = 1), monthly.copy(index = 2))
        assertEquals(0, PlanOffers.yearly(reversed)?.index)
        assertEquals(14, PlanOffers.trialDays(reversed))
    }

    @Test
    fun monthlyIsNeverPromisedATrialAndIsPickedByItsPeriod() {
        val monthlyTrial = PlanOffer(index = 3, periodDays = 30, freeDays = 7)
        val offers = listOf(yearlyTrial, monthlyTrial)
        assertEquals(yearlyTrial, PlanOffers.forPlan(offers, PlanType.YEARLY))
        assertEquals(monthlyTrial, PlanOffers.forPlan(offers, PlanType.MONTHLY))
        // the trial the picker shows is the yearly plan's, never the monthly one's
        assertEquals(14, PlanOffers.trialDays(offers))
    }

    @Test
    fun unknownPeriodsAreNeitherPlan() {
        val unknown = PlanOffer(index = 0, periodDays = 0, freeDays = 0)
        assertNull(PlanOffers.yearly(listOf(unknown)))
        assertNull(PlanOffers.monthly(listOf(unknown)))
        assertNull(PlanOffers.forPlan(listOf(unknown), PlanType.YEARLY))
        assertEquals(0, PlanOffers.trialDays(emptyList()))
        assertNull(PlanOffers.forPlan(emptyList(), PlanType.MONTHLY))
    }
}
