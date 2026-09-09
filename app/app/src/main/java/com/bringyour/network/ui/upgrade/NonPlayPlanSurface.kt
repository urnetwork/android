package com.bringyour.network.ui.upgrade

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.bringyour.network.R
import com.bringyour.network.analytics.ClientEvents
import com.bringyour.network.ui.shared.enums.PlanType
import com.bringyour.network.ui.shared.viewmodels.SubscriptionBalanceViewModel
import com.bringyour.network.utils.createPaymentReference
import com.bringyour.sdk.Sdk

/**
 * The plan surface on the flavors without Play Billing: the shared card picker
 * (sold by the flavor's [PlanPurchaser]) and the Solana Pay alternative below
 * it. The Solana quote comes from the server (`yearly_onboarding` at the
 * offer's 75% while the offer is active, else `yearly`); the wallet is opened
 * by the flavor's [SolanaPayLauncher] and the balance is polled when the app
 * comes back.
 */
@Composable
fun NonPlayPlanSurface(
    presentation: PlanPresentation,
    subscriptionBalanceViewModel: SubscriptionBalanceViewModel,
    surface: String,
    purchaser: PlanPurchaser,
    solanaLauncher: SolanaPayLauncher,
    upgradeInProgress: Boolean,
    createSolanaPaymentIntent: (
        reference: String,
        plan: String,
        onSuccess: (amountUsd: Double) -> Unit,
        onError: () -> Unit
    ) -> Unit,
    onSolanaUriOpened: (String) -> Unit,
    isCheckingSolanaTransaction: Boolean,
    freeTrialDays: Int = FREE_TRIAL_DAYS,
) {
    val context = LocalContext.current
    var selectedPlan by rememberSaveable { mutableStateOf(PlanType.YEARLY) }
    var isPromptingSolanaPayment by remember { mutableStateOf(false) }

    val payWithSolana: () -> Unit = {
        isPromptingSolanaPayment = true
        val reference = createPaymentReference()
        val plan = if (presentation.offer != null) Sdk.PlanYearlyOnboarding else Sdk.PlanYearly
        createSolanaPaymentIntent(
            reference,
            plan,
            { amountUsd ->
                // amountUsd is what the SERVER quoted: the webhook checks the payment against it
                ClientEvents.purchaseStarted(Sdk.EventStoreSolana, ClientEvents.PRODUCT_SOLANA_PRO_YEARLY, plan, false, amountUsd, "USD")
                if (presentation.offer != null) {
                    ClientEvents.offerCtaTapped(plan, Sdk.EventStoreSolana)
                }
                val opened = solanaLauncher.open(reference, amountUsd, plan)
                isPromptingSolanaPayment = false
                if (opened) {
                    subscriptionBalanceViewModel.expectSolanaPurchase(plan, amountUsd)
                    onSolanaUriOpened(reference)
                } else {
                    ClientEvents.purchaseFailed(Sdk.EventStoreSolana, ClientEvents.PRODUCT_SOLANA_PRO_YEARLY, plan, false, amountUsd, "USD", "no_wallet")
                }
            },
            {
                isPromptingSolanaPayment = false
                Toast.makeText(context, context.getString(R.string.payment_not_completed), Toast.LENGTH_SHORT).show()
            }
        )
    }

    Column {
        PlanPicker(
            presentation = presentation,
            selectedPlan = selectedPlan,
            setSelectedPlan = { selectedPlan = it },
            purchaser = purchaser,
            upgradeInProgress = upgradeInProgress,
            surface = surface,
            experiment = subscriptionBalanceViewModel.offerExperiment,
            freeTrialDays = freeTrialDays,
        )
        SolanaPaySection(
            presentation = presentation,
            onPay = payWithSolana,
            inProgress = isPromptingSolanaPayment || isCheckingSolanaTransaction,
            enabled = !upgradeInProgress,
        )
    }
}

/** How a flavor opens the wallet for a Solana Pay url: a deep link, or a sheet with a QR code and the link. */
fun interface SolanaPayLauncher {
    /** Returns true when the wallet (or the sheet) was opened. */
    fun open(reference: String, amountUsd: Double, plan: String): Boolean
}
