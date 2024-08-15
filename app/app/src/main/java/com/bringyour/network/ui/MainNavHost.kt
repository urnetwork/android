package com.bringyour.network.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.bringyour.network.ui.components.BottomNavBar
import com.bringyour.network.ui.connect.ConnectScreen
import com.bringyour.network.ui.feedback.FeedbackScreen
import com.bringyour.network.ui.theme.URNetworkTheme

@Composable
fun MainNavHost() {

    val navController = rememberNavController()

    Scaffold(
        modifier = Modifier
            .fillMaxSize(),
        bottomBar = { BottomNavBar(navController) }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "connect",
            // todo - for some reason innerPadding is 0
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("connect") { ConnectScreen() }
            composable("account") { AccountNavHost() }
            composable("support") { FeedbackScreen() }
        }
    }

}

@Preview
@Composable
fun MainNavHostPreview() {
    URNetworkTheme {
        MainNavHost()
    }
}