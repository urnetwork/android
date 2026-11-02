package com.bringyour.network.ui.upgrade

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.bringyour.network.ui.shared.viewmodels.PlanViewModel
import com.bringyour.sdk.OnboardingOffer
import com.bringyour.sdk.PriceTier
import com.bringyour.sdk.WinbackOffer

/**
 * The plan presentation for the current tier, offers and the store's prices
 * (the flavor's PlanViewModel exposes the store prices it has; null where the
 * app has no store, and the store it sells the monthly plan through, whose
 * win-back offer alone it shows). Recomputed when any input changes.
 */
@Composable
fun rememberPlanPresentation(
    planViewModel: PlanViewModel,
    priceTier: PriceTier?,
    offer: OnboardingOffer?,
    winbackOffer: WinbackOffer? = null,
): PlanPresentation {
    val storeYearly = planViewModel.storeYearlyPrice
    val storeMonthly = planViewModel.storeMonthlyPrice
    val winbackStore = planViewModel.winbackStore
    // Play's price for the win-back offer's first month, once Play lists the tagged offer
    val storeWinbackFirstMonth = winbackOffer?.playOfferTag?.takeIf { it.isNotEmpty() }?.let { planViewModel.storeMonthlyOfferPrices[it] }
    return remember(priceTier, offer, winbackOffer, storeYearly, storeMonthly, storeWinbackFirstMonth) {
        PlanPresentations.build(
            priceTier,
            offer,
            storeYearly,
            storeMonthly,
            winback = winbackOffer,
            winbackStore = winbackStore,
            storeWinbackFirstMonth = storeWinbackFirstMonth,
        )
    }
}
