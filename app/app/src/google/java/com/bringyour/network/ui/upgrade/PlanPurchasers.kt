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
 * year) and, for monthly, the win-back offer's tag while that offer is active
 * (`winback-monthly-50`: the first month at half price), and launches the
 * billing flow through PlanViewModel.upgrade. The
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
            // the tag of the offer the server named for this plan: the welcome
            // offer's for yearly, the win-back offer's for monthly (shown only
            // while Play lists it); without one no tagged offer is bought
            planViewModel.preferredOfferTag = when (plan) {
                PlanType.YEARLY -> presentation.offer?.playOfferTag
                PlanType.MONTHLY -> presentation.winback?.playOfferTag
            }?.takeIf { it.isNotEmpty() }
            planViewModel.setSelectedPlan(plan)
            val yearly = plan == PlanType.YEARLY
            val price = presentation.firstChargeAmount(plan)
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

/**
 * Play Billing runs in the Play Store and Play services, other apps to the tunnel,
 * which leaves out only this app and a short list that does not name them
 * (MainService). So a tunnel holding traffic stops a purchase, and the out-of-balance
 * alert says to disconnect first.
 */
internal const val PLAN_CHECKOUT_RIDES_TUNNEL = true
