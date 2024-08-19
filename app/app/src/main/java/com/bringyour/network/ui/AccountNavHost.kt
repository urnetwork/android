package com.bringyour.network.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.bringyour.network.ui.account.AccountScreen
import com.bringyour.network.ui.components.LoginMode
import com.bringyour.network.ui.profile.ProfileScreen
import com.bringyour.network.ui.refer.ReferScreen
import com.bringyour.network.ui.settings.SettingsScreen
import com.bringyour.network.ui.wallet.WalletScreen

@Composable
fun AccountNavHost() {

    val navController = rememberNavController()

    // for testing
    val loginMode = LoginMode.Authenticated

    NavHost(
        navController = navController,
        startDestination = "account",
        enterTransition = { slideInHorizontally(
            initialOffsetX = { fullWidth -> fullWidth },
            animationSpec = tween(durationMillis = 300)
        ) + fadeIn(animationSpec = tween(300)
        ) },
        exitTransition = {
            fadeOut(
                animationSpec = tween(300))
        },
        popEnterTransition = {
            fadeIn(animationSpec = tween(300))
        },
        popExitTransition = {
            slideOutHorizontally(
                targetOffsetX = { fullWidth -> fullWidth },
                animationSpec = tween(durationMillis = 300)
            )
        }
    ) {
        composable("account") { AccountScreen(navController) }
        composable("profile") { ProfileScreen(
            navController,
            loginMode = loginMode
        ) }
        composable("settings") { SettingsScreen(navController) }
        composable("wallet") { WalletScreen() }
        composable("refer") { ReferScreen() }
    }

}