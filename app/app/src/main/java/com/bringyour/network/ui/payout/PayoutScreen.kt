package com.bringyour.network.ui.payout

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.bringyour.network.R
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.TopBarTitleTextStyle
import com.bringyour.network.ui.wallet.AccountPoints
import com.bringyour.network.utils.formatDecimalString
import com.bringyour.sdk.AccountPayment
import androidx.core.net.toUri
import com.bringyour.network.ui.theme.Pink

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PayoutScreen(
    navController: NavHostController,
    accountPayment: AccountPayment?,
    payoutPoints: Double,
    multiplierPoints: Double,
    referralPoints: Double,
    reliabilityPoints: Double,
    totalAccountPoints: Double,
    holdsMultiplier: Boolean
) {

    val context = LocalContext.current

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        if (accountPayment?.completed == true)
                            "+${formatDecimalString(accountPayment.tokenAmount, 2)} ${accountPayment.tokenType} (${accountPayment.completeTime.format("Jan 2")})"
                        else
                            stringResource(id = R.string.pending_payout),
                        style = TopBarTitleTextStyle
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Black
                ),
                actions = {},
            )
        }
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
        ) {

            if (accountPayment != null) {
                if (accountPayment.completed) {
                    Text(
                        stringResource(id = R.string.date_payout, accountPayment.completeTime.format("Jan 2, 2006")),
                        style = MaterialTheme.typography.headlineMedium
                    )
                } else {
                    Text(
                        stringResource(id = R.string.pending_payout),
                        style = MaterialTheme.typography.headlineMedium
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                AccountPoints(
                    holdsMultiplier = holdsMultiplier,
                    payoutPoints = payoutPoints,
                    multiplierPoints = multiplierPoints,
                    referralPoints = referralPoints,
                    reliabilityPoints = reliabilityPoints,
                    totalAccountPoints = totalAccountPoints
                )

                Spacer(modifier = Modifier.height(24.dp))

                if (accountPayment.completed) {

                    Row() {
                        Text(
                            stringResource(id = R.string.amount),
                            style = TextStyle(
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                        )
                    }
                    Row() {
                        Text("${accountPayment.tokenAmount} ${accountPayment.tokenType}")
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row() {
                        Text(
                            stringResource(id = R.string.wallet_address),
                            style = TextStyle(
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                        )
                    }
                    Row() {
                        Text(accountPayment.walletAddress)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row() {

                        Text(
                            stringResource(id = R.string.transaction),
                            style = TextStyle(
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                        )
                    }
                    Row() {
                        Text(
                            accountPayment.txHash,
                            modifier = Modifier.clickable {

                                val baseUrl = if (accountPayment.blockchain == "SOL")
                                    "https://solscan.io/tx/"
                                else
                                    "https://polygonscan.com/tx/"

                                val intent = Intent(Intent.ACTION_VIEW,
                                    "${baseUrl}/${accountPayment.txHash}".toUri())
                                context.startActivity(intent)
                            },
                            color = Pink
                        )
                    }

                } else {

                    Icon(
                        Icons.Filled.HourglassTop,
                        contentDescription = "Payment pending",
                        tint = TextMuted
                    )

                    Text(stringResource(id = R.string.pending_payout))

                }

            } else {
                // payment not found error
                Icon(
                    Icons.Filled.Warning,
                    contentDescription = "Payment not found",
                    tint = TextMuted
                )
            }

        }

    }

}