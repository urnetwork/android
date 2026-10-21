package com.bringyour.network.ui.upgrade

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.bringyour.network.ui.shared.viewmodels.PlanViewModel
import com.bringyour.network.ui.shared.viewmodels.SubscriptionBalanceViewModel

/**
 * The plan picker on this flavor: the shared card picker over the server's
 * tier and welcome offer (sold by this flavor's purchaser), with the payment
 * method row above its button for the coins the server enables
 * (NonPlayPlanSurface). The same signature as the Play flavor's picker, so
 * the onboarding plan step and the Get Pro screen are shared.
 */
@Composable
fun SubscriptionOptions(
    planViewModel: PlanViewModel,
    subscriptionBalanceViewModel: SubscriptionBalanceViewModel,
    // OfferSurface* -- where this picker is shown, for the events
    surface: String,
    onStripePaymentSuccess: () -> Unit,
) {
    val priceTier by subscriptionBalanceViewModel.priceTier.collectAsState()
    val onboardingOffer by subscriptionBalanceViewModel.onboardingOffer.collectAsState()
    val presentation = rememberPlanPresentation(planViewModel, priceTier, onboardingOffer)
    val purchaser = rememberPlanPurchaser(planViewModel, subscriptionBalanceViewModel, onPurchaseSuccess = onStripePaymentSuccess)

    NonPlayPlanSurface(
        presentation = presentation,
        subscriptionBalanceViewModel = subscriptionBalanceViewModel,
        surface = surface,
        purchaser = purchaser,
        upgradeInProgress = planViewModel.inProgress,
        freeTrialDays = planViewModel.freeTrialDays,
    )
}
