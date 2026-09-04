package com.bringyour.network.ui

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.bringyour.network.ui.components.overlays.FullScreenOverlay
import com.bringyour.network.ui.components.overlays.WelcomeAnimatedOverlayLogin
import com.bringyour.network.ui.login.AuthCodeLoadingScreen
import com.bringyour.network.ui.login.CreateNetworkInstant
import com.bringyour.network.ui.login.CreateNetworkInstantViewModel
import com.bringyour.network.ui.login.LoginCreateNetwork
import com.bringyour.network.ui.login.LoginCreateNetworkParams
import com.bringyour.network.ui.login.LoginInitial
import com.bringyour.network.ui.login.LoginPassword
import com.bringyour.network.ui.login.LoginPasswordReset
import com.bringyour.network.ui.login.LoginPasswordResetAfterSend
import com.bringyour.network.ui.login.LoginVerify
import com.bringyour.network.ui.login.LoginViewModel
import com.bringyour.network.ui.login.SeedphraseDisplayScreen
import com.bringyour.network.ui.login.SwitchAccountScreen
import com.bringyour.network.ui.login.toWalletCreateBundle
import com.bringyour.network.ui.shared.viewmodels.OverlayViewModel
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LoginNavHost(
    loginViewModel: LoginViewModel,
    promptAccountSwitch: Boolean,
    currentNetworkName: String? = null,
    targetJwt: String? = null,
    startInstantCreate: Boolean = false,
    isLoadingAuthCode: Boolean,
    // the entrance animation for a sign-in that completes in the activity (an
    // auth code link, the Apple or bittensor return) rather than on a screen
    welcomeOverlayVisible: Boolean = false,
    referralCode: String?,
    activityResultSender: ActivityResultSender?,
    walletCreateNetworkParams: LoginCreateNetworkParams.LoginCreateWalletParams? = null,
    jwtCreateNetworkParams: LoginCreateNetworkParams.LoginCreateAuthJwtParams? = null,
    overlayViewModel: OverlayViewModel = hiltViewModel()
) {
    val navController = rememberNavController()

    var switchAccount by remember { mutableStateOf(promptAccountSwitch) }

    LaunchedEffect(promptAccountSwitch) {
        if (promptAccountSwitch) {
            switchAccount = true
        }
    }

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
                    setSwitchAccount = { switchAccount = it }
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

                    composable("create-network/{blockchain}/{walletAddress}/{signedMessage}/{signature}") { backStackEntry ->

                        val blockchain = backStackEntry.arguments?.getString("blockchain") ?: ""
                        val walletAddress = backStackEntry.arguments?.getString("walletAddress") ?: ""
                        val signedMessage = backStackEntry.arguments?.getString("signedMessage") ?: ""
                        val signature = backStackEntry.arguments?.getString("signature") ?: ""

                        val createNetworkParams = LoginCreateNetworkParams.LoginCreateWalletParams(
                            blockchain = blockchain,
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

                    composable("create-network-wallet/{bundle}") { backStackEntry ->

                        val bundleArg = backStackEntry.arguments?.getString("bundle") ?: ""
                        val walletBundle = bundleArg.toWalletCreateBundle()

                        if (walletBundle == null) {
                            // invalid/corrupted bundle - don't strand the user on a
                            // create-network screen with blank, unusable wallet params
                            LaunchedEffect(Unit) {
                                navController.popBackStack()
                            }
                        } else {
                            val createNetworkParams = LoginCreateNetworkParams.LoginCreateWalletParams(
                                blockchain = walletBundle.blockchain,
                                publicKey = walletBundle.publicKey,
                                signedMessage = walletBundle.signedMessage,
                                signature = walletBundle.signature,
                                referralCode = referralCode
                            )

                            LoginCreateNetwork(
                                createNetworkParams,
                                navController
                            )
                        }
                    }

                    composable("create-network-jwt/{authJwtType}/{userAuth}/{authJwt}/{userName}") { backStackEntry ->

                        val userAuth = backStackEntry.arguments?.getString("userAuth") ?: ""
                        val authJwt = backStackEntry.arguments?.getString("authJwt") ?: ""
                        val userName = backStackEntry.arguments?.getString("userName") ?: ""
                        // "google" from the native button, "apple" (or "google") from the ur.io sso bridge
                        val authJwtType = backStackEntry.arguments?.getString("authJwtType")?.ifEmpty { null } ?: "google"

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

                    composable("create-network-instant") {
                        val context = LocalContext.current
                        val application = context.applicationContext as? com.bringyour.network.MainApplication
                        val loginActivity = context as? com.bringyour.network.LoginActivity
                        val createNetworkInstantViewModel: CreateNetworkInstantViewModel = hiltViewModel()
                        val seedphrase by createNetworkInstantViewModel.seedphrase.collectAsState()

                        val scope = rememberCoroutineScope()
                        var contentVisible by remember { mutableStateOf(true) }
                        var welcomeVisible by remember { mutableStateOf(false) }

                        // the account exists server side and its jwt is persisted
                        // before the phrase is shown, so there is no way back out of
                        // this screen -- both exits go forward into the app, and a
                        // failed finish leaves the phrase up to retry instead of
                        // stranding an account whose seedphrase was never seen.
                        // Entering plays the welcome animation every other sign-in
                        // plays before the main activity opens on its Enter card
                        val continueIntoApp: () -> Unit = {
                            if (!welcomeVisible) {
                                scope.launch {
                                    contentVisible = false
                                    delay(500)
                                    welcomeVisible = true
                                    delay(2250)
                                    val activity = loginActivity ?: run {
                                        welcomeVisible = false
                                        contentVisible = true
                                        return@launch
                                    }
                                    activity.authClientAndFinish { error ->
                                        if (error != null) {
                                            android.util.Log.e("LoginNavHost", "auth client finish err: $error")
                                            android.widget.Toast.makeText(context, "Error logging in, please try again.", android.widget.Toast.LENGTH_LONG).show()
                                            welcomeVisible = false
                                            contentVisible = true
                                        }
                                    }
                                }
                            }
                        }

                        val createdSeedphrase = seedphrase
                        if (createdSeedphrase == null) {
                            CreateNetworkInstant(
                                appLogin = { jwt, newNetwork -> application?.login(jwt, newNetwork = newNetwork) },
                                onBack = {
                                    navController.popBackStack()
                                },
                                createNetworkInstantViewModel = createNetworkInstantViewModel
                            )
                        } else {
                            AnimatedVisibility(
                                visible = contentVisible,
                                exit = fadeOut(),
                            ) {
                                SeedphraseDisplayScreen(
                                    seedphrase = createdSeedphrase,
                                    onConfirmed = continueIntoApp,
                                    onBack = continueIntoApp
                                )
                            }
                            if (welcomeVisible) {
                                WelcomeAnimatedOverlayLogin()
                            }
                        }
                    }
                }

                // a "?guest" deep link: guest accounts are gone, so offer the
                // instant (seedphrase-backed) account instead of silently creating
                // one whose generated seedphrase the user never sees
                LaunchedEffect(startInstantCreate) {
                    if (startInstantCreate) {
                        // singleTop: this effect re-runs after an activity
                        // recreate, when the nav controller has already restored
                        // this destination
                        navController.navigate("create-network-instant") {
                            launchSingleTop = true
                        }
                    }
                }

                // an unlinked wallet auth was received by the activity (eg the bittensor
                // sign message deep link) -> route into the create network flow
                // an sso identity (apple / google through the ur.io bridge) for a user
                // with no network yet -> route into the create network flow
                LaunchedEffect(jwtCreateNetworkParams) {
                    jwtCreateNetworkParams?.let { params ->
                        val encodedType = Uri.encode(params.authJwtType)
                        val encodedUserAuth = Uri.encode(params.userAuth.ifEmpty { "-" })
                        val encodedAuthJwt = Uri.encode(params.authJwt)
                        val encodedUserName = Uri.encode(params.userName.ifEmpty { "-" })
                        navController.navigate("create-network-jwt/${encodedType}/${encodedUserAuth}/${encodedAuthJwt}/${encodedUserName}")
                    }
                }

                LaunchedEffect(walletCreateNetworkParams) {
                    walletCreateNetworkParams?.let { params ->

                        val encodedBlockchain = Uri.encode(params.blockchain)
                        val encodedPublicKey = Uri.encode(params.publicKey)
                        val encodedSignedMessage = Uri.encode(params.signedMessage)
                        val encodedSignature = Uri.encode(params.signature)

                        navController.navigate("create-network/${encodedBlockchain}/${encodedPublicKey}/${encodedSignedMessage}/${encodedSignature}")
                    }
                }

                FullScreenOverlay(
                    referralCode = null,
                    overlayViewModel = overlayViewModel
                )
            }

        }

        if (welcomeOverlayVisible) {
            WelcomeAnimatedOverlayLogin()
        }

    }

}
