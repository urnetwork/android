package com.bringyour.network.ui

import com.bringyour.network.ui.introduction.LocalIntroConnector
import com.bringyour.network.ui.introduction.IntroConnectorState
import com.bringyour.network.ui.introduction.FloatingIntroConnector
import androidx.navigation.compose.currentBackStackEntryAsState
import com.bringyour.network.ui.introduction.IntroductionQuickConnect
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import android.content.res.Configuration
import android.util.Log
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.StackedLineChart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.bringyour.network.ui.account.AccountScreen
import com.bringyour.network.ui.settings.DeveloperScreen
import com.bringyour.network.ui.account.ProviderIdentitiesScreen
import com.bringyour.network.ui.components.overlays.FullScreenOverlay
import com.bringyour.network.ui.components.overlays.WelcomeAnimatedMainOverlay
import com.bringyour.network.ui.components.referral.LocalReferralTerms
import com.bringyour.network.ui.components.referral.ReferralRoyalToast
import com.bringyour.network.ui.connect.ConnectScreen
import com.bringyour.network.ui.connect.ConnectViewModel
import com.bringyour.network.ui.feedback.FeedbackScreen
import com.bringyour.network.ui.settings.SettingsViewModel
import com.bringyour.network.ui.shared.viewmodels.OverlayViewModel
import com.bringyour.network.ui.shared.viewmodels.PlanViewModel
import com.bringyour.network.ui.shared.viewmodels.ReferralCelebration
import com.bringyour.network.ui.shared.viewmodels.ReferralCodeViewModel
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.MainBorderBase
import com.bringyour.network.utils.isTablet
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.navigation
import androidx.navigation.toRoute
import com.bringyour.network.ui.account.AccountViewModel
import com.bringyour.network.ui.components.nestedLinkBottomSheet.NestedLinkBottomSheet
import com.bringyour.network.ui.connect.BrowseLocationsScreen
import com.bringyour.network.ui.connect.LocationsListViewModel
import com.bringyour.network.ui.leaderboard.LeaderboardScreen
import com.bringyour.network.ui.referrals.ReferralsScreen
import com.bringyour.network.ui.widgets.WidgetsScreen
import com.bringyour.network.ui.profile.ProfileScreen
import com.bringyour.network.ui.profile.ProfileViewModel
import com.bringyour.network.ui.settings.SettingsScreen
import com.bringyour.network.ui.shared.viewmodels.SubscriptionBalanceViewModel
import com.bringyour.network.ui.wallet.EarningsViewModel
import com.bringyour.network.ui.wallet.EarningsScreen
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.bringyour.network.R
import com.bringyour.network.TAG
import com.bringyour.network.ui.api_error.ApiErrorScreen
import com.bringyour.network.ui.balance_codes.BalanceCodesScreen
import com.bringyour.network.ui.blocked_regions.BlockedRegionsScreen
import com.bringyour.network.ui.components.overlays.OverlayMode
import com.bringyour.network.ui.introduction.IntroductionInitial
import com.bringyour.network.ui.introduction.IntroductionReferral
import com.bringyour.network.ui.introduction.IntroductionSettings
import com.bringyour.network.ui.introduction.IntroductionUsageBar
import com.bringyour.network.ui.shared.models.BundleStore
import com.bringyour.network.ui.shared.models.ProvideControlMode
import com.bringyour.network.ui.shared.viewmodels.AccountPointsViewModel
import com.bringyour.network.ui.shared.viewmodels.NetworkReliabilityViewModel
import com.bringyour.network.ui.shared.viewmodels.Plan
import com.bringyour.network.ui.shared.viewmodels.SolanaPaymentViewModel
import com.bringyour.network.ui.stats.AppSplitRulesScreen
import com.bringyour.network.ui.stats.ContractStatsScreen
import com.bringyour.network.ui.connect.providerlocations.MockLocationGuideScreen
import com.bringyour.network.ui.connect.providerlocations.MockLocationSection
import com.bringyour.network.ui.connect.providerlocations.ProviderLocationsScreen
import com.bringyour.network.ui.stats.DnsSettingsScreen
import com.bringyour.network.ui.stats.TransportSettingsKind
import com.bringyour.network.ui.stats.TransportSettingsScreen
import com.bringyour.network.ui.stats.SplitRulesScreen
import com.bringyour.network.ui.theme.Pink
import com.bringyour.network.ui.upgrade.UpgradeScreen
import com.bringyour.sdk.ReliabilityWindow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainNavHost(
    earningsViewModel: EarningsViewModel,
    settingsViewModel: SettingsViewModel,
    planViewModel: PlanViewModel,
    subscriptionBalanceViewModel: SubscriptionBalanceViewModel,
    overlayViewModel: OverlayViewModel,
    animateIn: Boolean,
    targetLink: String?,
    defaultLocation: String?,
    activityResultSender: ActivityResultSender?,
    bundleStore: BundleStore?,
    isPro: Boolean,
    mainNavViewModel: MainNavViewModel = hiltViewModel<MainNavViewModel>(),
    referralCodeViewModel: ReferralCodeViewModel = hiltViewModel<ReferralCodeViewModel>(),
    connectViewModel: ConnectViewModel = hiltViewModel<ConnectViewModel>(),
    locationsListViewModel: LocationsListViewModel = hiltViewModel<LocationsListViewModel>(),
    networkReliabilityViewModel: NetworkReliabilityViewModel = hiltViewModel<NetworkReliabilityViewModel>(),
    solanaPaymentViewModel: SolanaPaymentViewModel = hiltViewModel<SolanaPaymentViewModel>(),
) {

    // the referral cap and bonus come from the server with the referral code;
    // everything signed-in reads them from here instead of hardcoding numbers
    val referralTerms by referralCodeViewModel.terms.collectAsState()

    CompositionLocalProvider(LocalReferralTerms provides referralTerms) {
        MainNavHostContent(
        earningsViewModel = earningsViewModel,
        settingsViewModel = settingsViewModel,
        planViewModel = planViewModel,
        subscriptionBalanceViewModel = subscriptionBalanceViewModel,
        overlayViewModel = overlayViewModel,
        animateIn = animateIn,
        targetLink = targetLink,
        defaultLocation = defaultLocation,
        activityResultSender = activityResultSender,
        bundleStore = bundleStore,
        isPro = isPro,
        mainNavViewModel = mainNavViewModel,
        referralCodeViewModel = referralCodeViewModel,
        connectViewModel = connectViewModel,
        locationsListViewModel = locationsListViewModel,
        networkReliabilityViewModel = networkReliabilityViewModel,
        solanaPaymentViewModel = solanaPaymentViewModel
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainNavHostContent(
    earningsViewModel: EarningsViewModel,
    settingsViewModel: SettingsViewModel,
    planViewModel: PlanViewModel,
    subscriptionBalanceViewModel: SubscriptionBalanceViewModel,
    overlayViewModel: OverlayViewModel,
    animateIn: Boolean,
    targetLink: String?,
    defaultLocation: String?,
    activityResultSender: ActivityResultSender?,
    bundleStore: BundleStore?,
    isPro: Boolean,
    mainNavViewModel: MainNavViewModel = hiltViewModel<MainNavViewModel>(),
    referralCodeViewModel: ReferralCodeViewModel = hiltViewModel<ReferralCodeViewModel>(),
    connectViewModel: ConnectViewModel = hiltViewModel<ConnectViewModel>(),
    locationsListViewModel: LocationsListViewModel = hiltViewModel<LocationsListViewModel>(),
    networkReliabilityViewModel: NetworkReliabilityViewModel = hiltViewModel<NetworkReliabilityViewModel>(),
    solanaPaymentViewModel: SolanaPaymentViewModel = hiltViewModel<SolanaPaymentViewModel>(),
) {

    val currentTopLevelRoute by mainNavViewModel.currentTopLevelRoute.collectAsState()
    val currentRoute by mainNavViewModel.currentRoute.collectAsState()
    val reliabilityWindow by networkReliabilityViewModel.reliabilityWindow.collectAsState()
    val totalReferralCount by referralCodeViewModel.totalReferralCount.collectAsState()
    val referralCode by referralCodeViewModel.referralCode.collectAsState()
    val pendingReferralCelebration by referralCodeViewModel.pendingCelebration.collectAsState()
    val pendingSolanaSubReference by solanaPaymentViewModel.pendingSolanaSubscriptionReference.collectAsState()
    val isCheckingSolanaTransaction by subscriptionBalanceViewModel.isCheckingSolanaTransaction.collectAsState()
    val displayIntroFunnel by mainNavViewModel.displayIntroFunnel.collectAsState()
    val allowPromptIntroFunnel by mainNavViewModel.allowDisplayIntroFunnel.collectAsState()
    val hasActiveSubscription by subscriptionBalanceViewModel.hasActiveSubscription.collectAsState()

    val navItemColors = NavigationSuiteDefaults.itemColors(
        navigationBarItemColors = NavigationBarItemDefaults.colors(
            indicatorColor = Color.Transparent,
            selectedIconColor = Pink
        ),
        navigationRailItemColors = NavigationRailItemDefaults.colors(
            indicatorColor = Color.Transparent,
            selectedIconColor = Pink
        ),
        navigationDrawerItemColors = NavigationDrawerItemDefaults.colors()
    )

    val customColors = NavigationSuiteDefaults.colors(
        navigationRailContainerColor = Black,
        navigationBarContentColor = Black,
        navigationBarContainerColor = Black,
    )

    val configuration = LocalConfiguration.current

    val adaptiveInfo = currentWindowAdaptiveInfo()

    val lifecycleOwner = LocalLifecycleOwner.current

    val scope = rememberCoroutineScope()

    // hoisted here so re-tapping the connect tab can collapse it
    val connectActionsSheetState = rememberStandardBottomSheetState(
        initialValue = SheetValue.PartiallyExpanded,
        skipHiddenState = true
    )

    val navSuiteLayoutType = with(adaptiveInfo) {

        if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE && isTablet()) {
            NavigationSuiteType.NavigationRail
        } else {
            NavigationSuiteType.NavigationBar
        }

    }

    val navController = rememberNavController()

    val nestedLinkScaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(
            initialValue = if (defaultLocation != null) SheetValue.Expanded else SheetValue.Hidden,
            skipHiddenState = false
        )
    )

    DisposableEffect(Unit) {

        val listener = NavController.OnDestinationChangedListener { _, destination, _ ->

            val route = Route.fromString(destination.route ?: "")
            if (route == Route.Connect && currentTopLevelRoute.route != Route.Connect) {
                mainNavViewModel.setCurrentTopLevelRoute(TopLevelScaffoldRoutes.CONNECT_CONTAINER)
            }

            if (route == Route.Support && currentTopLevelRoute.route != Route.Support) {
                mainNavViewModel.setCurrentTopLevelRoute(TopLevelScaffoldRoutes.SUPPORT)
            }

            if (route == Route.AccountContainer && currentTopLevelRoute.route != Route.AccountContainer) {
                mainNavViewModel.setCurrentTopLevelRoute(TopLevelScaffoldRoutes.ACCOUNT_CONTAINER)
            }

            mainNavViewModel.setCurrentRoute(Route.fromString(destination.route ?: ""))
        }

        navController.addOnDestinationChangedListener(listener)

        onDispose {
            navController.removeOnDestinationChangedListener(listener)
        }
    }

    /**
     * Select a bottom-bar tab. The bar's own onClick and the dashboard widget both
     * go through here, so a re-tap pops the tab to its root (and collapses the
     * connect drawer) the same way from either entry.
     */
    val selectTopLevelRoute: (TopLevelScaffoldRoutes) -> Unit = { screen ->

        if (currentTopLevelRoute.route == Route.AccountContainer
            && screen.route == Route.AccountContainer
            && currentRoute != Route.Account
        ) {
            navController.popBackStack(Route.Account, inclusive = false)
        } else if (
            currentTopLevelRoute.route == Route.ConnectContainer
            && screen.route == Route.ConnectContainer
        ) {

            if (currentRoute != Route.Connect) {
                navController.popBackStack(Route.Connect, inclusive = false)
            } else {
                // already on the connect screen: collapse the drawer
                scope.launch {
                    connectActionsSheetState.partialExpand()
                }
            }

        } else {

            navController.navigate(screen.route) {
                // from https://developer.android.com/develop/ui/compose/navigation#bottom-nav
                // Pop up to the start destination of the graph to
                // avoid building up a large stack of destinations
                // on the back stack as users select items
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                // Avoid multiple copies of the same destination when
                // reselecting the same item
                launchSingleTop = true
                // Restore state when reselecting a previously selected item
                restoreState = true

            }
        }
        mainNavViewModel.setCurrentTopLevelRoute(screen)
    }

    /**
     * A Home Screen widget tap asked for a screen (MainApplication.widgetRoute,
     * set by QuickConnectActivity): go to the connect tab, then push the
     * provider details or the client contract details on top. Observed as a
     * flow so it works whether the app was cold-started for the tap or was
     * already running behind the launcher.
     */
    val widgetApp = androidx.compose.ui.platform.LocalContext.current.applicationContext as? com.bringyour.network.MainApplication
    val widgetRoute by (widgetApp?.widgetRoute ?: kotlinx.coroutines.flow.MutableStateFlow<String?>(null)).collectAsState()
    LaunchedEffect(widgetRoute) {
        val route = widgetRoute ?: return@LaunchedEffect
        widgetApp?.widgetRoute?.value = null
        if (route == com.bringyour.network.QuickConnectActivity.ROUTE_CONNECT) {
            // the dashboard widget does exactly what a tap on the Connect tab does
            selectTopLevelRoute(TopLevelScaffoldRoutes.CONNECT_CONTAINER)
            return@LaunchedEffect
        }
        navController.navigate(TopLevelScaffoldRoutes.CONNECT_CONTAINER.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
        mainNavViewModel.setCurrentTopLevelRoute(TopLevelScaffoldRoutes.CONNECT_CONTAINER)
        when (route) {
            com.bringyour.network.QuickConnectActivity.ROUTE_PROVIDER_LOCATIONS ->
                navController.navigate(Route.ProviderLocations)
            com.bringyour.network.QuickConnectActivity.ROUTE_CONTRACT_STATS ->
                navController.navigate(Route.ContractStats(provider = false))
        }
    }

    // the ur.io bridge returned a signed wallet-connect challenge: show the earnings
    // screen, where the view model validates and attaches the coldkey
    val pendingEarningsNavigation by earningsViewModel.pendingEarningsNavigation.collectAsState()
    LaunchedEffect(pendingEarningsNavigation) {
        if (!pendingEarningsNavigation) {
            return@LaunchedEffect
        }
        earningsViewModel.consumeEarningsNavigation()
        selectTopLevelRoute(TopLevelScaffoldRoutes.ACCOUNT_CONTAINER)
        navController.navigate(Route.Earnings)
    }

    /**
     * For initial intro funnel prompting
     */
    LaunchedEffect(isPro, allowPromptIntroFunnel) {

        if (isPro) {
            mainNavViewModel.setDisplayIntroFunnel(false)
        } else {
            if (allowPromptIntroFunnel) {
                // display intro funnel
                mainNavViewModel.setDisplayIntroFunnel(true)
                // set time last prompted in localstorage
                mainNavViewModel.setIntroFunnelLastPrompted()
            }
        }

    }

    /**
     * On upgrade success, if in the intro funnel, close flow
     */
    LaunchedEffect(Unit) {
        planViewModel.upgradeSuccessSequence.collect { sequence ->
            if (!planViewModel.consumeUpgradeSuccessSequence(sequence)) {
                return@collect
            }

            overlayViewModel.launch(OverlayMode.Upgrade)
            subscriptionBalanceViewModel.pollSubscriptionBalance()

            if (mainNavViewModel.displayIntroFunnel.value) {
                mainNavViewModel.setDisplayIntroFunnel(false)
            } else {
                navController.popBackStack()
            }
        }
    }

    /**
     * EVERY billing error reaches the user, with a way out.
     *
     * `changePlanError` was set in half a dozen places -- a declined card, a billing
     * client failure, "Network not found" -- and rendered NOWHERE. A failed purchase
     * therefore looked exactly like nothing happening: the spinner stopped and the user
     * was left staring at the plan screen with no idea what went wrong or what to do.
     *
     * A payment error the user cannot see is a payment error they will hit again, so
     * this always offers the remedy (try again) rather than just reporting the failure.
     */
    planViewModel.changePlanError?.let { changePlanError ->
        AlertDialog(
            onDismissRequest = { planViewModel.setChangePlanError(null) },
            title = { Text(stringResource(id = R.string.payment_problem)) },
            text = { Text(changePlanError) },
            confirmButton = {
                val retryUpgrade = planViewModel.retryUpgrade
                TextButton(
                    onClick = {
                        planViewModel.setChangePlanError(null)
                        retryUpgrade?.invoke()
                    }
                ) {
                    Text(
                        stringResource(
                            id = if (retryUpgrade != null) R.string.try_again else R.string.close
                        )
                    )
                }
            },
            dismissButton = {
                // only a second button when there is actually something to retry
                if (planViewModel.retryUpgrade != null) {
                    TextButton(onClick = { planViewModel.setChangePlanError(null) }) {
                        Text(stringResource(id = R.string.close))
                    }
                }
            }
        )
    }

    /**
     * The post-payment confirmation poll exhausted its 2-minute budget without the
     * server confirming the subscription. This used to end in a log line
     * ("polling timed out") -- the honest terminal state is: payment received,
     * confirmation still in flight, the plan updates by itself, do NOT buy again.
     */
    var showConfirmationDelayedDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        subscriptionBalanceViewModel.confirmationTimedOutSequence.collect { sequence ->
            if (!subscriptionBalanceViewModel.consumeConfirmationTimedOutSequence(sequence)) {
                return@collect
            }

            showConfirmationDelayedDialog = true
        }
    }

    /**
     * The purchase is persisted client-side but the server never gave a terminal
     * answer to the report within the bounded in-session retries (Play flavor). Same
     * honest terminal state as the poll timeout: payment received, confirmation in
     * flight, do NOT buy again -- the daily reconcile worker carries the report.
     */
    LaunchedEffect(Unit) {
        planViewModel.purchaseReportDeferredSequence.collect { sequence ->
            if (!planViewModel.consumePurchaseReportDeferredSequence(sequence)) {
                return@collect
            }

            showConfirmationDelayedDialog = true
        }
    }

    if (showConfirmationDelayedDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmationDelayedDialog = false },
            title = { Text(stringResource(id = R.string.payment_received_title)) },
            text = { Text(stringResource(id = R.string.payment_confirmation_delayed)) },
            confirmButton = {
                TextButton(onClick = { showConfirmationDelayedDialog = false }) {
                    Text(stringResource(id = R.string.close))
                }
            }
        )
    }

    /**
     * The server verified the Play purchase but it belongs to a DIFFERENT network
     * than the one logged in. The purchase is real (it stays acknowledged so Play
     * does not auto-refund it) and the linked network is the one credited -- so no
     * success overlay here, just the honest way out: use the account it was
     * purchased under.
     */
    var showPurchaseWrongNetworkDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        planViewModel.purchaseWrongNetworkSequence.collect { sequence ->
            if (!planViewModel.consumePurchaseWrongNetworkSequence(sequence)) {
                return@collect
            }

            showPurchaseWrongNetworkDialog = true
        }
    }

    if (showPurchaseWrongNetworkDialog) {
        AlertDialog(
            onDismissRequest = { showPurchaseWrongNetworkDialog = false },
            text = { Text(stringResource(id = R.string.subscription_purchased_under_different_account)) },
            confirmButton = {
                TextButton(onClick = { showPurchaseWrongNetworkDialog = false }) {
                    Text(stringResource(id = R.string.close))
                }
            }
        )
    }

    /**
     * A purchase Play accepted but has NOT completed (awaiting approval, or an
     * out-of-band payment). There is nothing to poll for -- the PURCHASED state does
     * not arrive now -- so just tell the user, and do NOT close the screen or claim an
     * upgrade.
     *
     * Without this the pending case was silent: the spinner stopped, nothing else
     * happened, and the user concluded the purchase had failed and bought again.
     */
    LaunchedEffect(Unit) {
        planViewModel.purchasePendingSequence.collect { sequence ->
            if (!planViewModel.consumePurchasePendingSequence(sequence)) {
                return@collect
            }

            overlayViewModel.launch(OverlayMode.PurchasePending)
        }
    }

    /**
     * Recovered Play purchases should refresh entitlement state without closing the current screen.
     */
    LaunchedEffect(Unit) {
        planViewModel.restoredSubscriptionSequence.collect { sequence ->
            if (!planViewModel.consumeRestoredSubscriptionSequence(sequence)) {
                return@collect
            }

            overlayViewModel.launch(OverlayMode.Upgrade)
            subscriptionBalanceViewModel.pollSubscriptionBalance()
        }
    }

    /**
     * This is for listening to Solana Wallet subscriptions
     * If there is a pending sub reference + the app regains focus, we start polling the subscription balance
     */
    DisposableEffect(lifecycleOwner, pendingSolanaSubReference) {

        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (!pendingSolanaSubReference.isNullOrEmpty()) {
                    scope.launch {
                        // poll subscription balance until it's updated
                        subscriptionBalanceViewModel.pollSolanaTransaction()
                        solanaPaymentViewModel.setPendingSolanaSubscriptionReference(null)
                    }
                }
            }
        }

        if (pendingSolanaSubReference != null) {
            lifecycleOwner.lifecycle.addObserver(observer)
        }

        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(lifecycleOwner) {

        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                mainNavViewModel.refreshTokenOnForeground()
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    AnimatedContent(
        targetState = displayIntroFunnel,
        label = "intro-main-switch",
        transitionSpec = {
            if (targetState) {
                // Main -> Intro: Intro slides up from bottom and fades in
                (slideInVertically(initialOffsetY = { it }) + fadeIn()) togetherWith
                        // Main fades out (keep it simple to avoid conflicting motion)
                        fadeOut()
            } else {
                // Intro -> Main (closing): Intro slides down and fades out
                fadeIn() togetherWith
                        (slideOutVertically(targetOffsetY = { it }) + fadeOut())
            }
        }
    ) { introIsVisible ->
        if (introIsVisible) {
            IntroNavHost(
                dismiss = {
                    mainNavViewModel.setDisplayIntroFunnel(false)
                },
                subscriptionBalanceViewModel = subscriptionBalanceViewModel,
                meanReliabilityWeight = reliabilityWindow?.meanReliabilityWeight ?: 0.0,
                totalReferralCount = totalReferralCount,
                referralCode = referralCode,
                provideControlMode = settingsViewModel.provideControlMode,
                setProvideControlMode = settingsViewModel.setProvideControlMode,
                provideIndicatorColor = settingsViewModel.provideIndicatorColor,
                allowProvideCell = settingsViewModel.allowProvideOnCell.collectAsState().value,
                toggleProvideCell = settingsViewModel.toggleAllowProvideOnCell,
                planViewModel = planViewModel,
                solanaPaymentViewModel = solanaPaymentViewModel,
                isCheckingSolanaTransaction = isCheckingSolanaTransaction,
                overlayViewModel = overlayViewModel
            )
        } else {

            NestedLinkBottomSheet(
                scaffoldState = nestedLinkScaffoldState,
                targetLink = targetLink,
                defaultLocation = defaultLocation,
                connectViewModel = connectViewModel
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Black)
                    // .padding(top = 36.dp)
                    // .systemBarsPadding()
                    // .windowInsetsPadding(WindowInsets.systemBars)
                ) {

                    NavigationSuiteScaffold(
                        containerColor = Black,
                        contentColor = Black,
                        navigationSuiteColors = customColors,
                        layoutType = navSuiteLayoutType,

                        navigationSuiteItems = {
                            TopLevelScaffoldRoutes.entries.forEach { screen ->
                                item(
                                    icon = {

                                        val iconRes = if (screen == currentTopLevelRoute) {
                                            screen.selectedIcon
                                        } else {
                                            screen.unselectedIcon
                                        }

                                        if (screen.route == Route.Leaderboard) {
                                            Icon(imageVector = Icons.Filled.StackedLineChart, contentDescription = stringResource(id = R.string.leaderboard))
                                        } else {
                                            Icon(
                                                painter = painterResource(id = iconRes),
                                                contentDescription = screen.description,
                                                modifier = Modifier.testTag("acceptance.nav.${screen.description.lowercase()}")
                                            )
                                        }

                                    },
                                    selected = screen == currentTopLevelRoute,
                                    onClick = { selectTopLevelRoute(screen) },
                                    colors = navItemColors,
                                )
                            }
                        }
                    ) {

                        if (isTablet()) {

                            Column(
                                modifier = Modifier.padding(bottom = 1.dp)
                            ) {
                                Row {
                                    MainNavContent(
                                        settingsViewModel = settingsViewModel,
                                        planViewModel = planViewModel,
                                        overlayViewModel = overlayViewModel,
                                        navController = navController,
                                        earningsViewModel = earningsViewModel,
                                        connectViewModel = connectViewModel,
                                        locationsListViewModel = locationsListViewModel,
                                        activityResultSender = activityResultSender,
                                        subscriptionBalanceViewModel = subscriptionBalanceViewModel,
                                        referralCodeViewModel = referralCodeViewModel,
                                        bundleStore = bundleStore,
                                        reliabilityWindow = reliabilityWindow,
                                        totalReferralCount = totalReferralCount,
                                        solanaPaymentViewModel = solanaPaymentViewModel,
                                        isCheckingSolanaTransaction = isCheckingSolanaTransaction,
                                        isPro = isPro,
                                        connectActionsSheetState = connectActionsSheetState
                                    )
                                }

                                if (configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                                    HorizontalDivider(
                                        modifier = Modifier
                                            .height(1.dp)
                                            .fillMaxWidth(),
                                        color = MainBorderBase
                                    )
                                }
                            }

                        } else {

                            Column(
                                modifier = Modifier.padding(bottom = 1.dp)
                            ) {
                                MainNavContent(
                                    settingsViewModel = settingsViewModel,
                                    planViewModel = planViewModel,
                                    overlayViewModel = overlayViewModel,
                                    navController = navController,
                                    earningsViewModel = earningsViewModel,
                                    connectViewModel = connectViewModel,
                                    locationsListViewModel = locationsListViewModel,
                                    activityResultSender = activityResultSender,
                                    subscriptionBalanceViewModel = subscriptionBalanceViewModel,
                                    referralCodeViewModel = referralCodeViewModel,
                                    bundleStore = bundleStore,
                                    reliabilityWindow = reliabilityWindow,
                                    totalReferralCount = totalReferralCount,
                                    solanaPaymentViewModel = solanaPaymentViewModel,
                                    isCheckingSolanaTransaction = isCheckingSolanaTransaction,
                                    isPro = isPro,
                                    connectActionsSheetState = connectActionsSheetState
                                )

                                HorizontalDivider(
                                    modifier = Modifier
                                        .height(1.dp)
                                        .fillMaxWidth(),
                                    color = MainBorderBase
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    WelcomeAnimatedMainOverlay(
        animateIn = animateIn,
    )

    FullScreenOverlay(
        overlayViewModel,
        referralCode = referralCode,
        planUpgradeConfirmed = hasActiveSubscription,
        referralCelebrationJoined = pendingReferralCelebration?.joined ?: 1L,
        onReferralCelebrationDismissed = {
            referralCodeViewModel.clearCelebration()
        },
    )

    /**
     * Referral celebrations: the first referral gets the full-screen crowning
     * overlay; later ones get a passing gold toast. Detected by the referral
     * poll against the per-network celebrated baseline.
     */
    LaunchedEffect(pendingReferralCelebration) {
        val celebration = pendingReferralCelebration ?: return@LaunchedEffect
        if (celebration.isFirst) {
            overlayViewModel.launch(OverlayMode.ReferralCelebration)
        } else {
            // let the toast play, then clear it
            delay(5000)
            referralCodeViewModel.clearCelebration()
        }
    }

    // keep the last toast content mounted so the exit animation has text
    var lastToastCelebration by remember {
        mutableStateOf<ReferralCelebration?>(null)
    }
    pendingReferralCelebration?.let { celebration ->
        if (!celebration.isFirst) {
            lastToastCelebration = celebration
        }
    }

    lastToastCelebration?.let { celebration ->
        ReferralRoyalToast(
            visible = pendingReferralCelebration != null && pendingReferralCelebration?.isFirst == false,
            text = pluralStringResource(
                id = R.plurals.referral_toast_joined,
                count = celebration.joined.toInt(),
                celebration.joined.toInt(),
                LocalReferralTerms.current.bonusGibPerDay
            ),
            onClick = {
                referralCodeViewModel.clearCelebration()
                navController.navigate(Route.Referrals)
            },
        )
    }

}

@Composable
fun IntroNavHost(
    dismiss: () -> Unit,
    meanReliabilityWeight: Double,
    totalReferralCount: Long,
    referralCode: String,
    provideControlMode: ProvideControlMode,
    setProvideControlMode: (ProvideControlMode) -> Unit,
    provideIndicatorColor: Color,
    allowProvideCell: Boolean,
    toggleProvideCell: () -> Unit,
    subscriptionBalanceViewModel: SubscriptionBalanceViewModel,
    planViewModel: PlanViewModel,
    isCheckingSolanaTransaction: Boolean,
    solanaPaymentViewModel: SolanaPaymentViewModel,
    overlayViewModel: OverlayViewModel
) {

    val introNavController = rememberNavController()
    val scope = rememberCoroutineScope()

    // the connector mark that flies from page 1's route line into the header
    val introConnector = remember { IntroConnectorState() }
    val introEntry by introNavController.currentBackStackEntryAsState()
    LaunchedEffect(introEntry) {
        val route = introEntry?.destination?.route ?: return@LaunchedEffect
        introConnector.inHeader = !route.contains(IntroRoute.IntroductionInitial::class.qualifiedName.toString())
    }

    CompositionLocalProvider(LocalIntroConnector provides introConnector) {
    Box(modifier = Modifier.fillMaxSize()) {

    NavHost(
        navController = introNavController,
        startDestination = IntroRoute.IntroductionInitial,
    ) {

        composable<IntroRoute.IntroductionInitial> {
            IntroductionInitial(
                navController = introNavController,
                dismiss = dismiss,
                planViewModel = planViewModel,
                createSolanaPaymentIntent = solanaPaymentViewModel.createSolanaPaymentIntent,
                setPendingSolanaSubscriptionReference = solanaPaymentViewModel.setPendingSolanaSubscriptionReference,
                onStripePaymentSuccess = {
                    subscriptionBalanceViewModel.pollSubscriptionBalance()
                    dismiss()
                },
                onRedeemTransferBalanceCodeSuccess = {
                    subscriptionBalanceViewModel.pollSubscriptionBalance()
                    overlayViewModel.launch(OverlayMode.Upgrade)
                    scope.launch {
                        // bandaid for overlapping modal state getting weird
                        delay(1000)
                        dismiss()
                    }
                },
                isCheckingSolanaTransaction = isCheckingSolanaTransaction
            )
        }

        composable<IntroRoute.IntroductionUsageBar> {
            IntroductionUsageBar(
                navController = introNavController,
                dismiss = dismiss,
                usedBytes = subscriptionBalanceViewModel.usedBalanceByteCount,
                pendingBytes = subscriptionBalanceViewModel.pendingBalanceByteCount,
                availableBytes = subscriptionBalanceViewModel.availableBalanceByteCount.collectAsState().value,
                meanReliabilityWeight = meanReliabilityWeight,
                totalReferrals = totalReferralCount,
                dailyByteCount = subscriptionBalanceViewModel.startBalanceByteCount.collectAsState().value
            )
        }

        composable<IntroRoute.IntroductionSettings> {
            IntroductionSettings(
                navController = introNavController,
                dismiss = dismiss,
                provideControlMode = provideControlMode,
                setProvideControlMode = setProvideControlMode,
                provideIndicatorColor = provideIndicatorColor,
                allowProvideCell = allowProvideCell,
                toggleProvideCell = toggleProvideCell
            )
        }

        composable<IntroRoute.IntroductionReferral> {
            IntroductionReferral(
                navController = introNavController,
                dismiss = dismiss,
                totalReferrals = totalReferralCount,
                referralCode = referralCode
            )
        }

        composable<IntroRoute.IntroductionQuickConnect> {
            IntroductionQuickConnect(
                navController = introNavController,
                dismiss = dismiss
            )
        }

    }

    FloatingIntroConnector(introConnector)

    }
    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainNavContent(
    earningsViewModel: EarningsViewModel,
    settingsViewModel: SettingsViewModel,
    planViewModel: PlanViewModel,
    overlayViewModel: OverlayViewModel,
    navController: NavHostController,
    connectViewModel: ConnectViewModel,
    locationsListViewModel: LocationsListViewModel,
    activityResultSender: ActivityResultSender?,
    subscriptionBalanceViewModel: SubscriptionBalanceViewModel,
    referralCodeViewModel: ReferralCodeViewModel,
    bundleStore: BundleStore?,
    reliabilityWindow: ReliabilityWindow?,
    totalReferralCount: Long,
    solanaPaymentViewModel: SolanaPaymentViewModel,
    isCheckingSolanaTransaction: Boolean,
    isPro: Boolean,
    connectActionsSheetState: SheetState,
    accountViewModel: AccountViewModel = hiltViewModel<AccountViewModel>(),
    profileViewModel: ProfileViewModel = hiltViewModel<ProfileViewModel>(),
    accountPointsViewModel: AccountPointsViewModel = hiltViewModel<AccountPointsViewModel>(),
) {
    val localDensityCurrent = LocalDensity.current
    val canvasSizePx =
        with(localDensityCurrent) { connectViewModel.canvasSize.times(0.4f).toPx() }


    LaunchedEffect(Unit) {
        connectViewModel.initSuccessPoints(canvasSizePx)
    }

    LifecycleResumeEffect(Unit) {
        connectViewModel.update()

        onPauseOrDispose {
        }
    }

    val configuration = LocalConfiguration.current

    if (isTablet() && configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
        VerticalDivider(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight(),
            color = MainBorderBase
        )
    }

    NavHost(
        navController = navController,
        startDestination = Route.ConnectContainer,
    ) {

        navigation<Route.ConnectContainer>(
            startDestination = Route.Connect,
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None }
        ) {
            composable<Route.Connect> {
                ConnectScreen(
                    connectViewModel,
                    overlayViewModel,
                    locationsListViewModel,
                    navController,
                    subscriptionBalanceViewModel,
                    planViewModel,
                    bundleStore,
                    meanReliabilityWeight = reliabilityWindow?.meanReliabilityWeight ?: 0.0,
                    totalReferrals = totalReferralCount,
                    isPro = isPro,
                    connectActionsSheetState = connectActionsSheetState
                )
            }

            composable<Route.BrowseLocations>(
                enterTransition = NavigationAnimations.enterTransition(),
                exitTransition = NavigationAnimations.exitTransition(),
                popEnterTransition = NavigationAnimations.popEnterTransition(),
                popExitTransition = NavigationAnimations.popExitTransition()
            ) {
                BrowseLocationsScreen(
                    navController = navController,
                    connectViewModel = connectViewModel,
                    locationsListViewModel = locationsListViewModel,
                    connectActionsSheetState = connectActionsSheetState
                )
            }

            composable<Route.ContractStats>(
                enterTransition = NavigationAnimations.enterTransition(),
                exitTransition = NavigationAnimations.exitTransition(),
                popEnterTransition = NavigationAnimations.popEnterTransition(),
                popExitTransition = NavigationAnimations.popExitTransition()
            ) { backStackEntry ->
                val route: Route.ContractStats = backStackEntry.toRoute()
                ContractStatsScreen(
                    navController = navController,
                    provider = route.provider,
                )
            }

            composable<Route.SplitRules>(
                enterTransition = NavigationAnimations.enterTransition(),
                exitTransition = NavigationAnimations.exitTransition(),
                popEnterTransition = NavigationAnimations.popEnterTransition(),
                popExitTransition = NavigationAnimations.popExitTransition()
            ) {
                SplitRulesScreen(
                    navController = navController,
                )
            }

            composable<Route.AppSplitRules>(
                enterTransition = NavigationAnimations.enterTransition(),
                exitTransition = NavigationAnimations.exitTransition(),
                popEnterTransition = NavigationAnimations.popEnterTransition(),
                popExitTransition = NavigationAnimations.popExitTransition()
            ) {
                AppSplitRulesScreen(
                    navController = navController,
                )
            }

            composable<Route.DnsSettings>(
                enterTransition = NavigationAnimations.enterTransition(),
                exitTransition = NavigationAnimations.exitTransition(),
                popEnterTransition = NavigationAnimations.popEnterTransition(),
                popExitTransition = NavigationAnimations.popExitTransition()
            ) {
                DnsSettingsScreen(
                    navController = navController,
                )
            }

            composable<Route.TransportSettings>(
                enterTransition = NavigationAnimations.enterTransition(),
                exitTransition = NavigationAnimations.exitTransition(),
                popEnterTransition = NavigationAnimations.popEnterTransition(),
                popExitTransition = NavigationAnimations.popExitTransition()
            ) { backStackEntry ->
                val route: Route.TransportSettings = backStackEntry.toRoute()
                TransportSettingsScreen(
                    navController = navController,
                    kind = if (route.provider) TransportSettingsKind.PROVIDER else TransportSettingsKind.CLIENT,
                )
            }

            composable<Route.ProviderLocations>(
                enterTransition = NavigationAnimations.enterTransition(),
                exitTransition = NavigationAnimations.exitTransition(),
                popEnterTransition = NavigationAnimations.popEnterTransition(),
                popExitTransition = NavigationAnimations.popExitTransition()
            ) {
                ProviderLocationsScreen(
                    navController = navController,
                    getLocationColor = locationsListViewModel.getLocationColor,
                    mockLocationSection = {
                        MockLocationSection(navController = navController)
                    },
                )
            }

            composable<Route.MockLocationGuide>(
                enterTransition = NavigationAnimations.enterTransition(),
                exitTransition = NavigationAnimations.exitTransition(),
                popEnterTransition = NavigationAnimations.popEnterTransition(),
                popExitTransition = NavigationAnimations.popExitTransition()
            ) {
                MockLocationGuideScreen(
                    navController = navController,
                )
            }
        }

        composable<Route.Support>(
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None }
        ) {
            FeedbackScreen(
                overlayViewModel = overlayViewModel,
                bundleStore = bundleStore
            )
        }

        composable<Route.Leaderboard>(
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None }
        ) {
            LeaderboardScreen()
        }

        composable<Route.Upgrade>(
            enterTransition = NavigationAnimations.enterTransition(),
            exitTransition = NavigationAnimations.exitTransition(),
            popEnterTransition = NavigationAnimations.popEnterTransition(),
            popExitTransition = NavigationAnimations.popExitTransition()
        ) {
            UpgradeScreen(
                navController = navController,
                planViewModel = planViewModel,
                setPendingSolanaSubscriptionReference = solanaPaymentViewModel.setPendingSolanaSubscriptionReference,
                createSolanaPaymentIntent = solanaPaymentViewModel.createSolanaPaymentIntent,
                onStripePaymentSuccess = {
                    subscriptionBalanceViewModel.pollSubscriptionBalance()
                    overlayViewModel.launch(OverlayMode.Upgrade)
                    navController.popBackStack()
                },
                isCheckingSolanaTransaction = isCheckingSolanaTransaction
            )
        }

        navigation<Route.AccountContainer>(
            startDestination = Route.Account,
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None }
        ) {
            composable<Route.Account> {
                AccountScreen(
                    navController,
                    accountViewModel,
                    totalAccountPoints = accountPointsViewModel.totalAccountPoints.collectAsState().value,
                    accountPointsLoaded = accountPointsViewModel.pointsLoaded.collectAsState().value,
                    planViewModel = planViewModel,
                    subscriptionBalanceViewModel = subscriptionBalanceViewModel,
                    overlayViewModel = overlayViewModel,
                    totalReferrals = totalReferralCount,
                    meanReliabilityWeight = reliabilityWindow?.meanReliabilityWeight ?: 0.0,
                    isPro = isPro
                )
            }
            composable<Route.Profile>(
                enterTransition = NavigationAnimations.enterTransition(),
                exitTransition = NavigationAnimations.exitTransition(),
                popEnterTransition = NavigationAnimations.popEnterTransition(),
                popExitTransition = NavigationAnimations.popExitTransition()
            ) { ProfileScreen(
                navController,
                accountViewModel,
                profileViewModel,
                overlayViewModel
            ) }
            composable<Route.Settings>(
                enterTransition = NavigationAnimations.enterTransition(),
                exitTransition = NavigationAnimations.exitTransition(),
                popEnterTransition = NavigationAnimations.popEnterTransition(),
                popExitTransition = NavigationAnimations.popExitTransition()
            ) { SettingsScreen(
                navController,
                accountViewModel,
                planViewModel,
                settingsViewModel,
                overlayViewModel,
                activityResultSender,
                earningsViewModel,
                isPro = isPro
            ) }

            composable<Route.ProviderIdentities>(
                enterTransition = NavigationAnimations.enterTransition(),
                exitTransition = NavigationAnimations.exitTransition(),
                popEnterTransition = NavigationAnimations.popEnterTransition(),
                popExitTransition = NavigationAnimations.popExitTransition()
            ) {
                ProviderIdentitiesScreen(
                    navController = navController,
                )
            }

            composable<Route.Developer>(
                enterTransition = NavigationAnimations.enterTransition(),
                exitTransition = NavigationAnimations.exitTransition(),
                popEnterTransition = NavigationAnimations.popEnterTransition(),
                popExitTransition = NavigationAnimations.popExitTransition()
            ) {
                DeveloperScreen(navController = navController)
            }

            composable<Route.BlockedRegions>(
                enterTransition = NavigationAnimations.enterTransition(),
                exitTransition = NavigationAnimations.exitTransition(),
                popEnterTransition = NavigationAnimations.popEnterTransition(),
                popExitTransition = NavigationAnimations.popExitTransition()
            ) {
                BlockedRegionsScreen(
                    navController = navController,
                    countries = locationsListViewModel.connectCountries,
                    getLocationColor = locationsListViewModel.getLocationColor
                )
            }

            composable<Route.BalanceCodes>(
                enterTransition = NavigationAnimations.enterTransition(),
                exitTransition = NavigationAnimations.exitTransition(),
                popEnterTransition = NavigationAnimations.popEnterTransition(),
                popExitTransition = NavigationAnimations.popExitTransition()
            ) {
                BalanceCodesScreen(
                    navController = navController,
                )
            }

            composable<Route.Earnings>(
                enterTransition = NavigationAnimations.enterTransition(),
                exitTransition = NavigationAnimations.exitTransition(),
                popEnterTransition = NavigationAnimations.popEnterTransition(),
                popExitTransition = NavigationAnimations.popExitTransition()
            ) {
                EarningsScreen(
                    navController = navController,
                    earningsViewModel = earningsViewModel,
                    overlayViewModel = overlayViewModel,
                    totalAccountPoints = accountPointsViewModel.totalAccountPoints.collectAsState().value,
                    payoutPoints = accountPointsViewModel.payoutPoints.collectAsState().value,
                    referralPoints = accountPointsViewModel.referralPoints.collectAsState().value,
                    multiplierPoints = accountPointsViewModel.multiplierPoints.collectAsState().value,
                    reliabilityPoints = accountPointsViewModel.reliabilityPoints.collectAsState().value,
                    fetchAccountPoints = { accountPointsViewModel.fetchAccountPoints() },
                    reliabilityWindow = reliabilityWindow
                )
            }

            composable<Route.Referrals>(
                enterTransition = NavigationAnimations.enterTransition(),
                exitTransition = NavigationAnimations.exitTransition(),
                popEnterTransition = NavigationAnimations.popEnterTransition(),
                popExitTransition = NavigationAnimations.popExitTransition()
            ) {
                ReferralsScreen(
                    navController = navController,
                    settingsViewModel = settingsViewModel,
                    referralCode = referralCodeViewModel.referralCode.collectAsState().value,
                    totalReferrals = totalReferralCount,
                    referralPoints = accountPointsViewModel.referralPoints.collectAsState().value,
                    pointsLoaded = accountPointsViewModel.pointsLoaded.collectAsState().value,
                    fetchAccountPoints = { accountPointsViewModel.fetchAccountPoints() },
                )
            }

            composable<Route.Widgets>(
                enterTransition = NavigationAnimations.enterTransition(),
                exitTransition = NavigationAnimations.exitTransition(),
                popEnterTransition = NavigationAnimations.popEnterTransition(),
                popExitTransition = NavigationAnimations.popExitTransition()
            ) {
                WidgetsScreen(navController = navController)
            }
        }
    }

}

private const val ANIMATION_DURATION = 280
private const val HORIZONTAL_OFFSET_FACTOR = 0.07f // Subtle horizontal movement (7% of screen width)

object NavigationAnimations {
    // Forward navigation (entering a new screen) - fade in with subtle slide from right
    fun enterTransition(): AnimatedContentTransitionScope<*>.() -> EnterTransition = {
        fadeIn(
            animationSpec = tween(
                durationMillis = ANIMATION_DURATION,
                easing = LinearOutSlowInEasing
            )
        ) + slideInHorizontally(
            initialOffsetX = { fullWidth -> (fullWidth * HORIZONTAL_OFFSET_FACTOR).toInt() },
            animationSpec = tween(
                durationMillis = ANIMATION_DURATION,
                easing = LinearOutSlowInEasing
            )
        )
    }

    // Forward navigation (exiting the current screen) - simple fade out
    fun exitTransition(): AnimatedContentTransitionScope<*>.() -> ExitTransition = {
        fadeOut(
            animationSpec = tween(
                durationMillis = ANIMATION_DURATION * 3/4,  // Slightly faster to feel responsive
                easing = FastOutLinearInEasing
            )
        )
    }

    // Back navigation (entering previous screen) - fade in with subtle slide from left
    fun popEnterTransition(): AnimatedContentTransitionScope<*>.() -> EnterTransition = {
        fadeIn(
            animationSpec = tween(
                durationMillis = ANIMATION_DURATION,
                easing = LinearOutSlowInEasing
            )
        )
    }

    // Back navigation (exiting current screen) - fade out with subtle slide to right
    fun popExitTransition(): AnimatedContentTransitionScope<*>.() -> ExitTransition = {
        fadeOut(
            animationSpec = tween(
                durationMillis = ANIMATION_DURATION,
                easing = FastOutLinearInEasing
            )
        ) + slideOutHorizontally(
            targetOffsetX = { fullWidth -> (fullWidth * HORIZONTAL_OFFSET_FACTOR).toInt() },
            animationSpec = tween(
                durationMillis = ANIMATION_DURATION,
                easing = FastOutLinearInEasing
            )
        )
    }
}
