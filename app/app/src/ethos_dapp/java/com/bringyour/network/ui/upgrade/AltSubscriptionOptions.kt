package com.bringyour.network.ui.upgrade

import com.bringyour.network.ui.theme.ProGoldLight
import com.bringyour.network.ui.components.BestValuePill
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.bringyour.network.R
import com.bringyour.network.ui.components.PlanOptionContainer
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.shared.enums.PlanType
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.TopBarTitleTextStyle
import com.stripe.android.PaymentConfiguration
import com.stripe.android.paymentsheet.PaymentSheet
import com.stripe.android.paymentsheet.PaymentSheetResult

//@OptIn(ExperimentalCustomerSessionApi::class)
@Composable
fun AltSubscriptionOptions(
    // the prices the picker prints, from PlanViewModel (they match the Stripe prices sold)
    monthlyCostFormatted: String,
    yearlyCostFormatted: String,
    onStripePaymentSuccess: () -> Unit,
    // the payment sheet's error detail, for the shared payment-problem dialog. This
    // used to be `{}` -- a declined card looked exactly like nothing happening.
    onStripePaymentFailed: (String?) -> Unit,
    upgradeSolana: () -> Unit,
    isPromptingSolanaPayment: Boolean,
    setIsPromptingSolanaPayment: (Boolean) -> Unit,
    isCheckingSolanaTransaction: Boolean,
    stripePaymentIntentViewModel: StripePaymentIntentViewModel = hiltViewModel()
) {

    val context = LocalContext.current
    val paymentSheet = remember { PaymentSheet.Builder { result ->
        onPaymentSheetResult(
            result,
            onStripePaymentSuccess = onStripePaymentSuccess,
            onStripePaymentFailed = onStripePaymentFailed
        )
    } }.build()

    val isCreatingIntents by stripePaymentIntentViewModel.isCreatingIntents.collectAsState()


    val upgradeStripeMonthly: () -> Unit = {

        stripePaymentIntentViewModel.createPaymentIntent() { result ->
             val monthlyPaymentIntentClientSecret = stripePaymentIntentViewModel.findClientSecret(result.paymentIntents, "monthly")

            if (monthlyPaymentIntentClientSecret == null) {
                Toast.makeText(context, "Error creating payment intent", Toast.LENGTH_SHORT).show()
                return@createPaymentIntent
            }

//            yearlyPaymentIntentClientSecret = stripePaymentIntentViewModel.findClientSecret(result.paymentIntents, "yearly")
//
            val customerConfig = PaymentSheet.CustomerConfiguration(
                id = result.customerId,
                ephemeralKeySecret = result.ephemeralKey
            )

            PaymentConfiguration.init(context, result.publishableKey)

            presentPaymentSheet(
                paymentSheet,
                customerConfig,
                monthlyPaymentIntentClientSecret
            )

        }

    }

    val upgradeStripeYearly: () -> Unit = {

        stripePaymentIntentViewModel.createPaymentIntent() { result ->
            val yearlyPaymentIntentClientSecret = stripePaymentIntentViewModel.findClientSecret(result.paymentIntents, "yearly")

            if (yearlyPaymentIntentClientSecret == null) {
                Toast.makeText(context, "Error creating payment intent", Toast.LENGTH_SHORT).show()
                return@createPaymentIntent
            }

            val customerConfig = PaymentSheet.CustomerConfiguration(
                id = result.customerId,
                ephemeralKeySecret = result.ephemeralKey
            )

            PaymentConfiguration.init(context, result.publishableKey)

            presentPaymentSheet(
                paymentSheet,
                customerConfig,
                yearlyPaymentIntentClientSecret
            )

        }

    }



    /**
     * The same plan picker onboarding shows: yearly is the highlighted default
     * in the Pro-gold dress with the Best value pill and the free trial,
     * monthly the quiet alternative with no trial, and ONE button whose label
     * follows the selection. This used to be two cards with a button each
     * ("$3.33/month · $40 Yearly" and a "Pay with Stripe" monthly card), which
     * was not the deal onboarding presents.
     */
    val (selectedPlan, setSelectedPlan) = remember { mutableStateOf(PlanType.YEARLY) }

    Column {

        /**
         * Yearly
         */
        PlanOptionContainer(
            isSelected = selectedPlan == PlanType.YEARLY,
            select = {
                setSelectedPlan(PlanType.YEARLY)
            },
            content = {
                Column {
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

        /**
         * Monthly: no trial
         */
        PlanOptionContainer(
            isSelected = selectedPlan == PlanType.MONTHLY,
            select = {
                setSelectedPlan(PlanType.MONTHLY)
            },
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
                    if (selectedPlan == PlanType.YEARLY) {
                        upgradeStripeYearly()
                    } else {
                        upgradeStripeMonthly()
                    }
                },
                enabled = !isCreatingIntents,
                isProcessing = isCreatingIntents
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
         * Solana yearly: a one-time USDC payment, only for the yearly plan
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

private fun presentPaymentSheet(
    paymentSheet: PaymentSheet,
    customerConfig: PaymentSheet.CustomerConfiguration,
    paymentIntentClientSecret: String
) {
    paymentSheet.presentWithPaymentIntent(
        paymentIntentClientSecret,
        PaymentSheet.Configuration.Builder(merchantDisplayName = "URnetwork")
            .customer(customerConfig)
            // Set `allowsDelayedPaymentMethods` to true if your business handles
            // delayed notification payment methods like US bank accounts.
            .allowsDelayedPaymentMethods(true)
            .build()
    )
}

private fun onPaymentSheetResult(
    paymentSheetResult: PaymentSheetResult,
    onStripePaymentSuccess: () -> Unit,
    onStripePaymentFailed: (String?) -> Unit
) {
    when(paymentSheetResult) {
        is PaymentSheetResult.Canceled -> {
            // do nothing
        }
        is PaymentSheetResult.Failed -> {
            Log.e("AltSubscriptionOptions", "payment sheet failed", paymentSheetResult.error)
            onStripePaymentFailed(paymentSheetResult.error.localizedMessage)
        }
        is PaymentSheetResult.Completed -> {
            onStripePaymentSuccess()
        }
    }
}

//@Preview
//@Composable
//private fun UpgradeStripePreview() {
//    URNetworkTheme {
//        Scaffold { padding ->
//
//            Column(
//                modifier = Modifier
//                    .padding(padding)
//                    .padding(16.dp)
//            ) {
//                AltSubscriptionOptions(
//                    upgradeStripeMonthly = {},
//                    upgradeStripeYearly = {},
//                    upgradeInProgress = false,
//                    upgradeSolana = {},
//                    isPromptingSolanaPayment = false,
//                    setIsPromptingSolanaPayment = {}
//                )
//            }
//        }
//    }
//}