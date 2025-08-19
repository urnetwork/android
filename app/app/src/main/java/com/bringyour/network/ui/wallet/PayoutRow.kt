package com.bringyour.network.ui.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.URNetworkTheme
import com.bringyour.network.R
import com.bringyour.network.ui.theme.Green
import androidx.compose.material.icons.filled.Schedule
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.bringyour.network.ui.Route
import com.bringyour.network.utils.formatDecimalString
import com.bringyour.network.utils.todayFormatted
import com.bringyour.sdk.Id

@Composable
fun PayoutRow(
    navController: NavHostController,
    walletAddress: String,
    completeTime: String?,
    amountUsd: Double,
    payoutByteCount: Long,
    completed: Boolean,
    id: Id
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                vertical = 8.dp,
                horizontal = 16.dp
            )
            .clickable {
                navController.navigate(Route.Payout(id.idStr))
            },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,

    ) {

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .height(48.dp)
                    .background(
                        Color(0x0AFFFFFF),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {

                if (completed) {
                    Icon(
                        painter = painterResource(id = R.drawable.circle_check),
                        contentDescription = "Payment complete",
                        tint = Green
                    )
                } else {
                    Icon(
                        Icons.Default.Schedule,
                        contentDescription = "Payment pending",
                        tint = TextMuted
                    )
                }

            }

            Spacer(modifier = Modifier.width(16.dp))

            Column() {

                if (completed) {

                    Text(
                        "+${formatDecimalString(amountUsd, 2)} USDC",
                        style = MaterialTheme.typography.bodyLarge
                    )

                } else {
                    Text(
                        "Pending: ${formatDecimalString(payoutByteCount / (1024.0 * 1024.0), 2)} MB provided",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextMuted
                    )
                }

                // Spacer(modifier = Modifier.height(8.dp))

                Text(
                    completeTime ?: todayFormatted(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
            }
        }

        Row {
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = if (completed) "Payment on $completeTime" else "Pending payment",
                tint = TextMuted
            )
        }

    }
}

@Preview
@Composable
private fun PayoutRowPreview() {

    URNetworkTheme {
        PayoutRow(
            rememberNavController(),
            "0xb696b7a5e41c9ec487f1b81064ec487261a1c3ddbaff96d5892854c824530ca5",
            "Jan 2",
            1.25,
            9876,
            true,
            Id()
        )
    }
}



@Preview
@Composable
private fun PayoutRowPending() {

    URNetworkTheme {
        PayoutRow(
            rememberNavController(),
            "0xb696b7a5e41c9ec487f1b81064ec487261a1c3ddbaff96d5892854c824530ca5",
            null,
            1.25,
            123456789,
            false,
            Id()
        )
    }
}