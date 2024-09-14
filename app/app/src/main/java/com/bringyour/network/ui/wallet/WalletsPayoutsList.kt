package com.bringyour.network.ui.wallet

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bringyour.client.AccountPayment
import com.bringyour.client.AccountWallet

@Composable
fun WalletsPayoutsList(
    payouts: List<AccountPayment>,
    wallets: List<AccountWallet>
) {
    if (payouts.isNotEmpty()) {

        Text(
            "Earnings",
            style = MaterialTheme.typography.bodyLarge
        )

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn {

            items(payouts) { payout ->

                val wallet = wallets.find { it.walletId == payout.walletId }

                PayoutRow(
                    walletAddress = wallet?.walletAddress ?: "",
                    completeTime = payout.completeTime.format("Jan 2"),
                    amountUsd = payout.tokenAmount
                )
            }
        }

    } else {
        NoPayoutsFound()
    }
}