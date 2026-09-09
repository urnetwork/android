package com.bringyour.network.ui.introduction

import com.bringyour.network.analytics.ClientEvents
import com.bringyour.network.ui.IntroRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IntroSkipTest {

    private fun route(step: Any): String = "com.bringyour.network.ui." + step::class.qualifiedName + "/{args}"

    @Test
    fun skipFromAnyEarlierPageLandsOnTheOfferPage() {
        for (step in listOf(IntroRoute.IntroductionInitial, IntroRoute.IntroductionUsageBar, IntroRoute.IntroductionSettings, IntroRoute.IntroductionReferral, IntroRoute.IntroductionQuickConnect)) {
            assertEquals(IntroSkip.Action.GO_TO_OFFER, IntroSkip.decide(route(step), offerHoldout = false))
        }
    }

    @Test
    fun skipFromTheOfferPageLeavesTheFlowSoItIsShownOnce() {
        assertEquals(IntroSkip.Action.DISMISS, IntroSkip.decide(route(IntroRoute.IntroductionOffer), offerHoldout = false))
    }

    @Test
    fun theHoldoutNeverSeesTheOfferPage() {
        assertEquals(IntroSkip.Action.DISMISS, IntroSkip.decide(route(IntroRoute.IntroductionInitial), offerHoldout = true))
    }

    @Test
    fun theStepTimerNamesEveryPageInOrder() {
        assertEquals(listOf("welcome", "usage", "provide", "referral", "widgets", "offer"), IntroStepTimer.STEPS)
        assertEquals(ClientEvents.STEP_WELCOME, IntroStepTimer.stepFor(route(IntroRoute.IntroductionInitial)))
        assertEquals(ClientEvents.STEP_WIDGETS, IntroStepTimer.stepFor(route(IntroRoute.IntroductionQuickConnect)))
        assertEquals(ClientEvents.STEP_OFFER, IntroStepTimer.stepFor(route(IntroRoute.IntroductionOffer)))
        assertEquals(5, IntroStepTimer.indexOf(ClientEvents.STEP_OFFER))
        assertNull(IntroStepTimer.stepFor("com.bringyour.network.ui.Route.Connect"))
    }
}
