package com.bringyour.network.ui

import android.content.res.Configuration
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
import androidx.compose.material3.SheetValue
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.res.painterResource
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
import com.bringyour.network.ui.components.overlays.FullScreenOverlay
import com.bringyour.network.ui.components.overlays.WelcomeAnimatedMainOverlay
import com.bringyour.network.ui.connect.ConnectScreen
import com.bringyour.network.ui.connect.ConnectViewModel
import com.bringyour.network.ui.feedback.FeedbackScreen
import com.bringyour.network.ui.settings.SettingsViewModel
import com.bringyour.network.ui.shared.viewmodels.OverlayViewModel
import com.bringyour.network.ui.shared.viewmodels.PlanViewModel
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
import com.bringyour.network.ui.profile.ProfileScreen
import com.bringyour.network.ui.profile.ProfileViewModel
import com.bringyour.network.ui.settings.SettingsScreen
import com.bringyour.network.ui.shared.viewmodels.SubscriptionBalanceViewModel
import com.bringyour.network.ui.wallet.WalletScreen
import com.bringyour.network.ui.wallet.WalletViewModel
import com.bringyour.network.ui.wallet.WalletsScreen
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.bringyour.network.R
import com.bringyour.network.ui.blocked_regions.BlockedRegionsScreen
import com.bringyour.network.ui.introduction.IntroductionInitial
import com.bringyour.network.ui.introduction.IntroductionReferral
import com.bringyour.network.ui.introduction.IntroductionSettings
import com.bringyour.network.ui.introduction.IntroductionUsageBar
import com.bringyour.network.ui.payout.PayoutScreen
import com.bringyour.network.ui.shared.models.BundleStore
import com.bringyour.network.ui.shared.models.ProvideControlMode
import com.bringyour.network.ui.shared.viewmodels.AccountPointEvent
import com.bringyour.network.ui.shared.viewmodels.AccountPointsViewModel
import com.bringyour.network.ui.shared.viewmodels.NetworkReliabilityViewModel
import com.bringyour.network.ui.shared.viewmodels.Plan
import com.bringyour.network.ui.shared.viewmodels.SolanaPaymentViewModel
import com.bringyour.network.ui.theme.Pink
import com.bringyour.network.ui.upgrade.UpgradeScreen
import com.bringyour.sdk.ReliabilityWindow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainNavHost(
    walletViewModel: WalletViewModel,
    settingsViewModel: SettingsViewModel,
    planViewModel: PlanViewModel,
    subscriptionBalanceViewModel: SubscriptionBalanceViewModel,
    overlayViewModel: OverlayViewModel,
    animateIn: Boolean,
    targetLink: String?,
    defaultLocation: String?,
    activityResultSender: ActivityResultSender?,
    bundleStore: BundleStore?,
    mainNavViewModel: MainNavViewModel = hiltViewModel<MainNavViewModel>(),
    referralCodeViewModel: ReferralCodeViewModel = hiltViewModel<ReferralCodeViewModel>(),
    connectViewModel: ConnectViewModel = hiltViewModel<ConnectViewModel>(),
    locationsListViewModel: LocationsListViewModel = hiltViewModel<LocationsListViewModel>(),
    networkReliabilityViewModel: NetworkReliabilityViewModel = hiltViewModel<NetworkReliabilityViewModel>()
) {

    val currentTopLevelRoute by mainNavViewModel.currentTopLevelRoute.collectAsState()
    val currentRoute by mainNavViewModel.currentRoute.collectAsState()
    val currentPlanLoaded by subscriptionBalanceViewModel.isInitialized.collectAsState()
    val currentPlan by subscriptionBalanceViewModel.currentPlan.collectAsState()
    val reliabilityWindow by networkReliabilityViewModel.reliabilityWindow.collectAsState()
    val totalReferralCount by referralCodeViewModel.totalReferralCount.collectAsState()
    var displayIntro by remember { mutableStateOf(true) }

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

    LaunchedEffect(currentPlanLoaded, currentPlan) {

        if (currentPlanLoaded && currentPlan == Plan.Supporter) {
            displayIntro = false
        }
    }

    AnimatedContent(
        targetState = displayIntro,
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
                    displayIntro = false
                },
                subscriptionBalanceViewModel = subscriptionBalanceViewModel,
                meanReliabilityWeight = reliabilityWindow?.meanReliabilityWeight ?: 0.0,
                totalReferralCount = totalReferralCount,
                provideControlMode = settingsViewModel.provideControlMode,
                setProvideControlMode = settingsViewModel.setProvideControlMode,
                provideIndicatorColor = settingsViewModel.provideIndicatorColor,
                allowProvideCell = settingsViewModel.allowProvideOnCell.collectAsState().value,
                toggleProvideCell = settingsViewModel.toggleAllowProvideOnCell,
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
                                            Icon(painterResource(id = iconRes), contentDescription = screen.description)
                                        }

                                    },
                                    selected = screen == currentTopLevelRoute,
                                    onClick = {

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
                                    },
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
                                        walletViewModel = walletViewModel,
                                        connectViewModel = connectViewModel,
                                        locationsListViewModel = locationsListViewModel,
                                        activityResultSender = activityResultSender,
                                        subscriptionBalanceViewModel = subscriptionBalanceViewModel,
                                        referralCodeViewModel = referralCodeViewModel,
                                        bundleStore = bundleStore,
//                                    currentPlanLoaded = currentPlanLoaded,
//                                    currentPlan = currentPlan,
                                        launchIntro = {
                                            displayIntro = true
                                        },
                                        reliabilityWindow = reliabilityWindow,
                                        totalReferralCount = totalReferralCount
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
                                    walletViewModel = walletViewModel,
                                    connectViewModel = connectViewModel,
                                    locationsListViewModel = locationsListViewModel,
                                    activityResultSender = activityResultSender,
                                    subscriptionBalanceViewModel = subscriptionBalanceViewModel,
                                    referralCodeViewModel = referralCodeViewModel,
                                    bundleStore = bundleStore,
                                    launchIntro = {
                                        displayIntro = true
                                    },
                                    reliabilityWindow = reliabilityWindow,
                                    totalReferralCount = totalReferralCount
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
        currentPlanLoaded = currentPlanLoaded
    )

    FullScreenOverlay(
        overlayViewModel,
        referralCodeViewModel.referralCode.collectAsState().value,
    )

}

@Composable
fun IntroNavHost(
    dismiss: () -> Unit,
    meanReliabilityWeight: Double,
    totalReferralCount: Long,
    provideControlMode: ProvideControlMode,
    setProvideControlMode: (ProvideControlMode) -> Unit,
    provideIndicatorColor: Color,
    allowProvideCell: Boolean,
    toggleProvideCell: () -> Unit,
    subscriptionBalanceViewModel: SubscriptionBalanceViewModel
) {

    val introNavController = rememberNavController()

    NavHost(
        navController = introNavController,
        startDestination = IntroRoute.IntroductionInitial,
    ) {

        composable<IntroRoute.IntroductionInitial> {
            IntroductionInitial(
                navController = introNavController,
                dismiss = dismiss
            )
        }

        composable<IntroRoute.IntroductionUsageBar> {
            IntroductionUsageBar(
                navController = introNavController,
                usedBytes = subscriptionBalanceViewModel.usedBalanceByteCount,
                pendingBytes = subscriptionBalanceViewModel.pendingBalanceByteCount,
                availableBytes = subscriptionBalanceViewModel.availableBalanceByteCount.collectAsState().value,
                meanReliabilityWeight = meanReliabilityWeight,
                totalReferrals = totalReferralCount,
            )
        }

        composable<IntroRoute.IntroductionSettings> {
            IntroductionSettings(
                navController = introNavController,
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
            )
        }
    }

}

@Composable
fun MainNavContent(
    walletViewModel: WalletViewModel,
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
    launchIntro: () -> Unit,
    reliabilityWindow: ReliabilityWindow?,
    totalReferralCount: Long,
    accountViewModel: AccountViewModel = hiltViewModel<AccountViewModel>(),
    profileViewModel: ProfileViewModel = hiltViewModel<ProfileViewModel>(),
    accountPointsViewModel: AccountPointsViewModel = hiltViewModel<AccountPointsViewModel>(),
    solanaPaymentViewModel: SolanaPaymentViewModel = hiltViewModel<SolanaPaymentViewModel>(),
) {
    val localDensityCurrent = LocalDensity.current
    val canvasSizePx =
        with(localDensityCurrent) { connectViewModel.canvasSize.times(0.4f).toPx() }

    val wallets by walletViewModel.wallets.collectAsState()
//    val totalReferralCount by referralCodeViewModel.totalReferralCount.collectAsState()

    val pendingSolanaSubReference by solanaPaymentViewModel.pendingSolanaSubscriptionReference.collectAsState()

    val lifecycleOwner = LocalLifecycleOwner.current

    val scope = rememberCoroutineScope()
//    var isFirstLaunch by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        connectViewModel.initSuccessPoints(canvasSizePx)

        planViewModel.onUpgradeSuccess.collect {

            // poll subscription balance until it's updated
            subscriptionBalanceViewModel.pollSubscriptionBalance()

        }

    }

//    LaunchedEffect(currentPlanLoaded, currentPlan) {
//        if (currentPlanLoaded && isFirstLaunch) {
//            if (currentPlan != Plan.Supporter) {
//                navController.navigate(Route.IntroductionContainer) {
//                    popUpTo(Route.ConnectContainer) { inclusive = true }
//                }
//            }
//            isFirstLaunch = false
//        }
//    }

    LifecycleResumeEffect(Unit) {
        connectViewModel.update()

        onPauseOrDispose {
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
                    launchIntro = launchIntro
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
                    locationsListViewModel = locationsListViewModel
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
                overlayViewModel = overlayViewModel,
                setPendingSolanaSubscriptionReference = solanaPaymentViewModel.setPendingSolanaSubscriptionReference,
                createSolanaPaymentIntent = solanaPaymentViewModel.createSolanaPaymentIntent,
                pollSubscriptionBalance = {
                    subscriptionBalanceViewModel.pollSubscriptionBalance()
                }
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
                    totalPayoutAmount = walletViewModel.totalPayoutAmount,
                    totalPayoutAmountInitialized = walletViewModel.totalPayoutAmountInitialized,
                    walletCount = wallets.size,
                    planViewModel = planViewModel,
                    subscriptionBalanceViewModel = subscriptionBalanceViewModel,
                    overlayViewModel = overlayViewModel,
                    totalReferrals = totalReferralCount,
                    meanReliabilityWeight = reliabilityWindow?.meanReliabilityWeight ?: 0.0
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
                subscriptionBalanceViewModel,
                activityResultSender,
                walletViewModel,
                bonusReferralCode = referralCodeViewModel.referralCode.collectAsState().value,
            ) }

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

            composable<Route.Wallets>(
                enterTransition = NavigationAnimations.enterTransition(),
                exitTransition = NavigationAnimations.exitTransition(),
                popEnterTransition = NavigationAnimations.popEnterTransition(),
                popExitTransition = NavigationAnimations.popExitTransition()
            ) {
                WalletsScreen(
                    navController,
                    walletViewModel,
                    activityResultSender,
                    overlayViewModel,
                    totalAccountPoints = accountPointsViewModel.totalAccountPoints.collectAsState().value,
                    payoutPoints = accountPointsViewModel.payoutPoints.collectAsState().value,
                    referralPoints = accountPointsViewModel.referralPoints.collectAsState().value,
                    multiplierPoints = accountPointsViewModel.multiplierPoints.collectAsState().value,
                    reliabilityPoints = accountPointsViewModel.reliabilityPoints.collectAsState().value,
                    fetchAccountPoints = accountPointsViewModel.fetchAccountPoints,
                    reliabilityWindow = reliabilityWindow,
                    totalReferralCount = totalReferralCount,
                    fetchReferralCode = referralCodeViewModel.fetchReferralCode
                )
            }

            composable<Route.Wallet>(
                enterTransition = NavigationAnimations.enterTransition(),
                exitTransition = NavigationAnimations.exitTransition(),
                popEnterTransition = NavigationAnimations.popEnterTransition(),
                popExitTransition = NavigationAnimations.popExitTransition()
            ) { backStackEntry ->

                val wallet: Route.Wallet = backStackEntry.toRoute()
                val accountWallet = walletViewModel.findWalletById(wallet.id)

                WalletScreen(
                    navController = navController,
                    accountWallet = accountWallet,
                    walletViewModel = walletViewModel,
                )
            }

            composable<Route.Payout>(
                enterTransition = NavigationAnimations.enterTransition(),
                exitTransition = NavigationAnimations.exitTransition(),
                popEnterTransition = NavigationAnimations.popEnterTransition(),
                popExitTransition = NavigationAnimations.popExitTransition()
            ) { backStackEntry ->

                val payoutRoute: Route.Payout = backStackEntry.toRoute()
                val payoutId = payoutRoute.id
                val accountPayment = walletViewModel.getPayoutById(payoutId)

                PayoutScreen(
                    accountPayment = accountPayment,
                    navController = navController,
                    totalAccountPoints = accountPointsViewModel.getTotalPointsByPaymentId(payoutId),
                    multiplierPoints = accountPointsViewModel.getPayoutEventPointsByPaymentId(payoutId, AccountPointEvent.PAYOUT_MULTIPLIER),
                    referralPoints = accountPointsViewModel.getPayoutEventPointsByPaymentId(payoutId, AccountPointEvent.PAYOUT_LINKED_ACCOUNT),
                    payoutPoints = accountPointsViewModel.getPayoutEventPointsByPaymentId(payoutId, AccountPointEvent.PAYOUT),
                    holdsMultiplier = walletViewModel.isSeekerHolder.collectAsState().value,
                    reliabilityPoints = accountPointsViewModel.getPayoutEventPointsByPaymentId(payoutId,
                        AccountPointEvent.PAYOUT_RELIABILITY)
                )
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