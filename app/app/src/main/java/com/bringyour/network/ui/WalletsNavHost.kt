package com.bringyour.network.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.bringyour.network.ui.shared.viewmodels.OverlayViewModel
import com.bringyour.network.ui.wallet.SagaViewModel
import com.bringyour.network.ui.wallet.WalletScreen
import com.bringyour.network.ui.wallet.WalletViewModel
import com.bringyour.network.ui.wallet.WalletsScreen

@Composable
fun WalletsNavHost(
    accountNavController: NavController,
    sagaViewModel: SagaViewModel,
    walletViewModel: WalletViewModel,
    overlayViewModel: OverlayViewModel,
) {

    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "wallets",
        enterTransition = { slideInHorizontally(
            initialOffsetX = { fullWidth -> fullWidth },
            animationSpec = tween(durationMillis = 300)
        ) + fadeIn(animationSpec = tween(300)
        ) },
        exitTransition = {
            fadeOut(
                animationSpec = tween(300)
            )
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
        composable("wallets") {
            WalletsScreen(
                navController,
                sagaViewModel,
                walletViewModel
            )
        }

        composable("wallet/{walletId}") { backStackEntry ->

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