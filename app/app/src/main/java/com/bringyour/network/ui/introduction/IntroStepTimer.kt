package com.bringyour.network.ui.introduction

import com.bringyour.network.analytics.ClientEvents
import com.bringyour.network.ui.IntroRoute

/**
 * Times each onboarding page and emits the step events
 * (`onboarding.step.shown|completed|skipped`) with the page's name, its index
 * and the time spent on it. A page counts as completed when the next page
 * appears (or the flow closes from it), as skipped when Skip leaves it.
 */
class IntroStepTimer {

    private var currentStep: String? = null
    private var currentShownAt = 0L

    /** A page appeared: complete the previous one, start timing this one. */
    fun shown(route: String) {
        val step = stepFor(route) ?: return
        if (step == currentStep) {
            return
        }
        currentStep?.let { previous ->
            ClientEvents.onboardingStepCompleted(previous, indexOf(previous), elapsed())
        }
        currentStep = step
        currentShownAt = System.currentTimeMillis()
        ClientEvents.onboardingStepShown(step, indexOf(step))
    }

    /** Skip left the current page. */
    fun skipped(route: String) {
        val step = stepFor(route) ?: currentStep ?: return
        ClientEvents.onboardingStepSkipped(step, indexOf(step), elapsed())
        currentStep = null
    }

    /** The page's own button moved on. */
    fun completed(route: String) {
        val step = stepFor(route) ?: currentStep ?: return
        ClientEvents.onboardingStepCompleted(step, indexOf(step), elapsed())
        currentStep = null
    }

    private fun elapsed(): Long = if (0L < currentShownAt) System.currentTimeMillis() - currentShownAt else 0L

    companion object {
        val STEPS = listOf(
            ClientEvents.STEP_WELCOME,
            ClientEvents.STEP_USAGE,
            ClientEvents.STEP_PROVIDE,
            ClientEvents.STEP_REFERRAL,
            ClientEvents.STEP_WIDGETS,
            ClientEvents.STEP_OFFER,
        )

        fun indexOf(step: String): Int = STEPS.indexOf(step).coerceAtLeast(0)

        /** The step name for a nav route (the route string contains the object's qualified name). */
        fun stepFor(route: String): String? = when {
            route.contains(IntroRoute.IntroductionInitial::class.qualifiedName.toString()) -> ClientEvents.STEP_WELCOME
            route.contains(IntroRoute.IntroductionUsageBar::class.qualifiedName.toString()) -> ClientEvents.STEP_USAGE
            route.contains(IntroRoute.IntroductionSettings::class.qualifiedName.toString()) -> ClientEvents.STEP_PROVIDE
            route.contains(IntroRoute.IntroductionReferral::class.qualifiedName.toString()) -> ClientEvents.STEP_REFERRAL
            route.contains(IntroRoute.IntroductionQuickConnect::class.qualifiedName.toString()) -> ClientEvents.STEP_WIDGETS
            route.contains(IntroRoute.IntroductionOffer::class.qualifiedName.toString()) -> ClientEvents.STEP_OFFER
            else -> null
        }
    }
}
