package com.bringyour.network.ui

import android.content.res.Configuration
import android.util.Log
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHost
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.bringyour.network.R
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
import com.bringyour.network.ui.wallet.SagaViewModel
import com.bringyour.network.utils.isTablet
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.navigation
import com.bringyour.network.ui.account.AccountViewModel
import com.bringyour.network.ui.profile.ProfileScreen
import com.bringyour.network.ui.profile.ProfileViewModel
import com.bringyour.network.ui.settings.SettingsScreen
import com.bringyour.network.ui.wallet.WalletScreen
import com.bringyour.network.ui.wallet.WalletViewModel
import com.bringyour.network.ui.wallet.WalletsScreen


@Composable
fun MainNavHost(
    sagaViewModel: SagaViewModel,
    settingsViewModel: SettingsViewModel,
    promptReviewViewModel: PromptReviewViewModel,
    planViewModel: PlanViewModel,
    animateIn: Boolean,
    mainNavViewModel: MainNavViewModel = hiltViewModel(),
    referralCodeViewModel: ReferralCodeViewModel = hiltViewModel(),
    overlayViewModel: OverlayViewModel = hiltViewModel()
) {

    val currentTopLevelRoute by mainNavViewModel.currentTopLevelRoute.collectAsState()
    val lastAccountRoute by mainNavViewModel.lastAccountRoute.collectAsState()

    // var currentTopLevelDestination by rememberSaveable { mutableStateOf(TopLevelScaffoldRoutes.CONNECT) }

    val navItemColors = NavigationSuiteDefaults.itemColors(
        navigationBarItemColors = NavigationBarItemDefaults.colors(
            indicatorColor = Color.Transparent,
        ),
        navigationRailItemColors = NavigationRailItemDefaults.colors(
            indicatorColor = Color.Transparent
        ),
        navigationDrawerItemColors = NavigationDrawerItemDefaults.colors(

        )
    )

    val accountNavHostController = rememberNavController()

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
    // var lastAccountDestination by remember { mutableStateOf("account") }


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

                            Log.i("MainNavHost", "pre navController.graph.findStartDestination().route: ${navController.graph.findStartDestination().route}")
                            Log.i("MainNavHost", "pre navController.graph.findStartDestination().parent?.route: ${navController.graph.findStartDestination().parent?.route}")
                            Log.i("MainNavHost", "pre navController.currentBackStackEntry?.destination?.route: ${navController.currentBackStackEntry?.destination?.route}")
                            Log.i("MainNavHost", "pre navController.currentDestination?.route: ${navController.currentDestination?.route}")
                            Log.i("MainNavHost", "pre navController.currentDestination?.parent?.route: ${navController.currentDestination?.parent?.route}")
                            Log.i("MainNavHost", "pre navController.currentDestination?.parent?.findStartDestination()?.route: ${navController.currentDestination?.parent?.findStartDestination()?.route}")

                            navController.navigate("${screen.route}")



                            Log.i("MainNavHost", "post navController.graph.findStartDestination().route: ${navController.graph.findStartDestination().route}")
                            Log.i("MainNavHost", "post navController.graph.findStartDestination().parent?.route: ${navController.graph.findStartDestination().parent?.route}")
                            Log.i("MainNavHost", "post navController.currentBackStackEntry?.destination?.route: ${navController.currentBackStackEntry?.destination?.route}")
                            Log.i("MainNavHost", "post navController.currentDestination?.route: ${navController.currentDestination?.route}")
                            Log.i("MainNavHost", "post navController.currentDestination?.parent?.route: ${navController.currentDestination?.parent?.route}")
                            Log.i("MainNavHost", "post navController.currentDestination?.parent?.findStartDestination()?.route: ${navController.currentDestination?.parent?.findStartDestination()?.route}")


//                            if (screen == TopLevelScaffoldRoutes.ACCOUNT && currentTopLevelRoute != TopLevelScaffoldRoutes.ACCOUNT) {
//                                // If navigating to Account from another top-level destination,
//                                // navigate to the last known Account destination
//                                navController.navigate("$lastAccountRoute")
//                            } else if (screen != TopLevelScaffoldRoutes.ACCOUNT) {
//                                // For other top-level destinations, navigate normally
//                                navController.navigate("${screen.route}") {
//                                    popUpTo(navController.graph.findStartDestination().id) {
//                                        saveState = true
//                                    }
//                                    launchSingleTop = true
//                                    restoreState = true
//                                }
//                            }
                            mainNavViewModel.setCurrentTopLevelRoute(screen)
                            // currentTopLevelDestination = screen
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
                            currentTopLevelRoute,
                            sagaViewModel,
                            accountNavHostController,
                            settingsViewModel,
                            promptReviewViewModel,
                            planViewModel,
                            overlayViewModel,
                            navController
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
                        currentTopLevelRoute,
                        sagaViewModel,
                        accountNavHostController,
                        settingsViewModel,
                        promptReviewViewModel,
                        planViewModel,
                        overlayViewModel,
                        navController
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

    WelcomeAnimatedMainOverlay(
        animateIn
    )

    FullScreenOverlay(
        overlayViewModel,
        referralCodeViewModel,
    )

}

@Composable
fun MainNavContent(
    currentTopLevelRoute: TopLevelScaffoldRoutes,
    sagaViewModel: SagaViewModel,
    accountNavHostController: NavHostController,
    settingsViewModel: SettingsViewModel,
    promptReviewViewModel: PromptReviewViewModel,
    planViewModel: PlanViewModel,
    overlayViewModel: OverlayViewModel,
    navController: NavHostController,

    accountViewModel: AccountViewModel = hiltViewModel(),
    walletViewModel: WalletViewModel = hiltViewModel(),
    profileViewModel: ProfileViewModel = hiltViewModel(),
    connectViewModel: ConnectViewModel = hiltViewModel(),
) {

    val localDensityCurrent = LocalDensity.current
    val canvasSizePx = with(localDensityCurrent) { connectViewModel.canvasSize.times(0.4f).toPx() }

    LaunchedEffect(Unit) {
        connectViewModel.initSuccessPoints(canvasSizePx)
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

    val nestedEnterTransition =  slideInHorizontally(
        initialOffsetX = { fullWidth -> fullWidth },
        animationSpec = tween(durationMillis = 300)
    ) + fadeIn(animationSpec = tween(300))
    val nestedExitTransition = fadeOut(
        animationSpec = tween(300)
    )
    val nestedPopEnterTransition = fadeIn(animationSpec = tween(300))
    val nestedPopExitTransition = slideOutHorizontally(
        targetOffsetX = { fullWidth -> fullWidth },
        animationSpec = tween(durationMillis = 300)
    )



    NavHost(
        navController = navController,
        startDestination = "connect",
    ) {

        composable(
            "${Route.CONNECT}",
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None }
        ) {
            ConnectScreen(
                connectViewModel,
                promptReviewViewModel,
                overlayViewModel
            )
        }

        composable(
            "${Route.SUPPORT}",
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None }
        ) {
            FeedbackScreen(
                overlayViewModel = overlayViewModel
            )
        }

        navigation(
            startDestination = "${Route.ACCOUNT}",
            route = "${Route.ACCOUNT_CONTAINER}",
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None }
        ) {
            composable("${Route.ACCOUNT}") {
                AccountScreen(
                    navController,
                    accountViewModel,
                    totalPayoutAmount = walletViewModel.totalPayoutAmount,
                    totalPayoutAmountInitialized = walletViewModel.totalPayoutAmountInitialized,
                    walletCount = walletViewModel.wallets.size,
                    planViewModel = planViewModel,
                    overlayViewModel = overlayViewModel
                )

                LaunchedEffect(Unit) {
                    // setLastAccountRoute(Route.Account)
                }
            }
            composable(
                "${Route.PROFILE}",
                enterTransition = {
                    Log.i("MainNavHost", "navController.previousBackStackEntry?.destination?.route? ${navController.previousBackStackEntry?.destination?.route}")
                    if (currentTopLevelRoute == TopLevelScaffoldRoutes.ACCOUNT) {
                        nestedEnterTransition
                    } else {
                        EnterTransition.None
                    }
                },
                exitTransition = {
                    if (currentTopLevelRoute == TopLevelScaffoldRoutes.ACCOUNT) {
                        nestedExitTransition
                    } else {
                        ExitTransition.None
                    }
                },
                popEnterTransition = { nestedPopEnterTransition },
                popExitTransition = { nestedPopExitTransition }
            ) { ProfileScreen(
                navController,
                accountViewModel,
                profileViewModel,
                overlayViewModel
            ) }
            composable(
                "${Route.SETTINGS}",
                enterTransition = { nestedEnterTransition },
                exitTransition = { nestedExitTransition },
                popEnterTransition = { nestedPopEnterTransition },
                popExitTransition = { nestedPopExitTransition }
            ) { SettingsScreen(
                navController,
                accountViewModel,
                planViewModel,
                settingsViewModel,
                overlayViewModel
            ) }

            composable(
                "${Route.WALLETS}",
                enterTransition = { nestedEnterTransition },
                exitTransition = { nestedExitTransition },
                popEnterTransition = { nestedPopEnterTransition },
                popExitTransition = { nestedPopExitTransition }
            ) {
                WalletsScreen(
                    // accountNavController,
                    navController,
                    sagaViewModel,
                    walletViewModel
                )
            }

            composable(
                "${Route.WALLETS}/{walletId}",
                enterTransition = { nestedEnterTransition },
                exitTransition = { nestedExitTransition },
                popEnterTransition = { nestedPopEnterTransition },
                popExitTransition = { nestedPopExitTransition }
            ) { backStackEntry ->

                val walletId = backStackEntry.arguments?.getString("walletId") ?: ""
                val accountWallet = walletViewModel.findWalletById(walletId)

                WalletScreen(
                    navController = navController,
                    accountWallet = accountWallet,
                    walletViewModel = walletViewModel,
                    overlayViewModel = overlayViewModel
                )
            }
        }
    }


//    when (currentDestination) {
//        AppDestinations.CONNECT -> ConnectScreen(
//            connectViewModel,
//            promptReviewViewModel,
//            overlayViewModel
//        )
//        AppDestinations.ACCOUNT -> AccountNavHost(
//            sagaViewModel,
//            accountNavHostController,
//            planViewModel,
//            settingsViewModel,
//            overlayViewModel = overlayViewModel
//        )
//        AppDestinations.SUPPORT -> FeedbackScreen(
//            overlayViewModel = overlayViewModel
//        )
//    }
}