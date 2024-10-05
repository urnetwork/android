package com.bringyour.network.ui.wallet

import androidx.compose.foundation.background
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.URNetworkTheme
import com.bringyour.network.R
import com.bringyour.network.ui.theme.Green

@Composable
fun PayoutRow(
    walletAddress: String,
    completeTime: String?,
    amountUsd: Double
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {

        Row {
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
                Icon(
                    painter = painterResource(id = R.drawable.circle_check),
                    contentDescription = "Payment complete",
                    tint = Green
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column() {
                Text(
                    "+$amountUsd USDC",
                    style = MaterialTheme.typography.bodyLarge
                )

                Spacer(modifier = Modifier.height(8.dp))


                Text(
                    completeTime ?: "Pending",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
            }
        }

        Row {
            Text(
                "***${walletAddress.takeLast(7)}",
                style = TextStyle(
                    color = TextMuted
                )
            )
        }

    }
}

@Preview
@Composable
private fun PayoutRowPreview() {

    URNetworkTheme {
        PayoutRow(
            "0xb696b7a5e41c9ec487f1b81064ec487261a1c3ddbaff96d5892854c824530ca5",
            "Jan 2",
            1.25
        )
    }
}

@Preview
@Composable
private fun PayoutRowEmptyCompleteTimePreview() {

    URNetworkTheme {
        PayoutRow(
            "0xb696b7a5e41c9ec487f1b81064ec487261a1c3ddbaff96d5892854c824530ca5",
            null,
            1.25
        )
    }
}