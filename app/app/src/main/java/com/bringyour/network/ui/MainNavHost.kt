package com.bringyour.network.ui

import android.content.res.Configuration
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.bringyour.network.R
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

enum class AppDestinations(
    val selectedIcon: Int,
    val unselectedIcon: Int,
    val description: String
) {
    CONNECT(R.drawable.main_nav_globe_filled, R.drawable.main_nav_globe, "Connect"),
    ACCOUNT(R.drawable.main_nav_user_filled, R.drawable.main_nav_user, "Account"),
    SUPPORT(R.drawable.main_nav_chat_filled, R.drawable.main_nav_chat, "Support")
}


@Composable
fun MainNavHost(
    sagaViewModel: SagaViewModel,
    settingsViewModel: SettingsViewModel,
    promptReviewViewModel: PromptReviewViewModel,
    planViewModel: PlanViewModel,
    animateIn: Boolean,
    referralCodeViewModel: ReferralCodeViewModel = hiltViewModel(),
    overlayViewModel: OverlayViewModel = hiltViewModel()
) {

    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.CONNECT) }

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
                AppDestinations.entries.forEach { screen ->
                    item(
                        icon = {

                            val iconRes = if (screen == currentDestination) {
                                screen.selectedIcon
                            } else {
                                screen.unselectedIcon
                            }
                            Icon(painterResource(id = iconRes), contentDescription = screen.description)
                        },
                        selected = screen == currentDestination,
                        onClick = { currentDestination = screen },
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
                            currentDestination,
                            sagaViewModel,
                            accountNavHostController,
                            settingsViewModel,
                            promptReviewViewModel,
                            planViewModel,
                            overlayViewModel
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
                        currentDestination,
                        sagaViewModel,
                        accountNavHostController,
                        settingsViewModel,
                        promptReviewViewModel,
                        planViewModel,
                        overlayViewModel
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
    currentDestination: AppDestinations,
    sagaViewModel: SagaViewModel,
    accountNavHostController: NavHostController,
    settingsViewModel: SettingsViewModel,
    promptReviewViewModel: PromptReviewViewModel,
    planViewModel: PlanViewModel,
    overlayViewModel: OverlayViewModel,
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

    when (currentDestination) {
        AppDestinations.CONNECT -> ConnectScreen(
            connectViewModel,
            promptReviewViewModel,
            overlayViewModel
        )
        AppDestinations.ACCOUNT -> AccountNavHost(
            sagaViewModel,
            accountNavHostController,
            planViewModel,
            settingsViewModel,
            overlayViewModel = overlayViewModel
        )
        AppDestinations.SUPPORT -> FeedbackScreen(
            overlayViewModel = overlayViewModel
        )
    }
}