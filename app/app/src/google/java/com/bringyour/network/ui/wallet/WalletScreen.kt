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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.BottomSheetScaffoldState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.bringyour.sdk.AccountPayment
import com.bringyour.sdk.AccountWallet
import com.bringyour.sdk.Id
import com.bringyour.network.R
import com.bringyour.network.ui.components.URDialog
import com.bringyour.network.ui.components.overlays.OverlayMode
import com.bringyour.network.ui.shared.viewmodels.OverlayViewModel
import com.bringyour.network.ui.theme.BlueMedium
import com.bringyour.network.ui.theme.HeadingLargeCondensed
import com.bringyour.network.ui.theme.MainTintedBackgroundBase
import com.bringyour.network.ui.theme.Red
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.utils.formatDecimalString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun WalletScreen(
    navController: NavController,
    accountWallet: AccountWallet?,
    walletViewModel: WalletViewModel,
    overlayViewModel: OverlayViewModel
) {

    val payoutWalletId = walletViewModel.payoutWalletId
    val isPayoutWallet = accountWallet?.walletId?.equals(payoutWalletId) ?: false
    val payouts by walletViewModel.payouts.collectAsState()

    WalletScreen(
        navController,
        walletId = accountWallet?.walletId,
        walletAddress = accountWallet?.walletAddress,
        isPayoutWallet = isPayoutWallet,
        blockchain = Blockchain.fromString(accountWallet?.blockchain ?: ""),
        isCircleWallet = !accountWallet?.circleWalletId.isNullOrEmpty(),
        payouts = payouts.filter { payout -> payout.walletId.equals(accountWallet?.walletId) },
        setPayoutWallet = walletViewModel.setPayoutWallet,
        isSettingPayoutWallet = walletViewModel.isSettingPayoutWallet,
        removeWallet = walletViewModel.removeWallet,
        isRemovingWallet = walletViewModel.isRemovingWallet,
        removeWalletModalVisible = walletViewModel.removeWalletModalVisible,
        openRemoveWalletModal = walletViewModel.openRemoveWalletModal,
        closeRemoveWalletModal = walletViewModel.closeRemoveWalletModal,
        circleWalletBalance = walletViewModel.circleWalletBalance,
        setCircleWalletBalance = walletViewModel.setCircleWalletBalance,
        launchOverlay = overlayViewModel.launch,
        refresh = walletViewModel.refreshWalletInfo,
        isRefreshing = walletViewModel.isRefreshingWallet
    )
}

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
    circleWalletBalance: Double,
    setCircleWalletBalance: (Double) -> Unit,
    launchOverlay: (OverlayMode) -> Unit,
    refresh: (Boolean) -> Unit,
    isRefreshing: Boolean,
) {

    if (isCircleWallet) {
        CircleWalletScaffold(
            navController,
            walletId = walletId,
            walletAddress = walletAddress,
            isPayoutWallet = isPayoutWallet,
            blockchain = blockchain,
            payouts = payouts,
            setPayoutWallet = setPayoutWallet,
            isSettingPayoutWallet = isSettingPayoutWallet,
            removeWallet = removeWallet,
            isRemovingWallet = isRemovingWallet,
            removeWalletModalVisible = removeWalletModalVisible,
            closeRemoveWalletModal = closeRemoveWalletModal,
            walletBalance = circleWalletBalance,
            setCircleWalletBalance = setCircleWalletBalance,
            launchOverlay = launchOverlay,
            refresh = refresh,
            isRefreshing = isRefreshing
        )
    } else {
        ExternalWalletScreenContent(
            navController = navController,
            walletId = walletId,
            walletAddress = walletAddress,
            isPayoutWallet = isPayoutWallet,
            blockchain = blockchain,
            payouts = payouts,
            setPayoutWallet = setPayoutWallet,
            isSettingPayoutWallet = isSettingPayoutWallet,
            removeWallet = removeWallet,
            isRemovingWallet = isRemovingWallet,
            removeWalletModalVisible = removeWalletModalVisible,
            closeRemoveWalletModal = closeRemoveWalletModal,
            openRemoveWalletModal = openRemoveWalletModal,
            refresh = refresh,
            isRefreshing = isRefreshing
        )
    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletContentScaffold(
    navController: NavController,
    refresh: (Boolean) -> Unit,
    isRefreshing: Boolean,
    isCircleWallet: Boolean,
    content: @Composable () -> Unit,
) {

    val refreshState = rememberPullToRefreshState()

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

        Box(
            modifier = Modifier.padding(innerPadding)
        ) {
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                state = refreshState,
                onRefresh = {
                    refresh(isCircleWallet)
                            },
            ) {
                content()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CircleWalletScaffold(
    navController: NavController,
    walletId: Id?,
    walletAddress: String?,
    isPayoutWallet: Boolean,
    blockchain: Blockchain?,
    payouts: List<AccountPayment>,
    setPayoutWallet: (Id) -> Unit,
    isSettingPayoutWallet: Boolean,
    removeWallet: (Id) -> Unit,
    isRemovingWallet: Boolean,
    removeWalletModalVisible: Boolean,
    closeRemoveWalletModal: () -> Unit,
    walletBalance: Double,
    setCircleWalletBalance: (Double) -> Unit,
    launchOverlay: (OverlayMode) -> Unit,
    refresh: (Boolean) -> Unit,
    isRefreshing: Boolean,
    circleViewModel: CircleTransferViewModel = hiltViewModel()
) {
    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(
            initialValue = SheetValue.Hidden,
            skipHiddenState = false
        ),
    )
    val scope = rememberCoroutineScope()

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetShape = RoundedCornerShape(
            0.dp,
        ),
        sheetContainerColor = Black,
        sheetContentColor = Black,
        sheetPeekHeight = 0.dp,
        sheetDragHandle = {},
        sheetContent = {
            CircleTransferSheet(
                scaffoldState = scaffoldState,
                scope = scope,
                transferAmountTextFieldValue = circleViewModel.transferAmountTextFieldValue,
                setTransferAmountFieldValue = circleViewModel.setTransferAmount,
                sendToAddress = circleViewModel.sendToAddress,
                setSendToAddress = circleViewModel.setSendToAddress,
                walletBalance = walletBalance,
                isSendToAddressValid = circleViewModel.isSendToAddressValid,
                isSendToAddressValidating = circleViewModel.isSendToAddressValidating,
                setTransferError = circleViewModel.setTransferError,
                setTransferInProgress = circleViewModel.setTransferInProgress,
                transfer = circleViewModel.transfer,
                setCircleWalletBalance = setCircleWalletBalance,
                transferInProgress = circleViewModel.transferInProgress,
                transferAmountValid = circleViewModel.transferAmountValid,
                launchOverlay = launchOverlay
            )
        }
    ) {
        CircleWalletScreenContent(
            navController = navController,
            walletId = walletId,
            walletAddress = walletAddress,
            isPayoutWallet = isPayoutWallet,
            blockchain = blockchain,
            isCircleWallet = true,
            payouts = payouts,
            setPayoutWallet = setPayoutWallet,
            isSettingPayoutWallet = isSettingPayoutWallet,
            removeWallet = removeWallet,
            isRemovingWallet = isRemovingWallet,
            removeWalletModalVisible = removeWalletModalVisible,
            closeRemoveWalletModal = closeRemoveWalletModal,
            walletBalance = walletBalance,
            scaffoldState = scaffoldState,
            scope = scope,
            setSendToAddress = circleViewModel.setSendToAddress,
            setTransferAmountFieldValue = circleViewModel.setTransferAmount,
            refresh = refresh,
            isRefreshing = isRefreshing
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CircleWalletScreenContent(
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
    walletBalance: Double,
    scope: CoroutineScope,
    scaffoldState: BottomSheetScaffoldState,
    setSendToAddress: (TextFieldValue) -> Unit,
    setTransferAmountFieldValue: (TextFieldValue, Double) -> Unit,
    refresh: (Boolean) -> Unit,
    isRefreshing: Boolean,
) {
    WalletContentScaffold(
        navController,
        refresh,
        isRefreshing,
        isCircleWallet
    ) {

        LazyColumn(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxSize()
        ) {

            item {

                Column {

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
                                "Circle Wallet",
                                style = MaterialTheme.typography.headlineSmall
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            MaskedWalletAddress(walletAddress)

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
                                stringResource(id = R.string.make_default),
                                style = buttonTextStyle
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // circle wallet balance
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MainTintedBackgroundBase, shape = RoundedCornerShape(8.dp))
                            .padding(
                                start = 16.dp,
                                top = 16.dp,
                                bottom = 10.dp, // accounting for line-height issue
                                end = 16.dp
                            )
                    ) {
                        Text(
                            stringResource(id = R.string.balance),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted
                        )
                        Row(
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Text(
                                formatDecimalString(walletBalance, 2),
                                style = HeadingLargeCondensed
                            )

                            Spacer(modifier = Modifier.width(2.dp))

                            // this is really hacky, but setting line-height isn't being acknowledged
                            Box(
                                modifier = Modifier.offset(y = -(11).dp)
                            ) {
                                Text(
                                    "USDC",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextMuted
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    URButton(
                        onClick = {
                            scope.launch {
                                scaffoldState.bottomSheetState.expand()
                                setSendToAddress(TextFieldValue(""))
                                setTransferAmountFieldValue(TextFieldValue(""), walletBalance)
                            }
                        }
                    ) { buttonTextStyle ->
                        Text(
                            stringResource(id = R.string.transfer_funds),
                            style = buttonTextStyle
                        )
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                }

            }

            item {
                PayoutsList(
                    payouts,
                    walletAddress
                )
            }

        }
    }

    RemoveWalletDialog(
        navController,
        removeWalletModalVisible,
        closeRemoveWalletModal,
        walletId,
        removeWallet,
        isRemovingWallet
    )
}

@Composable
fun PayoutsList(
    payouts: List<AccountPayment>,
    walletAddress: String?,
) {

    if (payouts.isNotEmpty()) {

        Text(
            stringResource(id = R.string.earnings),
            style = MaterialTheme.typography.bodyLarge
        )

        Spacer(modifier = Modifier.height(8.dp))

        Column {

            for (payout in payouts) {

                PayoutRow(
                    walletAddress = walletAddress ?: "",
                    completeTime = if (payout.completeTime != null) payout.completeTime.format("Jan 2") else null,
                    amountUsd = payout.tokenAmount
                )
            }
        }

    } else {
        NoPayoutsFound()
    }
}

@Composable
fun RemoveWalletDialog(
    navController: NavController,
    removeWalletModalVisible: Boolean,
    closeRemoveWalletModal: () -> Unit,
    walletId: Id?,
    removeWallet: (Id) -> Unit,
    isRemovingWallet: Boolean
) {
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

@Composable
fun ExternalWalletScreenContent(
    navController: NavController,
    walletId: Id?,
    walletAddress: String?,
    isPayoutWallet: Boolean,
    blockchain: Blockchain?,
    payouts: List<AccountPayment>,
    setPayoutWallet: (Id) -> Unit,
    isSettingPayoutWallet: Boolean,
    removeWallet: (Id) -> Unit,
    isRemovingWallet: Boolean,
    removeWalletModalVisible: Boolean,
    closeRemoveWalletModal: () -> Unit,
    openRemoveWalletModal: () -> Unit,
    refresh: (Boolean) -> Unit,
    isRefreshing: Boolean,
) {

    WalletContentScaffold(
        navController,
        refresh,
        isRefreshing,
        isCircleWallet = false
    ) {

        LazyColumn(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxSize()
        ) {

            item {
                Column {

                    Row(
                        modifier = Modifier.fillMaxWidth()
                    ) {

                        WalletChainIcon(
                            blockchain = blockchain,
                            isCircleWallet = false
                        )

                        Spacer(modifier = Modifier.width(16.dp))

                        Column {
                            Text(
                                "${blockchain.toString().lowercase().capitalize()} Wallet",
                                style = MaterialTheme.typography.headlineSmall
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            MaskedWalletAddress(walletAddress)

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
                                stringResource(id = R.string.make_default),
                                style = buttonTextStyle
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

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
                                stringResource(id = R.string.remove),
                                style = buttonTextStyle
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                }

            }

            item {
                PayoutsList(
                    payouts,
                    walletAddress
                )
            }

        }

    }

    RemoveWalletDialog(
        navController,
        removeWalletModalVisible,
        closeRemoveWalletModal,
        walletId,
        removeWallet,
        isRemovingWallet
    )
}

@Composable
fun MaskedWalletAddress(
    walletAddress: String?
) {
    Text(
        "***${walletAddress?.takeLast(7)}",
        style = TextStyle(
            fontSize = 20.sp,
            fontFamily = ppNeueBitBold
        )
    )
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
            closeRemoveWalletModal = {},
            circleWalletBalance = 1.1,
            setCircleWalletBalance = {},
            launchOverlay = {},
            refresh = {},
            isRefreshing = false
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
            isCircleWallet = false,
            blockchain = Blockchain.POLYGON,
            payouts = listOf(),
            setPayoutWallet = {},
            isSettingPayoutWallet = false,
            removeWallet = {},
            isRemovingWallet = false,
            removeWalletModalVisible = false,
            openRemoveWalletModal = {},
            closeRemoveWalletModal = {},
            circleWalletBalance = 1.1,
            setCircleWalletBalance = {},
            launchOverlay = {},
            refresh = {},
            isRefreshing = false
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
            isCircleWallet = false,
            blockchain = Blockchain.POLYGON,
            payouts = listOf(),
            setPayoutWallet = {},
            isSettingPayoutWallet = false,
            removeWallet = {},
            isRemovingWallet = false,
            removeWalletModalVisible = true,
            openRemoveWalletModal = {},
            closeRemoveWalletModal = {},
            circleWalletBalance = 1.1,
            setCircleWalletBalance = {},
            launchOverlay = {},
            refresh = {},
            isRefreshing = false
        )
    }
}