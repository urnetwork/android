package com.bringyour.network.ui.upgrade

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.bringyour.network.ui.components.AltSubscriptionOptions
import com.bringyour.network.ui.components.UpgradeScreenHeader
import com.bringyour.network.ui.components.overlays.OverlayMode
import com.bringyour.network.ui.shared.viewmodels.OverlayViewModel
import com.bringyour.network.ui.shared.viewmodels.PlanViewModel
import com.bringyour.network.utils.buildSolanaPaymentUrl
import com.bringyour.network.utils.createPaymentReference
import com.bringyour.network.utils.isTablet

@Composable
fun UpgradePlanAlt(
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

    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        planViewModel.onUpgradeSuccess.collect {
            overlayViewModel.launch(OverlayMode.Upgrade)
        }
    }

    val promptWalletTransaction: (reference: String) -> Unit = { reference ->

        val url = buildSolanaPaymentUrl(reference)

        uriHandler.openUri(url)

        setPendingSolanaSubscriptionReference(reference)

        navController.popBackStack()

    }

    val upgradeWithSolana: () -> Unit = {

        val reference = createPaymentReference()

        createSolanaPaymentIntent(
            reference,
            {
                // on success
                promptWalletTransaction(reference)
            },
            {
                // on error
                Toast.makeText(context, "Error creating payment reference", Toast.LENGTH_SHORT).show()
            }
        )

    }


    Scaffold { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            UpgradePlanContent(
                upgradeStripeMonthly = {
                    uriHandler.openUri("https://pay.ur.io/b/3csaIs85tgIrh208wE?client_reference_id=${planViewModel.networkId}")
                },
                upgradeStripeYearly = {
                    uriHandler.openUri("https://pay.ur.io/b/28E3cvaUEbb3b9Og1u9ws09?client_reference_id=${planViewModel.networkId}")
                },
                upgradeSolana = upgradeWithSolana,
                upgradeInProgress = planViewModel.inProgress,
                formattedSubscriptionPrice = planViewModel.formattedSubscriptionPrice
            )
        }
    }

}

@Composable
private fun UpgradePlanContent(
    upgradeInProgress: Boolean,
    upgradeStripeMonthly: () -> Unit,
    upgradeStripeYearly: () -> Unit,
    upgradeSolana: () -> Unit,
    formattedSubscriptionPrice: String,
) {

    var isPromptingSolanaPayment by remember { mutableStateOf(false) }

    val colModifier = Modifier

    if (!isTablet()) {
        colModifier.fillMaxSize()
    }

    Column(
        modifier = colModifier
            .padding(horizontal = 16.dp)
            .fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {

        UpgradeScreenHeader()

        Spacer(modifier = Modifier.height(64.dp))

        Column {

            AltSubscriptionOptions(
                upgradeStripeMonthly = upgradeStripeMonthly,
                upgradeStripeYearly = upgradeStripeYearly,
                upgradeInProgress = upgradeInProgress,
                upgradeSolana = upgradeSolana,
                isPromptingSolanaPayment = isPromptingSolanaPayment,
                setIsPromptingSolanaPayment = {
                    isPromptingSolanaPayment = it
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

        }
    }
}