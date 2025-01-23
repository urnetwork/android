package com.bringyour.network.ui.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.rememberNavController
import com.bringyour.sdk.AccountWallet
import com.bringyour.network.ui.components.URDialog
import com.bringyour.network.ui.components.URTextInput
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.BlueMedium
import com.bringyour.network.ui.theme.HeadingLargeCondensed
import com.bringyour.network.ui.theme.MainTintedBackgroundBase
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.TopBarTitleTextStyle
import com.bringyour.network.ui.theme.URNetworkTheme
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.bringyour.sdk.AccountPayment
import com.bringyour.sdk.Id
import com.bringyour.network.R
import com.bringyour.network.ui.components.InfoIconWithOverlay
import com.bringyour.network.ui.theme.BlueLight

@Composable
fun WalletsScreen(
    navController: NavHostController,
    walletViewModel: WalletViewModel,
) {

    LaunchedEffect(Unit) {
        walletViewModel.fetchTransferStats()
    }

    WalletsScreen(
        navController,
        connectSagaWallet = walletViewModel.connectSagaWallet,
        addExternalWalletModalVisible = walletViewModel.addExternalWalletModalVisible,
        openExternalWalletModal = walletViewModel.openExternalWalletModal,
        closeModal = walletViewModel.closeExternalWalletModal,
        wallets = walletViewModel.wallets,
        externalWalletAddress = walletViewModel.externalWalletAddress,
        setExternalWalletAddress = walletViewModel.setExternaWalletAddress,
        walletValidationState = walletViewModel.externalWalletAddressIsValid,
        linkWallet = walletViewModel.linkWallet,
        isProcessingExternalWallet = walletViewModel.isProcessingExternalWallet,
        payoutWalletId = walletViewModel.payoutWalletId,
        isInitializingFirstWallet = walletViewModel.initializingFirstWallet,
        setInitializingFirstWallet = walletViewModel.setInitializingFirstWallet,
        payouts = walletViewModel.payouts,
        isRemovingWallet = walletViewModel.isRemovingWallet,
        initializingWallets = walletViewModel.initializingWallets,
        unpaidMegaByteCount = walletViewModel.unpaidMegaByteCount,
        refresh = walletViewModel.refreshWalletsInfo,
        isRefreshing = walletViewModel.isRefreshingWallets
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletsScreen(
    navController: NavHostController,
    connectSagaWallet: () -> Unit,
    addExternalWalletModalVisible: Boolean,
    openExternalWalletModal: () -> Unit,
    closeModal: () -> Unit,
    externalWalletAddress: TextFieldValue,
    setExternalWalletAddress: (TextFieldValue) -> Unit,
    walletValidationState: WalletValidationState,
    linkWallet: () -> Unit,
    isProcessingExternalWallet: Boolean,
    payoutWalletId: Id?,
    isInitializingFirstWallet: Boolean,
    wallets: List<AccountWallet>,
    setInitializingFirstWallet: (Boolean) -> Unit,
    payouts: List<AccountPayment>,
    isRemovingWallet: Boolean,
    initializingWallets: Boolean,
    unpaidMegaByteCount: String,
    refresh: () -> Unit,
    isRefreshing: Boolean,
    viewModel: WalletsScreenViewModel = hiltViewModel()
) {

    val refreshState = rememberPullToRefreshState()

    val connectWalletSheetState = rememberModalBottomSheetState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(id = R.string.payout_wallets),
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

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                state = refreshState,
                onRefresh = refresh,
            ) {

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
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
                                    stringResource(id = R.string.unpaid_mb),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextMuted
                                )
                                Row(
                                    verticalAlignment = Alignment.Bottom
                                ) {
                                    Text(
                                        unpaidMegaByteCount,
                                        style = HeadingLargeCondensed
                                    )

                                    Spacer(modifier = Modifier.width(2.dp))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row {
                            Text(
                                stringResource(id = R.string.payouts_amount_threshold),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted
                            )
                        }

                        Spacer(modifier = Modifier.height(32.dp))

                    }

                    if (isInitializingFirstWallet) {

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.width(24.dp),
                                color = MaterialTheme.colorScheme.secondary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            )
                        }

                    } else {

                        if (initializingWallets) {

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.width(24.dp),
                                    color = MaterialTheme.colorScheme.secondary,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                )
                            }

                        } else {
                            if (wallets.isEmpty()) {
                                Column(
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    SetupWallet(
                                        connectSagaWallet = connectSagaWallet,
                                        openModal = {
                                            viewModel.setIsPresentedConnectWalletSheet(true)
                                        }
                                        // openModal = openExternalWalletModal,
                                    )
                                }
                            } else {

                                Row(
                                    modifier = Modifier
                                        .padding(16.dp)
                                        .fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row {
                                        Text(
                                            stringResource(id = R.string.wallets),
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                        Spacer(modifier = Modifier.width(2.dp))

                                        InfoIconWithOverlay() {
                                            Column() {
                                                Text(
                                                    stringResource(id = R.string.chains_supported),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = BlueLight
                                                )
                                            }
                                        }
                                    }

                                    AddWallet(
                                        connectSagaWallet = connectSagaWallet,
                                        openExternalWalletModal = openExternalWalletModal
                                    )

                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                if (isRemovingWallet) {
                                    Row(
                                        modifier = Modifier
                                            .height(124.dp)
                                            .fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.width(24.dp),
                                            color = MaterialTheme.colorScheme.secondary,
                                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                        )
                                    }
                                } else {
                                    LazyRow(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(124.dp)
                                    ) {

                                        item {
                                            Spacer(modifier = Modifier.width(16.dp))
                                        }

                                        items(wallets) { wallet ->

                                            WalletCard(
                                                isCircleWallet = !wallet.circleWalletId.isNullOrEmpty(),
                                                blockchain = Blockchain.fromString(wallet.blockchain),
                                                isPayoutWallet = wallet.walletId.equals(payoutWalletId),
                                                walletAddress = wallet.walletAddress,
                                                walletId = wallet.walletId,
                                                navController = navController
                                            )

                                            Spacer(modifier = Modifier.width(16.dp))

                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(32.dp))

                                Column(
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    WalletsPayoutsList(
                                        payouts,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        /**
         * Connect wallet sheet
         */
        if (viewModel.isPresentedConnectWalletSheet) {
            ConnectWalletSheet(
                setIsPresentedConnectWalletSheet = viewModel.setIsPresentedConnectWalletSheet,
                connectWalletSheetState = connectWalletSheetState,
                externalWalletAddress = externalWalletAddress,
                setExternalWalletAddress = setExternalWalletAddress,
                onSubmit = {
                    if ((walletValidationState.solana || walletValidationState.polygon) && !isProcessingExternalWallet) {
                        linkWallet()
                        // viewModel.setIsPresentedConnectWalletSheet(false)
                    }
                },
                walletValidationState = walletValidationState,
                isProcessingWallet = isProcessingExternalWallet
            )
        }

        /**
         * todo: deprecate
         */
        URDialog(
            visible = addExternalWalletModalVisible,
            onDismiss = { closeModal() }
        ) {
            Column() {
                Text(
                    stringResource(id = R.string.connect_external_wallet),
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    stringResource(id = R.string.connect_external_wallet_supported_chains),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                URTextInput(
                    value = externalWalletAddress,
                    onValueChange = { newValue ->
                        setExternalWalletAddress(newValue)
                                    },
                    label = stringResource(id = R.string.wallet_address_label),
                    placeholder = stringResource(id = R.string.wallet_address_placeholder),
                    maxLines = 2
                )

                Spacer(modifier = Modifier.height(32.dp))


                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = AnnotatedString(
                            stringResource(id = R.string.cancel),
                            spanStyle = SpanStyle(
                                color = BlueMedium,
                                fontSize = 14.sp
                            )
                        ),
                        modifier = Modifier.clickable {
                            closeModal()
                            setExternalWalletAddress(TextFieldValue(""))
                        },
                    )

                    Spacer(modifier = Modifier.width(32.dp))

                    Text(
                        text = AnnotatedString(
                            stringResource(id = R.string.connect),
                            spanStyle = if ((walletValidationState.solana || walletValidationState.polygon) && !isProcessingExternalWallet)
                                SpanStyle(
                                    color = BlueMedium,
                                    fontSize = 14.sp
                                ) else
                                SpanStyle(
                                    color = TextMuted,
                                    fontSize = 14.sp
                                )
                        ),
                        modifier = Modifier.clickable {
                            if ((walletValidationState.solana || walletValidationState.polygon) && !isProcessingExternalWallet) {
                                linkWallet()
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
        WalletsScreen(
            navController,
            connectSagaWallet = {},
            addExternalWalletModalVisible = false,
            openExternalWalletModal = {},
            closeModal = {},
            wallets = listOf(),
            externalWalletAddress = TextFieldValue(""),
            setExternalWalletAddress = {},
            walletValidationState = WalletValidationState(),
            linkWallet = {},
            isProcessingExternalWallet = false,
            payoutWalletId = null,
            isInitializingFirstWallet = false,
            setInitializingFirstWallet = {},
            payouts = listOf(),
            isRemovingWallet = false,
            initializingWallets = false,
            unpaidMegaByteCount = "124.64",
            refresh = {},
            isRefreshing = false
        )
    }
}

@Preview
@Composable
private fun WalletScreenSagaPreview() {

    val navController = rememberNavController()

    URNetworkTheme {
        WalletsScreen(
            navController,
            connectSagaWallet = {},
            addExternalWalletModalVisible = false,
            openExternalWalletModal = {},
            closeModal = {},
            wallets = listOf(),
            externalWalletAddress = TextFieldValue(""),
            setExternalWalletAddress = {},
            walletValidationState = WalletValidationState(),
            linkWallet = {},
            isProcessingExternalWallet = false,
            payoutWalletId = null,
            isInitializingFirstWallet = false,
            setInitializingFirstWallet = {},
            payouts = listOf(),
            isRemovingWallet = false,
            initializingWallets = false,
            unpaidMegaByteCount = "124.64",
            refresh = {},
            isRefreshing = false
        )
    }
}

@Preview
@Composable
private fun WalletScreenExternalWalletModalPreview() {

    val navController = rememberNavController()

    URNetworkTheme {
        WalletsScreen(
            navController,
            connectSagaWallet = {},
            addExternalWalletModalVisible = true,
            openExternalWalletModal = {},
            closeModal = {},
            wallets = listOf(),
            externalWalletAddress = TextFieldValue(""),
            setExternalWalletAddress = {},
            walletValidationState = WalletValidationState(),
            linkWallet = {},
            isProcessingExternalWallet = false,
            payoutWalletId = null,
            isInitializingFirstWallet = false,
            setInitializingFirstWallet = {},
            payouts = listOf(),
            isRemovingWallet = false,
            initializingWallets = false,
            unpaidMegaByteCount = "124.64",
            refresh = {},
            isRefreshing = false
        )
    }
}

@Preview
@Composable
private fun WalletScreenInitializingWalletPreview() {

    val navController = rememberNavController()

    URNetworkTheme {
        WalletsScreen(
            navController,
            connectSagaWallet = {},
            addExternalWalletModalVisible = false,
            openExternalWalletModal = {},
            closeModal = {},
            wallets = listOf(),
            externalWalletAddress = TextFieldValue(""),
            setExternalWalletAddress = {},
            walletValidationState = WalletValidationState(),
            linkWallet = {},
            isProcessingExternalWallet = false,
            payoutWalletId = null,
            isInitializingFirstWallet = true,
            setInitializingFirstWallet = {},
            payouts = listOf(),
            isRemovingWallet = false,
            initializingWallets = false,
            unpaidMegaByteCount = "124.64",
            refresh = {},
            isRefreshing = false
        )
    }
}

@Preview
@Composable
private fun WalletScreenRemovingWalletPreview() {

    val navController = rememberNavController()

    URNetworkTheme {
        WalletsScreen(
            navController,
            connectSagaWallet = {},
            addExternalWalletModalVisible = false,
            openExternalWalletModal = {},
            closeModal = {},
            wallets = listOf(),
            externalWalletAddress = TextFieldValue(""),
            setExternalWalletAddress = {},
            walletValidationState = WalletValidationState(),
            linkWallet = {},
            isProcessingExternalWallet = false,
            payoutWalletId = null,
            isInitializingFirstWallet = true,
            setInitializingFirstWallet = {},
            payouts = listOf(),
            isRemovingWallet = true,
            initializingWallets = false,
            unpaidMegaByteCount = "124.64",
            refresh = {},
            isRefreshing = false
        )
    }
}

