package com.bringyour.network.ui.upgrade

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.bringyour.network.ui.shared.enums.PlanType
import com.bringyour.network.ui.shared.viewmodels.PlanViewModel
import com.bringyour.network.ui.shared.viewmodels.SubscriptionBalanceViewModel

/**
 * The Play plan picker: the shared [PlanPicker] over the server's tier and
 * offer, with Play's localized prices once the store has answered. Both plans
 * render unconditionally, before, during and after the store query: the store
 * only refines the printed prices, and a plan it cannot sell surfaces the
 * store's error on purchase instead of disappearing.
 */
@Composable
fun SubscriptionOptions(
    planViewModel: PlanViewModel,
    subscriptionBalanceViewModel: SubscriptionBalanceViewModel,
    // OfferSurface* -- where this picker is shown, for the events
    surface: String,
    /**
     * keep below params for different build flavors
     */
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

    // Play's billing country is the storefront: the server resolves the tier from it
    val billingCountry = planViewModel.billingCountry
    LaunchedEffect(billingCountry) {
        subscriptionBalanceViewModel.setStorefrontCountry(billingCountry)
    }

    PlanPicker(
        presentation = presentation,
        selectedPlan = planViewModel.selectedPlan,
        setSelectedPlan = planViewModel.setSelectedPlan,
        purchaser = purchaser,
        upgradeInProgress = planViewModel.inProgress,
        surface = surface,
        experiment = subscriptionBalanceViewModel.offerExperiment,
        freeTrialDays = planViewModel.freeTrialDays,
        countryName = countryDisplayName(billingCountry),
    )
}

/** "Nigeria" for "NG" in the device locale; null when unknown. */
fun countryDisplayName(iso: String?): String? =
    iso?.takeIf { it.length == 2 }?.let { java.util.Locale("", it).displayCountry.takeIf { name -> name.isNotEmpty() } }

/** The same picker on static inputs, for previews and tests. */
@Composable
fun SubscriptionOptions(
    presentation: PlanPresentation,
    selectedPlan: PlanType = PlanType.YEARLY,
    setSelectedPlan: (PlanType) -> Unit = {},
    upgradeInProgress: Boolean = false,
) {
    PlanPicker(
        presentation = presentation,
        selectedPlan = selectedPlan,
        setSelectedPlan = setSelectedPlan,
        purchaser = PlanPurchaser(store = "play") { _, _ -> },
        upgradeInProgress = upgradeInProgress,
        surface = "intro_step",
        experiment = Pair("", ""),
    )
}
