package com.bringyour.network.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.bringyour.network.ui.account.AccountScreen
import com.bringyour.network.ui.connect.ConnectScreen
import com.bringyour.network.ui.feedback.FeedbackScreen
import com.bringyour.network.ui.profile.ProfileScreen
import com.bringyour.network.ui.refer.ReferScreen
import com.bringyour.network.ui.settings.SettingsScreen
import com.bringyour.network.ui.wallet.WalletScreen

@Composable
fun AccountNavHost() {

    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "account",
    ) {
        composable("account") { AccountScreen(navController) }
        composable("profile") { ProfileScreen() }
        composable("settings") { SettingsScreen() }
        composable("wallet") { WalletScreen() }
        composable("refer") { ReferScreen() }
    }

}