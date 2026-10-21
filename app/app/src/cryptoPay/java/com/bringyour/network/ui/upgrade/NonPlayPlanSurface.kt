package com.bringyour.network.ui.upgrade

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.bringyour.network.ui.shared.enums.PlanType
import com.bringyour.network.ui.shared.viewmodels.SubscriptionBalanceViewModel

/**
 * The plan surface on the flavors without Play Billing (Get Pro and the
 * onboarding plan step): the shared card picker, sold by the flavor's
 * [PlanPurchaser], with the payment method row directly above its button --
 * Card · USDC · Zcash · Monero, the coins the server enables (crypto-pay
 * mmm/ZCASH-CONTRACT.md §6.1). A coin's button opens the crypto invoice
 * (CryptoInvoiceHost), above this screen, for the yearly plan or the welcome
 * offer while it is active; the server quotes it. With no coin enabled the
 * row is not there and this is the card picker alone.
 */
@Composable
fun NonPlayPlanSurface(
    presentation: PlanPresentation,
    subscriptionBalanceViewModel: SubscriptionBalanceViewModel,
    surface: String,
    purchaser: PlanPurchaser,
    upgradeInProgress: Boolean,
    freeTrialDays: Int = FREE_TRIAL_DAYS,
) {
    var selectedPlan by rememberSaveable { mutableStateOf(PlanType.YEARLY) }

    PlanPicker(
        presentation = presentation,
        selectedPlan = selectedPlan,
        setSelectedPlan = { selectedPlan = it },
        purchaser = purchaser,
        upgradeInProgress = upgradeInProgress,
        surface = surface,
        experiment = subscriptionBalanceViewModel.offerExperiment,
        freeTrialDays = freeTrialDays,
        methodSlot = rememberPlanMethodSlot(
            presentation = presentation,
            subscriptionBalanceViewModel = subscriptionBalanceViewModel,
            selectedPlan = selectedPlan,
            setSelectedPlan = { selectedPlan = it },
        ),
    )
}
