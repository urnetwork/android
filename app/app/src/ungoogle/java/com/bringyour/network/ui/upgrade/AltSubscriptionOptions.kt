package com.bringyour.network.ui.upgrade

import com.bringyour.network.ui.components.BestValuePill
import com.bringyour.network.ui.theme.ProGoldLight
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bringyour.network.R
import com.bringyour.network.ui.components.PlanOptionContainer
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.shared.enums.PlanType
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.OffBlack
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.TopBarTitleTextStyle
import com.bringyour.network.ui.theme.Yellow

@Composable
fun AltSubscriptionOptions(
    upgradeStripeMonthly: () -> Unit,
    upgradeStripeYearly: () -> Unit,
    // the prices the picker prints, from PlanViewModel (they match the Stripe payment links)
    monthlyCostFormatted: String,
    yearlyCostFormatted: String,
    // false until networkId is known. The payment links attach
    // `client_reference_id=networkId`; tapping before it resolves used to silently
    // do nothing -- keep the button disabled instead
    stripeEnabled: Boolean,
    upgradeSolana: () -> Unit,
    isPromptingSolanaPayment: Boolean,
    setIsPromptingSolanaPayment: (Boolean) -> Unit,
    isCheckingSolanaTransaction: Boolean
) {

    val (selectedPlan, setSelectedPlan) = remember { mutableStateOf(PlanType.YEARLY) }

    Column() {

        /**
         * Yearly
         */
        PlanOptionContainer(
            isSelected = selectedPlan == PlanType.YEARLY,
            select = {
                setSelectedPlan(PlanType.YEARLY)
            },
            content = {
                Column() {
                    Text(
                        stringResource(id = R.string.plan_price_per_year, yearlyCostFormatted),
                        style = TopBarTitleTextStyle
                    )
                    Text(
                        stringResource(id = R.string.includes_free_trial_days, FREE_TRIAL_DAYS),
                        style = MaterialTheme.typography.bodyMedium,
                        color = ProGoldLight
                    )
                }
            },
            badge = {
                BestValuePill()
            },
            glow = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        PlanOptionContainer(
            isSelected = selectedPlan == PlanType.MONTHLY,
            select = {
                setSelectedPlan(PlanType.MONTHLY)
            },
            content = {
                Column() {
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
                    if (selectedPlan == PlanType.YEARLY) {
                        upgradeStripeYearly()
                    } else {
                        upgradeStripeMonthly()
                    }
                },
                enabled = stripeEnabled,
            ) { buttonTextStyle ->
                Text(
                    stringResource(
                        id = if (selectedPlan == PlanType.YEARLY) {
                            R.string.start_free_trial
                        } else {
                            R.string.subscribe
                        }
                    ),
                    style = buttonTextStyle
                )
            }

        }

        Spacer(modifier = Modifier.height(16.dp))

        /**
         * Solana yearly
         */
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {

            URButton(
                onClick = {
                    setIsPromptingSolanaPayment(true)
                    upgradeSolana()
                },
                enabled = !isPromptingSolanaPayment &&
                        !isCheckingSolanaTransaction &&
                        selectedPlan == PlanType.YEARLY,
                isProcessing = isPromptingSolanaPayment || isCheckingSolanaTransaction
            ) { buttonTextStyle ->
                Text(
                    stringResource(id = R.string.join_solana_wallet),
                    style = buttonTextStyle
                )
            }

        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            stringResource(id = R.string.solana_one_time_payment),
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted
        )

        Text(
            stringResource(id = R.string.paid_in_usdc_on_solana),
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted
        )

    }

}
