package com.bringyour.network.ui

import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.bringyour.network.ui.components.overlays.FullScreenOverlay
import com.bringyour.network.ui.login.LoginCreateNetwork
import com.bringyour.network.ui.login.LoginCreateNetworkParams
import com.bringyour.network.ui.login.LoginInitial
import com.bringyour.network.ui.login.LoginPassword
import com.bringyour.network.ui.login.LoginPasswordReset
import com.bringyour.network.ui.login.LoginPasswordResetAfterSend
import com.bringyour.network.ui.login.LoginVerify
import com.bringyour.network.ui.login.LoginViewModel

@Composable
fun LoginNavHost(
    loginViewModel: LoginViewModel = hiltViewModel()
) {

    val navController = rememberNavController()

    Box(
       modifier = Modifier.fillMaxSize()
    ) {

        NavHost(
            navController = navController,
            startDestination = "login-initial",
            enterTransition = { slideInHorizontally(
                initialOffsetX = { fullWidth -> fullWidth },
                animationSpec = tween(durationMillis = 300)
            ) + fadeIn(animationSpec = tween(300)
            ) },
            exitTransition = {
                ExitTransition.None
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

            composable("login-initial") {
                LoginInitial(
                    navController,
                    loginViewModel
                )
            }

            composable("login-password/{userAuth}") { backStackEntry ->

                val userAuth = backStackEntry.arguments?.getString("userAuth") ?: ""

                LoginPassword(
                    userAuth,
                    navController
                )
            }

            composable("create-network/{userAuth}") { backStackEntry ->

                val userAuth = backStackEntry.arguments?.getString("userAuth") ?: ""

                val createNetworkParams = LoginCreateNetworkParams.LoginCreateUserAuthParams(
                    userAuth = userAuth,
                )

                LoginCreateNetwork(
                    createNetworkParams,
                    navController
                )
            }

            composable("create-network-jwt/{userAuth}/{authJwt}/{userName}") { backStackEntry ->

                val userAuth = backStackEntry.arguments?.getString("userAuth") ?: ""
                val authJwt = backStackEntry.arguments?.getString("authJwt") ?: ""
                val userName = backStackEntry.arguments?.getString("userName") ?: ""
                val authJwtType = "google"

                val createNetworkParams = LoginCreateNetworkParams.LoginCreateAuthJwtParams(
                    userAuth = userAuth,
                    authJwtType = authJwtType,
                    authJwt = authJwt,
                    userName = userName
                )

                LoginCreateNetwork(
                    createNetworkParams,
                    navController
                )
            }

            composable("verify/{userAuth}") { backStackEntry ->

                val userAuth = backStackEntry.arguments?.getString("userAuth") ?: ""

                LoginVerify(
                    userAuth,
                    navController
                )
            }

            composable("reset-password/{userAuth}") { backStackEntry ->

                val userAuth = backStackEntry.arguments?.getString("userAuth") ?: ""

                LoginPasswordReset(
                    userAuth,
                    navController
                )
            }

            composable("reset-password-after-send/{userAuth}") { backStackEntry ->

                val userAuth = backStackEntry.arguments?.getString("userAuth") ?: ""

                LoginPasswordResetAfterSend(
                    userAuth,
                    navController
                )
            }
        }

        FullScreenOverlay()
    }

}