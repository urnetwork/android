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
import androidx.lifecycle.lifecycleScope
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import com.bringyour.sdk.AuthNetworkClientArgs
import com.bringyour.sdk.NetworkCreateArgs
import com.bringyour.network.ui.LoginNavHost
import com.bringyour.network.ui.login.LoginViewModel
import com.bringyour.network.ui.theme.URNetworkTheme
import com.bringyour.sdk.AuthCodeLoginArgs
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlin.text.contains
import kotlin.text.substringBefore

@AndroidEntryPoint
class LoginActivity : AppCompatActivity() {

    private var app : MainApplication? = null


    private var referrerClient: InstallReferrerClient? = null
    private var referralCode: String? = null

    private val loginViewModel: LoginViewModel by viewModels()

    val activityResultSender = ActivityResultSender(this)

    private var promptAccountSwitch = false
    private var currentNetworkName: String? = null
    private var targetJwt: String? = null
    private var targetUrl: String? = null
    private var defaultLocation: String? = null
    private var switchToGuestMode = false
    private var isLoadingAuthCode = false

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        app = application as MainApplication

        // immutable shadow
        val app = app ?: return

        val action: String? = intent?.action

        if (Intent.ACTION_VIEW == action) {
            Log.i(TAG, "Intent.ACTION_VIEW == action")
            intent?.data?.let { u ->
                if (u.scheme == "https" && u.host == "ur.io" && u.path == "/c" || u.scheme == "ur") {
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
                    switchToGuestMode = switchToGuestMode,
                    isLoadingAuthCode = isLoadingAuthCode,
                    referralCode = referralCode,
                    activityResultSender = activityResultSender
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
        val guest = queryParameters.remove("guest").toBoolean()
        referralCode = queryParameters.remove("bonus")
        targetUrl = queryParameters.remove("target")

        val urlString = uri.toString()

        defaultLocation = if (urlString.contains('?')) {
            val params = urlString.substringAfter('?')
            if (params.contains('&')) {
                URLDecoder.decode(params.substringBefore('&'), StandardCharsets.UTF_8.name())
            } else {
                // Handle single parameter cases
                if (params.contains('=')) {
                    val key = params.substringBefore('=')
                    if (key.lowercase() != "guest" && key.lowercase() != "auth_code" && key.lowercase() != "target") {
                        URLDecoder.decode(key, StandardCharsets.UTF_8.name()) // Default location
                    } else {
                        null // Only guest parameter
                    }
                } else {
                    URLDecoder.decode(params, StandardCharsets.UTF_8.name()) // Single parameter, no '='
                }
            }
        } else {
            null // Handle no '?'
        }

        if (defaultLocation != null) {
            defaultLocation = defaultLocation?.removeSuffix("=")
        }

        val localState = app.asyncLocalState

        if (authCode != null) {

            isLoadingAuthCode = true

            val args = AuthCodeLoginArgs()
            args.authCode = authCode

            app.api?.authCodeLogin(args) { result, err ->

                if (err == null && result.jwt != null) {

                    runBlocking(Dispatchers.Main.immediate) {

                        if (app.asyncLocalState?.localState?.byJwt == result.jwt) {
                            // user already logged into this network

                            setLinksAndStartMain(
                                targetUrl = targetUrl,
                                defaultLocation = defaultLocation
                            )

                        } else if (!app.asyncLocalState?.localState?.byJwt.isNullOrEmpty() && app.asyncLocalState?.localState?.byJwt != result.jwt) {
                            // user is logged in, but not to the account related to the auth code
                            // prompt account switch

                            targetJwt = result.jwt
                            promptAccountSwitch = true

                            localState?.parseByJwt { jwt, _ ->
                                currentNetworkName = jwt.networkName
                            }

                        } else {

                            app.login(result.jwt)

                            authClientAndFinish(
                                callback = {err -> },
                            )
                        }

                        isLoadingAuthCode = false
                    }

                } else {
                    Log.i(TAG, "authCodeLogin: error: result is: $result")
                }

            }

        } else if (guest) {
            // login as guest

            if (localState != null) {
                localState.parseByJwt { jwt, _ ->

                    if (jwt.guestMode) {
                        setLinksAndStartMain(targetUrl, defaultLocation)
                    } else {
                        currentNetworkName = jwt.networkName
                        promptAccountSwitch = true
                        switchToGuestMode = true
                    }
                }
            } else {

                val args = NetworkCreateArgs()
                args.terms = true
                args.guestMode = true

                app.api?.networkCreate(args) { result, err ->
                    runBlocking(Dispatchers.Main.immediate) {

                        if (err != null) {
                            Log.i(TAG, "error ${err.message}")
                        } else if (result.error != null) {
                            Log.i(TAG, "error ${result.error.message}")
                        } else if (result.network != null && result.network.byJwt.isNotEmpty()) {

                            app.login(result.network.byJwt)

                            authClientAndFinish(
                                { error ->

                                    if (error != null) {
                                        Log.i(TAG, "authClientAndFinish error: $error")
                                    }
                                }
                            )

                        } else {
                            Log.i(TAG, "authClientAndFinish error: ${R.string.create_network_error}")
                        }
                    }
                }

            }

        } else if (app.device != null) {
            setLinksAndStartMain(targetUrl, defaultLocation)
        }
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

    fun authClientAndFinish(
        callback: (String?) -> Unit,
    ) {
        val app = app ?: return

        val authArgs = AuthNetworkClientArgs()
        authArgs.description = app.deviceDescription
        authArgs.deviceSpec = app.deviceSpec

        app.api?.authNetworkClient(authArgs) { result, err ->
            runBlocking(Dispatchers.Main.immediate) {
                if (err != null) {
                    callback(err.message)
                } else if (result.error != null) {
                    callback(result.error.message)
                } else if (result.byClientJwt.isNotEmpty()) {
                    callback(null)

                    app.loginClient(result.byClientJwt)

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