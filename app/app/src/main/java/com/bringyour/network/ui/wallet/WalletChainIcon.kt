package com.bringyour.network.ui.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bringyour.network.R
import com.bringyour.network.ui.theme.URNetworkTheme

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
private fun WalletChainIconCirclePreview() {
    URNetworkTheme {
        WalletChainIcon(
            isCircleWallet = true,
            blockchain = Blockchain.POLYGON
        )
    }
}

@Preview
@Composable
private fun WalletChainIconPolygonPreview() {
    URNetworkTheme {
        WalletChainIcon(
            isCircleWallet = false,
            blockchain = Blockchain.POLYGON
        )
    }
}

@Preview
@Composable
private fun WalletChainIconSolanaPreview() {
    URNetworkTheme {
        WalletChainIcon(
            isCircleWallet = false,
            blockchain = Blockchain.SOLANA
        )
    }
}