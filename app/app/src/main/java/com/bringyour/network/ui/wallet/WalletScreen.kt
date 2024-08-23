package com.bringyour.network.ui.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.bringyour.network.ui.components.ButtonStyle
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.BlueMedium
import com.bringyour.network.ui.theme.HeadingLargeCondensed
import com.bringyour.network.ui.theme.MainTintedBackgroundBase
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.TopBarTitleTextStyle
import com.bringyour.network.ui.theme.URNetworkTheme
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.Month
import java.time.ZoneId
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletScreen(
    navController: NavController,
    isSolanaSaga: Boolean
) {

    val getNextPayoutDate: () -> String = {
        val now = LocalDateTime.now(ZoneId.of("UTC"))
        val year = now.year
        val month = now.month
        val day = now.dayOfMonth

        val nextPayoutDate = when {
            day < 15 -> LocalDateTime.of(year, month, 15, 0, 0)
            month == Month.DECEMBER -> LocalDateTime.of(year + 1, Month.JANUARY, 1, 0, 0)
            else -> LocalDateTime.of(year, month.plus(1), 1, 0, 0)
        }

        val date = Date.from(nextPayoutDate.atZone(ZoneId.of("UTC")).toInstant())

        val dateFormat = SimpleDateFormat("MMM d", Locale.US)
        dateFormat.format(date)
    }

    // todo - populate this with real data
    val estimatedPayoutAmount = "0.25"

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text("Wallet", style = TopBarTitleTextStyle)
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
                .fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("URwallet", style = MaterialTheme.typography.headlineSmall)
                }

                Spacer(modifier = Modifier.height(24.dp))

                Box(
                    modifier = Modifier
                        .background(
                            color = MainTintedBackgroundBase,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(
                            start = 16.dp,
                            top = 16.dp,
                            bottom = 10.dp, // hacky due to line-height issue
                            end = 16.dp
                        )
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "Estimated on ${getNextPayoutDate()}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted
                        )
                        Row(
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Text(
                                estimatedPayoutAmount,
                                style = HeadingLargeCondensed
                            )

                            Spacer(modifier = Modifier.width(2.dp))

                            // this is really hacky, but setting line-height isn't being acknowledged
                            Box(
                                modifier = Modifier.offset(y = -(10).dp)
                            ) {
                                Text(
                                    "USDC",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextMuted
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    "You share with others, we share with you. Earn a share of revenue when you provide data to others in the network.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextMuted
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    "To start earning, connect your cryptocurrency wallet to URnetwork or set one up with Circle.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextMuted
                )
            }

            Column {
                URButton(onClick = { /*TODO*/ }) { buttonTextStyle ->
                    Text("Set up Circle Wallet", style = buttonTextStyle)
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (isSolanaSaga) {

                    URButton(
                        onClick = { /*TODO*/ },
                        style = ButtonStyle.OUTLINE
                    ) { buttonTextStyle ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text("Connect Saga wallet", style = buttonTextStyle)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        ClickableText(
                            text = AnnotatedString("Connect another wallet"),
                            style = MaterialTheme.typography.bodyLarge.copy(color = BlueMedium),
                            onClick = {}
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                } else {
                    // non Saga wallet

                    URButton(
                        onClick = { /*TODO*/ },
                        style = ButtonStyle.OUTLINE
                    ) { buttonTextStyle ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text("Connect external wallet", style = buttonTextStyle)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                }
                
            }
        }
    }
}

@Preview
@Composable
fun WalletScreenPreview() {

    val navController = rememberNavController()

    URNetworkTheme {
        WalletScreen(
            navController,
            isSolanaSaga = false
        )
    }
}

@Preview
@Composable
fun WalletScreenSagaPreview() {

    val navController = rememberNavController()

    URNetworkTheme {
        WalletScreen(
            navController,
            isSolanaSaga = true
        )
    }
}