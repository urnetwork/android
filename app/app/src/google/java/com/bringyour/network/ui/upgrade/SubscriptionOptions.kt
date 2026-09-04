package com.bringyour.network.ui.upgrade

import com.bringyour.network.ui.theme.TopBarTitleTextStyle
import com.bringyour.network.ui.shared.enums.PlanType
import com.bringyour.network.ui.components.PlanOptionContainer
import com.bringyour.network.ui.theme.ProGoldLight
import com.bringyour.network.ui.components.BestValuePill
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bringyour.network.R
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.shared.viewmodels.PlanViewModel
import com.bringyour.network.ui.theme.TextMuted

@Composable
fun SubscriptionOptions(
    planViewModel: PlanViewModel,
    /**
     * keep below params for different build flavors
     */
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

    SubscriptionOptions(
        upgrade = planViewModel.upgrade,
        upgradeInProgress = planViewModel.inProgress,
        monthlyCostFormatted = planViewModel.formattedMonthlySubscriptionPrice,
        yearlyCostFormatted = planViewModel.formattedYearlySubscriptionPrice,
        selectedPlan = planViewModel.selectedPlan,
        setSelectedPlan = planViewModel.setSelectedPlan,
        freeTrialDays = planViewModel.freeTrialDays
    )

}

/**
 * The Play plan picker: yearly ($40/year) is the highlighted default in the
 * Pro-gold dress with the Best value pill and the free trial; monthly is the
 * quiet alternative with no trial. Only the yearly plan has a trial. Both
 * plans render unconditionally, before, during and after the store query:
 * the store only refines the printed prices, and a plan it cannot sell
 * surfaces the store's error on purchase instead of disappearing.
 */
@Composable
fun SubscriptionOptions(
    upgrade: () -> Unit,
    upgradeInProgress: Boolean,
    monthlyCostFormatted: String,
    // Play's price once loaded, the shared fallback until then
    yearlyCostFormatted: String = FALLBACK_YEARLY_PRICE,
    selectedPlan: PlanType = PlanType.YEARLY,
    setSelectedPlan: (PlanType) -> Unit = {},
    // the yearly plan's free trial, in days
    freeTrialDays: Int = FREE_TRIAL_DAYS,
) {

    val yearlySelected = selectedPlan == PlanType.YEARLY
    val trialOffered = 0 < freeTrialDays

    Column {
        PlanOptionContainer(
            isSelected = yearlySelected,
            select = { setSelectedPlan(PlanType.YEARLY) },
            content = {
                Column {
                    Text(
                        stringResource(id = R.string.plan_price_per_year, yearlyCostFormatted),
                        style = TopBarTitleTextStyle
                    )
                    if (trialOffered) {
                        Text(
                            stringResource(id = R.string.includes_free_trial_days, freeTrialDays),
                            style = MaterialTheme.typography.bodyMedium,
                            color = ProGoldLight
                        )
                    }
                }
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
                Column {
                    Text(
                        stringResource(id = R.string.plan_price_per_month, monthlyCostFormatted),
                        style = TopBarTitleTextStyle
                    )
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth()) {

            URButton(
                onClick = {
                    upgrade()
                },
                enabled = !upgradeInProgress,
                isProcessing = upgradeInProgress
            ) { buttonTextStyle ->
                Text(
                    stringResource(id = if (yearlySelected && trialOffered) R.string.start_free_trial else R.string.subscribe),
                    style = buttonTextStyle
                )
            }

        }

    }

}
