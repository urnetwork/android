package com.bringyour.network.ui.upgrade

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bringyour.network.R
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.theme.OffBlack
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.Yellow

@Composable
fun AltSubscriptionOptions(
    upgradeStripeMonthly: () -> Unit,
    upgradeStripeYearly: () -> Unit,
    onStripePaymentSuccess: () -> Unit,
    upgradeSolana: () -> Unit,
    isPromptingSolanaPayment: Boolean,
    setIsPromptingSolanaPayment: (Boolean) -> Unit,
) {

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
            "Monthly",
            fontWeight = FontWeight.Bold,
            color = TextMuted
        )

        Spacer(modifier = Modifier.height(16.dp))

        /**
         * Stripe monthly
         */
        Row(modifier = Modifier.fillMaxWidth()) {

            URButton(
                onClick = {
                    upgradeStripeMonthly()
                },
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
            "Yearly",
            fontWeight = FontWeight.Bold,
            color = TextMuted
        )

        Spacer(modifier = Modifier.height(16.dp))

        /**
         * Stripe yearly
         */
        Row(modifier = Modifier.fillMaxWidth()) {

            URButton(
                onClick = {
                    upgradeStripeYearly()
                },
            ) { buttonTextStyle ->
                Text(
                    stringResource(id = R.string.pay_with_stripe),
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
                enabled = !isPromptingSolanaPayment,
                isProcessing = isPromptingSolanaPayment
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