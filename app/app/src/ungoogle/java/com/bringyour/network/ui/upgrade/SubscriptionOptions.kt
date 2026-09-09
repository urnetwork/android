package com.bringyour.network.ui.upgrade

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.bringyour.network.ui.shared.viewmodels.PlanViewModel
import com.bringyour.network.ui.shared.viewmodels.SubscriptionBalanceViewModel

/**
 * The plan picker on this flavor: the shared card picker over the server's
 * tier and welcome offer (sold by this flavor's purchaser) and the Solana Pay
 * alternative below it. The same signature as the Play flavor's picker, so
 * the onboarding plan step and the Get Pro screen are shared.
 */
@Composable
fun SubscriptionOptions(
    planViewModel: PlanViewModel,
    subscriptionBalanceViewModel: SubscriptionBalanceViewModel,
    // OfferSurface* -- where this picker is shown, for the events
    surface: String,
    createSolanaPaymentIntent: (
        reference: String,
        plan: String,
        onSuccess: (amountUsd: Double) -> Unit,
        onError: () -> Unit
    ) -> Unit,
    onSolanaUriOpened: (String) -> Unit,
    onStripePaymentSuccess: () -> Unit,
    isCheckingSolanaTransaction: Boolean
) {
    val priceTier by subscriptionBalanceViewModel.priceTier.collectAsState()
    val onboardingOffer by subscriptionBalanceViewModel.onboardingOffer.collectAsState()
    val presentation = rememberPlanPresentation(planViewModel, priceTier, onboardingOffer)
    val purchaser = rememberPlanPurchaser(planViewModel, subscriptionBalanceViewModel, onPurchaseSuccess = onStripePaymentSuccess)
    val solanaLauncher = rememberSolanaPayLauncher()

    NonPlayPlanSurface(
        presentation = presentation,
        subscriptionBalanceViewModel = subscriptionBalanceViewModel,
        surface = surface,
        purchaser = purchaser,
        solanaLauncher = solanaLauncher,
        upgradeInProgress = planViewModel.inProgress,
        createSolanaPaymentIntent = createSolanaPaymentIntent,
        onSolanaUriOpened = onSolanaUriOpened,
        isCheckingSolanaTransaction = isCheckingSolanaTransaction,
        freeTrialDays = planViewModel.freeTrialDays,
    )
}
