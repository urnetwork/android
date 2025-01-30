package com.bringyour.network.ui

import android.content.res.Configuration
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
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
import com.bringyour.network.ui.shared.viewmodels.PromptReviewViewModel
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
import com.bringyour.network.ui.profile.ProfileScreen
import com.bringyour.network.ui.profile.ProfileViewModel
import com.bringyour.network.ui.settings.SettingsScreen
import com.bringyour.network.ui.wallet.WalletScreen
import com.bringyour.network.ui.wallet.WalletViewModel
import com.bringyour.network.ui.wallet.WalletsScreen
import com.bringyour.network.utils.isTv
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainNavHost(
    walletViewModel: WalletViewModel,
    settingsViewModel: SettingsViewModel,
    promptReviewViewModel: PromptReviewViewModel,
    planViewModel: PlanViewModel,
    animateIn: Boolean,
    targetLink: String?,
    defaultLocation: String?,
    activityResultSender: ActivityResultSender,
    mainNavViewModel: MainNavViewModel = hiltViewModel(),
    referralCodeViewModel: ReferralCodeViewModel = hiltViewModel(),
    overlayViewModel: OverlayViewModel = hiltViewModel(),
    connectViewModel: ConnectViewModel = hiltViewModel(),
    locationsListViewModel: LocationsListViewModel = hiltViewModel()
) {

    val currentTopLevelRoute by mainNavViewModel.currentTopLevelRoute.collectAsState()
    val currentRoute by mainNavViewModel.currentRoute.collectAsState()
    val previousRoute by mainNavViewModel.previousRoute.collectAsState()

    val navItemColors = NavigationSuiteDefaults.itemColors(
        navigationBarItemColors = NavigationBarItemDefaults.colors(
            indicatorColor = Color.Transparent,
        ),
        navigationRailItemColors = NavigationRailItemDefaults.colors(
            indicatorColor = Color.Transparent
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

        if (isTv()) {
            NavigationSuiteType.NavigationDrawer
        } else if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE && isTablet()) {
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
                .padding(top = 36.dp)
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
                                Icon(painterResource(id = iconRes), contentDescription = screen.description)
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

                                    locationsListViewModel.refreshLocations()

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
                            label = { if (isTv()) Text(screen.description) else Text("") }
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
                                previousRoute,
                                settingsViewModel = settingsViewModel,
                                promptReviewViewModel = promptReviewViewModel,
                                planViewModel = planViewModel,
                                overlayViewModel = overlayViewModel,
                                navController = navController,
                                walletViewModel = walletViewModel,
                                connectViewModel = connectViewModel,
                                locationsListViewModel = locationsListViewModel,
                                activityResultSender = activityResultSender
                            )
                        }

                        if (configuration.orientation == Configuration.ORIENTATION_PORTRAIT && !isTv()) {
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
                            previousRoute = previousRoute,
                            settingsViewModel = settingsViewModel,
                            promptReviewViewModel = promptReviewViewModel,
                            planViewModel = planViewModel,
                            overlayViewModel = overlayViewModel,
                            navController = navController,
                            walletViewModel = walletViewModel,
                            connectViewModel = connectViewModel,
                            locationsListViewModel = locationsListViewModel,
                            activityResultSender = activityResultSender
                        )

                        if (!isTv()) {
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

    WelcomeAnimatedMainOverlay(
        animateIn = animateIn && !isTv()
    )

    FullScreenOverlay(
        overlayViewModel,
        referralCodeViewModel,
    )

}

@Composable
fun MainNavContent(
    previousRoute: Route?,
    walletViewModel: WalletViewModel,
    settingsViewModel: SettingsViewModel,
    promptReviewViewModel: PromptReviewViewModel,
    planViewModel: PlanViewModel,
    overlayViewModel: OverlayViewModel,
    navController: NavHostController,
    connectViewModel: ConnectViewModel,
    locationsListViewModel: LocationsListViewModel,
    activityResultSender: ActivityResultSender,
    accountViewModel: AccountViewModel = hiltViewModel(),
    profileViewModel: ProfileViewModel = hiltViewModel(),
) {
    val localDensityCurrent = LocalDensity.current
    val canvasSizePx = if (isTv())
        with(localDensityCurrent) { connectViewModel.canvasSize.times(0.4f).div(2).toPx() } else
        with(localDensityCurrent) { connectViewModel.canvasSize.times(0.4f).toPx() }

    val isTv = isTv()

    val wallets by walletViewModel.wallets.collectAsState()

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

    val nestedPopEnterTransition = fadeIn(animationSpec = tween(300))

    val nestedEnterTransition = {
        if (previousRoute == Route.Support) {
            EnterTransition.None
        } else {
            slideInHorizontally(
                initialOffsetX = { fullWidth -> fullWidth },
                animationSpec = tween(durationMillis = 300)
            ) + fadeIn(animationSpec = tween(300))
        }
    }

    val nestedPopExitTransition = {
        val destinationRoute = Route.fromString(navController.currentDestination?.route ?: "")
        if (destinationRoute == Route.Support) {
            ExitTransition.None
        } else {
            slideOutHorizontally(
                targetOffsetX = { fullWidth -> fullWidth },
                animationSpec = tween(durationMillis = 300)
            )
        }
    }

    NavHost(
        navController = navController,
        startDestination = Route.ConnectContainer,
        enterTransition = {
            if (isTv) {
                slideInHorizontally(
                    initialOffsetX = { fullWidth -> fullWidth },
                    animationSpec = tween(durationMillis = 300)
                ) + fadeIn(animationSpec = tween(300))
            } else {
                EnterTransition.None
            }
        }
    ) {

        navigation<Route.ConnectContainer>(
            startDestination = Route.Connect,
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None }
        ) {
            composable<Route.Connect> {
                ConnectScreen(
                    connectViewModel,
                    promptReviewViewModel,
                    overlayViewModel,
                    locationsListViewModel,
                    navController,
                )
            }

            composable<Route.BrowseLocations>(
                enterTransition = { nestedEnterTransition() },
                popEnterTransition = { nestedPopEnterTransition },
                popExitTransition = { nestedPopExitTransition() }
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
                overlayViewModel = overlayViewModel
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
                    overlayViewModel = overlayViewModel
                )
            }
            composable<Route.Profile>(
                enterTransition = { nestedEnterTransition() },
                popExitTransition = { nestedPopExitTransition() }
            ) { ProfileScreen(
                navController,
                accountViewModel,
                profileViewModel,
                overlayViewModel
            ) }
            composable<Route.Settings>(
                enterTransition = { nestedEnterTransition() },
                popExitTransition = { nestedPopExitTransition() }
            ) { SettingsScreen(
                navController,
                accountViewModel,
                planViewModel,
                settingsViewModel,
                overlayViewModel
            ) }

            composable<Route.Wallets>(
                enterTransition = { nestedEnterTransition() },
                popEnterTransition = { nestedPopEnterTransition },
                popExitTransition = { nestedPopExitTransition() }
            ) {
                WalletsScreen(
                    navController,
                    walletViewModel,
                    activityResultSender
                )
            }

            composable<Route.Wallet>(
                enterTransition = { nestedEnterTransition() },
                popExitTransition = { nestedPopExitTransition() }
            ) { backStackEntry ->

                val wallet: Route.Wallet = backStackEntry.toRoute()
                val accountWallet = walletViewModel.findWalletById(wallet.id)

                WalletScreen(
                    navController = navController,
                    accountWallet = accountWallet,
                    walletViewModel = walletViewModel,
                    overlayViewModel = overlayViewModel
                )
            }
        }
    }
}