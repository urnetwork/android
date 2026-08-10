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
import com.bringyour.network.utils.SOLANA_PLAN_YEARLY
import com.bringyour.network.utils.buildSolanaPaymentUrl
import com.bringyour.network.utils.createPaymentReference

@Composable
fun SubscriptionOptions(
    planViewModel: PlanViewModel,
    createSolanaPaymentIntent: (
        reference: String,
        plan: String,
        onSuccess: (amountUsd: Double) -> Unit,
        onError: () -> Unit
    ) -> Unit,
    onSolanaUriOpened: (String) -> Unit,
    onStripePaymentSuccess: () -> Unit,
    isCheckingSolanaTransaction: Boolean
) {

    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current

    // networkId was init-only: stale after an account switch, null after a slow jwt
    // parse. Re-derive it whenever this upgrade UI appears; the Stripe buttons stay
    // disabled until it resolves.
    LaunchedEffect(Unit) {
        planViewModel.refreshNetworkId()
    }

    val promptWalletTransaction: (reference: String, amountUsd: Double) -> Unit = { reference, amountUsd ->

        var uriOpened = false

        try {
            val url = buildSolanaPaymentUrl(reference, amountUsd, SOLANA_PLAN_YEARLY)
            uriHandler.openUri(url)
            uriOpened = true
        } catch (e: IllegalArgumentException) {
            // the sdk refused to build the url -- a bad amount, reference or address
            Toast.makeText(context, "Could not start the payment. Please try again.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(context, "No wallet app found to handle Solana payment.", Toast.LENGTH_LONG).show()
        }

        if (uriOpened) {
            onSolanaUriOpened(reference)
        }

    }

    val upgradeWithSolana: () -> Unit = {

        val reference = createPaymentReference()

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

    SubscriptionOptions(
        networkId = planViewModel.networkId,
        upgradeSolana = upgradeWithSolana,
        onStripePaymentSuccess = onStripePaymentSuccess,
        isCheckingSolanaTransaction = isCheckingSolanaTransaction
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
}