package com.bringyour.network

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.RemoteException
import androidx.activity.compose.setContent
import android.util.Log
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import com.bringyour.client.AuthNetworkClientArgs
import com.bringyour.network.ui.LoginNavHost
import com.bringyour.network.ui.theme.URNetworkTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

@AndroidEntryPoint
class LoginActivity : AppCompatActivity() {

    private var app : MainApplication? = null


    private var referrerClient: InstallReferrerClient? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        app = application as MainApplication

        // immutable shadow
        val app = app ?: return

        val action: String? = intent?.action

        if (Intent.ACTION_VIEW == action) {
            intent?.data?.let { u ->
                if (u.scheme == "https" && u.host == "ur.io" && u.path == "/c" || u.scheme == "ur") {
                    createWithUri(u)
                }
            }

        }
        // FIXME google play referrer
        else if (app.byDevice != null) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        } else {
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
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // fixme use a custom view to show up/down statistics and hot linpath spark

        setContent {
            URNetworkTheme {
                LoginNavHost()
            }
        }

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
        val guest = queryParameters.remove("guest")
        if (authCode != null) {
            // FIXME
            // start api to resolve auth code, show message in login screen until done
            // FIXME include queryParameters in launch main

            // FIXME if same network, just finish to main activity (do not log out)
            // FIXME otherwise confirm with the user if they want to switch account (do you want to disconnect and switch accounts)
        } else if (guest != null) {
            // FIXME
            // login as guest
            // FIXME include queryParameters in launch main

            // FIXME if already guest, just finish to main activity (do not log out)
            // FIXME otherwise confirm with the user if they want to switch account (do you want to disconnect and switch accounts)
        } else if (app.byDevice != null) {
            // FIXME, open main activity with query
        } else {
            // FIXME else, store query for when logging in

        }
    }

    fun authClientAndFinish(callback: (String?) -> Unit) {
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
                } else if (0 < result.byClientJwt.length) {
                    callback(null)

                    app.loginClient(result.byClientJwt)

                    val intent = Intent(this@LoginActivity, MainActivity::class.java)
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_TASK_ON_HOME)
                    intent.putExtra("ANIMATE_IN", true)
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