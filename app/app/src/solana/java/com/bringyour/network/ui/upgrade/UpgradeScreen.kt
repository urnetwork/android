package com.bringyour.network.ui.upgrade

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import com.bringyour.network.ui.shared.viewmodels.OverlayViewModel
import com.bringyour.network.ui.shared.viewmodels.PlanViewModel

@Composable
fun UpgradeScreen(
    navController: NavHostController,
    planViewModel: PlanViewModel,
    overlayViewModel: OverlayViewModel,
    setPendingSolanaSubscriptionReference: (String) -> Unit,
    createSolanaPaymentIntent: (
        reference: String,
        onSuccess: () -> Unit,
        onError: () -> Unit
    ) -> Unit
) {

    /**
     * Stripe + Solana upgrade options
     */
    UpgradePlanAlt(
        navController,
        planViewModel,
        overlayViewModel,
        setPendingSolanaSubscriptionReference,
        createSolanaPaymentIntent
    )

}
