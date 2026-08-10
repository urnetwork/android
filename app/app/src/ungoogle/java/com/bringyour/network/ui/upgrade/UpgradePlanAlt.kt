package com.bringyour.network.ui.upgrade

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import com.bringyour.network.ui.upgrade.AltSubscriptionOptions
import com.bringyour.network.ui.components.UpgradeScreenHeader
import com.bringyour.network.ui.components.overlays.OverlayMode
import com.bringyour.network.ui.shared.viewmodels.OverlayViewModel
import com.bringyour.network.ui.shared.viewmodels.PlanViewModel
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.utils.SOLANA_PLAN_YEARLY
import com.bringyour.network.utils.buildSolanaPaymentUrl
import com.bringyour.network.utils.createPaymentReference
import com.bringyour.network.utils.isTablet
import com.bringyour.sdk.Id

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpgradePlanAlt(
    navController: NavHostController,
    planViewModel: PlanViewModel,
//    overlayViewModel: OverlayViewModel,
    setPendingSolanaSubscriptionReference: (String) -> Unit,
    createSolanaPaymentIntent: (
        reference: String,
        plan: String,
        onSuccess: (amountUsd: Double) -> Unit,
        onError: () -> Unit
    ) -> Unit,
//    pollSubscriptionBalance: () -> Unit,
    onStripePaymentSuccess: () -> Unit,
    isCheckingSolanaTransaction: Boolean
//    onUpgradeSuccess: () -> Unit
) {

    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current

    // networkId was init-only: stale after an account switch, null after a slow jwt
    // parse. Re-derive it whenever this screen appears; the Stripe buttons stay
    // disabled until it resolves.
    LaunchedEffect(Unit) {
        planViewModel.refreshNetworkId()
    }

//    LaunchedEffect(Unit) {
//        planViewModel.onUpgradeSuccess.collect {
////            overlayViewModel.launch(OverlayMode.Upgrade)
//        }
//    }

    val promptWalletTransaction: (reference: String, amountUsd: Double) -> Unit = { reference, amountUsd ->

        var uriOpened = false

        try {
            val url = buildSolanaPaymentUrl(reference, amountUsd, SOLANA_PLAN_YEARLY)
            Log.i("promptWalletTransaction", "url is: $url")
            uriHandler.openUri(url)
            uriOpened = true
        } catch (e: IllegalArgumentException) {
            // the sdk refused to build the url -- a bad amount, reference or address
            Toast.makeText(context, "Could not start the payment. Please try again.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(context, "No wallet app found to handle Solana payment.", Toast.LENGTH_LONG).show()
        }

        if (uriOpened) {
            setPendingSolanaSubscriptionReference(reference)
            navController.popBackStack()
        }

    }

    val upgradeWithSolana: () -> Unit = {

        val reference = createPaymentReference()
        Log.i("upgradeWithSolana", "createPaymentReference: $reference")

        createSolanaPaymentIntent(
            reference,
            SOLANA_PLAN_YEARLY,
            { amountUsd ->
                // on success -- amountUsd is what the SERVER quoted
                promptWalletTransaction(reference, amountUsd)
            },
            {
                // on error
                Toast.makeText(context, "Error creating payment reference", Toast.LENGTH_SHORT).show()
            }
        )

    }


    Scaffold(
        topBar = {
            TopAppBar(
                title = {Text("")},
                navigationIcon = {
                    IconButton(onClick = {
                        navController.popBackStack()
                    }) {
                        Icon(
                            Icons.Filled.ChevronLeft,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Black
                ),
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            UpgradePlanContent(
                upgradeSolana = upgradeWithSolana,
                upgradeInProgress = planViewModel.inProgress,
                formattedSubscriptionPrice = planViewModel.formattedSubscriptionPrice,
//                onStripePaymentSuccess = {
//                    pollSubscriptionBalance()
//                    overlayViewModel.launch(OverlayMode.Upgrade)
//                    navController.popBackStack()
//                },
                onStripePaymentSuccess = onStripePaymentSuccess,
                networkId = planViewModel.networkId,
                isCheckingSolanaTransaction = isCheckingSolanaTransaction
            )
        }
    }

}

@Composable
private fun UpgradePlanContent(
    upgradeInProgress: Boolean,
    upgradeSolana: () -> Unit,
    formattedSubscriptionPrice: String,
    onStripePaymentSuccess: () -> Unit,
    networkId: String?,
    isCheckingSolanaTransaction: Boolean
) {

    val uriHandler = LocalUriHandler.current

    var isPromptingSolanaPayment by remember { mutableStateOf(false) }

    val colModifier = Modifier

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
                upgradeSolana = upgradeSolana,
                isPromptingSolanaPayment = isPromptingSolanaPayment,
                setIsPromptingSolanaPayment = {
                    isPromptingSolanaPayment = it
                },
                upgradeStripeMonthly = {
                    if (networkId != null) {
                        uriHandler.openUri("https://pay.ur.io/b/3csaIs85tgIrh208wE?client_reference_id=${networkId}")
                    }
                },
                upgradeStripeYearly = {
                    if (networkId != null) {
                        uriHandler.openUri("https://pay.ur.io/b/28E3cvaUEbb3b9Og1u9ws09?client_reference_id=${networkId}")
                    }
                },
                stripeEnabled = networkId != null,
                isCheckingSolanaTransaction = isCheckingSolanaTransaction
            )
            Spacer(modifier = Modifier.height(16.dp))

        }
    }
}