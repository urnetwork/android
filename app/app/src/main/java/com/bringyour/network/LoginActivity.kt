package com.bringyour.network

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import android.util.Log
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.bringyour.sdk.AuthNetworkClientArgs
import com.bringyour.network.ui.LoginNavHost
import com.bringyour.network.ui.theme.URNetworkTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

@AndroidEntryPoint
class LoginActivity : AppCompatActivity() {

    private var app : MainApplication? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        app = application as MainApplication

        // immutable shadow
        val app = app ?: return

        if (app.byDevice != null) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
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

}