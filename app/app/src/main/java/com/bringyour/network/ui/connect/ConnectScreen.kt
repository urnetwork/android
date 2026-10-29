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
import androidx.lifecycle.compose.LifecycleStartEffect
import com.bringyour.network.ui.components.TapSequenceGate
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.toSize
import com.bringyour.network.ui.components.CONNECT_GRAPHIC_FULL_DP
import com.bringyour.network.ui.components.CONNECT_GRAPHIC_MIN_DP
import com.bringyour.network.ui.components.CONNECT_GRAPHIC_PANE_MAX_DP
import com.bringyour.network.ui.components.ConnectArrangement
import com.bringyour.network.ui.components.DataInfoSheet
import com.bringyour.network.ui.components.LocalWindowLayout
import com.bringyour.network.ui.components.connectArrangementFor
import com.bringyour.network.ui.components.connectDrawerExpandedHeight
import com.bringyour.network.ui.components.dataInfoShowsFreeRefresh
import com.bringyour.network.ui.components.mainPaneWidth
import com.bringyour.network.ui.components.sideBySideSplit
import com.bringyour.network.ui.components.stackedSplit
import com.bringyour.network.ui.stats.AppSplitRulesPanel
import com.bringyour.network.ui.stats.ConnectStatsTiles
import com.bringyour.network.ui.theme.MainTintedBackgroundBase
import com.bringyour.network.ui.theme.Pink
import com.bringyour.network.ui.theme.TopBarTitleTextStyle
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
import com.bringyour.network.PrivateDnsMode
import com.bringyour.network.privateDnsStrictNoticeHost
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
    // the referral toast draws over the top of the screen (MainNavHost): the
    // slow internet pill makes way for it
    referralToastVisible: Boolean = false,
    // the location picker open as a dialog in the pane arrangements, and the
    // list's scroll position; kept above the screen (MainNavHost), so a fold
    // turns the dialog into the picker's screen and back without losing them
    locationPickerOpen: Boolean = false,
    setLocationPickerOpen: (Boolean) -> Unit = {},
    locationListPosition: LocationListPosition = rememberLocationListPosition(),
    accountViewModel: AccountViewModel = hiltViewModel<AccountViewModel>(),
    throughputViewModel: com.bringyour.network.ui.stats.ThroughputViewModel = hiltViewModel(),
    blockActionsViewModel: com.bringyour.network.ui.stats.BlockActionsViewModel = hiltViewModel(),
    dnsSettingsViewModel: com.bringyour.network.ui.stats.DnsSettingsViewModel = hiltViewModel(),
    blockerViewModel: com.bringyour.network.ui.stats.BlockerViewModel = hiltViewModel(),
    networkPeersViewModel: com.bringyour.network.ui.stats.NetworkPeersViewModel = hiltViewModel(),
) {

    val connectStatus by connectViewModel.connectStatus.collectAsState()
    // what the connect controls show: connecting from the tap, also while a
    // start waits on its balance (StartConnectAdmission)
    val displayConnectStatus by connectViewModel.displayConnectStatus.collectAsState()
    val contractStatus by connectViewModel.contractStatus.collectAsState()
    val transportDelay by connectViewModel.transportDelay.collectAsState()

    val networkUser by accountViewModel.networkUser.collectAsState()

    val availableBytes by subscriptionBalanceViewModel.availableBalanceByteCount.collectAsState()
    val dailyByteCount by subscriptionBalanceViewModel.startBalanceByteCount.collectAsState()

    // the last fetched account balance, as the start connect gate outside the
    // ui reads it (MainApplication.checkStartConnect)
    val screenContext = LocalContext.current
    val balanceChanges by com.bringyour.network.widgets.WidgetSnapshotStore.changes.collectAsState()
    val accountBalance = remember(balanceChanges) {
        com.bringyour.network.widgets.WidgetSnapshotStore.loadBalance(screenContext)
    }
    val balanceExhausted = accountBalanceExhausted(accountBalance)
    // a connect insufficient balance blocked, waiting to be retried (BalanceRecovery)
    val balanceRecoveryState by (
        (screenContext.applicationContext as? MainApplication)?.balanceRecoveryState
            ?: kotlinx.coroutines.flow.MutableStateFlow(BalanceRecoveryState())
        ).collectAsState()
    // upgrade shown in place of connect on the account balance alone: make
    // sure that balance is current, so a stale zero does not hide connect
    val balanceGatesConnect = balanceExhausted && displayConnectStatus == ConnectStatus.DISCONNECTED
    LaunchedEffect(balanceGatesConnect, balanceChanges) {
        if (balanceGatesConnect) {
            (screenContext.applicationContext as? MainApplication)?.refreshStartConnectBalanceIfStale()
        }
    }
    // a start waiting on its balance shows connecting, not the account
    // balance's upgrade; the gate refuses it if the fetched balance is out
    val displayInsufficientBalance = com.bringyour.network.ui.connect.displayInsufficientBalance(
        contractInsufficientBalance = contractStatus?.insufficientBalance == true,
        accountBalanceExhausted = balanceExhausted,
        connectRequested = displayConnectStatus != ConnectStatus.DISCONNECTED,
    ) && !isPro

    var promptSolanaReview by rememberSaveable { mutableStateOf(false) }

    val setPromptSolanaReview: (Boolean) -> Unit = {
        promptSolanaReview = it
    }


    val reviewManagerRequest = rememberReviewManager()
    val context = LocalContext.current
    val application = context.applicationContext as? MainApplication

    // Strict Private DNS silently breaks name resolution while connected; warn
    // only in that state (privateDnsStrictNoticeHost).
    val privateDnsMode by (
        application?.privateDnsMode
            ?: kotlinx.coroutines.flow.MutableStateFlow<PrivateDnsMode>(PrivateDnsMode.Off)
        ).collectAsState()
    val privateDnsStrictHost = privateDnsStrictNoticeHost(
        privateDnsMode,
        connectStatus == ConnectStatus.CONNECTED,
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

    // started only while this screen shows and the app is in the foreground
    LifecycleStartEffect(locationsViewModel) {
        locationsViewModel.setHomeVisible(true)
        onStopOrDispose {
            locationsViewModel.setHomeVisible(false)
        }
    }

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

    // For a selected network-peer location, resolve the device name from the live peer list so
    // the drawer shows the same label as the peer list (the location name is a connect-time
    // snapshot that can be a stale client id).
    val selectedPeerName = connectViewModel.selectedLocation?.connectLocationId?.clientId?.idStr?.let { cid ->
        networkPeersViewModel.connectedProvidePeers.firstOrNull { it.clientId == cid }?.displayName
    }

    // the selected location's count from the last locations fetch, not the
    // copy taken when it was selected
    val selectedLocation = connectViewModel.selectedLocation
    val selectedProviderCount = selectedProviderCount(
        selectedId = selectedLocation?.connectLocationId?.toString(),
        selectedCount = selectedLocation?.providerCount ?: 0,
        fetched = sequenceOf(
            locationsViewModel.connectCountries,
            locationsViewModel.regions,
            locationsViewModel.cities,
            locationsViewModel.devices,
            locationsViewModel.bestSearchMatches,
        ).flatMap { it.asSequence() }.map { it.connectLocationId.toString() to it.providerCount },
    )

    // the connect screen's arrangement for the window (AdaptiveLayout.kt):
    // the phone layout at compact width; from medium width up the drawer
    // becomes a pane, always open, beside or below the connect graphic
    val windowLayout = LocalWindowLayout.current
    val arrangement = connectArrangementFor(windowLayout.widthDp, windowLayout.heightDp, windowLayout.fold)
    val currentPlan = if (isPro) Plan.Supporter else Plan.Basic

    // Above the arrangements, so a fold, an unfold or a resize keeps them: the
    // "About your data" sheet (Why? under the out-of-balance notice), the
    // panes' scroll, and the taps of the connected easter egg.
    var dataInfoPresented by rememberSaveable { mutableStateOf(false) }
    val paneScrollState = rememberScrollState()
    // silent: no ripple change, no counter, no announcement; a 2 s gap or
    // leaving the connected state starts the count over
    val connectedTapGate = remember { TapSequenceGate(count = 5, windowMillis = 2_000L) }
    LaunchedEffect(displayConnectStatus) {
        if (displayConnectStatus != ConnectStatus.CONNECTED) {
            connectedTapGate.reset()
        }
    }

    // The connect graphic is movable content: it moves between the
    // arrangements with its provider grid, success dots and animations, so a
    // fold neither re-centers nor restarts it.
    val movableGraphic = remember {
        movableContentOf { button: @Composable () -> Unit -> button() }
    }
    val connectGraphic: @Composable () -> Unit = {
        movableGraphic {
            ConnectButton(
                onClick = {
                    if (displayConnectStatus == ConnectStatus.DISCONNECTED) {
                        connectViewModel.connect(connectViewModel.selectedLocation)
                    } else if (displayConnectStatus == ConnectStatus.CONNECTED) {
                        // the easter egg: five quick taps on the connected
                        // connector replay the Pro celebration
                        if (connectedTapGate.tap(System.currentTimeMillis())) {
                            overlayViewModel.launchSunglassesFlight()
                        }
                    }
                },
                updatedStatus = displayConnectStatus,
                providerGridPoints = connectViewModel.providerGridPoints,
                grid = connectViewModel.grid,
                animatedSuccessPoints = connectViewModel.shuffledSuccessPoints,
                shuffleSuccessPoints = connectViewModel.shuffleSuccessPoints,
                getStateColor = connectViewModel.getStateColor,
                displayReconnectTunnel = connectViewModel.displayReconnectTunnel,
                insufficientBalance = displayInsufficientBalance,
                isPollingSubscriptionBalance = subscriptionBalanceViewModel.isPollingSubscriptionBalance
            )
        }
    }

    val connectStatusIndicator: @Composable () -> Unit = {
        ConnectStatusIndicator(
            status = displayConnectStatus,
            windowCurrentSize = connectViewModel.windowCurrentSize,
            networkName = networkUser?.networkName,
            guestMode = accountViewModel.loginMode == LoginMode.Guest,
            displayReconnectTunnel = connectViewModel.displayReconnectTunnel,
            contractStatus = contractStatus,
            currentPlan = currentPlan,
            isPollingSubscriptionBalance = subscriptionBalanceViewModel.isPollingSubscriptionBalance,
            onShowProviderLocations = { navController.navigate(Route.ProviderLocations) }
        )
    }

    // the location picker: its own screen in the phone layout, a dialog over
    // the location row's pane in the pane arrangements
    val presentSelectProvider: (Boolean) -> Unit = {
        if (arrangement == ConnectArrangement.Drawer) {
            navController.navigate(Route.BrowseLocations)
        } else {
            setLocationPickerOpen(true)
        }
    }
    // compact again with the dialog open (folded, or the window narrowed):
    // the picker continues as its screen, with its search and scroll
    if (arrangement == ConnectArrangement.Drawer && locationPickerOpen) {
        LaunchedEffect(Unit) {
            setLocationPickerOpen(false)
            navController.navigate(Route.BrowseLocations)
        }
    }

    val connect = { connectViewModel.connect(connectViewModel.selectedLocation) }
    val reconnectTunnel = { application?.startVpnService(); Unit }
    val actionButtons = connectActionButtons(
        insufficientBalance = displayInsufficientBalance,
        currentPlan = currentPlan,
        isPollingSubscriptionBalance = subscriptionBalanceViewModel.isPollingSubscriptionBalance,
        connectStatus = displayConnectStatus,
        displayReconnectTunnel = connectViewModel.displayReconnectTunnel,
    )
    // the connect buttons with their notices, at the foot of a pane
    val paneActions: @Composable () -> Unit = {
        Column(modifier = Modifier.fillMaxWidth()) {
            ConnectActionNotices(
                actionButtons = actionButtons,
                outOfBalanceKind = outOfBalanceKind(accountBalance),
                reservedBytes = accountBalance?.openTransferByteCount ?: 0L,
                balanceRecovery = balanceRecoveryState,
                cancelBalanceRecovery = { application?.clearBalanceRecovery() },
                privateDnsStrictHost = privateDnsStrictHost,
                showDataInfo = { dataInfoPresented = true },
            )
            Spacer(modifier = Modifier.height(12.dp))
            ConnectActionButtonRow(
                actionButtons = actionButtons,
                navController = navController,
                connect = connect,
                disconnect = connectViewModel.disconnect,
                reconnectTunnel = reconnectTunnel,
            )
        }
    }

    // the location row's pane and the row itself, on screen: where the
    // location dialog opens
    var locationPaneOnScreen by remember { mutableStateOf(Rect.Zero) }
    var locationRowOnScreen by remember { mutableStateOf(Rect.Zero) }
    val locationRowModifier = Modifier.onGloballyPositioned {
        locationRowOnScreen = Rect(it.positionOnScreen(), it.size.toSize())
    }

    val drawerContent: @Composable (Dp, Boolean, Dp, (Int) -> Unit) -> Unit =
        { minHeight, showActions, belowFoldGap, onFoldMarkerPositioned ->
            ConnectActions(
                navController = navController,
                onFoldMarkerPositioned = onFoldMarkerPositioned,
                belowFoldGap = belowFoldGap,
                showActions = showActions,
                locationRowModifier = if (showActions) Modifier else locationRowModifier,
                selectedLocation = connectViewModel.selectedLocation,
                peerCount = networkPeersViewModel.connectedCount,
                providerDiscoverable = networkPeersViewModel.providerDiscoverable,
                deviceName = networkPeersViewModel.deviceName,
                selectedPeerName = selectedPeerName,
                selectedProviderCount = selectedProviderCount,
                presentSelectProvider = presentSelectProvider,
                getLocationColor = locationsViewModel.getLocationColor,
                minHeight = minHeight,
                currentPlan = currentPlan,
                connect = connect,
                disconnect = connectViewModel.disconnect,
                reconnectTunnel = reconnectTunnel,
                connectStatus = displayConnectStatus,
                isPollingSubscriptionBalance = subscriptionBalanceViewModel.isPollingSubscriptionBalance,
                displayReconnectTunnel = connectViewModel.displayReconnectTunnel,
                privateDnsStrictHost = privateDnsStrictHost,
                insufficientBalance = displayInsufficientBalance,
                outOfBalanceKind = outOfBalanceKind(accountBalance),
                reservedBytes = accountBalance?.openTransferByteCount ?: 0L,
                balanceRecovery = balanceRecoveryState,
                cancelBalanceRecovery = { application?.clearBalanceRecovery() },
                showDataInfo = { dataInfoPresented = true },
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
                ipFamilyPoints = connectViewModel.ipFamilyPoints,
                onReferralClick = {
                    navController.navigate(Route.Referrals)
                },
            )
        }

    // where this screen sits in the window, to place a fold in it
    var screenInWindow by remember { mutableStateOf(Rect.Zero) }
    val density = LocalDensity.current
    val sidePaneOnLeft = LocalLayoutDirection.current == LayoutDirection.Ltr

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { screenInWindow = it.boundsInWindow() }
    ) {
        when (arrangement) {
            ConnectArrangement.Drawer -> {
                // in tabletop the open drawer stops at the fold
                val tabletopFold = windowLayout.fold?.takeIf { !it.vertical }
                ConnectActionsSheetScaffold(
                    drawerState = connectActionsSheetState,
                    foldBottom = tabletopFold?.let { (it.end - screenInWindow.top / density.density).dp },
                    sheetContent = { minSheetHeight, belowFoldGap, onFoldMarkerPositioned ->
                        drawerContent(minSheetHeight, true, belowFoldGap, onFoldMarkerPositioned)
                    },
                    mainContent = {
                        ConnectMainContent(
                            graphic = connectGraphic,
                            status = connectStatusIndicator,
                        )
                    }
                )
            }

            ConnectArrangement.SideBySide -> ConnectPanes(
                sideBySide = true,
                splitFor = { width, _ ->
                    sideBySideSplit(
                        containerStart = screenInWindow.left / this.density,
                        width = width / this.density,
                        fold = windowLayout.hinge,
                        sidePaneOnLeft = sidePaneOnLeft,
                    )
                },
                // the drawer, always open, beside the connect pane: the
                // location row, the peers, the options, every statistics card
                // and the plan
                statsPane = {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(SheetBlack)
                            .onGloballyPositioned {
                                locationPaneOnScreen = Rect(it.positionOnScreen(), it.size.toSize())
                            }
                            .verticalScroll(paneScrollState)
                            .windowInsetsPadding(
                                WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.Start)
                            )
                            .padding(16.dp)
                    ) {
                        drawerContent(0.dp, false, 0.dp) {}
                    }
                },
                // the connect graphic and its status, the connect buttons at the foot
                connectPane = {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(
                                WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.End)
                            )
                            .padding(horizontal = 16.dp)
                            .padding(top = SlowInternetPillRoom, bottom = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        ConnectGraphicLayout(
                            minSize = CONNECT_GRAPHIC_MIN_DP.dp,
                            maxSize = CONNECT_GRAPHIC_PANE_MAX_DP.dp,
                            topSpacing = 0.dp,
                            gap = 32.dp,
                            graphic = connectGraphic,
                            below = connectStatusIndicator,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Box(modifier = Modifier.widthIn(max = PaneActionsMaxWidth)) {
                            paneActions()
                        }
                    }
                },
            )

            ConnectArrangement.Stacked -> ConnectPanes(
                sideBySide = false,
                splitFor = { _, height ->
                    stackedSplit(
                        containerTop = screenInWindow.top / this.density,
                        height = height / this.density,
                        fold = windowLayout.fold,
                    )
                },
                // below (in tabletop, under the hands): the location row, the
                // statistics in two columns, the rest of the drawer as it
                // scrolls, and the connect buttons at the foot
                statsPane = {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(SheetBlack)
                            .onGloballyPositioned {
                                locationPaneOnScreen = Rect(it.positionOnScreen(), it.size.toSize())
                            }
                            .windowInsetsPadding(
                                WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
                            )
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(paneScrollState)
                                .padding(horizontal = 16.dp)
                                .padding(top = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MainTintedBackgroundBase, RoundedCornerShape(12.dp))
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                OpenProviderListButton(
                                    selectedLocation = connectViewModel.selectedLocation,
                                    selectedPeerName = selectedPeerName,
                                    providerCount = selectedProviderCount,
                                    getLocationColor = locationsViewModel.getLocationColor,
                                    onClick = { presentSelectProvider(true) },
                                    modifier = locationRowModifier,
                                )
                            }

                            ConnectStatsTiles(
                                navController = navController,
                                throughputViewModel = throughputViewModel,
                                blockActionsViewModel = blockActionsViewModel,
                                dnsSettingsViewModel = dnsSettingsViewModel,
                                blockerViewModel = blockerViewModel,
                            )

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MainTintedBackgroundBase, RoundedCornerShape(12.dp))
                                    .padding(16.dp)
                            ) {
                                DeviceDiscoverableLine(
                                    providerDiscoverable = networkPeersViewModel.providerDiscoverable,
                                    deviceName = networkPeersViewModel.deviceName,
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                ConnectOptions(
                                    fixedIpSize = connectViewModel.fixedIpSize,
                                    toggleFixedIpSize = connectViewModel.toggleFixedIp,
                                    selectedWindowType = connectViewModel.selectedWindowType,
                                    setSelectedWindowType = connectViewModel.setSelectedWindowType,
                                    allowDirect = connectViewModel.allowDirect,
                                    toggleAllowDirect = connectViewModel.toggleAllowDirect,
                                    postQuantumEncryption = connectViewModel.postQuantumEncryption,
                                    togglePostQuantumEncryption = connectViewModel.togglePostQuantumEncryption,
                                )
                            }

                            AppSplitRulesPanel(
                                includedCount = blockActionsViewModel.tunnelIncludedAppIds.size,
                                excludedCount = blockActionsViewModel.tunnelExcludedAppIds.size,
                                activeAppIds = blockActionsViewModel.tunnelIncludedAppIds.ifEmpty {
                                    blockActionsViewModel.tunnelExcludedAppIds
                                },
                                onClick = { navController.navigate(Route.AppSplitRules) },
                            )

                            ConnectPlanCard(
                                navController = navController,
                                currentPlan = currentPlan,
                                isPollingSubscriptionBalance = subscriptionBalanceViewModel.isPollingSubscriptionBalance,
                                usedBytes = subscriptionBalanceViewModel.usedBalanceByteCount,
                                availableBytes = availableBytes,
                                pendingBytes = subscriptionBalanceViewModel.pendingBalanceByteCount,
                                meanReliabilityWeight = meanReliabilityWeight,
                                totalReferrals = totalReferrals,
                                dailyByteCount = dailyByteCount,
                                onReferralClick = { navController.navigate(Route.Referrals) },
                            )

                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            paneActions()
                        }
                    }
                },
                // above (in tabletop, on the raised half): the connect graphic,
                // its status and the peers online
                connectPane = {
                    ConnectGraphicLayout(
                        minSize = CONNECT_GRAPHIC_MIN_DP.dp,
                        maxSize = CONNECT_GRAPHIC_PANE_MAX_DP.dp,
                        topSpacing = 0.dp,
                        gap = 24.dp,
                        graphic = connectGraphic,
                        below = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                connectStatusIndicator()
                                Spacer(modifier = Modifier.height(12.dp))
                                NetworkPeersStatusLine(
                                    peerCount = networkPeersViewModel.connectedCount,
                                    onClick = { presentSelectProvider(true) },
                                    horizontalArrangement = Arrangement.Center,
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(
                                WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
                            )
                            .padding(horizontal = 16.dp)
                            .padding(top = SlowInternetPillRoom, bottom = 16.dp),
                    )
                },
            )
        }

        // slow internet (apple/DELAY.md §7.1): follows the sdk's level directly,
        // while a connection is requested. At the top end of the screen, which
        // is the top of the connect pane in every arrangement; beside the
        // statistics it keeps to that pane's width
        val connectPaneWidth = if (arrangement == ConnectArrangement.SideBySide) {
            val screenWidth = screenInWindow.width / density.density
            mainPaneWidth(
                split = sideBySideSplit(
                    containerStart = screenInWindow.left / density.density,
                    width = screenWidth,
                    fold = windowLayout.hinge,
                    sidePaneOnLeft = sidePaneOnLeft,
                ),
                width = screenWidth,
                sidePaneOnLeft = sidePaneOnLeft,
            ).coerceAtLeast(0f).dp
        } else {
            Dp.Unspecified
        }
        SlowInternetPill(
            transportDelay = transportDelay,
            available = displayConnectStatus != ConnectStatus.DISCONNECTED,
            hidden = referralToastVisible,
            setPillVisible = connectViewModel.setSlowInternetPillVisible,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .widthIn(max = connectPaneWidth),
        )
    }

    // the location picker over its pane, in the pane arrangements: anchored
    // under the location row, or over the whole pane when the room under the
    // row is short
    if (locationPickerOpen && arrangement != ConnectArrangement.Drawer) {
        val pane = locationPaneOnScreen
        val row = locationRowOnScreen
        val minRoomPx = with(density) { LocationDialogMinHeight.toPx() }
        val top = if (row.bottom in pane.top..(pane.bottom - minRoomPx)) row.bottom else pane.top
        LocationPickerDialog(
            regionOnScreen = Rect(pane.left, top, pane.right, pane.bottom),
            onDismiss = { setLocationPickerOpen(false) },
        ) {
            val keyboardController = LocalSoftwareKeyboardController.current
            val fetchLocationsState by remember { locationsViewModel.filterLocationsState }.collectAsState()
            val dialogListState = rememberLocationListState(locationListPosition)
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 4.dp, top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(id = R.string.browse_locations),
                        style = TopBarTitleTextStyle,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { setLocationPickerOpen(false) }) {
                        Text(stringResource(id = R.string.close), color = Pink)
                    }
                }
                BrowseLocations(
                    connectCountries = locationsViewModel.connectCountries,
                    cities = locationsViewModel.cities,
                    regions = locationsViewModel.regions,
                    devices = locationsViewModel.devices,
                    bestSearchMatches = locationsViewModel.bestSearchMatches,
                    selectedLocation = connectViewModel.selectedLocation,
                    keyboardController = keyboardController,
                    currentSearchQuery = locationsViewModel.currentSearchQuery,
                    setSearchQueryTextFieldValue = locationsViewModel.setSearchQueryTextFieldValue,
                    searchQueryTextFieldValue = locationsViewModel.searchQueryTextFieldValue,
                    fetchLocationsState = fetchLocationsState,
                    // a pick updates the connect target in place
                    connect = {
                        connectViewModel.connect(it)
                        setLocationPickerOpen(false)
                    },
                    getLocationColor = locationsViewModel.getLocationColor,
                    filterLocations = locationsViewModel.filterLocations,
                    lazyListState = dialogListState,
                    refreshLocations = locationsViewModel.refreshLocations,
                    networkPeers = networkPeersViewModel.connectedProvidePeers,
                    onConnectPeer = { peer ->
                        networkPeersViewModel.connectLocationForPeer(peer)?.let { location ->
                            connectViewModel.connect(location)
                            setLocationPickerOpen(false)
                        }
                    },
                )
            }
        }
    }

    DataInfoSheet(
        presented = dataInfoPresented,
        onDismiss = { dataInfoPresented = false },
        startBalanceByteCount = dailyByteCount,
        availableByteCount = availableBytes,
        pendingByteCount = subscriptionBalanceViewModel.pendingBalanceByteCount,
        showFreeRefresh = dataInfoShowsFreeRefresh(currentPlan),
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
    // in tabletop, the bottom edge of the fold measured from the top of the
    // scaffold: the open drawer stops there instead of crossing it
    foldBottom: Dp? = null,
) {

    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    // Only the part of the system bottom inset that nothing below the sheet
    // absorbs may be added to the sheet geometry. Above the bottom tab bar
    // (phones) NavigationSuiteScaffold consumes the inset — the tab bar itself
    // clears the system bar — so the remainder is zero. Next to a navigation
    // rail (tablet landscape) the sheet reaches the screen edge and the full
    // inset remains. Adding the raw inset here double-counted it above the tab
    // bar, so the gap under the connect button varied with the device's nav
    // mode (gesture vs 3-button) instead of staying standard.
    var consumedWindowInsets by remember { mutableStateOf(WindowInsets(0, 0, 0, 0)) }
    val unconsumedBottomInset = with(density) {
        (WindowInsets.navigationBars.getBottom(this) - consumedWindowInsets.getBottom(this))
            .coerceAtLeast(0)
            .toDp()
    }

    // The collapsed drawer shows exactly the above-the-fold content — the
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

    // the drawer is the phone layout's: the pane arrangements (AdaptiveLayout.kt)
    // take over from medium width up, so it is always the edge-to-edge sheet
    Box(
        // reports the insets ancestors already consumed (the tab bar scaffold),
        // so the sheet geometry above only adds the unconsumed remainder
        modifier = Modifier
            .onConsumedWindowInsetsChanged { consumedWindowInsets = it }
    ) {

        // The main content stays outside the drawer's BoxWithConstraints: the
        // connect graphic in it is movable content (ConnectScreen), and a
        // subcomposition between it and the arrangements would drop its state.
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

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val layoutHeightPx = constraints.maxHeight
            val peekPx = with(density) { sheetPeekHeight.roundToPx() }
            // The expanded drawer stops part-way up the screen, leaving the
            // connect graphic behind it partially exposed, instead of running to
            // the status bar with the drag handle under it (iOS caps its sheet
            // the same way). It never runs past the top of a short window, and in
            // tabletop it stops at the fold (connectDrawerExpandedHeight). The
            // scrolling content is capped to the expanded height less the drag
            // handle; the content scrolls within it.
            val sheetContentMaxHeight = with(density) {
                (connectDrawerExpandedHeight(
                    layoutHeight = maxHeight.value,
                    peekHeight = sheetPeekHeight.value,
                    fraction = ConnectSheetExpandedFraction,
                    foldBottom = foldBottom?.value,
                ).dp - dragHandleHeightPx.toDp()).coerceAtLeast(0.dp)
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

            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .offset {
                        val offset = drawerState.draggable.offset
                        IntOffset(0, (if (offset.isNaN()) restingOffsetPx else offset).roundToInt())
                    }
                    .onSizeChanged { sheetHeightPx = it.height }
                    .nestedScroll(separateSheetAndContentGestures),
                shape = BottomSheetDefaults.ExpandedShape,
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
}

/**
 * The phone layout's main content above the drawer: the connect graphic and
 * its status, centered in the room the collapsed drawer leaves. A short
 * window (a landscape split screen, a flip cover, a bubble) shrinks the
 * graphic and its spacing so the status stays above the drawer; a phone in
 * portrait keeps the full 256dp graphic.
 */
@Composable
fun ConnectMainContent(
    graphic: @Composable () -> Unit,
    status: @Composable () -> Unit,
) {
    ConnectGraphicLayout(
        minSize = CONNECT_GRAPHIC_MIN_DP.dp,
        maxSize = CONNECT_GRAPHIC_FULL_DP.dp,
        topSpacing = 16.dp,
        gap = 32.dp,
        graphic = graphic,
        below = status,
        modifier = Modifier
            .fillMaxSize()
            .animateContentSize()
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp),
    )
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

/** Room at the top of a connect pane for the slow internet pill above the graphic. */
private val SlowInternetPillRoom = 48.dp

/** The connect buttons at the foot of a connect pane keep to a readable width. */
private val PaneActionsMaxWidth = 400.dp

/** Under this much room below the location row, the location dialog covers the whole pane. */
private val LocationDialogMinHeight = 320.dp
