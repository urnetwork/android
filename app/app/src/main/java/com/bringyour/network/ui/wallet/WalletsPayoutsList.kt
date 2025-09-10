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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.bringyour.network.R
import com.bringyour.network.utils.formatDateLocalized
import com.bringyour.sdk.AccountPayment
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

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
                stringResource(id = R.string.earnings),
                style = MaterialTheme.typography.bodyLarge
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Column {

            for (payout in payouts) {
                HorizontalDivider()
                PayoutRow(
                    walletAddress = payout.walletAddress,
                    completeTime = payout.completeTime?.let {
                        // localize the date
                        val millis = it.unixMilli()
                        val instant = Instant.ofEpochMilli(millis)
                        val localDateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
                        formatDateLocalized(localDateTime)
                    },
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