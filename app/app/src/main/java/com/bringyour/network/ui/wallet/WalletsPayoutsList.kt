package com.bringyour.network.ui.wallet

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bringyour.sdk.AccountPayment

@Composable
fun WalletsPayoutsList(
    payouts: List<AccountPayment>,
) {
    if (payouts.isNotEmpty()) {

        Text(
            "Earnings",
            style = MaterialTheme.typography.bodyLarge
        )

        Spacer(modifier = Modifier.height(8.dp))

        Column {

            for (payout in payouts) {
                PayoutRow(
                    walletAddress = payout.walletAddress,
                    completeTime = if (payout.completeTime != null) payout.completeTime.format("Jan 2") else null,
                    amountUsd = payout.tokenAmount
                )
            }
        }

    } else {
        NoPayoutsFound()
    }
}