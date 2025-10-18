package com.bringyour.network.ui.upgrade

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import com.bringyour.network.ui.shared.viewmodels.PlanViewModel

@Composable
fun UpgradeScreen(
    navController: NavHostController,
    planViewModel: PlanViewModel,
    setPendingSolanaSubscriptionReference: (String) -> Unit,
    createSolanaPaymentIntent: (
        reference: String,
        onSuccess: () -> Unit,
        onError: () -> Unit
    ) -> Unit,
    onStripePaymentSuccess: () -> Unit,
    isCheckingSolanaTransaction: Boolean
) {

    /**
     * Stripe + Solana upgrade options
     */
    UpgradePlanAlt(
        navController,
        planViewModel,
        setPendingSolanaSubscriptionReference,
        createSolanaPaymentIntent,
        onStripePaymentSuccess = onStripePaymentSuccess,
        isCheckingSolanaTransaction = isCheckingSolanaTransaction
    )

}
