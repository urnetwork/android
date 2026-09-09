package com.bringyour.network.ui.upgrade

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.bringyour.network.R
import com.bringyour.network.ui.components.BestValuePill
import com.bringyour.network.ui.components.PlanOptionContainer
import com.bringyour.network.ui.shared.enums.PlanType
import com.bringyour.network.ui.theme.ProGoldLight
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.TopBarTitleTextStyle
import java.text.DateFormat
import java.util.Date

/**
 * The two plan cards every plan surface renders (mmm/onboarding/PLAN.md "PRICE
 * PRESENTATION" and "Plan screens alignment"): yearly on top in the Pro-gold
 * dress with the Best value pill, monthly below as the quiet alternative.
 *
 * Yearly prints the headline "$39.99/year", the per-month equivalent as a
 * sub-line only when the SDK says so (never on the regional tier), the saving
 * against monthly, and the trial line. While the welcome offer is active the
 * yearly card becomes the offer card: "$29.99 for your first year", "then
 * $39.99/year", the trial line, and the static deadline. Monthly prints
 * "$4.99/month" and "Billed monthly · cancel anytime". The two cards are
 * always the same height.
 */
@Composable
fun PlanCards(
    presentation: PlanPresentation,
    selectedPlan: PlanType,
    setSelectedPlan: (PlanType) -> Unit,
    freeTrialDays: Int = FREE_TRIAL_DAYS,
    // the regional tier names the country its price is for
    countryName: String? = null,
) {
    val yearlySelected = selectedPlan == PlanType.YEARLY
    val trialOffered = 0 < freeTrialDays
    val offer = presentation.offer

    Column {
        PlanOptionContainer(
            isSelected = yearlySelected,
            select = { setSelectedPlan(PlanType.YEARLY) },
            content = {
                YearlyCardLines(presentation, offer, trialOffered, freeTrialDays, countryName)
            },
            badge = {
                BestValuePill()
            },
            glow = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        PlanOptionContainer(
            isSelected = !yearlySelected,
            select = { setSelectedPlan(PlanType.MONTHLY) },
            content = {
                // size the monthly card like the yearly card (an invisible copy of
                // its lines, not read aloud) so both are equal height at any font
                // scale, and center the visible lines in that space
                Box(contentAlignment = Alignment.CenterStart) {
                    Column(
                        modifier = Modifier
                            .alpha(0f)
                            .clearAndSetSemantics {}
                    ) {
                        YearlyCardLines(presentation, offer, trialOffered, freeTrialDays, countryName)
                    }
                    Column {
                        Text(
                            stringResource(id = R.string.plan_price_per_month, presentation.monthlyPrice),
                            style = TopBarTitleTextStyle
                        )
                        Text(
                            stringResource(id = R.string.plan_billed_monthly_cancel_anytime),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted
                        )
                    }
                }
            }
        )
    }
}

@Composable
private fun YearlyCardLines(
    presentation: PlanPresentation,
    offer: OfferPresentation?,
    trialOffered: Boolean,
    freeTrialDays: Int,
    countryName: String?,
) {
    Column {
        if (offer != null) {
            Text(
                stringResource(id = R.string.offer_first_year_price, offer.firstYearPrice),
                style = TopBarTitleTextStyle
            )
            Text(
                stringResource(id = R.string.offer_then_regular_price, offer.regularYearPrice),
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )
        } else {
            Text(
                stringResource(id = R.string.plan_price_per_year, presentation.yearlyPrice),
                style = TopBarTitleTextStyle
            )
            val equivalent = presentation.monthlyEquivalent
            if (equivalent != null) {
                Text(
                    stringResource(id = R.string.plan_monthly_equivalent_line, equivalent),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
            } else if (presentation.isRegional && countryName != null) {
                Text(
                    stringResource(id = R.string.plan_billed_yearly_price_for_country, countryName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
            }
        }
        if (trialOffered) {
            Text(
                stringResource(id = R.string.includes_free_trial_days, freeTrialDays),
                style = MaterialTheme.typography.bodyMedium,
                color = ProGoldLight
            )
        }
        if (offer != null && 0L < offer.expiresAtMillis) {
            Text(
                stringResource(id = R.string.offer_available_until, formatOfferDeadline(offer.expiresAtMillis)),
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )
        } else if (offer == null && 0 < presentation.savingPercent) {
            Text(
                stringResource(id = R.string.plan_save_percent, presentation.savingPercent),
                style = MaterialTheme.typography.bodyMedium,
                color = ProGoldLight
            )
        }
    }
}

/** "Sep 14, 2026, 9:41 AM" in the device's locale and time zone: a static deadline, never a countdown. */
fun formatOfferDeadline(expiresAtMillis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(expiresAtMillis))

/**
 * The terms line under the primary button: the trial, the first charge and
 * the cancel rule, so the price is never hidden (App Store 3.1.2(c) and the
 * Play equivalent).
 */
@Composable
fun PlanTermsLine(
    presentation: PlanPresentation,
    selectedPlan: PlanType,
    freeTrialDays: Int = FREE_TRIAL_DAYS,
) {
    val offer = presentation.offer
    val text = when {
        selectedPlan == PlanType.MONTHLY ->
            stringResource(id = R.string.plan_billed_monthly_cancel_anytime)
        offer != null ->
            stringResource(id = R.string.offer_terms_first_year, freeTrialDays, offer.firstYearPrice, offer.regularYearPrice)
        else ->
            stringResource(id = R.string.plan_terms_yearly, freeTrialDays, presentation.yearlyPrice)
    }
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = TextMuted,
        modifier = Modifier.fillMaxWidth()
    )
}
