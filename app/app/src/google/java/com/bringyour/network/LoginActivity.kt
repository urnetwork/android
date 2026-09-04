package com.bringyour.network

import android.content.Intent
import android.graphics.Color.TRANSPARENT
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import android.util.Log
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import com.bringyour.sdk.AuthNetworkClientArgs
import com.bringyour.sdk.WalletAuthArgs
import com.bringyour.network.ui.LoginNavHost
import com.bringyour.network.ui.login.BITTENSOR_SIGN_PURPOSE_CONNECT
import com.bringyour.network.ui.login.BITTENSOR_SIGN_PURPOSE_CREATE
import com.bringyour.network.ui.login.BITTENSOR_SIGN_PURPOSE_LOGIN
import com.bringyour.network.ui.login.LoginCreateNetworkParams
import com.bringyour.network.ui.login.LoginViewModel
import com.bringyour.network.ui.login.launchBittensorSignMessage
import com.bringyour.network.ui.login.AUTH_JWT_TYPE_APPLE
import com.bringyour.network.ui.login.AppleOAuthSession
import com.bringyour.network.ui.login.appleOAuthUserName
import com.bringyour.network.ui.login.isAppleOAuthReturn
import com.bringyour.network.ui.login.ssoJwtPayload
import com.bringyour.network.ui.login.requestBittensorChallenge
import com.bringyour.network.ui.wallet.SnWalletConnectExtras
import com.bringyour.network.ui.theme.URNetworkTheme
import com.bringyour.sdk.AuthCodeLoginArgs
import com.bringyour.sdk.AuthLoginArgs
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LoginActivity : AppCompatActivity() {

    private var app : MainApplication? = null


    private var referrerClient: InstallReferrerClient? = null
    private var referralCode by mutableStateOf<String?>(null)

    private val loginViewModel: LoginViewModel by viewModels()

    val activityResultSender = ActivityResultSender(this)

    private var promptAccountSwitch by mutableStateOf(false)
    private var currentNetworkName by mutableStateOf<String?>(null)
    private var targetJwt by mutableStateOf<String?>(null)
    private var targetUrl: String? = null
    private var defaultLocation: String? = null
    private var startInstantCreate by mutableStateOf(false)
    private var isLoadingAuthCode by mutableStateOf(false)

    // the entrance animation before the main activity opens, for the sign-ins
    // that complete in this activity rather than on a login screen
    private var welcomeOverlayVisible by mutableStateOf(false)
    private var walletCreateNetworkParams by mutableStateOf<LoginCreateNetworkParams.LoginCreateWalletParams?>(null)
    private var jwtCreateNetworkParams by mutableStateOf<LoginCreateNetworkParams.LoginCreateAuthJwtParams?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {

        val lightTransparentStyle = SystemBarStyle.dark(
            scrim = TRANSPARENT
        )
        enableEdgeToEdge(
            statusBarStyle = lightTransparentStyle,
            navigationBarStyle = lightTransparentStyle
        )

        super.onCreate(savedInstanceState)

        app = application as MainApplication

        // immutable shadow
        val app = app ?: return

        val action: String? = intent?.action

        if (Intent.ACTION_VIEW == action) {
            Log.i(TAG, "Intent.ACTION_VIEW == action")
            intent?.data?.let { u ->
                if (isAppleOAuthReturn(u)) {
                    Log.i(TAG, "appleOAuthLogin $u")
                    appleOAuthLogin(u)
                } else if (u.scheme == "ur" && u.host == "bittensor-sign-message") {
                    if (u.getQueryParameter("purpose") == BITTENSOR_SIGN_PURPOSE_CONNECT && app.device != null) {
                        // the earnings screen's wallet connect: the main activity owns that flow
                        Log.i(TAG, "forwardWalletConnectToMain $u")
                        forwardWalletConnectToMain(u)
                        return
                    }
                    Log.i(TAG, "bittensorSignMessageLogin $u")
                    bittensorSignMessageLogin(u)
                } else if ((u.scheme == "https" && u.host == "ur.io" && u.path == "/c") || (u.scheme == "ur")) {
                    Log.i(TAG, "createWithUri $u")
                    createWithUri(u)
                }
            }

        } else if (app.device != null) {
            navigateToMain()
            return
        } else if (app.deviceManager.canRefer) {
            // fresh install, async check the install referrer
            // see https://developer.android.com/google/play/installreferrer/library

            referrerClient = InstallReferrerClient.newBuilder(this).build()
            referrerClient?.startConnection(object : InstallReferrerStateListener {
                override fun onInstallReferrerSetupFinished(responseCode: Int) {
                    lifecycleScope.launch {
                        try {
                            when (responseCode) {
                                InstallReferrerClient.InstallReferrerResponse.OK -> {
                                    try {
                                        referrerClient?.installReferrer?.let { details ->
                                            details.installReferrer?.let {
                                                val u = Uri.parse(it)
                                                if (u.scheme == "https" && u.host == "ur.io" && u.path == "/c") {
                                                    Log.i(TAG, "referrerClient createWithUri $u")
                                                    createWithUri(Uri.parse(it))
                                                }
                                            }
                                        }
                                    } catch (e: Exception) {
                                        // do nothing
                                    }
                                }
                            }
                        } finally {
                            app.deviceManager.canRefer = false

                            referrerClient?.endConnection()
                            referrerClient = null
                        }
                    }
                }

                override fun onInstallReferrerServiceDisconnected() {
                }
            })
        }

        // this is so overlays don't get cut by top bar and bottom drawer
        // WindowCompat.setDecorFitsSystemWindows(window, false)

        // fixme use a custom view to show up/down statistics and hot linpath spark

        setContent {
            URNetworkTheme {
                LoginNavHost(
                    loginViewModel,
                    promptAccountSwitch = promptAccountSwitch,
                    targetJwt = targetJwt,
                    currentNetworkName = currentNetworkName,
                    startInstantCreate = startInstantCreate,
                    isLoadingAuthCode = isLoadingAuthCode,
                    welcomeOverlayVisible = welcomeOverlayVisible,
                    referralCode = referralCode,
                    activityResultSender = activityResultSender,
                    walletCreateNetworkParams = walletCreateNetworkParams,
                    jwtCreateNetworkParams = jwtCreateNetworkParams
                )
            }
        }
    }

    fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }


    private fun createWithUri(uri: Uri) {
        val app = app ?: return

        val queryParameters = mutableMapOf<String, String>()
        for (name in uri.queryParameterNames) {
            uri.getQueryParameter(name)?.let {
                queryParameters[name] = it
            }
        }
        val authCode = queryParameters.remove("auth_code")
        val guest = hasTruthyQueryParameter(uri, "guest")
        val upgradeSuccess = hasTruthyQueryParameter(uri, "subscription")
        referralCode = queryParameters.remove("bonus")
        targetUrl = queryParameters.remove("target")

        defaultLocation = extractDefaultLocation(uri)

        if (defaultLocation != null) {
            defaultLocation = defaultLocation?.removeSuffix("=")
        }

        val localState = app.asyncLocalState

        if (authCode != null) {

            isLoadingAuthCode = true

            val args = AuthCodeLoginArgs()
            args.authCode = authCode

            app.api?.authCodeLogin(args) { result, err ->

                val loginJwt = result.jwt

                if (err == null && loginJwt != null) {

                    lifecycleScope.launch {

                        if (app.asyncLocalState?.localState?.byJwt == loginJwt) {
                            // user already logged into this network

                            isLoadingAuthCode = false
                            setLinksAndStartMain(
                                targetUrl = targetUrl,
                                defaultLocation = defaultLocation
                            )

                        } else if (!app.asyncLocalState?.localState?.byJwt.isNullOrEmpty() && app.asyncLocalState?.localState?.byJwt != loginJwt) {
                            // user is logged in, but not to the account related to the auth code
                            // prompt account switch

                            if (localState != null) {
                                localState.parseByJwt { jwt, success ->
                                    lifecycleScope.launch {
                                        if (success && jwt != null) {
                                            targetJwt = loginJwt
                                            currentNetworkName = jwt.networkName
                                            promptAccountSwitch = true
                                            isLoadingAuthCode = false
                                        } else {
                                            Log.i(TAG, "authCodeLogin: local byJwt parse failed")
                                            app.logout()
                                            app.login(loginJwt)
                                            welcomeThenAuthClientAndFinish(
                                                callback = { error ->
                                                    if (error != null) {
                                                        Log.i(TAG, "authClientAndFinish error: $error")
                                                    }
                                                    isLoadingAuthCode = false
                                                },
                                            )
                                        }
                                    }
                                }
                            } else {
                                Log.i(TAG, "authCodeLogin: local state missing")
                                app.logout()
                                app.login(loginJwt)
                                welcomeThenAuthClientAndFinish(
                                    callback = { error ->
                                        if (error != null) {
                                            Log.i(TAG, "authClientAndFinish error: $error")
                                        }
                                        isLoadingAuthCode = false
                                    },
                                )
                            }

                        } else {

                            app.login(loginJwt)

                            welcomeThenAuthClientAndFinish(
                                callback = { err ->
                                    if (err != null) {
                                        Log.i(TAG, "authClientAndFinish error: $err")
                                    }
                                    isLoadingAuthCode = false
                                },
                            )
                        }
                    }

                } else {
                    isLoadingAuthCode = false
                    Log.i(TAG, "authCodeLogin: error: result is: $result")
                }

            } ?: run {
                isLoadingAuthCode = false
            }

        } else if (guest) {
            // guest accounts are gone. the same request now mints a permanent
            // seedphrase-backed account, so a "?guest" link offers the instant
            // account screen (which shows the generated seedphrase) instead of
            // creating one silently and discarding the only credential for it
            if (localState != null) {
                localState.parseByJwt { jwt, success ->
                    lifecycleScope.launch {
                        if (success && jwt != null) {
                            setLinksAndStartMain(targetUrl, defaultLocation)
                        } else {
                            Log.i(TAG, "guest link: no local byJwt, offering instant account")
                            startInstantCreate = true
                        }
                    }
                }
            } else {
                startInstantCreate = true
            }

        } else if (upgradeSuccess) {
            upgradeSubscriptionSuccessStartMain()
        } else if (app.device != null) {
            setLinksAndStartMain(targetUrl, defaultLocation)
        }
    }

    // the earnings screen asked the ur.io bridge to sign a wallet-connect challenge;
    // the main activity owns the earnings view model, so hand the signed result over
    private fun forwardWalletConnectToMain(uri: Uri) {
        val errorCode = uri.getQueryParameter("errorCode")
        val errorMessage = uri.getQueryParameter("errorMessage")
        val address = uri.getQueryParameter("address")
        val signature = uri.getQueryParameter("signature")
        val message = uri.getQueryParameter("message")

        val intent = Intent(this@LoginActivity, MainActivity::class.java)
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_TASK_ON_HOME)
        if (errorCode == null && !address.isNullOrEmpty() && !signature.isNullOrEmpty() && !message.isNullOrEmpty()) {
            intent.putExtra(SnWalletConnectExtras.ADDRESS, address)
            intent.putExtra(SnWalletConnectExtras.SIGNATURE, signature)
            intent.putExtra(SnWalletConnectExtras.MESSAGE, message)
        } else {
            Log.i(TAG, "wallet connect: error: code=$errorCode message=$errorMessage")
            intent.putExtra(SnWalletConnectExtras.ERROR, errorMessage ?: getString(R.string.login_error))
        }
        startActivity(intent)
        finish()
    }

    // handles the return from Sign in with Apple (LoginUtils.kt: Apple's web
    // flow in a Custom Tab, posted to the api's /auth/apple/callback, which
    // redirects here): ur://oauth/apple?state=<state>&id_token=<identity token>[&code=…&user=…]
    // or ur://oauth/apple?state=<state>&error=<message>
    private fun appleOAuthLogin(uri: Uri) {
        val app = app ?: return

        val authJwt = uri.getQueryParameter("id_token")
        val state = uri.getQueryParameter("state")
        val error = uri.getQueryParameter("error")
        val provider = AUTH_JWT_TYPE_APPLE

        // the attempt this round trip belongs to: a fresh state per launch, consumed
        // here, so a stale or forged return cannot sign anyone in
        val pending = AppleOAuthSession.take(this, state)
        if (pending == null) {
            Log.i(TAG, "appleOAuthLogin: no pending attempt for this state")
            loginViewModel.setLoginError(getString(R.string.login_error))
            return
        }
        if (error != null || authJwt.isNullOrEmpty()) {
            Log.i(TAG, "appleOAuthLogin: error: $error")
            loginViewModel.setLoginError(error ?: getString(R.string.login_error))
            return
        }
        // the token must carry the nonce this launch asked Apple for
        val claims = ssoJwtPayload(authJwt)
        if (claims == null || claims.optString("nonce") != pending.nonce) {
            Log.i(TAG, "appleOAuthLogin: nonce mismatch")
            loginViewModel.setLoginError(getString(R.string.login_error))
            return
        }
        val email = claims.optString("email").ifEmpty { "" }
        // the name Apple sends with the first authorization only
        val appleUserName = appleOAuthUserName(uri.getQueryParameter("user"))

        isLoadingAuthCode = true

        val args = AuthLoginArgs()
        args.authJwt = authJwt
        args.authJwtType = provider

        app.api?.authLogin(args) { result, err ->
            lifecycleScope.launch {
                if (err != null) {
                    isLoadingAuthCode = false
                    loginViewModel.setLoginError(err.message)
                } else if (result.error != null) {
                    isLoadingAuthCode = false
                    loginViewModel.setLoginError(result.error.message)
                } else if (result.network != null && result.network.byJwt.isNotEmpty()) {
                    loginViewModel.setLoginError(null)

                    app.login(result.network.byJwt)

                    welcomeThenAuthClientAndFinish(
                        callback = { finishError ->
                            if (finishError != null) {
                                Log.i(TAG, "authClientAndFinish error: $finishError")
                            }
                            isLoadingAuthCode = false
                        },
                    )
                } else if (result.authAllowed != null) {
                    val authAllowed = mutableListOf<String>()
                    for (i in 0 until result.authAllowed.len()) {
                        authAllowed.add(result.authAllowed.get(i))
                    }
                    isLoadingAuthCode = false
                    loginViewModel.setLoginError(getString(R.string.login_error_auth_allowed, authAllowed.joinToString(",")))
                } else {
                    // a new user: continue into create network with the identity token
                    loginViewModel.setLoginError(null)
                    isLoadingAuthCode = false
                    jwtCreateNetworkParams = LoginCreateNetworkParams.LoginCreateAuthJwtParams(
                        authJwt = authJwt,
                        authJwtType = provider,
                        userName = (result.userName ?: "").ifEmpty { appleUserName },
                        userAuth = email,
                        referralCode = referralCode
                    )
                }
            }
        } ?: run {
            isLoadingAuthCode = false
        }
    }

    // handles the redirect back from the ur.io wallet-connect bittensor sign message flow
    // ur://bittensor-sign-message?address=<ss58>&signature=<0xhex>&message=...&purpose=...
    // or ur://bittensor-sign-message?errorCode=-1&errorMessage=...
    private fun bittensorSignMessageLogin(uri: Uri) {
        val app = app ?: return

        val errorCode = uri.getQueryParameter("errorCode")
        val errorMessage = uri.getQueryParameter("errorMessage")
        val address = uri.getQueryParameter("address")
        val signature = uri.getQueryParameter("signature")
        val message = uri.getQueryParameter("message")
        val purpose = uri.getQueryParameter("purpose")

        if (errorCode != null || address.isNullOrEmpty() || signature.isNullOrEmpty() ||
            message.isNullOrEmpty() ||
            (purpose != BITTENSOR_SIGN_PURPOSE_LOGIN && purpose != BITTENSOR_SIGN_PURPOSE_CREATE)
        ) {
            Log.i(TAG, "bittensorSignMessageLogin: error: code=$errorCode message=$errorMessage")
            loginViewModel.setLoginError(errorMessage ?: getString(R.string.login_error))
            return
        }

        isLoadingAuthCode = true

        val args = AuthLoginArgs()
        val walletAuth = WalletAuthArgs()

        walletAuth.blockchain = "TAO"
        walletAuth.publicKey = address
        walletAuth.message = message
        walletAuth.signature = signature

        if (purpose == BITTENSOR_SIGN_PURPOSE_CREATE) {
            loginViewModel.setLoginError(null)
            walletCreateNetworkParams = LoginCreateNetworkParams.LoginCreateWalletParams(
                blockchain = "TAO",
                publicKey = address,
                signedMessage = message,
                signature = signature,
                referralCode = referralCode
            )
            isLoadingAuthCode = false
            return
        }

        args.walletAuth = walletAuth

        app.api?.authLogin(args) { result, err ->
            lifecycleScope.launch {

                if (err != null) {
                    isLoadingAuthCode = false
                    loginViewModel.setLoginError(err.message)
                } else if (result.error != null) {
                    isLoadingAuthCode = false
                    loginViewModel.setLoginError(result.error.message)
                } else if (result.network != null && result.network.byJwt.isNotEmpty()) {
                    loginViewModel.setLoginError(null)

                    app.login(result.network.byJwt)

                    welcomeThenAuthClientAndFinish(
                        callback = { error ->
                            if (error != null) {
                                Log.i(TAG, "authClientAndFinish error: $error")
                            }
                            isLoadingAuthCode = false
                        },
                    )
                } else if (result.walletAuth != null) {
                    loginViewModel.setLoginError(null)
                    val api = app.api
                    if (api == null) {
                        isLoadingAuthCode = false
                        loginViewModel.setLoginError(getString(R.string.login_error))
                        return@launch
                    }

                    requestBittensorChallenge(api, address)
                        .onSuccess { createMessage ->
                            isLoadingAuthCode = false
                            if (!launchBittensorSignMessage(
                                    this@LoginActivity,
                                    createMessage,
                                    BITTENSOR_SIGN_PURPOSE_CREATE
                                )
                            ) {
                                loginViewModel.setLoginError(getString(R.string.login_error))
                            }
                        }
                        .onFailure { challengeError ->
                            isLoadingAuthCode = false
                            Log.i(TAG, "unable to fetch create-network wallet challenge: $challengeError")
                            loginViewModel.setLoginError(getString(R.string.login_error))
                        }
                } else {
                    isLoadingAuthCode = false
                    loginViewModel.setLoginError(getString(R.string.login_error))
                }
            }
        } ?: run {
            isLoadingAuthCode = false
        }
    }

    private fun extractDefaultLocation(uri: Uri): String? {
        val reservedQueryNames = setOf("auth_code", "guest", "target", "subscription", "bonus")
        return uri.queryParameterNames
            .firstOrNull { it.lowercase() !in reservedQueryNames }
            ?.removeSuffix("=")
            ?.replace('+', ' ')
            ?.takeIf { it.isNotBlank() }
    }

    private fun hasTruthyQueryParameter(uri: Uri, name: String): Boolean {
        val queryName = uri.queryParameterNames.firstOrNull { it.equals(name, ignoreCase = true) }
            ?: return false
        val value = uri.getQueryParameter(queryName)
        return value == null || value.isBlank() || value.equals("true", ignoreCase = true) || value == "1"
    }

    private fun upgradeSubscriptionSuccessStartMain() {
        val intent = Intent(this@LoginActivity, MainActivity::class.java)
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_TASK_ON_HOME)

        intent.putExtra("UPGRADE_SUBSCRIPTION_SUCCESS", true)

        startActivity(intent)

        finish()
    }

    private fun setLinksAndStartMain(
        targetUrl: String?,
        defaultLocation: String?
    ) {
        val intent = Intent(this@LoginActivity, MainActivity::class.java)
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_TASK_ON_HOME)

        if (targetUrl != null) {
            intent.putExtra("TARGET_URL", targetUrl)
        }

        if (defaultLocation != null) {
            intent.putExtra("DEFAULT_LOCATION", defaultLocation)
        }

        startActivity(intent)

        finish()
    }

    /**
     * Plays the welcome animation the login screens play, then finishes into
     * the main activity. For the sign-ins that complete here (an auth code
     * link, the Apple and bittensor returns) instead of on a screen that has
     * its own overlay; a failed finish takes the overlay down for the retry.
     */
    private fun welcomeThenAuthClientAndFinish(
        callback: (String?) -> Unit,
    ) {
        welcomeOverlayVisible = true
        lifecycleScope.launch(Dispatchers.Main) {
            delay(2250)
            authClientAndFinish { error ->
                if (error != null) {
                    welcomeOverlayVisible = false
                }
                callback(error)
            }
        }
    }

    fun authClientAndFinish(
        callback: (String?) -> Unit,
    ) {
        val app = app ?: return

        val authArgs = AuthNetworkClientArgs()
        authArgs.deviceDescription = app.deviceDescription
        authArgs.deviceSpec = app.deviceSpec

        app.api?.authNetworkClient(authArgs) { result, err ->
            lifecycleScope.launch(Dispatchers.Main) {
                if (err != null) {
                    callback(err.message)
                } else if (result.error != null) {
                    callback(result.error.message)
                } else if (result.byClientJwt.isNotEmpty()) {

                    if (!app.loginClient(result.byClientJwt)) {
                        callback(getString(R.string.login_client_error))
                        return@launch
                    }

                    val intent = Intent(this@LoginActivity, MainActivity::class.java)
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_TASK_ON_HOME)
                    intent.putExtra("ANIMATE_IN", true)

                    if (targetUrl != null) {
                        intent.putExtra("TARGET_URL", targetUrl)
                        // clear targetUrl
                        targetUrl = null
                    }

                    if (defaultLocation != null) {
                        intent.putExtra("DEFAULT_LOCATION", defaultLocation)
                        // clear default location
                        defaultLocation = null
                    }

                    startActivity(intent)

                    if (Build.VERSION.SDK_INT >= 34) {
                        overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
                    } else {
                        overridePendingTransition(0, 0)
                    }

                    finish()
                } else {
                    callback(getString(R.string.login_client_error))
                }
            }
        }
    }



    override fun onDestroy() {
        super.onDestroy()

        referrerClient?.endConnection()
    }

}
