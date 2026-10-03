package com.bringyour.network.ui.upgrade

import com.bringyour.network.ui.shared.enums.PlanType

/**
 * How this flavor sells a plan: Play Billing on the play flavor, the inline
 * Stripe payment sheet on the other Google-services flavors, the ur.io pay
 * page in a web view on the F-Droid build. Each flavor's `rememberPlanPurchaser`
 * builds one; the shared screens only call [purchase].
 *
 * `store` names the store on the purchase events (EventStore*).
 */
class PlanPurchaser(
    val store: String,
    val purchase: (plan: PlanType, presentation: PlanPresentation) -> Unit,
)

/** What a purchase tap does on the flavors that prepare the purchase on the server. */
enum class PurchaseStart {
    Start,

    /** A purchase is already in flight: the tap is a no-op. */
    InProgress,

    /**
     * No api for the active network space (not logged in yet, or the space is
     * still loading): the purchase could not be prepared or credited, so the user
     * is told instead of the tap silently doing nothing (UPGRADE.md N5).
     */
    AccountNotReady,
}

fun purchaseStartFor(apiAvailable: Boolean, inProgress: Boolean): PurchaseStart {
    if (inProgress) {
        return PurchaseStart.InProgress
    }
    if (!apiAvailable) {
        return PurchaseStart.AccountNotReady
    }
    return PurchaseStart.Start
}
