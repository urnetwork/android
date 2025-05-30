package com.bringyour.network.ui.wallet

import android.net.Uri
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
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.bringyour.sdk.AccountWallet
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.HeadingLargeCondensed
import com.bringyour.network.ui.theme.MainTintedBackgroundBase
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.TopBarTitleTextStyle
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.bringyour.sdk.AccountPayment
import com.bringyour.sdk.Id
import com.bringyour.network.R
import com.bringyour.network.ui.components.InfoIconWithOverlay
import com.bringyour.network.ui.components.overlays.OverlayMode
import com.bringyour.network.ui.login.NoSolanaWalletsAlert
import com.bringyour.network.ui.shared.viewmodels.OverlayViewModel
import com.bringyour.network.ui.shared.viewmodels.ReferralCodeViewModel
import com.bringyour.network.ui.theme.BlueLight
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.solana.mobilewalletadapter.clientlib.ConnectionIdentity
import com.solana.mobilewalletadapter.clientlib.MobileWalletAdapter
import com.solana.mobilewalletadapter.clientlib.Solana
import com.solana.mobilewalletadapter.clientlib.TransactionResult
import com.solana.publickey.SolanaPublicKey
import kotlinx.coroutines.launch

@Composable
fun WalletsScreen(
    navController: NavHostController,
    walletViewModel: WalletViewModel,
    activityResultSender: ActivityResultSender?,
    referralCodeViewModel: ReferralCodeViewModel,
    overlayViewModel: OverlayViewModel,
    totalAccountPoints: Int,
    fetchAccountPoints: () -> Unit?
) {

    val wallets by walletViewModel.wallets.collectAsState()
    val payouts by walletViewModel.payouts.collectAsState()

    LaunchedEffect(Unit) {
        walletViewModel.fetchTransferStats()
    }

    WalletsScreen(
        navController,
        connectSagaWallet = walletViewModel.connectSagaWallet,
        wallets = wallets,
        externalWalletAddress = walletViewModel.externalWalletAddress,
        setExternalWalletAddress = walletViewModel.setExternaWalletAddress,
        walletValidationState = walletViewModel.externalWalletAddressIsValid,
        linkWallet = walletViewModel.linkWallet,
        isProcessingExternalWallet = walletViewModel.isProcessingExternalWallet,
        payoutWalletId = walletViewModel.payoutWalletId,
        isInitializingFirstWallet = walletViewModel.initializingFirstWallet,
        setInitializingFirstWallet = walletViewModel.setInitializingFirstWallet,
        payouts = payouts,
        isRemovingWallet = walletViewModel.isRemovingWallet,
        initializingWallets = walletViewModel.initializingWallets,
        unpaidMegaByteCount = walletViewModel.unpaidMegaByteCount,
        refresh = {
            walletViewModel.refreshWalletsInfo()
            referralCodeViewModel.fetchReferralLink()
            fetchAccountPoints()
                  },
        isRefreshing = walletViewModel.isRefreshingWallets,
        setExternalWalletAddressIsValid = walletViewModel.setExternalWalletAddressIsValid,
        activityResultSender = activityResultSender,
        totalReferrals = referralCodeViewModel.totalReferralCount,
        isSeekerHolder = walletViewModel.isSeekerHolder.collectAsState().value,
        launchOverlay = overlayViewModel.launch,
        totalAccountPoints = totalAccountPoints,
        fetchAccountPoints = fetchAccountPoints
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletsScreen(
    navController: NavHostController,
    connectSagaWallet: () -> Unit,
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
    activityResultSender: ActivityResultSender?,
    setExternalWalletAddressIsValid: (chain: String, isValid: Boolean) -> Unit,
    isSeekerHolder: Boolean,
    totalReferrals: Long,
    launchOverlay: (OverlayMode) -> Unit,
    totalAccountPoints: Int,
    fetchAccountPoints: () -> Unit?,
    viewModel: WalletsScreenViewModel = hiltViewModel()
) {

    val refreshState = rememberPullToRefreshState()

    val connectWalletSheetState = rememberModalBottomSheetState()

    val scope = rememberCoroutineScope()

    val solanaUri = Uri.parse("https://ur.io")
    val iconUri = Uri.parse("favicon.ico")
    val identityName = "URnetwork"

    var noSolanaWalletsFound by remember { mutableStateOf(false) }

    val connectSolanaWallet = {

        scope.launch {

            // `connect` dispatches an association intent to MWA-compatible wallet apps.
            activityResultSender?.let { activityResultSender ->

                // Instantiate the MWA client object
                val walletAdapter = MobileWalletAdapter(
                    connectionIdentity = ConnectionIdentity(
                        identityUri = solanaUri,
                        iconUri = iconUri,
                        identityName = identityName,
                    ),
                )
                walletAdapter.blockchain = Solana.Mainnet

                when (val result = walletAdapter.connect(activityResultSender)) {
                    is TransactionResult.Success -> {
                        val authResult = result.authResult
                        val account = SolanaPublicKey(authResult.accounts.first().publicKey)

                        setExternalWalletAddress(TextFieldValue(account.base58()))
                        // setExternalWalletAddress(TextFieldValue(address))
                        // since this is taken directly from the solana mobile adapter,
                        // we can mark this as true without calling our API to validate
                        setExternalWalletAddressIsValid("SOL", true)

                        linkWallet()

                    }

                    is TransactionResult.NoWalletFound -> {
                        println("No MWA compatible wallet app found on device.")
                        noSolanaWalletsFound = true
                    }

                    is TransactionResult.Failure -> {
                        println("Error connecting to wallet: " + result.e.message)
                    }
                }
            }

        }
    }

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

                                HorizontalDivider()

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    /**
                                     * Total referrals
                                     */
                                    Column {
                                        Text(
                                            stringResource(id = R.string.total_referrals),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = TextMuted
                                        )

                                        Row(
                                            verticalAlignment = Alignment.Bottom
                                        ) {
                                            Text(
                                                "$totalReferrals",
                                                style = HeadingLargeCondensed,
                                                modifier = Modifier.clickable {
                                                    launchOverlay(OverlayMode.Refer)
                                                }
                                            )

                                            // Spacer(modifier = Modifier.width(2.dp))
                                        }
                                    }

                                    /**
                                     *  Total Account Points
                                     */
                                    Column {
                                        Text(
                                            stringResource(id = R.string.total_account_points),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = TextMuted
                                        )

                                        Row(
                                            verticalAlignment = Alignment.Bottom
                                        ) {
                                            Text(
                                                "$totalAccountPoints",
                                                style = HeadingLargeCondensed,
//                                                modifier = Modifier.clickable {
//                                                  // todo: navigate to account points screen
//                                                }
                                            )

                                            // Spacer(modifier = Modifier.width(2.dp))
                                        }
                                    }

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
                                        openExternalConnectModal = {
                                            viewModel.setIsPresentedConnectWalletSheet(true)
                                        },
                                        openSolanaConnectModal = {
                                            connectSolanaWallet()
                                        }
                                    )
                                }
                            } else {

                                /**
                                 * Wallets Populated
                                 */

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
                                        openSolanaConnectModal = {
                                            connectSolanaWallet()
                                        },
                                        openExternalConnectModal = {
                                            viewModel.setIsPresentedConnectWalletSheet(true)
                                        },
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
                                                blockchain = Blockchain.fromString(wallet.blockchain),
                                                isPayoutWallet = wallet.walletId.equals(
                                                    payoutWalletId
                                                ),
                                                walletAddress = wallet.walletAddress,
                                                walletId = wallet.walletId,
                                                navController = navController,
                                                payouts = payouts
                                            )

                                            Spacer(modifier = Modifier.width(16.dp))

                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(32.dp))

                                if (isSeekerHolder) {

                                    /**
                                     * Seeker Token Holder handling
                                     */
                                    Column(
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                    ) {
                                        Text(
                                            stringResource(id = R.string.claim_multiplier)
                                        )


                                        Spacer(modifier = Modifier.height(8.dp))

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.point_multiplier),
                                                contentDescription = "Earning double points",
                                                tint = Color.Unspecified,
                                                modifier = Modifier
                                                    .width(36.dp)
                                                // .padding(12.dp)
                                            )

                                            Spacer(modifier = Modifier.width(12.dp))

                                            Text(
                                                stringResource(id = R.string.seeker_token_verified),
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                    }

                                    Spacer(modifier = Modifier.height(32.dp))

                                }

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
            ManualWalletAddressSheet(
                setIsPresentedConnectWalletSheet = viewModel.setIsPresentedConnectWalletSheet,
                connectWalletSheetState = connectWalletSheetState,
                externalWalletAddress = externalWalletAddress,
                setExternalWalletAddress = setExternalWalletAddress,
                onSubmit = {
                    if ((walletValidationState.solana || walletValidationState.polygon) && !isProcessingExternalWallet) {
                        linkWallet()
                        viewModel.setIsPresentedConnectWalletSheet(false)
                    }
                },
                walletValidationState = walletValidationState,
                isProcessingWallet = isProcessingExternalWallet
            )
        }

        if (noSolanaWalletsFound) {

            NoSolanaWalletsAlert(
                onDismiss = {
                    noSolanaWalletsFound = false
                }
            )

        }
    }
}

// passing ActivityResultSender breaks previews

//@Preview
//@Composable
//private fun WalletScreenPreview() {
//
//    val navController = rememberNavController()
//
//    URNetworkTheme {
//        WalletsScreen(
//            navController,
//            connectSagaWallet = {},
//            addExternalWalletModalVisible = false,
//            openExternalWalletModal = {},
//            closeModal = {},
//            wallets = listOf(),
//            externalWalletAddress = TextFieldValue(""),
//            setExternalWalletAddress = {},
//            walletValidationState = WalletValidationState(),
//            linkWallet = {},
//            isProcessingExternalWallet = false,
//            payoutWalletId = null,
//            isInitializingFirstWallet = false,
//            setInitializingFirstWallet = {},
//            payouts = listOf(),
//            isRemovingWallet = false,
//            initializingWallets = false,
//            unpaidMegaByteCount = "124.64",
//            refresh = {},
//            isRefreshing = false,
//            activityResultSender = ActivityResultSender(null)
//        )
//    }
//}
//
//@Preview
//@Composable
//private fun WalletScreenSagaPreview() {
//
//    val navController = rememberNavController()
//
//    URNetworkTheme {
//        WalletsScreen(
//            navController,
//            connectSagaWallet = {},
//            addExternalWalletModalVisible = false,
//            openExternalWalletModal = {},
//            closeModal = {},
//            wallets = listOf(),
//            externalWalletAddress = TextFieldValue(""),
//            setExternalWalletAddress = {},
//            walletValidationState = WalletValidationState(),
//            linkWallet = {},
//            isProcessingExternalWallet = false,
//            payoutWalletId = null,
//            isInitializingFirstWallet = false,
//            setInitializingFirstWallet = {},
//            payouts = listOf(),
//            isRemovingWallet = false,
//            initializingWallets = false,
//            unpaidMegaByteCount = "124.64",
//            refresh = {},
//            isRefreshing = false
//        )
//    }
//}
//
//@Preview
//@Composable
//private fun WalletScreenExternalWalletModalPreview() {
//
//    val navController = rememberNavController()
//
//    URNetworkTheme {
//        WalletsScreen(
//            navController,
//            connectSagaWallet = {},
//            addExternalWalletModalVisible = true,
//            openExternalWalletModal = {},
//            closeModal = {},
//            wallets = listOf(),
//            externalWalletAddress = TextFieldValue(""),
//            setExternalWalletAddress = {},
//            walletValidationState = WalletValidationState(),
//            linkWallet = {},
//            isProcessingExternalWallet = false,
//            payoutWalletId = null,
//            isInitializingFirstWallet = false,
//            setInitializingFirstWallet = {},
//            payouts = listOf(),
//            isRemovingWallet = false,
//            initializingWallets = false,
//            unpaidMegaByteCount = "124.64",
//            refresh = {},
//            isRefreshing = false
//        )
//    }
//}
//
//@Preview
//@Composable
//private fun WalletScreenInitializingWalletPreview() {
//
//    val navController = rememberNavController()
//
//    URNetworkTheme {
//        WalletsScreen(
//            navController,
//            connectSagaWallet = {},
//            addExternalWalletModalVisible = false,
//            openExternalWalletModal = {},
//            closeModal = {},
//            wallets = listOf(),
//            externalWalletAddress = TextFieldValue(""),
//            setExternalWalletAddress = {},
//            walletValidationState = WalletValidationState(),
//            linkWallet = {},
//            isProcessingExternalWallet = false,
//            payoutWalletId = null,
//            isInitializingFirstWallet = true,
//            setInitializingFirstWallet = {},
//            payouts = listOf(),
//            isRemovingWallet = false,
//            initializingWallets = false,
//            unpaidMegaByteCount = "124.64",
//            refresh = {},
//            isRefreshing = false
//        )
//    }
//}
//
//@Preview
//@Composable
//private fun WalletScreenRemovingWalletPreview() {
//
//    val navController = rememberNavController()
//
//    URNetworkTheme {
//        WalletsScreen(
//            navController,
//            connectSagaWallet = {},
//            addExternalWalletModalVisible = false,
//            openExternalWalletModal = {},
//            closeModal = {},
//            wallets = listOf(),
//            externalWalletAddress = TextFieldValue(""),
//            setExternalWalletAddress = {},
//            walletValidationState = WalletValidationState(),
//            linkWallet = {},
//            isProcessingExternalWallet = false,
//            payoutWalletId = null,
//            isInitializingFirstWallet = true,
//            setInitializingFirstWallet = {},
//            payouts = listOf(),
//            isRemovingWallet = true,
//            initializingWallets = false,
//            unpaidMegaByteCount = "124.64",
//            refresh = {},
//            isRefreshing = false
//        )
//    }
//}

