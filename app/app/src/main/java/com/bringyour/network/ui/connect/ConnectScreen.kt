package com.bringyour.network.ui.connect

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.onConsumedWindowInsetsChanged
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomSheetDefaults
import com.bringyour.network.ui.components.isTabletWidth
import com.bringyour.network.ui.components.tabletDrawerWidth
import com.bringyour.network.ui.components.TabletLayout
import com.bringyour.network.ui.components.TapSequenceGate
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.math.abs

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
    isPro: Boolean,
    connectActionsSheetState: ConnectDrawerState,
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
        drawerState = connectActionsSheetState,
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
                    navController.navigate(Route.Referrals)
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
                launchProCelebration = { overlayViewModel.launchSunglassesFlight() },
                showProviderLocations = { navController.navigate(Route.ProviderLocations) },
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
    drawerState: ConnectDrawerState,
    sheetContent: @Composable (peekHeight: Dp, belowFoldGap: Dp, onFoldMarkerPositioned: (Int) -> Unit) -> Unit,
    mainContent: @Composable () -> Unit,
    baseSheetPeekHeight: Dp = dimensionResource(id = R.dimen.connect_actions_sheet_peek_height),
) {

    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

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

    // When the drawer heads back to the collapsed position, reset the content
    // to the top so the peek always shows the top of the actions (matches the
    // iOS drawer). The target flips as soon as the close is under way; a
    // one-shot animateScrollTo was lost whenever something held the content's
    // scroll at that moment (a drag still in flight, a fling running out), so
    // this keeps asking, one attempt per frame, until the content is at the
    // top.
    LaunchedEffect(drawerState) {
        snapshotFlow { drawerState.targetValue }
            .collect { target ->
                if (target != ConnectDrawerValue.Collapsed) {
                    return@collect
                }
                while (scrollState.value > 0 && isActive) {
                    try {
                        scrollState.animateScrollTo(0)
                    } catch (e: CancellationException) {
                        if (!isActive) throw e
                        withFrameNanos { }
                    }
                }
            }
    }

    // The drawer's open/close and its content's scroll are separate gestures
    // that never hand off to each other (mmm/DESIGNSTYLE.md). Each touch is
    // classified at its down (ConnectSheetGesture): a touch on the open or
    // opening drawer waits for its first movement, any other touch is a
    // drawer gesture. This connection, under the content, then routes the
    // whole gesture to one side: a drawer gesture moves the drawer by raw
    // deltas and settles it on release, and never scrolls the content; a
    // content gesture scrolls the content, stops at its top, and never moves
    // the drawer. Only user input counts: the content's own flings and
    // animations pass through untouched.
    val velocityThresholdPx = with(density) { SheetFlingVelocityThreshold.toPx() }
    val positionalThresholdPx = with(density) { SheetPullThreshold.toPx() }
    val sheetGesture = remember { ConnectSheetGesture() }
    val separateSheetAndContentGestures = remember(drawerState, scrollState, sheetGesture, scope, velocityThresholdPx, positionalThresholdPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) {
                    return Offset.Zero
                }
                val wasUndecided = sheetGesture.kind == ConnectSheetGesture.Kind.Undecided
                sheetGesture.onFirstMovement(available.y, contentAtTop = !scrollState.canScrollBackward)
                if (sheetGesture.kind != ConnectSheetGesture.Kind.Sheet) {
                    return Offset.Zero
                }
                if (wasUndecided) {
                    // a pull down on the open drawer while it still animates:
                    // take over from the animation
                    scope.launch { drawerState.interruptAnimation() }
                }
                if (!drawerState.draggable.offset.isNaN()) {
                    drawerState.draggable.dispatchRawDelta(available.y)
                }
                // the drawer took what it could; none of it scrolls the content
                return Offset(0f, available.y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (sheetGesture.kind != ConnectSheetGesture.Kind.Sheet) {
                    return Velocity.Zero
                }
                val velocity = available.y
                scope.launch { drawerState.release(velocity, velocityThresholdPx, positionalThresholdPx) }
                return Velocity(0f, velocity)
            }
        }
    }
    val handleFlingBehavior = AnchoredDraggableDefaults.flingBehavior(
        state = drawerState.draggable,
        positionalThreshold = { positionalThresholdPx },
    )

    // tablet convention (mmm/DESIGNSTYLE.md "Tablet layouts"): the drawer wraps its
    // content in a centered panel of the readable width, rounded on every corner and
    // floating above the bar below it, instead of a full-width shelf; phones keep
    // the edge-to-edge sheet
    val floatingDrawer = isTabletWidth()
    BoxWithConstraints(
        // reports the insets ancestors already consumed (the tab bar scaffold),
        // so the sheet geometry above only adds the unconsumed remainder
        modifier = Modifier
            .onConsumedWindowInsetsChanged { consumedWindowInsets = it }
            .then(
                if (floatingDrawer) Modifier.padding(bottom = TabletLayout.drawerFloatGap) else Modifier
            )
    ) {
        val layoutHeightPx = constraints.maxHeight
        val peekPx = with(density) { sheetPeekHeight.roundToPx() }
        // The expanded drawer stops part-way up the screen, leaving the
        // connect graphic behind it partially exposed, instead of running to
        // the status bar with the drag handle under it (iOS caps its sheet
        // the same way). The scrolling content is capped to the expanded
        // height less the drag handle; the content scrolls within it.
        val sheetContentMaxHeight = with(density) {
            (maxHeight * ConnectSheetExpandedFraction - dragHandleHeightPx.toDp())
                .coerceAtLeast(baseSheetPeekHeight)
        }

        // the drawer's two positions follow from the layout height, the peek
        // and the measured sheet; the state snaps to its current target
        // whenever they change
        var sheetHeightPx by remember { mutableIntStateOf(0) }
        if (sheetHeightPx > 0 && layoutHeightPx > 0) {
            val anchors = remember(layoutHeightPx, sheetHeightPx, peekPx) {
                DraggableAnchors {
                    ConnectDrawerValue.Collapsed at (layoutHeightPx - peekPx).toFloat()
                    ConnectDrawerValue.Expanded at (layoutHeightPx - sheetHeightPx).toFloat()
                }
            }
            SideEffect {
                drawerState.draggable.updateAnchors(anchors, drawerState.targetValue)
            }
        }
        val restingOffsetPx = (layoutHeightPx - peekPx).toFloat()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Black)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = sheetPeekHeight)
            ) {
                // Main screen content
                mainContent()
            }
        }

        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .then(if (floatingDrawer) Modifier.width(tabletDrawerWidth()) else Modifier.fillMaxWidth())
                .offset {
                    val offset = drawerState.draggable.offset
                    IntOffset(0, (if (offset.isNaN()) restingOffsetPx else offset).roundToInt())
                }
                .onSizeChanged { sheetHeightPx = it.height }
                .nestedScroll(separateSheetAndContentGestures),
            shape = if (floatingDrawer) {
                RoundedCornerShape(TabletLayout.drawerCornerRadius)
            } else {
                BottomSheetDefaults.ExpandedShape
            },
            color = SheetBlack,
            shadowElevation = 1.dp,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // the drag handle: the drawer's own drag target, grabbing the
                // drawer even mid-animation, with the expand/collapse actions
                // for assistive tech. Measure only its local size: moving the
                // sheet does not change it.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onSizeChanged { dragHandleHeightPx = it.height }
                        .anchoredDraggable(
                            state = drawerState.draggable,
                            orientation = Orientation.Vertical,
                            flingBehavior = handleFlingBehavior,
                        )
                        .semantics(mergeDescendants = true) {
                            if (drawerState.currentValue == ConnectDrawerValue.Collapsed) {
                                expand {
                                    scope.launch { drawerState.expand() }
                                    true
                                }
                            } else {
                                collapse {
                                    scope.launch { drawerState.partialExpand() }
                                    true
                                }
                            }
                        },
                    contentAlignment = Alignment.TopCenter,
                ) {
                    BottomSheetDefaults.DragHandle()
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = sheetContentMaxHeight)
                        // classifies each touch before anything else sees it:
                        // a touch on the open (or opening) drawer waits for
                        // its first movement, any other touch is a drawer
                        // gesture, which takes over from a running animation
                        .pointerInput(sheetGesture, drawerState) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                sheetGesture.onDown(
                                    sheetSettledOpen = drawerState.targetValue == ConnectDrawerValue.Expanded
                                )
                                if (sheetGesture.kind == ConnectSheetGesture.Kind.Sheet) {
                                    scope.launch { drawerState.interruptAnimation() }
                                }
                            }
                        }
                        .nestedScroll(separateSheetAndContentGestures)
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
    promptReview: () -> Unit,
    showProviderLocations: () -> Unit,
    // the easter egg: five quick taps on the connected connector replay the
    // Pro celebration
    launchProCelebration: () -> Unit = {},
) {

    // silent: no ripple change, no counter, no announcement; a 2 s gap or
    // leaving the connected state starts the count over
    val connectedTapGate = remember { TapSequenceGate(count = 5, windowMillis = 2_000L) }
    LaunchedEffect(connectStatus) {
        if (connectStatus != ConnectStatus.CONNECTED) {
            connectedTapGate.reset()
        }
    }

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
                            } else if (connectStatus == ConnectStatus.CONNECTED) {
                                if (connectedTapGate.tap(System.currentTimeMillis())) {
                                    launchProCelebration()
                                }
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
                    isPollingSubscriptionBalance = isPollingSubscriptionBalance,
                    onShowProviderLocations = showProviderLocations
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

/**
 * How much of the connect screen the expanded drawer covers. Two thirds
 * leaves the top of the connect graphic visible above the drawer on phones
 * and keeps the drag handle clear of the status bar.
 */
private const val ConnectSheetExpandedFraction = 2f / 3f

/**
 * The release speed above which a drawer gesture opens or closes the drawer
 * regardless of how far it travelled: the same 125 dp/s the drag handle's
 * fling behaviour uses, so both ways of moving the drawer decide alike.
 */
private val SheetFlingVelocityThreshold = 125.dp

/**
 * How far a slower drawer gesture must travel from where the drawer rested
 * for its release to open or close it. The Material sheet's own positional
 * threshold.
 */
private val SheetPullThreshold = 56.dp
