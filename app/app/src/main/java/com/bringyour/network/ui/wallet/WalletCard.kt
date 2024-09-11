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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bringyour.network.R
import com.bringyour.network.ui.theme.MainTintedBackgroundBase
import com.bringyour.network.ui.theme.URNetworkTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.bringyour.client.Id
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.gravityCondensedFamily
import com.bringyour.network.ui.theme.ppNeueBitBold

@Composable
fun WalletCard(
    isCircleWallet: Boolean,
    blockchain: Blockchain?,
    isPayoutWallet: Boolean,
    walletAddress: String,
    walletId: Id,
    navController: NavController,
) {

    val walletType = if (isCircleWallet) "Circle"
        else if (blockchain == Blockchain.SOLANA)
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
            .padding(16.dp)
            .clickable {
                navController.navigate("wallet/${walletId}")
            }
    ) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            WalletChainIcon(isCircleWallet, blockchain)

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
                                "Default".uppercase(),
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
                    "0.00 USDC",
                    style = TextStyle(
                        fontFamily = gravityCondensedFamily,
                        fontWeight = FontWeight(900),
                        fontSize = 24.sp,
                        lineHeight = 32.sp,
                        color = Color.White
                    )
                )


                Text(
                    "total payouts",
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

@Composable
fun WalletChainIcon(
    isCircleWallet: Boolean,
    blockchain: Blockchain?
) {

    val painterResourceId = if (isCircleWallet) R.drawable.circle_logo
        else if (blockchain == Blockchain.SOLANA)
            R.drawable.solana_logo
            else
            R.drawable.polygon_logo

    val description = if (isCircleWallet) "Circle Wallet"
        else if (blockchain == Blockchain.SOLANA)
            "Solana Wallet"
        else
            "Polygon Wallet"

    val padding = if (isCircleWallet) 12.dp
        else if (blockchain == Blockchain.SOLANA)
            12.dp
        else
            0.dp

    val width = if (isCircleWallet) 32.dp // circle
        else if (blockchain == Blockchain.SOLANA)
            32.dp // solana
        else
            54.dp // polygon

    val backgroundColor = if (isCircleWallet)
        Brush.linearGradient(
            colors = listOf(
                Color(0xFF68D7FA),
                Color(0xFF7EF1B3)
            )
        )
    else if (blockchain == Blockchain.SOLANA)
        Brush.linearGradient(
            colors = listOf(
                Color(0xFF9945FF),
                Color(0xFF14F195)
            )
        )
    else
        Brush.linearGradient(
            colors = listOf(
                Color(0xFF8A46FF),
                Color(0xFF6E38CC)
            )
        )

    Box(
        modifier = Modifier
            .background(backgroundColor, shape = CircleShape)
            .padding(padding)
    ) {
        Icon(
            painter = painterResource(id = painterResourceId),
            tint = Color.White,
            contentDescription = description,
            modifier = Modifier.width(width).height(width)
        )
    }

}

@Preview
@Composable
private fun WalletCardCirclePreview() {
    val navController = rememberNavController()

    URNetworkTheme {
        WalletCard(
            isCircleWallet = true,
            blockchain = Blockchain.POLYGON,
            isPayoutWallet = true,
            walletAddress = "0x0000000000000000000000",
            navController = navController,
            walletId = Id()
        )
    }
}

@Preview
@Composable
private fun WalletCardSolanaPreview() {
    val navController = rememberNavController()

    URNetworkTheme {
        WalletCard(
            isCircleWallet = false,
            blockchain = Blockchain.SOLANA,
            isPayoutWallet = false,
            walletAddress = "0x0000000000000000000000",
            navController = navController,
            walletId = Id()
        )
    }
}

@Preview
@Composable
private fun WalletCardPolygonPreview() {
    val navController = rememberNavController()

    URNetworkTheme {
        WalletCard(
            isCircleWallet = false,
            blockchain = Blockchain.POLYGON,
            isPayoutWallet = false,
            walletAddress = "0x0000000000000000000000",
            navController = navController,
            walletId = Id()
        )
    }
}