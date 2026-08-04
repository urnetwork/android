package com.bringyour.network.ui.connect

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.onConsumedWindowInsetsChanged
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.BottomSheetScaffoldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
    throughputViewModel: com.bringyour.network.ui.stats.ThroughputViewModel = hiltViewModel(),
    blockActionsViewModel: com.bringyour.network.ui.stats.BlockActionsViewModel = hiltViewModel(),
    dnsSettingsViewModel: com.bringyour.network.ui.stats.DnsSettingsViewModel = hiltViewModel(),
    blockerViewModel: com.bringyour.network.ui.stats.BlockerViewModel = hiltViewModel(),
    networkPeersViewModel: com.bringyour.network.ui.stats.NetworkPeersViewModel = hiltViewModel(),
) {

    val connectStatus by connectViewModel.connectStatus.collectAsState()
    val contractStatus by connectViewModel.contractStatus.collectAsState()

    val networkUser by accountViewModel.networkUser.collectAsState()

    val availableBytes by subscriptionBalanceViewModel.availableBalanceByteCount.collectAsState()
    val dailyByteCount by subscriptionBalanceViewModel.startBalanceByteCount.collectAsState()

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

    // For a selected network-peer location, resolve the device name from the LIVE peer list so
    // the drawer shows the same label as the peer list (the location name is a connect-time
    // snapshot that can be a stale client id).
    val selectedPeerName = connectViewModel.selectedLocation?.connectLocationId?.clientId?.idStr?.let { cid ->
        networkPeersViewModel.connectedProvidePeers.firstOrNull { it.clientId == cid }?.displayName
    }

    ConnectActionsSheetScaffold(
        scaffoldState = scaffoldState,
        sheetContent = { minSheetHeight, belowFoldGap, onFoldMarkerPositioned ->
            ConnectActions(
                navController = navController,
                onFoldMarkerPositioned = onFoldMarkerPositioned,
                belowFoldGap = belowFoldGap,
                selectedLocation = connectViewModel.selectedLocation,
                peerCount = networkPeersViewModel.connectedCount,
                providerDiscoverable = networkPeersViewModel.providerDiscoverable,
                deviceName = networkPeersViewModel.deviceName,
                selectedPeerName = selectedPeerName,
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
                availableBytes = availableBytes,
                meanReliabilityWeight = meanReliabilityWeight,
                totalReferrals = totalReferrals,
                launchIntro = launchIntro,
                dailyByteCount = dailyByteCount,
                fixedIpSize = connectViewModel.fixedIpSize,
                toggleFixedIpSize = connectViewModel.toggleFixedIp,
                selectedWindowType = connectViewModel.selectedWindowType,
                setSelectedWindowType = connectViewModel.setSelectedWindowType,
                allowDirect = connectViewModel.allowDirect,
                toggleAllowDirect = connectViewModel.toggleAllowDirect,
                postQuantumEncryption = connectViewModel.postQuantumEncryption,
                togglePostQuantumEncryption = connectViewModel.togglePostQuantumEncryption,
                throughputViewModel = throughputViewModel,
                blockActionsViewModel = blockActionsViewModel,
                dnsSettingsViewModel = dnsSettingsViewModel,
                blockerViewModel = blockerViewModel,
                onReferralClick = {
                    overlayViewModel.launch(OverlayMode.Refer)
                },
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
    sheetContent: @Composable (peekHeight: Dp, belowFoldGap: Dp, onFoldMarkerPositioned: (Int) -> Unit) -> Unit,
    mainContent: @Composable () -> Unit,
    baseSheetPeekHeight: Dp = dimensionResource(id = R.dimen.connect_actions_sheet_peek_height),
) {

    val scrollState = rememberScrollState()
    val density = LocalDensity.current

    // Only the part of the system bottom inset that nothing below the sheet
    // absorbs may be added to the sheet geometry. Above the bottom tab bar
    // (phones) NavigationSuiteScaffold consumes the inset — the tab bar itself
    // clears the system bar — so the remainder is zero. Next to a navigation
    // rail (tablet landscape) the sheet reaches the screen edge and the full
    // inset remains. Adding the RAW inset here double-counted it above the tab
    // bar, so the gap under the connect button varied with the device's nav
    // mode (gesture vs 3-button) instead of staying standard.
    var consumedWindowInsets by remember { mutableStateOf(WindowInsets(0, 0, 0, 0)) }
    val unconsumedBottomInset = with(density) {
        (WindowInsets.navigationBars.getBottom(this) - consumedWindowInsets.getBottom(this))
            .coerceAtLeast(0)
            .toDp()
    }

    // The collapsed drawer shows EXACTLY the above-the-fold content — the
    // location row with the connect button — and nothing else (iOS parity: no
    // peers line, no hint of the connection-type selector below the fold). A
    // fixed peek constant cannot do that across devices and font scales. Use
    // only local integer geometry: the handle height plus the fold marker's
    // offset within sheet content. Root coordinates feed the sheet's own
    // movement back into sheetPeekHeight and can create a perpetual
    // measure/layout/render loop. Until both measurements land, the dimen keeps
    // a sane peek.
    // the standard visible padding between the bottom of the connect button
    // and the top of the tab bar at the collapsed peek — the same 12dp on
    // every device and nav mode, matching the iOS drawer
    val foldGap = 12.dp
    var dragHandleHeightPx by remember { mutableIntStateOf(0) }
    var foldMarkerOffsetPx by remember { mutableIntStateOf(0) }
    val sheetPeekHeight = with(density) {
        connectSheetPeekHeightPx(
            baseHeightPx = baseSheetPeekHeight.roundToPx(),
            dragHandleHeightPx = dragHandleHeightPx,
            foldMarkerOffsetPx = foldMarkerOffsetPx,
            foldGapPx = foldGap.roundToPx(),
            unconsumedBottomInsetPx = unconsumedBottomInset.roundToPx(),
        ).toDp()
    }

    // spacing between the connect button and the peers line: it must exceed
    // the collapsed peek's overhang past the fold marker (foldGap + the
    // unconsumed inset) so the peers line never peeks out of the collapsed
    // drawer, and at 24dp on phones it matches the iOS expanded spacing
    val belowFoldGap = 24.dp + unconsumedBottomInset

    // when the sheet settles back to the peek, reset the content to the top so
    // the peek always shows the top of the actions (matches the iOS drawer)
    LaunchedEffect(scaffoldState.bottomSheetState) {
        snapshotFlow { scaffoldState.bottomSheetState.currentValue }
            .collect { value ->
                if (value == SheetValue.PartiallyExpanded) {
                    scrollState.animateScrollTo(0)
                }
            }
    }

    Box(
        // reports the insets ancestors already consumed (the tab bar scaffold),
        // so the sheet geometry above only adds the unconsumed remainder
        modifier = Modifier.onConsumedWindowInsetsChanged { consumedWindowInsets = it }
    ) {
        BottomSheetScaffold(
            sheetPeekHeight = sheetPeekHeight,
            scaffoldState = scaffoldState,
            sheetContainerColor = SheetBlack,
            sheetDragHandle = {
                // Measure only the handle's local size. Moving the sheet does not
                // change it.
                Box(
                    modifier = Modifier.onSizeChanged { dragHandleHeightPx = it.height }
                ) {
                    BottomSheetDefaults.DragHandle()
                }
            },
            sheetContent = {
                // the sheet body scrolls when expanded. BottomSheetScaffold installs a
                // nested scroll connection so a downward drag with the content at the
                // top hands off to the sheet and collapses it instead of overscrolling,
                // and an upward drag expands the sheet before the content scrolls
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .padding(horizontal = 16.dp)
                        // the same standard gap above the sheet's bottom edge
                        // (the tab bar on phones, the screen edge past the
                        // unconsumed inset next to a rail), so the expanded
                        // content's last card ends with the padding the
                        // collapsed connect button gets
                        .padding(bottom = foldGap + unconsumedBottomInset)
                ) {
                    sheetContent(
                        baseSheetPeekHeight,
                        belowFoldGap,
                        { foldMarkerOffsetPx = it },
                    )
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
    disconnect: () -> Unit,
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


            }
        }

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
