package com.bringyour.network.ui.wallet

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.bringyour.network.ui.theme.URNetworkTheme

@Composable
fun WalletScreen() {
    Column {
        Text("Wallet Screen")
    }
}

@Preview
@Composable
fun WalletScreenPreview() {
    URNetworkTheme {
        WalletScreen()
    }
}