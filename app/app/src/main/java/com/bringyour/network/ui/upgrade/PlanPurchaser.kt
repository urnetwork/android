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
