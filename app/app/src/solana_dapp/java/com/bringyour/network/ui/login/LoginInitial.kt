package com.bringyour.network.ui.login

import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Scaffold
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bringyour.network.ui.components.ButtonStyle
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.components.URTextInput
import com.bringyour.network.ui.login.NetworkServerSelector
import com.bringyour.network.ui.theme.URNetworkTheme
import androidx.compose.ui.res.painterResource
import com.bringyour.network.ui.theme.TextMuted
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.api.ApiException
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.bringyour.sdk.AuthLoginResult
import com.bringyour.sdk.Api
import com.bringyour.network.LoginActivity
import com.bringyour.network.MainApplication
import com.bringyour.network.R
import com.bringyour.network.TAG
import com.bringyour.network.ui.components.URInlineErrorText
import com.bringyour.network.ui.components.overlays.WelcomeAnimatedOverlayLogin
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import kotlinx.coroutines.launch

@Composable()
fun LoginInitial(
    navController: NavController,
    loginViewModel: LoginViewModel,
    activityResultSender: ActivityResultSender?,
) {

    val context = LocalContext.current
    val application = context.applicationContext as? MainApplication
    val loginActivity = context as? LoginActivity
    var contentVisible by remember { mutableStateOf(true) }
    var welcomeOverlayVisible by remember { mutableStateOf(false) }
    var noSolanaWalletsFound by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        if (loginViewModel.solanaAuthInProgress) {
            loginViewModel.setSolanaAuthInProgress(false)
        }
    }

    // clear the bittensor auth spinner when returning from the sign message browser flow
    LifecycleResumeEffect(Unit) {
        if (loginViewModel.bittensorAuthInProgress) {
            loginViewModel.setBittensorAuthInProgress(false)
        }
        if (loginViewModel.appleAuthInProgress) {
            loginViewModel.setAppleAuthInProgress(false)
        }
        onPauseOrDispose {}
    }

    val onLogin: (String) -> Unit = { networkJwt ->
        handleLoginFlow(
            networkJwt = networkJwt,
            scope = scope,
            appLogin = { application?.login(networkJwt) },
            onContentVisibilityChange = {
                contentVisible = it
            },
            onErr = {
                Toast.makeText(context, "Error logging in, please try again.", Toast.LENGTH_LONG).show()
            },
            onWelcomeOverlayVisibilityChange = {
                welcomeOverlayVisible = it
            },
            authClientAndFinish = { cb ->
                loginActivity?.authClientAndFinish(cb)
            }
        )
    }

    val onCreateNetworkSolana: (
        blockchain: String,
        publicKey: String,
        signedMessage: String,
        signature: String
            ) -> Unit = { blockchain, publicKey, _, _ ->

        scope.launch {
            activityResultSender?.let { sender ->
                val api = application?.api
                if (api == null) {
                    loginViewModel.setLoginError("Error connecting to wallet")
                    return@launch
                }

                // the message/signature this callback was invoked with were
                // already consumed by the just-completed /auth/login call —
                // fetch and sign a brand-new challenge before creating the network
                when (val result = requestAndSignSolanaChallenge(sender, api)) {
                    is SolanaChallengeSignResult.Success -> {
                        Log.d("LoginInitial", "onCreateNetworkSolana signed pk=${result.signed.publicKey.isNotEmpty()}, message=${result.signed.message.isNotEmpty()}, signature=${result.signed.signature.isNotEmpty()}")

                        if (result.signed.publicKey != publicKey) {
                            loginViewModel.setLoginError("Wallet account changed. Please use the same account for create network.")
                            return@launch
                        }

                        val bundle = WalletCreateBundle(
                            blockchain = blockchain,
                            publicKey = result.signed.publicKey,
                            signedMessage = result.signed.message,
                            signature = result.signed.signature
                        )

                        navController.navigate("create-network-wallet/${bundle.toBase64Json()}")
                    }
                    is SolanaChallengeSignResult.NoWalletFound -> {
                        noSolanaWalletsFound = true
                    }
                    is SolanaChallengeSignResult.Failure -> {
                        loginViewModel.setLoginError("Error connecting to wallet")
                    }
                }
            }
        }
    }

    val connectSolanaWallet = {
        scope.launch {
            activityResultSender?.let { sender ->
                val api = application?.api
                if (api == null) {
                    loginViewModel.setLoginError("Error connecting to wallet")
                    return@launch
                }

                when (val result = requestAndSignSolanaChallenge(sender, api)) {
                    is SolanaChallengeSignResult.Success -> {
                        loginViewModel.walletLogin(
                            context,
                            api,
                            result.signed.publicKey,
                            result.signed.message,
                            result.signed.signature,
                            { loginResult -> onLogin(loginResult.network.byJwt) },
                            onCreateNetworkSolana
                        )
                    }
                    is SolanaChallengeSignResult.NoWalletFound -> {
                        noSolanaWalletsFound = true
                        Log.i("LoginInitial", "No MWA compatible wallet app found on device.")
                    }
                    is SolanaChallengeSignResult.Failure -> {
                        loginViewModel.setLoginError("Error connecting to wallet")
                        Log.i("LoginInitial", "Error connecting to wallet: ${result.error}")
                    }
                }
            }
        }
    }

    val connectBittensorWallet = {
        loginViewModel.setLoginError(null)

        scope.launch {
            val api = application?.api
            if (api == null) {
                loginViewModel.setLoginError(context.getString(R.string.login_error))
                return@launch
            }

            requestBittensorChallenge(api)
                .onSuccess { message ->
                    if (launchBittensorSignMessage(context, message, BITTENSOR_SIGN_PURPOSE_LOGIN)) {
                        loginViewModel.setBittensorAuthInProgress(true)
                    } else {
                        loginViewModel.setLoginError(context.getString(R.string.login_error))
                    }
                }
                .onFailure { error ->
                    Log.i("LoginInitial", "Error fetching Bittensor challenge: $error")
                    loginViewModel.setLoginError(context.getString(R.string.login_error))
                }
        }
    }

    // Apple has no Android SDK: Apple's own web flow runs in a Custom Tab and
    // the api's callback returns through ur://oauth/apple (handled by the LoginActivity)
    val connectApple = {
        loginViewModel.setLoginError(null)
        val apiUrl = application?.networkSpaceManagerProvider?.getNetworkSpace()?.apiUrl
        if (launchAppleOAuth(context, apiUrl)) {
            loginViewModel.setAppleAuthInProgress(true)
        } else {
            loginViewModel.setLoginError(context.getString(R.string.login_error))
        }
    }

    // the seed sign-in is a slide-up sheet, like the auth code sign-in
    var seedphraseLoginSheetVisible by remember { mutableStateOf(false) }
    val onSeedphraseLogin: () -> Unit = {
        seedphraseLoginSheetVisible = true
    }

    val onInstantAccountCreate: () -> Unit = {
        navController.navigate("create-network-instant")
    }

    LoginInitial(
        navController,
        userAuth = loginViewModel.userAuth,
        setUserAuth = loginViewModel.setUserAuth,
        userAuthInProgress = loginViewModel.userAuthInProgress,
        isValidUserAuth = loginViewModel.isValidUserAuth,
        login = loginViewModel.login,
        googleLogin = loginViewModel.googleLogin,
        loginError = loginViewModel.loginError,
        setGoogleAuthInProgress = loginViewModel.setGoogleAuthInProgress,
        setLoginError = loginViewModel.setLoginError,
        googleAuthInProgress = loginViewModel.googleAuthInProgress,
        allowGoogleSso = loginViewModel.allowGoogleSso,
        solanaLogin = {
            connectSolanaWallet()
                      },
        solanaAuthInProgress = loginViewModel.solanaAuthInProgress,
        bittensorLogin = {
            connectBittensorWallet()
        },
        bittensorAuthInProgress = loginViewModel.bittensorAuthInProgress,
        appleLogin = {
            connectApple()
        },
        appleAuthInProgress = loginViewModel.appleAuthInProgress,
        onLogin = onLogin,
        contentVisible = contentVisible,
        setContentVisible = {
            contentVisible = it
        },
        welcomeOverlayVisible = welcomeOverlayVisible,
        setWelcomeOverlayVisible = {
            welcomeOverlayVisible = it
        },
        onSeedphraseLogin = onSeedphraseLogin,
        onInstantAccountCreate = onInstantAccountCreate
    )

    SeedphraseLoginSheet(
        isPresenting = seedphraseLoginSheetVisible,
        setIsPresenting = { seedphraseLoginSheetVisible = it },
        // deliberately not the welcome-overlay login flow: its delays only exist
        // to time the overlay, which this sheet doesn't show, and they leave a
        // window where the session is already persisted but the login hasn't
        // finished
        onLogin = { jwt ->
            val application = context.applicationContext as? com.bringyour.network.MainApplication
            val loginActivity = context as? com.bringyour.network.LoginActivity
            application?.login(jwt)
            loginActivity?.authClientAndFinish { error ->
                if (error != null) {
                    android.util.Log.e("LoginInitial", "auth client finish err: $error")
                    android.widget.Toast.makeText(context, "Error logging in, please try again.", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    )

    if (noSolanaWalletsFound) {

        NoSolanaWalletsAlert(
            onDismiss = {
                noSolanaWalletsFound = false
            }
        )

    }

}

@Composable()
fun LoginInitial(
    navController: NavController,
    userAuth: TextFieldValue,
    setUserAuth: (TextFieldValue) -> Unit,
    userAuthInProgress: Boolean,
    isValidUserAuth: Boolean,
    login: (
        ctx: Context,
        api: Api?,
        onLogin: (AuthLoginResult) -> Unit,
        onNewNetwork: (AuthLoginResult) -> Unit,
    ) -> Unit,
    googleLogin: (
        context: Context,
        api: Api?,
        account: GoogleSignInAccount,
        onLogin: (AuthLoginResult) -> Unit,
        onCreateNetwork: (email: String?, authJwt: String?, userName: String) -> Unit,
    ) -> Unit,
    loginError: String?,
    setLoginError: (String?) -> Unit,
    googleAuthInProgress: Boolean,
    setGoogleAuthInProgress: (Boolean) -> Unit,
    allowGoogleSso: () -> Boolean,
    solanaLogin: () -> Unit,
    solanaAuthInProgress: Boolean,
    bittensorLogin: () -> Unit,
    bittensorAuthInProgress: Boolean,
    appleLogin: () -> Unit = {},
    appleAuthInProgress: Boolean = false,
    onLogin: (String) -> Unit, // network jwt
    contentVisible: Boolean,
    setContentVisible: (Boolean) -> Unit,
    welcomeOverlayVisible: Boolean,
    setWelcomeOverlayVisible: (Boolean) -> Unit,
    onSeedphraseLogin: () -> Unit,
    onInstantAccountCreate: () -> Unit,
) {

    val context = LocalContext.current
    val application = context.applicationContext as? MainApplication
    val scope = rememberCoroutineScope()

    var authCodeLoginSheetVisible by remember { mutableStateOf(false) }

    val setAuthCodeLoginSheetVisible: (Boolean) -> Unit = { isVisible ->
        authCodeLoginSheetVisible = isVisible
    }

    val loginActivity = context as? LoginActivity

    val navigateToLoginPassword: (AuthLoginResult) -> Unit = { result ->
        navController.navigate("login-password/${Uri.encode(result.userAuth)}")
    }

    val onNewNetwork: (AuthLoginResult) -> Unit = { result ->
        navController.navigate("create-network/${Uri.encode(result.userAuth)}")
    }

    val googleClientId = stringResource(id = R.string.google_client_id)
    val googleSignInOpts = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestIdToken(googleClientId)
        .requestEmail()
        .build()
    val googleSignInClient = GoogleSignIn.getClient(context, googleSignInOpts)
    val createNetworkError = stringResource(id = R.string.create_network_error)

    LaunchedEffect(Unit) {
        googleSignInClient.signOut()
        setGoogleAuthInProgress(false)
    }

    val onNetworkCreateGoogle: (
        email: String?,
        authJwt: String?,
        userName: String
            ) -> Unit = { email, authJwt, userName ->
        navController.navigate("create-network-jwt/google/${Uri.encode(email)}/${Uri.encode(authJwt)}/${Uri.encode(userName)}")
    }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)

            googleLogin(
                context,
                application?.api,
                account,
                { result ->
                    onLogin(result.network.byJwt)
                },
                onNetworkCreateGoogle
            )

        } catch (e: ApiException) {
            setLoginError("Error signing in with Google")
        }
    }

    AnimatedVisibility(
        visible = contentVisible,
        enter = EnterTransition.None,
        exit = fadeOut()
    ) {

        Scaffold { innerPadding ->

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(innerPadding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Column(
                    modifier = Modifier.imePadding()
                ) {
                    OnboardingCarousel()

                    Spacer(modifier = Modifier.height(64.dp))

                    LoginInitialActions(
                        userAuth = userAuth,
                        setUserAuth = setUserAuth,
                        userAuthInProgress = userAuthInProgress,
                        isValidUserAuth = isValidUserAuth,
                        googleAuthInProgress = googleAuthInProgress,
                        loginError = loginError,
                        onLogin = {
                            login(
                                context,
                                application?.api,
                                navigateToLoginPassword,
                                onNewNetwork,
                            )
                        },
                        onGoogleLogin = {
                            googleSignInLauncher.launch(googleSignInClient.signInIntent)
                        },
                        allowGoogleSso = allowGoogleSso,
                        onSolanaLogin = solanaLogin,
                        solanaAuthInProgress = solanaAuthInProgress,
                        onBittensorLogin = bittensorLogin,
                        bittensorAuthInProgress = bittensorAuthInProgress,
                        onAppleLogin = appleLogin,
                        appleAuthInProgress = appleAuthInProgress,
                        launchAuthCodeLoginSheet = {
                            setAuthCodeLoginSheetVisible(true)
                        },
                        onSeedphraseLogin = onSeedphraseLogin,
                        onInstantAccountCreate = onInstantAccountCreate
                    )
                }

            }
        }
    }

    AuthCodeLoginSheet(
        isPresenting = authCodeLoginSheetVisible,
        setIsPresenting = {
            setAuthCodeLoginSheetVisible(it)
        },
        onLogin = { jwt ->
            onLogin(jwt)
        }
    )

    if (welcomeOverlayVisible) {

        WelcomeAnimatedOverlayLogin()

    }
}

@Composable
fun LoginInitialActions(
    userAuth: TextFieldValue,
    setUserAuth: (TextFieldValue) -> Unit,
    userAuthInProgress: Boolean,
    isValidUserAuth: Boolean,
    googleAuthInProgress: Boolean,
    loginError: String?,
    onLogin: () -> Unit,
    onGoogleLogin: () -> Unit,
    allowGoogleSso: () -> Boolean,
    onSolanaLogin: () -> Unit,
    solanaAuthInProgress: Boolean,
    onBittensorLogin: () -> Unit,
    bittensorAuthInProgress: Boolean,
    onAppleLogin: () -> Unit = {},
    appleAuthInProgress: Boolean = false,
    launchAuthCodeLoginSheet: () -> Unit,
    onSeedphraseLogin: () -> Unit,
    onInstantAccountCreate: () -> Unit,
) {
    val isLoginInProgress = userAuthInProgress || googleAuthInProgress || appleAuthInProgress || solanaAuthInProgress || bittensorAuthInProgress

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 512.dp),
            horizontalAlignment = Alignment.Start
        ) {

            // the login stack rule (LoginStack.kt): up to three full-width buttons,
            // then icon tiles four per row with each row filled; this flavor's lists
            LoginStack(
                full = listOf(
                    solanaLoginMethod(onClick = onSolanaLogin, processing = solanaAuthInProgress),
                    googleLoginMethod(onClick = onGoogleLogin, processing = googleAuthInProgress),
                    instantAccountLoginMethod(onClick = onInstantAccountCreate)
                ),
                tiles = listOf(
                    secretKeyLoginMethod(onClick = onSeedphraseLogin),
                    authCodeLoginMethod(onClick = launchAuthCodeLoginSheet, tile = true),
                    appleLoginMethod(onClick = onAppleLogin, processing = appleAuthInProgress, tile = true),
                    bittensorLoginMethod(onClick = onBittensorLogin, processing = bittensorAuthInProgress, tile = true)
                ),
                enabled = !isLoginInProgress
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    "or",
                    color = TextMuted
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            /**
             * Email / phone
             */
            URTextInput(
                value = userAuth,
                onValueChange = {
                    setUserAuth(it)
                },
                placeholder = stringResource(id = R.string.user_auth_placeholder),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Go
                ),
                onGo = {
                    if (!isLoginInProgress && isValidUserAuth) {
                        onLogin()
                    }
                },
                label = stringResource(id = R.string.user_auth_label),
                enabled = !isLoginInProgress,
                modifier = Modifier.testTag("acceptance.password.user")
            )

            Spacer(modifier = Modifier.height(16.dp))

            URButton(
                onClick = {
                    onLogin()
                },
                enabled = !isLoginInProgress && isValidUserAuth,
                isProcessing = userAuthInProgress,
                modifier = Modifier.testTag("acceptance.password.next")
            ) { buttonTextStyle ->
                Text(stringResource(id = R.string.get_started), style = buttonTextStyle)
            }

            if (!loginError.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                URInlineErrorText(loginError)
            }

            Spacer(modifier = Modifier.height(16.dp))

            NetworkServerSelector(enabled = !isLoginInProgress)
        }
    }

}

@Preview()
@Composable
private fun LoginInitialPreview() {

    val navController = rememberNavController()

    val login: (
        Context,
        Api?,
        (AuthLoginResult) -> Unit,
        (AuthLoginResult) -> Unit,
    ) -> Unit = { context, api, onLogin, onNewNetwork ->

    }

    val googleLogin : (
        Context,
        Api?,
        GoogleSignInAccount,
        (AuthLoginResult) -> Unit,
        (email: String?, authJwt: String?, userName: String) -> Unit,
    ) -> Unit = { context, api, account, onLogin, onNewNetwork ->

    }

    URNetworkTheme {
        Scaffold(
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                LoginInitial(
                    navController = navController,
                    userAuth = TextFieldValue("hello@ur.io"),
                    setUserAuth = {},
                    userAuthInProgress = false,
                    isValidUserAuth = true,
                    login = login,
                    googleLogin = googleLogin,
                    loginError = null,
                    setLoginError = {},
                    googleAuthInProgress = false,
                    setGoogleAuthInProgress = {},
                    allowGoogleSso = { true },
                    solanaAuthInProgress = false,
                    solanaLogin = {},
                    bittensorAuthInProgress = false,
                    bittensorLogin = {},
                    onLogin = {},
                    contentVisible = true,
                    setContentVisible = {},
                    welcomeOverlayVisible = false,
                    setWelcomeOverlayVisible = {},
                    onSeedphraseLogin = {},
                    onInstantAccountCreate = {},
                )
            }
        }
    }
}

@Preview(
    name = "Landscape Preview",
    device = "spec:width=1920dp,height=1080dp,dpi=480"
)
@Composable
private fun LoginInitialLandscapePreview() {
    val navController = rememberNavController()

    val login: (
        Context,
        Api?,
        (AuthLoginResult) -> Unit,
        (AuthLoginResult) -> Unit,
    ) -> Unit = { context, api, onLogin, onNewNetwork ->

    }

    val googleLogin : (
        Context,
        Api?,
        GoogleSignInAccount,
        (AuthLoginResult) -> Unit,
        (email: String?, authJwt: String?, userName: String) -> Unit,
    ) -> Unit = { context, api, account, onLogin, onNewNetwork ->

    }

    URNetworkTheme {
        Scaffold(
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                LoginInitial(
                    navController = navController,
                    userAuth = TextFieldValue("hello@ur.io"),
                    setUserAuth = {},
                    userAuthInProgress = false,
                    isValidUserAuth = true,
                    login = login,
                    googleLogin = googleLogin,
                    loginError = null,
                    setLoginError = {},
                    googleAuthInProgress = false,
                    setGoogleAuthInProgress = {},
                    allowGoogleSso = { true },
                    solanaAuthInProgress = false,
                    solanaLogin = {},
                    bittensorAuthInProgress = false,
                    bittensorLogin = {},
                    onLogin = {},
                    contentVisible = true,
                    setContentVisible = {},
                    welcomeOverlayVisible = false,
                    setWelcomeOverlayVisible = {},
                    onSeedphraseLogin = {},
                    onInstantAccountCreate = {},
                )
            }
        }
    }
}
