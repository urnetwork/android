package com.bringyour.network.ui.wallet

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.bringyour.sdk.AccountPayment

@Composable
fun WalletsPayoutsList(
    payouts: List<AccountPayment>,
    navController: NavHostController,
) {
    if (payouts.isNotEmpty()) {

        Row(
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            Text(
                "Earnings",
                style = MaterialTheme.typography.bodyLarge
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Column {

            for (payout in payouts) {
                HorizontalDivider()
                PayoutRow(
                    walletAddress = payout.walletAddress,
                    completeTime = if (payout.completeTime != null) payout.completeTime.format("Jan 2") else null,
                    amountUsd = payout.tokenAmount,
                    payoutByteCount = payout.payoutByteCount,
                    completed = payout.completed,
                    navController = navController,
                    id = payout.paymentId
                )
            }
        }

    } else {
        NoPayoutsFound()
    }
}