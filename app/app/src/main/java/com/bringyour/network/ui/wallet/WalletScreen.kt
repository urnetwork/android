package com.bringyour.network.ui.wallet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.bringyour.network.ui.components.ButtonStyle
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.TopBarTitleTextStyle
import com.bringyour.network.ui.theme.URNetworkTheme
import com.bringyour.network.ui.theme.ppNeueBitBold
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.TextFieldValue
import com.bringyour.client.AccountPayment
import com.bringyour.client.AccountWallet
import com.bringyour.client.Id
import com.bringyour.network.R
import com.bringyour.network.ui.components.URDialog
import com.bringyour.network.ui.components.URTextInput
import com.bringyour.network.ui.theme.BlueMedium
import com.bringyour.network.ui.theme.Red
import com.bringyour.network.ui.theme.TextMuted


@Composable
fun WalletScreen(
    navController: NavController,
    accountWallet: AccountWallet?,
    walletViewModel: WalletViewModel,
) {

    val payoutWalletId = walletViewModel.payoutWalletId
    val isPayoutWallet = accountWallet?.walletId?.equals(payoutWalletId) ?: false

    WalletScreen(
        navController,
        walletId = accountWallet?.walletId,
        walletAddress = accountWallet?.walletAddress,
        isPayoutWallet = isPayoutWallet,
        blockchain = Blockchain.fromString(accountWallet?.blockchain ?: ""),
        isCircleWallet = !accountWallet?.circleWalletId.isNullOrEmpty(),
        payouts = walletViewModel.payouts,
        setPayoutWallet = walletViewModel.setPayoutWallet,
        isSettingPayoutWallet = walletViewModel.isSettingPayoutWallet,
        removeWallet = walletViewModel.removeWallet,
        isRemovingWallet = walletViewModel.isRemovingWallet,
        removeWalletModalVisible = walletViewModel.removeWalletModalVisible,
        openRemoveWalletModal = walletViewModel.openRemoveWalletModal,
        closeRemoveWalletModal = walletViewModel.closeRemoveWalletModal
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletScreen(
    navController: NavController,
    walletId: Id?,
    walletAddress: String?,
    isPayoutWallet: Boolean,
    blockchain: Blockchain?,
    isCircleWallet: Boolean,
    payouts: List<AccountPayment>,
    setPayoutWallet: (Id) -> Unit,
    isSettingPayoutWallet: Boolean,
    removeWallet: (Id) -> Unit,
    isRemovingWallet: Boolean,
    removeWalletModalVisible: Boolean,
    closeRemoveWalletModal: () -> Unit,
    openRemoveWalletModal: () -> Unit,
) {

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text("Wallet", style = TopBarTitleTextStyle)
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Back"
                        )
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

            Row(
                modifier = Modifier.fillMaxWidth()
            ) {

                WalletChainIcon(
                    blockchain = blockchain,
                    isCircleWallet = isCircleWallet
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Text(
                        if (isCircleWallet) "Circle Wallet" else
                            "${blockchain.toString().lowercase().capitalize()} Wallet",
                        style = MaterialTheme.typography.headlineSmall
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        "***${walletAddress?.takeLast(7)}",
                        style = TextStyle(
                            fontSize = 20.sp,
                            fontFamily = ppNeueBitBold
                        )
                    )

                }

            }

            Spacer(modifier = Modifier.height(24.dp))

            if (!isPayoutWallet) {
                URButton(
                    onClick = {
                        if (walletId != null) {
                            setPayoutWallet(walletId)
                        }
                    },
                    enabled = !isSettingPayoutWallet && walletId != null
                ) { buttonTextStyle ->
                    Text(
                        "Make default",
                        style = buttonTextStyle
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (!isCircleWallet) {
                URButton(
                    onClick = {
                        openRemoveWalletModal()
                    },
                    style = ButtonStyle.OUTLINE,
                    enabled = !removeWalletModalVisible
                ) { buttonTextStyle ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            "Remove",
                            style = buttonTextStyle
                        )
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }


            if (payouts.isNotEmpty()) {

                Text(
                    "Earnings",
                    style = MaterialTheme.typography.bodyLarge
                )

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn {

                    items(payouts) { payout ->

                        PayoutRow(
                            walletAddress = walletAddress ?: "",
                            completeTime = payout.completeTime.format("Jan 2"),
                            amountUsd = payout.tokenAmount
                        )
                    }
                }

            } else {
                NoPayoutsFound()
            }
        }

        URDialog(
            visible = removeWalletModalVisible,
            onDismiss = { closeRemoveWalletModal() }
        ) {
            Column() {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Icon(
                        painter = painterResource(id = R.drawable.icon_warning),
                        contentDescription = "",
                        tint = Red
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        "Remove wallet?",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Are you sure you want to delete this wallet? If this is your default wallet, we’ll hold you have set another wallet as default.",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(32.dp))
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    ClickableText(
                        text = AnnotatedString(
                            "Cancel",
                            spanStyle = SpanStyle(
                                color = BlueMedium,
                                fontSize = 14.sp
                            )
                        ),
                        onClick = {
                            closeRemoveWalletModal()
                        },
                        )

                    Spacer(modifier = Modifier.width(32.dp))

                    ClickableText(
                        text = AnnotatedString(
                            "Remove wallet",
                            spanStyle = if (!isRemovingWallet && walletId != null)
                                SpanStyle(
                                    color = BlueMedium,
                                    fontSize = 14.sp
                                ) else
                                SpanStyle(
                                    color = TextMuted,
                                    fontSize = 14.sp
                                )
                        ),
                        onClick = {
                            if (walletId != null) {
                                removeWallet(walletId)
                                closeRemoveWalletModal()
                                navController.popBackStack()
                            }
                        },
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun WalletScreenPreview() {
    val navController = rememberNavController()

    URNetworkTheme {
        WalletScreen(
            navController,
            walletId = null,
            walletAddress = "0x000000000000000",
            isPayoutWallet = false,
            isCircleWallet = false,
            blockchain = Blockchain.POLYGON,
            payouts = listOf(),
            setPayoutWallet = {},
            isSettingPayoutWallet = false,
            removeWallet = {},
            isRemovingWallet = false,
            removeWalletModalVisible = false,
            openRemoveWalletModal = {},
            closeRemoveWalletModal = {}
        )
    }
}

@Preview
@Composable
private fun WalletScreenIsPayoutPreview() {
    val navController = rememberNavController()


    URNetworkTheme {
        WalletScreen(
            navController,
            walletId = null,
            walletAddress = "0x000000000000000",
            isPayoutWallet = true,
            isCircleWallet = true,
            blockchain = Blockchain.POLYGON,
            payouts = listOf(),
            setPayoutWallet = {},
            isSettingPayoutWallet = false,
            removeWallet = {},
            isRemovingWallet = false,
            removeWalletModalVisible = false,
            openRemoveWalletModal = {},
            closeRemoveWalletModal = {}
        )
    }
}

@Preview
@Composable
private fun WalletScreenRemoveWalletPreview() {
    val navController = rememberNavController()

    URNetworkTheme {
        WalletScreen(
            navController,
            walletId = null,
            walletAddress = "0x000000000000000",
            isPayoutWallet = true,
            isCircleWallet = true,
            blockchain = Blockchain.POLYGON,
            payouts = listOf(),
            setPayoutWallet = {},
            isSettingPayoutWallet = false,
            removeWallet = {},
            isRemovingWallet = false,
            removeWalletModalVisible = true,
            openRemoveWalletModal = {},
            closeRemoveWalletModal = {}
        )
    }
}