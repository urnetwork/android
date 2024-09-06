package com.bringyour.network.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.bringyour.network.ui.account.AccountScreen
import com.bringyour.network.ui.account.AccountViewModel
import com.bringyour.network.ui.profile.ProfileScreen
import com.bringyour.network.ui.settings.SettingsScreen
import com.bringyour.network.ui.wallet.SagaViewModel

@Composable
fun AccountNavHost(
    sagaViewModel: SagaViewModel,
    accountViewModel: AccountViewModel = hiltViewModel(),
) {

    val navController = rememberNavController()

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
        composable("account") { AccountScreen(
            navController,
            accountViewModel
        ) }
        composable("profile") { ProfileScreen(
            navController,
            accountViewModel
        ) }
        composable("settings") { SettingsScreen(navController) }
        
        composable("wallets") {
            WalletsNavHost(
                parentNavController = navController,
                sagaViewModel,
            )
        }
    }

}