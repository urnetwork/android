package com.bringyour.network.ui.introduction

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bringyour.network.R
import com.bringyour.network.analytics.ClientEvents
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.components.tabletReadableColumn
import com.bringyour.network.ui.shared.enums.PlanType
import com.bringyour.network.ui.theme.ProGold
import com.bringyour.network.ui.theme.ProGoldLight
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.upgrade.FREE_TRIAL_DAYS
import com.bringyour.network.ui.upgrade.PlanCards
import com.bringyour.network.ui.upgrade.PlanPresentation
import com.bringyour.network.ui.upgrade.PlanTermsLine
import com.bringyour.network.ui.upgrade.formatOfferDeadline
import com.bringyour.sdk.Sdk

/**
 * The last onboarding page, reached by everyone -- Skip from any earlier page
 * lands here once (mmm/onboarding/PLAN.md "THE IN-APP OFFER SCREEN"): the
 * welcome offer restated as an honest soft paywall. Headline, the trial
 * timeline, the offer card with the billed amount as the prominent number, a
 * static "Available until" (never a countdown), one primary CTA, and an
 * always-visible "Continue with the free plan" that closes the flow exactly
 * as Skip does. No second offer when the user declines.
 *
 * The events: `offer.screen.shown` when it appears, `offer.cta.tapped` on the
 * button, `offer.declined` with the control that closed it.
 */
@Composable
fun IntroductionOffer(
    presentation: PlanPresentation,
    experiment: Pair<String, String>,
    store: String,
    upgradeInProgress: Boolean,
    startTrial: () -> Unit,
    dismiss: () -> Unit,
    onBack: (() -> Unit)? = null,
) {
    val shownAt = remember { System.currentTimeMillis() }
    val offer = presentation.offer

    LaunchedEffect(Unit) {
        ClientEvents.offerScreenShown(
            surface = Sdk.OfferSurfaceFinalScreen,
            experiment = experiment.first,
            variant = experiment.second,
            tier = presentation.tier,
            priceShown = offer?.firstYearAmount ?: presentation.yearlyAmount,
            currency = presentation.currency,
            expiresInS = offer?.let { ((it.expiresAtMillis - System.currentTimeMillis()) / 1000L).coerceAtLeast(0L) } ?: 0L,
        )
    }

    val decline: (String) -> Unit = { control ->
        ClientEvents.offerDeclined(control, System.currentTimeMillis() - shownAt)
        ClientEvents.onboardingStepCompleted(ClientEvents.STEP_OFFER, 5, System.currentTimeMillis() - shownAt)
        dismiss()
    }

    // the system back gesture leaves the flow like the free-plan link
    BackHandler { decline(Sdk.OfferDeclineControlSystemDismiss) }

    Scaffold(
        topBar = {
            // this page has no Skip: the free-plan link below is the way out
            IntroductionTopBar(step = INTRO_STEP_COUNT, onSkip = { decline(Sdk.OfferDeclineControlFreePlanLink) }, onBack = onBack)
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .tabletReadableColumn()
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                if (offer != null) {
                    Text(
                        stringResource(id = R.string.welcome_offer_eyebrow),
                        style = MaterialTheme.typography.bodyMedium,
                        color = ProGold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Text(
                    if (offer != null) {
                        stringResource(id = R.string.offer_months_free_headline, offer.monthsFree)
                    } else {
                        stringResource(id = R.string.get_pro)
                    },
                    style = MaterialTheme.typography.headlineLarge
                )
                if (offer != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        stringResource(id = R.string.offer_percent_off_first_year, offer.percentOff),
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextMuted
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                TrialTimeline(presentation = presentation)

                Spacer(modifier = Modifier.height(24.dp))

                PlanCards(
                    presentation = presentation,
                    selectedPlan = PlanType.YEARLY,
                    setSelectedPlan = {},
                    showMonthly = false,
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(24.dp))

                URButton(
                    onClick = {
                        ClientEvents.offerCtaTapped(Sdk.PlanYearly, store)
                        startTrial()
                    },
                    enabled = !upgradeInProgress,
                    isProcessing = upgradeInProgress
                ) { btnStyle ->
                    Text(
                        if (offer != null) {
                            stringResource(id = R.string.offer_cta_start_trial_months_free, offer.monthsFree)
                        } else {
                            stringResource(id = R.string.start_free_trial)
                        },
                        style = btnStyle
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                PlanTermsLine(presentation = presentation, selectedPlan = PlanType.YEARLY)

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = { decline(Sdk.OfferDeclineControlFreePlanLink) },
                    colors = ButtonDefaults.textButtonColors(contentColor = TextMuted)
                ) {
                    Text(
                        stringResource(id = R.string.continue_with_free_plan),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

/**
 * The Blinkist-style trial timeline: today the free trial starts, day 12 a
 * reminder before the charge, day 14 the first charge, cancel anytime before.
 */
@Composable
fun TrialTimeline(
    presentation: PlanPresentation,
    freeTrialDays: Int = FREE_TRIAL_DAYS,
) {
    val chargeAmount = presentation.offer?.firstYearPrice ?: presentation.yearlyPrice
    Column {
        TimelineRow(
            label = stringResource(id = R.string.offer_timeline_today),
            detail = stringResource(id = R.string.offer_timeline_trial_starts),
            highlight = true
        )
        Spacer(modifier = Modifier.height(10.dp))
        TimelineRow(
            label = stringResource(id = R.string.offer_timeline_day, freeTrialDays - 2),
            detail = stringResource(id = R.string.offer_timeline_reminder),
            highlight = false
        )
        Spacer(modifier = Modifier.height(10.dp))
        TimelineRow(
            label = stringResource(id = R.string.offer_timeline_day, freeTrialDays),
            detail = stringResource(id = R.string.offer_timeline_first_charge, chargeAmount),
            highlight = false
        )
    }
}

@Composable
private fun TimelineRow(label: String, detail: String, highlight: Boolean) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (highlight) ProGoldLight else TextMuted,
            modifier = Modifier.width(72.dp)
        )
        Text(
            detail,
            style = MaterialTheme.typography.bodyMedium,
            color = if (highlight) ProGoldLight else TextMuted
        )
    }
}

/** The static deadline line shown with the offer card on the intro plan step. */
@Composable
fun OfferDeadlineLine(expiresAtMillis: Long) {
    if (0L < expiresAtMillis) {
        Text(
            stringResource(id = R.string.offer_available_until, formatOfferDeadline(expiresAtMillis)),
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted
        )
    }
}
