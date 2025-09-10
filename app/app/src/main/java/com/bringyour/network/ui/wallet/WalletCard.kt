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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bringyour.network.R
import com.bringyour.network.ui.theme.MainTintedBackgroundBase
import com.bringyour.network.ui.theme.URNetworkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.bringyour.sdk.Id
import com.bringyour.network.ui.Route
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.gravityCondensedFamily
import com.bringyour.network.ui.theme.ppNeueBitBold
import com.bringyour.network.utils.formatDecimalString
import com.bringyour.sdk.AccountPayment

@Composable
fun WalletCard(
    blockchain: Blockchain?,
    isPayoutWallet: Boolean,
    walletAddress: String,
    walletId: Id?,
    navController: NavController,
    payouts: List<AccountPayment>,
) {

    val walletPayments = payouts.filter { it.walletId == walletId }
    val totalPayouts = if (walletPayments.isEmpty()) {
        "0.00"
    } else {
        formatDecimalString(walletPayments.sumOf { it.tokenAmount }, 4)
    }

    val walletType = if (blockchain == Blockchain.SOLANA)
            "Solana"
        else
            "Polygon"

    Column(
        modifier = Modifier
            .width(240.dp)
            .height(124.dp)
            .background(
                MainTintedBackgroundBase,
                shape = RoundedCornerShape(size = 12.dp)
            )
            .clickable {
                navController.navigate(Route.Wallet("$walletId"))
            }
            .padding(16.dp)
    ) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            WalletChainIcon(blockchain)

            Column(
                horizontalAlignment = Alignment.End
            ) {

                Box(
                    modifier = Modifier.height(22.dp)
                ) {
                    if (isPayoutWallet) {

                        Box(
                            modifier = Modifier
                                .background(Color(0x0AFFFFFF), shape = RoundedCornerShape(6.dp))
                                .padding(6.dp)
                        ) {
                            Text(
                                stringResource(id = R.string.default_wallet).uppercase(),
                                style = TextStyle(
                                    fontSize = 16.sp,
                                    fontFamily = ppNeueBitBold
                                ),
                                color = TextMuted
                            )
                        }

                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // todo populate this
                Text(
                    "${totalPayouts ?: "0.00"} USDC",
                    style = TextStyle(
                        fontFamily = gravityCondensedFamily,
                        fontWeight = FontWeight(900),
                        fontSize = 24.sp,
                        lineHeight = 32.sp,
                        color = Color.White
                    )
                )


                Text(
                    stringResource(id = R.string.total_payouts),
                    style = TextStyle(
                        fontSize = 12.sp,
                        color = TextMuted
                    ),
                    modifier = Modifier.offset(y = -(4).dp)
                )

            }

        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                walletType,
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
            Text(
                "***${walletAddress.takeLast(7)}",
                style = TextStyle(
                    fontSize = 18.sp,
                    fontFamily = ppNeueBitBold,
                    color = Color.White
                )
            )
        }
    }
}

@Preview
@Composable
private fun WalletCardSolanaPreview() {
    val navController = rememberNavController()

    URNetworkTheme {
        WalletCard(
            blockchain = Blockchain.SOLANA,
            isPayoutWallet = false,
            walletAddress = "0x0000000000000000000000",
            navController = navController,
            walletId = null,
            payouts = listOf()
        )
    }
}

@Preview
@Composable
private fun WalletCardPolygonPreview() {
    val navController = rememberNavController()

    URNetworkTheme {
        WalletCard(
            blockchain = Blockchain.POLYGON,
            isPayoutWallet = false,
            walletAddress = "0x0000000000000000000000",
            navController = navController,
            walletId = null,
            payouts = listOf()
        )
    }
}