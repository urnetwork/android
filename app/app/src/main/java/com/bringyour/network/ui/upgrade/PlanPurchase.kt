package com.bringyour.network.ui.upgrade

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.bringyour.network.ui.shared.viewmodels.PlanViewModel
import com.bringyour.sdk.OnboardingOffer
import com.bringyour.sdk.PriceTier

/**
 * The plan presentation for the current tier, offer and the store's prices
 * (the flavor's PlanViewModel exposes the store prices it has; null where the
 * app has no store). Recomputed when any input changes.
 */
@Composable
fun rememberPlanPresentation(
    planViewModel: PlanViewModel,
    priceTier: PriceTier?,
    offer: OnboardingOffer?,
): PlanPresentation {
    val storeYearly = planViewModel.storeYearlyPrice
    val storeMonthly = planViewModel.storeMonthlyPrice
    return remember(priceTier, offer, storeYearly, storeMonthly) {
        PlanPresentations.build(priceTier, offer, storeYearly, storeMonthly)
    }
}
