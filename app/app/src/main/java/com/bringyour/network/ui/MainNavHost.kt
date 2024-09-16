package com.bringyour.network.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.bringyour.network.R
import com.bringyour.network.ui.components.overlays.FullScreenOverlay
import com.bringyour.network.ui.connect.ConnectScreen
import com.bringyour.network.ui.connect.ConnectViewModel
import com.bringyour.network.ui.feedback.FeedbackScreen
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
    connectViewModel: ConnectViewModel,
    sagaViewModel: SagaViewModel,
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

    Box(
        modifier = Modifier
            .fillMaxSize()
             .systemBarsPadding()
             // .windowInsetsPadding(WindowInsets.systemBars)
    ) {

        NavigationSuiteScaffold(
            containerColor = Black,
            contentColor = Black,
            navigationSuiteColors = customColors,
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

                Row {
                    MainNavContent(
                        currentDestination,
                        sagaViewModel,
                        connectViewModel,
                        accountNavHostController,
                    )
                }

            } else {

                Column(
                    modifier = Modifier.padding(bottom = 1.dp)
                ) {
                    MainNavContent(
                        currentDestination,
                        sagaViewModel,
                        connectViewModel,
                        accountNavHostController,
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

    FullScreenOverlay()

}

@Composable
fun MainNavContent(
    currentDestination: AppDestinations,
    sagaViewModel: SagaViewModel,
    connectViewModel: ConnectViewModel,
    accountNavHostController: NavHostController,
) {

    if (isTablet()) {
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
        )
        AppDestinations.ACCOUNT -> AccountNavHost(
            sagaViewModel,
            accountNavHostController
        )
        AppDestinations.SUPPORT -> FeedbackScreen()
    }

}