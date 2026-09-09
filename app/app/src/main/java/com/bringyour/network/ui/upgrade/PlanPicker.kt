package com.bringyour.network.ui.upgrade

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bringyour.network.R
import com.bringyour.network.analytics.ClientEvents
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.shared.enums.PlanType
import com.bringyour.sdk.Sdk

/**
 * The plan picker every plan surface renders: the two [PlanCards], the one
 * primary button, and the terms line under it. When the presentation carries
 * an active welcome offer the yearly card is the offer card and the button
 * reads "Start free trial with 3 months free"; the offer events are emitted
 * here (`offer.screen.shown` once per appearance with the surface,
 * `offer.card.tapped`, `offer.cta.tapped`).
 */
@Composable
fun PlanPicker(
    presentation: PlanPresentation,
    selectedPlan: PlanType,
    setSelectedPlan: (PlanType) -> Unit,
    purchaser: PlanPurchaser,
    upgradeInProgress: Boolean,
    // OfferSurface* -- where this picker is shown, for the events
    surface: String,
    experiment: Pair<String, String>,
    freeTrialDays: Int = FREE_TRIAL_DAYS,
    countryName: String? = null,
) {
    val offer = presentation.offer
    val yearlySelected = selectedPlan == PlanType.YEARLY
    val trialOffered = 0 < freeTrialDays

    LaunchedEffect(offer?.expiresAtMillis, presentation.tier, surface) {
        if (offer != null) {
            ClientEvents.offerScreenShown(
                surface = surface,
                experiment = experiment.first,
                variant = experiment.second,
                tier = presentation.tier,
                priceShown = offer.firstYearAmount,
                currency = presentation.currency,
                expiresInS = ((offer.expiresAtMillis - System.currentTimeMillis()) / 1000L).coerceAtLeast(0L),
            )
        }
    }

    Column {
        PlanCards(
            presentation = presentation,
            selectedPlan = selectedPlan,
            setSelectedPlan = { plan ->
                if (offer != null && plan != selectedPlan) {
                    ClientEvents.offerCardTapped(if (plan == PlanType.YEARLY) Sdk.PlanYearly else Sdk.PlanMonthly)
                }
                setSelectedPlan(plan)
            },
            freeTrialDays = freeTrialDays,
            countryName = countryName,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            URButton(
                onClick = {
                    val plan = if (yearlySelected) Sdk.PlanYearly else Sdk.PlanMonthly
                    if (offer != null) {
                        ClientEvents.offerCtaTapped(plan, purchaser.store)
                    }
                    purchaser.purchase(selectedPlan, presentation)
                },
                enabled = !upgradeInProgress,
                isProcessing = upgradeInProgress
            ) { buttonTextStyle ->
                Text(
                    when {
                        yearlySelected && offer != null && trialOffered ->
                            stringResource(id = R.string.offer_cta_start_trial_months_free, offer.monthsFree)
                        yearlySelected && trialOffered ->
                            stringResource(id = R.string.start_free_trial)
                        else ->
                            stringResource(id = R.string.subscribe)
                    },
                    style = buttonTextStyle
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        PlanTermsLine(presentation = presentation, selectedPlan = selectedPlan, freeTrialDays = freeTrialDays)
    }
}
