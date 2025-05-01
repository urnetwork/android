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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.bringyour.network.ui.components.overlays.FullScreenOverlay
import com.bringyour.network.ui.login.AuthCodeLoadingScreen
import com.bringyour.network.ui.login.LoginCreateNetwork
import com.bringyour.network.ui.login.LoginCreateNetworkParams
import com.bringyour.network.ui.login.LoginInitial
import com.bringyour.network.ui.login.LoginPassword
import com.bringyour.network.ui.login.LoginPasswordReset
import com.bringyour.network.ui.login.LoginPasswordResetAfterSend
import com.bringyour.network.ui.login.LoginVerify
import com.bringyour.network.ui.login.LoginViewModel
import com.bringyour.network.ui.login.SwitchAccountScreen
import com.bringyour.network.ui.shared.viewmodels.OverlayViewModel
import com.bringyour.network.utils.isTv
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender

@Composable
fun LoginNavHost(
    loginViewModel: LoginViewModel,
    promptAccountSwitch: Boolean,
    currentNetworkName: String? = null,
    targetJwt: String? = null,
    switchToGuestMode: Boolean,
    isLoadingAuthCode: Boolean,
    referralCode: String?,
    activityResultSender: ActivityResultSender?,
    overlayViewModel: OverlayViewModel = hiltViewModel()
) {
    val navController = rememberNavController()
    val isTv = isTv()

    val (switchAccount, setSwitchAccount) = remember { mutableStateOf(promptAccountSwitch) }

    Box(
       modifier = Modifier.fillMaxSize()
    ) {

        if (isLoadingAuthCode) {
            AuthCodeLoadingScreen()
        } else {

            if (switchAccount && !currentNetworkName.isNullOrEmpty()) {
                SwitchAccountScreen(
                    currentNetworkName = currentNetworkName,
                    targetJwt = targetJwt,
                    switchToGuestMode = switchToGuestMode,
                    setSwitchAccount = setSwitchAccount
                )
            } else {
                NavHost(
                    navController = navController,
                    startDestination = "login-initial",
                    enterTransition = { slideInHorizontally(
                        initialOffsetX = { fullWidth -> fullWidth },
                        animationSpec = tween(durationMillis = 300)
                    ) + fadeIn(animationSpec = tween(300)
                    ) },
                    exitTransition = {
                        if (isTv) {
                            slideOutHorizontally (
                                animationSpec = tween(durationMillis = 300)
                            ) + fadeOut(animationSpec = tween(300))
                        } else {
                            ExitTransition.None
                        }
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
                            loginViewModel,
                            activityResultSender
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
                            referralCode = referralCode
                        )

                        LoginCreateNetwork(
                            createNetworkParams,
                            navController
                        )
                    }

                    composable("create-network/{walletAddress}/{signedMessage}/{signature}") { backStackEntry ->

                        val walletAddress = backStackEntry.arguments?.getString("walletAddress") ?: ""
                        val signedMessage = backStackEntry.arguments?.getString("signedMessage") ?: ""
                        val signature = backStackEntry.arguments?.getString("signature") ?: ""

                        val createNetworkParams = LoginCreateNetworkParams.LoginCreateSolanaParams(
                            publicKey = walletAddress,
                            signedMessage = signedMessage,
                            signature = signature,
                            referralCode = referralCode
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
                            userName = userName,
                            referralCode = referralCode
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

                FullScreenOverlay(
                    referralCodeViewModel = null,
                    overlayViewModel = overlayViewModel
                )
            }

        }

    }

}