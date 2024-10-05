package com.bringyour.network.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.bringyour.network.ui.account.AccountScreen
import com.bringyour.network.ui.account.AccountViewModel
import com.bringyour.network.ui.profile.ProfileScreen
import com.bringyour.network.ui.profile.ProfileViewModel
import com.bringyour.network.ui.settings.SettingsScreen
import com.bringyour.network.ui.settings.SettingsViewModel
import com.bringyour.network.ui.shared.viewmodels.OverlayViewModel
import com.bringyour.network.ui.shared.viewmodels.PlanViewModel
import com.bringyour.network.ui.wallet.SagaViewModel
import com.bringyour.network.ui.wallet.WalletViewModel

@Composable
fun AccountNavHost(
    sagaViewModel: SagaViewModel,
    navController: NavHostController,
    planViewModel: PlanViewModel,
    settingsViewModel: SettingsViewModel,
    overlayViewModel: OverlayViewModel,
    accountViewModel: AccountViewModel = hiltViewModel(),
    walletViewModel: WalletViewModel = hiltViewModel(),
    profileViewModel: ProfileViewModel = hiltViewModel()
) {

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
            accountViewModel,
            totalPayoutAmount = walletViewModel.totalPayoutAmount,
            totalPayoutAmountInitialized = walletViewModel.totalPayoutAmountInitialized,
            walletCount = walletViewModel.wallets.size,
            planViewModel = planViewModel,
            overlayViewModel = overlayViewModel
        ) }
        composable("profile") { ProfileScreen(
            navController,
            accountViewModel,
            profileViewModel,
            overlayViewModel
        ) }
        composable("settings") { SettingsScreen(
            navController,
            accountViewModel,
            planViewModel,
            settingsViewModel,
            overlayViewModel
        ) }
        
        composable("wallets") {
            WalletsNavHost(
                accountNavController = navController,
                sagaViewModel,
                walletViewModel,
                overlayViewModel
            )
        }
    }

}