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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.onFocusChanged
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.api.ApiException
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.bringyour.network.BuildConfig
import com.bringyour.sdk.AuthLoginResult
import com.bringyour.sdk.Api
import com.bringyour.sdk.NetworkCreateArgs
import com.bringyour.network.LoginActivity
import com.bringyour.network.MainApplication
import com.bringyour.network.R
import com.bringyour.network.TAG
import com.bringyour.network.ui.components.URInlineErrorText
import com.bringyour.network.ui.components.overlays.WelcomeAnimatedOverlayLogin
import com.bringyour.network.ui.theme.BlueMedium
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.ethereumphone.walletsdk.WalletSDK

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

    val hasEthOsWallet = remember {
        hasEthOsWalletService(context)
    }

    LaunchedEffect(Unit) {
        if (loginViewModel.solanaAuthInProgress) {
            loginViewModel.setSolanaAuthInProgress(false)
        }

        if (loginViewModel.ethOsAuthInProgress) {
            loginViewModel.setEthOsAuthInProgress(false)
        }
    }

    // clear the bittensor auth spinner when returning from the sign message browser flow
    LifecycleResumeEffect(Unit) {
        if (loginViewModel.bittensorAuthInProgress) {
            loginViewModel.setBittensorAuthInProgress(false)
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

    val onCreateNetworkWallet: (
        blockchain: String,
        publicKey: String,
        signedMessage: String,
        signature: String
            ) -> Unit = { blockchain, pk, signedMessage, signature ->

        if (blockchain == "solana") {
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
                            Log.d("LoginInitial", "onCreateNetworkWallet (solana) signed pk=${result.signed.publicKey.isNotEmpty()}, message=${result.signed.message.isNotEmpty()}, signature=${result.signed.signature.isNotEmpty()}")

                            if (result.signed.publicKey != publicKey) {
                                loginViewModel.setLoginError("Wallet account changed. Please use the same account for create network.")
                                return@launch
                            }

                            val bundle = WalletCreateBundle(
                                blockchain = "solana",
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
        } else {
            // Ethereum path (out of scope): server does not require a wallet
            // challenge for this blockchain today — keep prior behavior as-is.
            val encodedPublicKey = Uri.encode(pk)
            val encodedSignedMessage = Uri.encode(signedMessage)
            val encodedSignature = Uri.encode(signature)
            val encodedBlockchain = Uri.encode(blockchain)

            navController.navigate("create-network/${encodedBlockchain}/${encodedPublicKey}/${encodedSignedMessage}/${encodedSignature}")
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
                        loginViewModel.walletLoginSolana(
                            context,
                            api,
                            result.signed.publicKey,
                            result.signed.message,
                            result.signed.signature,
                            { loginResult -> onLogin(loginResult.network.byJwt) },
                            onCreateNetworkWallet
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

    val connectEthOSWallet: () -> Unit = {

        if (hasEthOsWallet) {

            scope.launch(Dispatchers.IO) {
                try {
                    val wallet = WalletSDK(
                        context = context,
                        bundlerRPCUrl = BuildConfig.BUNDLER_RPC_URL,
                        // optional: override default web3 provider used for reads (eth_call, code, etc.)
//                web3jInstance = Web3j.build(HttpService("https://base.llamarpc.com")/)
                    )

                    val timestamp = Date().time.toString()
                    val message = "Welcome to URnetwork - $timestamp"

                    val address = wallet.getAddress()

                    val signature = wallet.signMessage(
                        message = message,
                        chainId = 1,
                        // type = "personal_sign" // optional (default)
                    )

                    loginViewModel.walletLoginEthereum(
                        context,
                        application?.api,
                        address,
                        message,
                        signature,
                        {result ->
                            onLogin(result.network.byJwt)
                        },
                        onCreateNetworkWallet
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "EthOS wallet error", e)
                    loginViewModel.setLoginError("EthOS wallet error: ${e.message}")
                }
            }

        } else {
            Toast.makeText(context, "No EthOs wallet found", Toast.LENGTH_SHORT).show()
        }

    }

    val connectBittensorWallet = {
        loginViewModel.setLoginError(null)

        // the signed message is returned to the LoginActivity
        // as ur://bittensor-sign-message
        if (launchBittensorSignMessage(context)) {
            loginViewModel.setBittensorAuthInProgress(true)
        } else {
            loginViewModel.setLoginError(context.getString(R.string.login_error))
        }
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
        createGuestModeInProgress = loginViewModel.createGuestModeInProgress,
        setCreateGuestModeInProgress = loginViewModel.setCreateGuestModeInProgress,
        allowGoogleSso = loginViewModel.allowGoogleSso,
        solanaLogin = {
            connectSolanaWallet()
                      },
        ethOsLogin = connectEthOSWallet,
        hasEthOsWallet = hasEthOsWallet,
        solanaAuthInProgress = loginViewModel.solanaAuthInProgress,
        ethOsAuthInProgress = loginViewModel.ethOsAuthInProgress,
        bittensorLogin = {
            connectBittensorWallet()
        },
        bittensorAuthInProgress = loginViewModel.bittensorAuthInProgress,
        onLogin = onLogin,
        contentVisible = contentVisible,
        setContentVisible = {
            contentVisible = it
        },
        welcomeOverlayVisible = welcomeOverlayVisible,
        setWelcomeOverlayVisible = {
            welcomeOverlayVisible = it
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
    createGuestModeInProgress: Boolean,
    setGoogleAuthInProgress: (Boolean) -> Unit,
    setCreateGuestModeInProgress: (Boolean) -> Unit,
    allowGoogleSso: () -> Boolean,
    solanaLogin: () -> Unit,
    ethOsLogin: () -> Unit,
    hasEthOsWallet: Boolean,
    solanaAuthInProgress: Boolean,
    ethOsAuthInProgress: Boolean,
    bittensorLogin: () -> Unit,
    bittensorAuthInProgress: Boolean,
    onLogin: (String) -> Unit,
    contentVisible: Boolean,
    setContentVisible: (Boolean) -> Unit,
    welcomeOverlayVisible: Boolean,
    setWelcomeOverlayVisible: (Boolean) -> Unit,
) {

    val context = LocalContext.current
    val application = context.applicationContext as? MainApplication

    val scope = rememberCoroutineScope()

    var guestModeOverlayVisible by remember { mutableStateOf(false) }

    val setGuestModeOverlayVisible: (Boolean) -> Unit = { isVisible ->
        if (isVisible) {
            setLoginError(null)
        }
        guestModeOverlayVisible = isVisible
    }

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
    val createNetworkLoginError = stringResource(id = R.string.create_network_error)

    val googleSignInOpts = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestIdToken(googleClientId)
        .requestEmail()
        .build()
    val googleSignInClient = GoogleSignIn.getClient(context, googleSignInOpts)


    val createGuestNetwork = createGuestNetwork@{
        if (createGuestModeInProgress) {
            return@createGuestNetwork
        }

        setLoginError(null)
        setCreateGuestModeInProgress(true)

        val args = NetworkCreateArgs()
        args.terms = true
        args.guestMode = true

        application?.api?.networkCreate(args) { result, err ->
            scope.launch {

                if (err != null) {
                    Log.i("OnboardingGuestModeOverlay", "error ${err.message}")
                    setLoginError(err.message)
                    setCreateGuestModeInProgress(false)
                } else if (result.error != null) {
                    Log.i("OnboardingGuestModeOverlay", "error ${result.error.message}")
                    setLoginError(result.error.message)
                    setCreateGuestModeInProgress(false)
                } else if (result.network != null && result.network.byJwt.isNotEmpty()) {
                    setLoginError(null)
                    setGuestModeOverlayVisible(false)

                    application.login(result.network.byJwt)

                    setContentVisible(false)

                    delay(500)

                    setWelcomeOverlayVisible(true)

                    delay(2250)

                    loginActivity?.authClientAndFinish(
                        { error ->
                            setCreateGuestModeInProgress(false)

                            setLoginError(error)

                            if (error != null) {
                                setContentVisible(true)
                            }
                        }
                    )

                } else {
                     setLoginError(createNetworkLoginError)
                    setCreateGuestModeInProgress(false)
                }
            }
        } ?: run {
            setLoginError(createNetworkLoginError)
            setCreateGuestModeInProgress(false)
        }

    }

    LaunchedEffect(Unit) {
        googleSignInClient.signOut()
        setGoogleAuthInProgress(false)
    }

    val onNetworkCreateGoogle: (
        email: String?,
        authJwt: String?,
        userName: String
            ) -> Unit = { email, authJwt, userName ->
        navController.navigate("create-network-jwt/${Uri.encode(email)}/${Uri.encode(authJwt)}/${Uri.encode(userName)}")
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
                {result ->
                    onLogin(result.network.byJwt)
                },
                onNetworkCreateGoogle
            )

        } catch (e: ApiException) {
            setLoginError("Error signing in with Google")
        }
    }

    val loginTitleSize: TextUnit = dimensionResource(id = R.dimen.login_title_size).value.sp

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
                    Row(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            stringResource(id = R.string.welcome_to_urnetwork),
                            style = MaterialTheme.typography.headlineLarge,
                            fontSize = loginTitleSize,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.login_margin_lg)))

                    LoginInitialActions(
                        userAuth = userAuth,
                        setUserAuth = setUserAuth,
                        userAuthInProgress = userAuthInProgress,
                        isValidUserAuth = isValidUserAuth,
                        setGuestModeOverlayVisible = setGuestModeOverlayVisible,
                        googleAuthInProgress = googleAuthInProgress,
                        createGuestModeInProgress = createGuestModeInProgress,
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
                        onEthOsLogin = ethOsLogin,
                        hasEthOsWallet = hasEthOsWallet,
                        ethOsAuthInProgress = ethOsAuthInProgress,
                        onBittensorLogin = bittensorLogin,
                        bittensorAuthInProgress = bittensorAuthInProgress,
                        launchAuthCodeLoginSheet = {
                            setAuthCodeLoginSheetVisible(true)
                        }
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

    OnboardingGuestModeSheet(
        isPresenting = guestModeOverlayVisible,
        setIsPresenting = {
            setGuestModeOverlayVisible(it)
        },
        onCreateGuestNetwork = {
            createGuestNetwork()
        },
        createGuestModeInProgress = createGuestModeInProgress,
        errorMessage = if (guestModeOverlayVisible) loginError else null
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
    setGuestModeOverlayVisible: (Boolean) -> Unit,
    googleAuthInProgress: Boolean,
    createGuestModeInProgress: Boolean,
    loginError: String?,
    onLogin: () -> Unit,
    onGoogleLogin: () -> Unit,
    allowGoogleSso: () -> Boolean,
    onSolanaLogin: () -> Unit,
    onEthOsLogin: () -> Unit,
    hasEthOsWallet: Boolean,
    ethOsAuthInProgress: Boolean,
    solanaAuthInProgress: Boolean,
    onBittensorLogin: () -> Unit,
    bittensorAuthInProgress: Boolean,
    launchAuthCodeLoginSheet: () -> Unit
) {

    val isLoginInProgress = userAuthInProgress || googleAuthInProgress || solanaAuthInProgress || ethOsAuthInProgress || bittensorAuthInProgress || createGuestModeInProgress

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 512.dp),
            horizontalAlignment = Alignment.Start
        ) {

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
                enabled = !isLoginInProgress
            )

            Spacer(modifier = Modifier.height(16.dp))

            URButton(
                onClick = {
                    onLogin()
                },
                enabled = !isLoginInProgress && isValidUserAuth,
                isProcessing = userAuthInProgress
            ) { buttonTextStyle ->
                Text(stringResource(id = R.string.get_started), style = buttonTextStyle)
            }

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
             * Google sign in
             */
            URButton(
                style = ButtonStyle.SECONDARY,
                onClick = {
                    onGoogleLogin()
                },
                enabled = !isLoginInProgress,
                isProcessing = googleAuthInProgress
            ) { buttonTextStyle ->
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    // todo - this looks a little blurry
                    Image(
                        painter = painterResource(id = R.drawable.google_login_icon),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        stringResource(id = R.string.google_auth_btn_text),
                        style = buttonTextStyle
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            /**
             * Bittensor Sign in
             */
            URButton(
                style = ButtonStyle.SECONDARY,
                onClick = {
                    onBittensorLogin()
                },
                enabled = !isLoginInProgress,
                isProcessing = bittensorAuthInProgress
            ) { buttonTextStyle ->
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Image(
                        painter = painterResource(id = R.drawable.bittensor_logo),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        stringResource(id = R.string.bittensor_sign_in),
                        style = buttonTextStyle
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            /**
             * Solana Sign in
             */
            URButton(
                style = ButtonStyle.SECONDARY,
                onClick = {
                    onSolanaLogin()
                },
                enabled = !isLoginInProgress,
                isProcessing = solanaAuthInProgress
            ) { buttonTextStyle ->
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Image(
                        painter = painterResource(id = R.drawable.solana_logo),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        stringResource(id = R.string.solana_sign_in),
                        style = buttonTextStyle
                    )
                }
            }

            if (hasEthOsWallet) {
                Spacer(modifier = Modifier.height(16.dp))

                /**
                 * EthOS Sign In
                 */
                URButton(
                    style = ButtonStyle.SECONDARY,
                    onClick = {
                        onEthOsLogin()
                    },
                     enabled = !isLoginInProgress,
                     isProcessing = ethOsAuthInProgress
                ) { buttonTextStyle ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        Image(
                            painter = painterResource(id = R.drawable.dgen1_logo),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                             stringResource(id = R.string.sign_in_dgen1),
                            style = buttonTextStyle
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            /**
             * Authentication code
             */
            URButton(
                style = ButtonStyle.SECONDARY,
                onClick = launchAuthCodeLoginSheet,
                enabled = !isLoginInProgress
            ) { buttonTextStyle ->
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Image(
                        painter = painterResource(id = R.drawable.auth_code),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        stringResource(id = R.string.auth_code_login_button_text),
                        style = buttonTextStyle
                    )
                }
            }

            // }

            if (!loginError.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                URInlineErrorText(loginError)
            }

            Spacer(modifier = Modifier.height(16.dp))

            TryGuestMode(
                setGuestModeOverlayVisible = setGuestModeOverlayVisible,
                enabled = !isLoginInProgress
            )

            Spacer(modifier = Modifier.height(16.dp))

            NetworkServerSelector(enabled = !isLoginInProgress)
        }
    }

}

@Composable
private fun TryGuestMode(
    setGuestModeOverlayVisible: (Boolean) -> Unit,
    enabled: Boolean
) {

    var isFocused by remember { mutableStateOf(false) }

    val guestModeStr = buildAnnotatedString {
        append(stringResource(id = R.string.commitment_issues))

        pushStringAnnotation(
            tag = "GUEST_MODE",
            annotation = "Guest Mode"
        )
        withStyle(
            style = SpanStyle(
                color = if (!enabled) TextMuted else if (isFocused) BlueMedium else Color.White
            )
        ) {
            append(" ${stringResource(id = R.string.try_guest_mode)}")
        }
        pop()

    }

    Row {
        Text(
            text = guestModeStr,
            modifier = Modifier
                .clickable {
                    if (enabled) {
                        setGuestModeOverlayVisible(true)
                    }
                }
                .onFocusChanged {
                    isFocused = it.isFocused
                }
                .focusable(),
            style = MaterialTheme.typography.bodyLarge.copy(color = TextMuted),
        )
    }
}

fun hasEthOsWalletService(context: Context): Boolean {
    // from the docs https://docs.freedomfactory.io/build/jvm-sdk#groovy
    return context.getSystemService("wallet") != null
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
                    createGuestModeInProgress = false,
                    setGoogleAuthInProgress = {},
                    setCreateGuestModeInProgress = {},
                    allowGoogleSso = { true },
                    solanaAuthInProgress = false,
                    ethOsAuthInProgress = false,
                    solanaLogin = {},
                    ethOsLogin = {},
                    hasEthOsWallet = true,
                    bittensorAuthInProgress = false,
                    bittensorLogin = {},
                    onLogin = {},
                    contentVisible = true,
                    setContentVisible = {},
                    welcomeOverlayVisible = false,
                    setWelcomeOverlayVisible = {}
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
                    createGuestModeInProgress = false,
                    setGoogleAuthInProgress = {},
                    setCreateGuestModeInProgress = {},
                    allowGoogleSso = { true },
                    solanaAuthInProgress = false,
                    ethOsAuthInProgress = false,
                    solanaLogin = {},
                    ethOsLogin = {},
                    hasEthOsWallet = true,
                    bittensorAuthInProgress = false,
                    bittensorLogin = {},
                    onLogin = {},
                    contentVisible = true,
                    setContentVisible = {},
                    welcomeOverlayVisible = false,
                    setWelcomeOverlayVisible = {}
                )
            }
        }
    }
}
