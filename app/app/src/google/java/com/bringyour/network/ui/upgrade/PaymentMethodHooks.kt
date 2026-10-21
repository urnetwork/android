package com.bringyour.network.ui.upgrade

import androidx.compose.runtime.Composable
import com.bringyour.network.ui.settings.ManageSubscriptionButton
import com.bringyour.network.ui.shared.enums.PlanType
import com.bringyour.network.ui.shared.viewmodels.PlanViewModel
import com.bringyour.network.ui.shared.viewmodels.SubscriptionBalanceViewModel

/*
 * The payment method hooks on the play flavor, which sells through Play
 * Billing only (mmm/ZCASH-CONTRACT.md §6.3, "Play is unchanged"): no method
 * row, no crypto invoice, no crypto copy. Shared code calls these by name; the
 * other flavors define them in src/cryptoPay/java. Nothing here reaches the
 * crypto payment code, which this flavor does not compile.
 */

/** No crypto invoice to provide: the content as it is. */
@Composable
fun PaymentMethodsScope(content: @Composable () -> Unit) {
    content()
}

/** No crypto invoice to host. */
@Composable
fun CryptoInvoiceHost(
    subscriptionBalanceViewModel: SubscriptionBalanceViewModel,
    planViewModel: PlanViewModel,
    onPaid: () -> Unit,
) {
}

/** No payment method row: the plan surfaces render as they always have. */
@Composable
fun rememberPlanMethodSlot(
    presentation: PlanPresentation,
    subscriptionBalanceViewModel: SubscriptionBalanceViewModel,
    selectedPlan: PlanType,
    setSelectedPlan: (PlanType) -> Unit,
): PlanMethodSlot? = null

/** No crypto lines in the account plan area. */
@Composable
fun AccountPaymentNotices(
    subscriptionBalanceViewModel: SubscriptionBalanceViewModel,
    openUpgrade: () -> Unit,
) {
}

/** The Settings plan row's subscription line: this flavor's manage button, as before. */
@Composable
fun SubscriptionManageLine(currentStore: String?, stripePortalUrl: String?) {
    ManageSubscriptionButton(stripePortalUrl)
}
