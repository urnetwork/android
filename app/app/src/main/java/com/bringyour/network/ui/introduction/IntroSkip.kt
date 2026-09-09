package com.bringyour.network.ui.introduction

/**
 * What Skip does on an onboarding page (mmm/onboarding/PLAN.md "THE IN-APP
 * OFFER SCREEN"): everyone lands on the final offer page once; from the offer
 * page itself, and for the in-app holdout, Skip leaves the flow.
 */
object IntroSkip {
    enum class Action { GO_TO_OFFER, DISMISS }

    fun decide(currentRoute: String, offerHoldout: Boolean): Action {
        val onOffer = currentRoute.contains(com.bringyour.network.ui.IntroRoute.IntroductionOffer::class.qualifiedName.toString())
        return if (offerHoldout || onOffer) Action.DISMISS else Action.GO_TO_OFFER
    }
}
