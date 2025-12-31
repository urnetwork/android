package com.bringyour.network.ui.connect

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.BottomSheetScaffoldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.bringyour.network.MainApplication
import com.bringyour.network.R
import com.bringyour.sdk.ConnectGrid
import com.bringyour.sdk.ConnectLocation
import com.bringyour.sdk.Id
import com.bringyour.sdk.ProviderGridPoint
import com.bringyour.network.ui.Route
import com.bringyour.network.ui.account.AccountViewModel
import com.bringyour.network.ui.components.LoginMode
import com.bringyour.network.ui.components.PromptSolanaDAppStoreReview
import com.bringyour.network.ui.components.overlays.OverlayMode
import com.bringyour.network.ui.shared.managers.rememberReviewManager
import com.bringyour.network.ui.shared.models.BundleStore
import com.bringyour.network.ui.shared.models.ConnectStatus
import com.bringyour.network.ui.shared.viewmodels.OverlayViewModel
import com.bringyour.network.ui.shared.viewmodels.Plan
import com.bringyour.network.ui.shared.viewmodels.PlanViewModel
import com.bringyour.network.ui.shared.viewmodels.SubscriptionBalanceViewModel
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.SheetBlack
import com.bringyour.sdk.ContractStatus
import com.bringyour.sdk.DeviceLocal
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectScreen(
    connectViewModel: ConnectViewModel,
    overlayViewModel: OverlayViewModel,
    locationsViewModel: LocationsListViewModel,
    navController: NavController,
    subscriptionBalanceViewModel: SubscriptionBalanceViewModel,
    planViewModel: PlanViewModel,
    bundleStore: BundleStore?,
    meanReliabilityWeight: Double,
    totalReferrals: Long,
    launchIntro: () -> Unit,
    isPro: Boolean,
    connectActionsSheetState: SheetState,
    accountViewModel: AccountViewModel = hiltViewModel<AccountViewModel>(),
) {

    val connectStatus by connectViewModel.connectStatus.collectAsState()
    val contractStatus by connectViewModel.contractStatus.collectAsState()
//    val currentPlan by subscriptionBalanceViewModel.currentPlan.collectAsState()

    val networkUser by accountViewModel.networkUser.collectAsState()

    val displayInsufficientBalance = contractStatus?.insufficientBalance == true && !isPro

    var promptSolanaReview by remember { mutableStateOf(false) }

    val setPromptSolanaReview: (Boolean) -> Unit = {
        promptSolanaReview = it
    }

    val reviewManagerRequest = rememberReviewManager()
    val context = LocalContext.current
    val application = context.applicationContext as? MainApplication

    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = connectActionsSheetState
    )

    val promptReview = {
        val activity = context as? android.app.Activity
        activity?.let {
            reviewManagerRequest.launchReviewFlow(
                activity = it,
//                bundleStore
            )
        }
    }

    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {

        connectViewModel.refreshContractStatus()

    }

    LaunchedEffect(connectStatus) {

        if (connectStatus == ConnectStatus.DISCONNECTED || connectStatus == ConnectStatus.CONNECTED) {

            if (connectViewModel.device?.shouldShowRatingDialog == true) {

                scope.launch {
                    connectViewModel.device?.canShowRatingDialog = false
                    delay(2000)

                    promptReview()

                }

            }

        }

    }

    ConnectActionsSheetScaffold(
        scaffoldState = scaffoldState,
        sheetContent = { minSheetHeight ->
            ConnectActions(
                navController = navController,
                selectedLocation = connectViewModel.selectedLocation,
                presentSelectProvider = {
                    navController.navigate(Route.BrowseLocations)
                },
                getLocationColor = locationsViewModel.getLocationColor,
                minHeight = minSheetHeight,
                currentPlan = if (isPro) Plan.Supporter else Plan.Basic,
                connect = { connectViewModel.connect(connectViewModel.selectedLocation) },
                disconnect = connectViewModel.disconnect,
                reconnectTunnel = {
                    application?.startVpnService()
                },
                connectStatus = connectStatus,
                isPollingSubscriptionBalance = subscriptionBalanceViewModel.isPollingSubscriptionBalance,
                displayReconnectTunnel = connectViewModel.displayReconnectTunnel,
                insufficientBalance = displayInsufficientBalance,
                usedBytes = subscriptionBalanceViewModel.usedBalanceByteCount,
                pendingBytes = subscriptionBalanceViewModel.pendingBalanceByteCount,
                availableBytes = subscriptionBalanceViewModel.availableBalanceByteCount.collectAsState().value,
                meanReliabilityWeight = meanReliabilityWeight,
                totalReferrals = totalReferrals,
                launchIntro = launchIntro
            )
        },
        mainContent = {
            ConnectMainContent(
                connectStatus = connectStatus,
                selectedLocation = connectViewModel.selectedLocation,
                networkName = networkUser?.networkName,
                connect = connectViewModel.connect,
                disconnect = connectViewModel.disconnect,
                providerGridPoints = connectViewModel.providerGridPoints,
                windowCurrentSize = connectViewModel.windowCurrentSize,
                grid = connectViewModel.grid,
                loginMode = accountViewModel.loginMode,
                animatedSuccessPoints = connectViewModel.shuffledSuccessPoints,
                shuffleSuccessPoints = connectViewModel.shuffleSuccessPoints,
                getStateColor = connectViewModel.getStateColor,
                launchOverlay = overlayViewModel.launch,
                locationsViewModel = locationsViewModel,
                navController = navController,
                displayReconnectTunnel = connectViewModel.displayReconnectTunnel,
                contractStatus = contractStatus,
                currentPlan = if (isPro) Plan.Supporter else Plan.Basic,
                displayInsufficientBalance = displayInsufficientBalance,
                isPollingSubscriptionBalance = subscriptionBalanceViewModel.isPollingSubscriptionBalance,
                device = connectViewModel.device,
                promptReview = {
                    if (bundleStore == BundleStore.SOLANA_DAPP) {
                        setPromptSolanaReview(true)
                    } else {
                        promptReview()
                    }
                },
            )
        }
    )

    if (promptSolanaReview) {
        PromptSolanaDAppStoreReview(
            promptReview = {
                promptReview()
            },
            dismiss = {
                setPromptSolanaReview(false)
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectActionsSheetScaffold(
    scaffoldState: BottomSheetScaffoldState,
    sheetContent: @Composable (peekHeight: Dp) -> Unit,
    mainContent: @Composable () -> Unit,
    sheetPeekHeight: Dp = dimensionResource(id = R.dimen.connect_actions_sheet_peek_height),
) {

    BottomSheetScaffold(
        sheetPeekHeight = sheetPeekHeight,
        scaffoldState = scaffoldState,
        sheetContainerColor = SheetBlack,
        sheetContent = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp)
            ) {
                sheetContent(sheetPeekHeight)
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Black)
        ) {

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Main screen content
                mainContent()
            }
        }
    }
}

@Composable
fun ConnectMainContent(
    connectStatus: ConnectStatus,
    selectedLocation: ConnectLocation?,
    networkName: String?,
    grid: ConnectGrid?,
    providerGridPoints: Map<Id, ProviderGridPoint>,
    windowCurrentSize: Int,
    connect: (ConnectLocation?) -> Unit,
    disconnect: () -> Unit?,
    loginMode: LoginMode,
    animatedSuccessPoints: List<AnimatedSuccessPoint>,
    shuffleSuccessPoints: () -> Unit,
    getStateColor: (ProviderPointState?) -> Color,
    launchOverlay: (OverlayMode) -> Unit,
    locationsViewModel: LocationsListViewModel,
    navController: NavController,
    displayReconnectTunnel: Boolean,
    displayInsufficientBalance: Boolean,
    contractStatus: ContractStatus?,
    currentPlan: Plan,
    isPollingSubscriptionBalance: Boolean,
    device: DeviceLocal?, // fixme, we don't need to pass the entire device
    promptReview: () -> Unit
) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .animateContentSize()
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Column(
            modifier = Modifier
                .weight(1f),
            verticalArrangement = Arrangement.Center
        ) {

            Column {

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center

                ) {
                    ConnectButton(
                        onClick = {
                            if (connectStatus == ConnectStatus.DISCONNECTED) {
                                connect(selectedLocation)
//                            checkTriggerPromptReview()
                            }
                        },
                        updatedStatus = connectStatus,
                        providerGridPoints = providerGridPoints,
                        grid = grid,
                        animatedSuccessPoints = animatedSuccessPoints,
                        shuffleSuccessPoints = shuffleSuccessPoints,
                        getStateColor = getStateColor,
                        displayReconnectTunnel = displayReconnectTunnel,
                        insufficientBalance = displayInsufficientBalance,
                        isPollingSubscriptionBalance = isPollingSubscriptionBalance
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                ConnectStatusIndicator(
                    status = connectStatus,
                    windowCurrentSize = windowCurrentSize,
                    networkName = networkName,
                    guestMode = loginMode == LoginMode.Guest,
                    displayReconnectTunnel = displayReconnectTunnel,
                    contractStatus = contractStatus,
                    currentPlan = currentPlan,
                    isPollingSubscriptionBalance = isPollingSubscriptionBalance
                )

//                Spacer(modifier = Modifier.height(16.dp))

//                Box(
//                    modifier = Modifier
//                        .height(48.dp)
//                ) {
//                    Row(
//                        modifier = Modifier
//                            .fillMaxWidth(),
//                        horizontalArrangement = Arrangement.Center
//                    ) {
//                        /**
//                         * Disconnect Button
//                         */
//                        AnimatedVisibility(
//                            visible = disconnectBtnVisible && !displayReconnectTunnel && !displayInsufficientBalance,
//                            enter = fadeIn(),
//                            exit = fadeOut()
//                        ) {
//
//                            URButton(
//                                onClick = {
//                                    disconnect()
//                                },
//                                style = ButtonStyle.OUTLINE
//                            ) { buttonTextStyle ->
//                                Text(
//                                    stringResource(id = R.string.disconnect),
//                                    style = buttonTextStyle,
//                                    modifier = Modifier.padding(horizontal = 16.dp)
//                                )
//                            }
//
//                        }
//                    }
//
//                    Row(
//                        modifier = Modifier
//                            .fillMaxWidth(),
//                        horizontalArrangement = Arrangement.Center
//                    ) {
//                        /**
//                         * Reconnect tunnel button
//                         */
//
//                        AnimatedVisibility(
//                            visible = displayReconnectTunnel,
//                            enter = fadeIn(),
//                            exit = fadeOut()
//                        ) {
//                            URButton(
//                                onClick = {
//                                    application?.startVpnService()
//                                },
//                                style = ButtonStyle.OUTLINE
//                            ) { buttonTextStyle ->
//                                Text(
//                                    stringResource(id = R.string.reconnect),
//                                    style = buttonTextStyle,
//                                    modifier = Modifier.padding(horizontal = 16.dp)
//                                )
//                            }
//                        }
//                    }
//
//
//                    Row(
//                        modifier = Modifier
//                            .fillMaxWidth(),
//                        horizontalArrangement = Arrangement.Center
//                    ) {
//                        /**
//                         * Insufficient balance, subscribe to fix
//                         */
//                        AnimatedVisibility(
//                            visible = displayInsufficientBalance && !isPollingSubscriptionBalance,
//                            enter = fadeIn(),
//                            exit = fadeOut()
//                        ) {
//                            URButton(
//                                onClick = {
//                                    navController.navigate(Route.Upgrade)
//                                },
//                                style = ButtonStyle.OUTLINE
//                            ) { buttonTextStyle ->
//                                Text(
//                                    "Subscribe to fix",
//                                    style = buttonTextStyle,
//                                    modifier = Modifier.padding(horizontal = 16.dp)
//                                )
//                            }
//                        }
//                    }
//                }
            }
        }

//        OpenProviderListButton(
//            selectedLocation = selectedLocation,
//            getLocationColor = locationsViewModel.getLocationColor,
//            onClick = {
//                locationsViewModel.refreshLocations()
//                navController.navigate(Route.BrowseLocations)
//            }
//        )

    }

}


//@Preview
//@Composable
//private fun ConnectMainContentPreview() {
//    val mockGetStateColor: (ProviderPointState?) -> Color = { Red }
//
//    URNetworkTheme {
//        Scaffold { innerPadding ->
//            Box(
//                modifier = Modifier
//                    .fillMaxSize()
//                    .padding(innerPadding)
//                    .padding(16.dp),
//            ) {
//                ConnectMainContent(
//                    connectStatus = ConnectStatus.DISCONNECTED,
//                    selectedLocation = null,
//                    networkName = "my_network",
//                    connect = {},
//                    disconnect = {},
//                    grid = null,
//                    providerGridPoints = mapOf(),
//                    windowCurrentSize = 16,
//                    loginMode = LoginMode.Authenticated,
//                    animatedSuccessPoints = listOf(),
//                    shuffleSuccessPoints = {},
//                    getStateColor = mockGetStateColor,
////                    checkTriggerPromptReview = {false},
//                    launchOverlay = {},
//                    locationsViewModel = LocationsListViewModel()
//                    // getLocationColor = { Color.Red }
//                )
//            }
//        }
//    }
//}

