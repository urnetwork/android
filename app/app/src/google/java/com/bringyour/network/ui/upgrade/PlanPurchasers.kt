package com.bringyour.network.ui.upgrade

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.bringyour.network.analytics.ClientEvents
import com.bringyour.network.ui.shared.enums.PlanType
import com.bringyour.network.ui.shared.viewmodels.PlanViewModel
import com.bringyour.network.ui.shared.viewmodels.SubscriptionBalanceViewModel
import com.bringyour.sdk.Sdk

/**
 * The play flavor sells through Google Play Billing: the purchase selects the
 * plan, prefers the welcome offer's tag while the offer is active (the
 * developer-determined `onboarding25` offer: trial, then the discounted first
 * year), and launches the billing flow through PlanViewModel.upgrade. The
 * success is observed where it always was (upgradeSuccessSequence in
 * MainNavHost), so [onPurchaseSuccess] is unused here.
 */
@Composable
fun rememberPlanPurchaser(
    planViewModel: PlanViewModel,
    subscriptionBalanceViewModel: SubscriptionBalanceViewModel,
    onPurchaseSuccess: () -> Unit,
): PlanPurchaser {
    return remember(planViewModel) {
        PlanPurchaser(store = Sdk.EventStorePlay) { plan, presentation ->
            val offer = presentation.offer
            planViewModel.preferredOfferTag = if (plan == PlanType.YEARLY) offer?.playOfferTag?.takeIf { it.isNotEmpty() } else null
            planViewModel.setSelectedPlan(plan)
            val yearly = plan == PlanType.YEARLY
            val price = if (yearly) (offer?.firstYearAmount ?: presentation.yearlyAmount) else presentation.monthlyAmount
            planViewModel.pendingPurchaseEvent = PlanViewModel.PendingPurchaseEvent(
                plan = if (yearly) Sdk.PlanYearly else Sdk.PlanMonthly,
                trial = yearly,
                price = price,
                currency = presentation.currency,
            )
            ClientEvents.purchaseStarted(
                Sdk.EventStorePlay,
                ClientEvents.PRODUCT_PLAY_SUPPORTER,
                if (yearly) Sdk.PlanYearly else Sdk.PlanMonthly,
                yearly,
                price,
                presentation.currency,
            )
            planViewModel.upgrade()
        }
    }
}
