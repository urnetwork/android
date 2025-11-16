package com.bringyour.network.ui.upgrade

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.bringyour.network.R
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.theme.OffBlack
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.Yellow
import com.stripe.android.PaymentConfiguration
import com.stripe.android.paymentsheet.PaymentSheet
import com.stripe.android.paymentsheet.PaymentSheetResult

//@OptIn(ExperimentalCustomerSessionApi::class)
@Composable
fun AltSubscriptionOptions(
    onStripePaymentSuccess: () -> Unit,
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
            onStripePaymentFailed = {}
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



    Column(
        modifier = Modifier
            .background(
                OffBlack,
                RoundedCornerShape(12.dp)
            )
            .fillMaxWidth()
            .padding(16.dp)
    ) {

        Row (
            verticalAlignment = Alignment.Bottom
        ) {

            Text(
                "$5",
                fontSize = 24.sp,
                style = MaterialTheme.typography.bodyLarge
            )

            Text(
                "/month",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = 2.dp)
            )

        }

        Text(
            // stringResource(id = R.string.pay_with_stripe)
            "Monthly",
            fontWeight = FontWeight.Bold,
            color = TextMuted
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth()) {

            URButton(
                onClick = {
                    upgradeStripeMonthly()
//                        if (customerConfig != null && monthlyPaymentIntentClientSecret != null) {
//                            presentPaymentSheet(
//                                paymentSheet,
//                                customerConfig!!,
//                                monthlyPaymentIntentClientSecret!!
//                            )
//                        }
                },
                 enabled = !isCreatingIntents,
                isProcessing = isCreatingIntents
            ) { buttonTextStyle ->
                Text(
                    stringResource(id = R.string.pay_with_stripe),
                    style = buttonTextStyle
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    Column(
        modifier = Modifier
            // .padding(16.dp)
            .background(
                OffBlack,
                RoundedCornerShape(12.dp)
            )
            .border(width = 2.dp, color = Yellow, shape = RoundedCornerShape(12.dp))
            .fillMaxWidth()
            .padding(16.dp)
    ) {

        Row (
            verticalAlignment = Alignment.Bottom
        ) {

            Text(
                "$3.33",
                fontSize = 24.sp,
                style = MaterialTheme.typography.bodyLarge
            )

            Text(
                "/month",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = 2.dp)
            )

        }

        Text(
            "$40 Yearly",
            fontWeight = FontWeight.Bold,
            color = TextMuted
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth()) {

            URButton(
                onClick = {
                    upgradeStripeYearly()

//                        if (customerConfig != null && yearlyPaymentIntentClientSecret != null) {
//                            presentPaymentSheet(
//                                paymentSheet,
//                                customerConfig!!,
//                                yearlyPaymentIntentClientSecret!!
//                            )
//                        }
                },
                enabled = !isCreatingIntents,
                isProcessing = isCreatingIntents
            ) { buttonTextStyle ->
                Text(
                    stringResource(id = R.string.pay_with_stripe),
                    style = buttonTextStyle
                )
            }

        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {

            URButton(
                onClick = {
                    setIsPromptingSolanaPayment(true)
                    upgradeSolana()
                },
                enabled = !isPromptingSolanaPayment && !isCheckingSolanaTransaction,
                isProcessing = isPromptingSolanaPayment || isCheckingSolanaTransaction
            ) { buttonTextStyle ->
                Text(
                    "Join with Solana Wallet",
                    style = buttonTextStyle
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            stringResource(id = R.string.solana_payment_insufficient_funds_warning),
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
    onStripePaymentFailed: () -> Unit
) {
    when(paymentSheetResult) {
        is PaymentSheetResult.Canceled -> {
            // do nothing
        }
        is PaymentSheetResult.Failed -> {
            print("Error: ${paymentSheetResult.error}")
            onStripePaymentFailed()
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