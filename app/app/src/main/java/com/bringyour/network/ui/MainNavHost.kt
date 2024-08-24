package com.bringyour.network.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.bringyour.network.ui.components.BottomNavBar
import com.bringyour.network.ui.components.overlays.FullScreenOverlay
import com.bringyour.network.ui.connect.ConnectScreen
import com.bringyour.network.ui.connect.ConnectViewModel
import com.bringyour.network.ui.feedback.FeedbackScreen
import com.bringyour.network.ui.theme.URNetworkTheme


@Composable
fun MainNavHost(
    connectViewModel: ConnectViewModel
) {

    val navController = rememberNavController()

    Box(
        modifier = Modifier
            .fillMaxSize()
            // .systemBarsPadding()
            // .windowInsetsPadding(WindowInsets.systemBars)
    ) {

        Scaffold(
            modifier = Modifier
                .fillMaxSize(),
            bottomBar = { BottomNavBar(navController) }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = "connect",
                // todo - for some reason innerPadding is 0
                modifier = Modifier
                    .padding(innerPadding),
                    // .systemBarsPadding(),
                enterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None }
            ) {
                composable("connect") {
                    ConnectScreen(connectViewModel)
                }
                composable("account") { AccountNavHost() }
                composable("support") { FeedbackScreen() }
            }
        }

        FullScreenOverlay()
    }
}

//@Preview
//@Composable
//private fun MainNavHostPreview() {
//    URNetworkTheme {
//        MainNavHost()
//    }
//}