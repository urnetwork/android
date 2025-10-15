package com.bringyour.network.ui.upgrade

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import com.bringyour.network.ui.components.overlays.OverlayMode
import com.bringyour.network.ui.shared.viewmodels.PlanViewModel
import com.bringyour.network.utils.buildSolanaPaymentUrl
import com.bringyour.network.utils.createPaymentReference

@Composable
fun SubscriptionOptions(
    planViewModel: PlanViewModel,
//    pollSubscriptionBalance: () -> Unit,
    createSolanaPaymentIntent: (
        reference: String,
        onSuccess: () -> Unit,
        onError: () -> Unit
    ) -> Unit,
//    setPendingSolanaSubscriptionReference: (String) -> Unit,
    onSolanaUriOpened: (String) -> Unit,
    onStripePaymentSuccess: () -> Unit,
    isCheckingSolanaTransaction: Boolean
) {

    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current

    val promptWalletTransaction: (reference: String) -> Unit = { reference ->

        val url = buildSolanaPaymentUrl(reference)

        uriHandler.openUri(url)
        var uriOpened = false

        try {
            uriHandler.openUri(url)
            uriOpened = true
        } catch (e: Exception) {
            Toast.makeText(context, "No wallet app found to handle Solana payment.", Toast.LENGTH_LONG).show()
        }

        if (uriOpened) {
//            setPendingSolanaSubscriptionReference(reference)
//            navController.popBackStack()
            onSolanaUriOpened(reference)
        }

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

    SubscriptionOptions(
        networkId = planViewModel.networkId,
        upgradeSolana = upgradeWithSolana,
        onStripePaymentSuccess = onStripePaymentSuccess,
        isCheckingSolanaTransaction = isCheckingSolanaTransaction
//        onStripePaymentSuccess = {
//            pollSubscriptionBalance()
//            overlayViewModel.launch(OverlayMode.Upgrade)
//            navController.popBackStack()
//        },
    )


}

@Composable
fun SubscriptionOptions(
    networkId: String?,
    upgradeSolana: () -> Unit,
    onStripePaymentSuccess: () -> Unit,
    isCheckingSolanaTransaction: Boolean
) {

    val uriHandler = LocalUriHandler.current
    var isPromptingSolanaPayment by remember { mutableStateOf(false) }

    LaunchedEffect(isCheckingSolanaTransaction) {
        if (isPromptingSolanaPayment) {
            isPromptingSolanaPayment = false
        }
    }

    AltSubscriptionOptions(
        upgradeSolana = upgradeSolana,
        isPromptingSolanaPayment = isPromptingSolanaPayment,
        setIsPromptingSolanaPayment = {
            isPromptingSolanaPayment = it
        },
        onStripePaymentSuccess = onStripePaymentSuccess,
        upgradeStripeMonthly = {
            uriHandler.openUri("https://pay.ur.io/b/3csaIs85tgIrh208wE?client_reference_id=${networkId}")
        },
        upgradeStripeYearly = {
            uriHandler.openUri("https://pay.ur.io/b/28E3cvaUEbb3b9Og1u9ws09?client_reference_id=${networkId}")
        },
        isCheckingSolanaTransaction = isCheckingSolanaTransaction
    )
}