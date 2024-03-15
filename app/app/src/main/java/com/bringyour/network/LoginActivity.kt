package com.bringyour.network

import android.content.ComponentCallbacks
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import com.bringyour.client.AuthNetworkClientArgs
import com.bringyour.network.databinding.ActivityLoginBinding
import com.google.android.gms.common.SignInButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Callable


class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    private var app : MainApplication? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        app = application as MainApplication

        // immutable shadow
        val app = app ?: return


        Log.i("BY", "LOGIN ACTIVITY")

        if (app.byDevice != null) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }
        // else set up the activity as normal


        // FIXME ui is one box to enter client jwt, and submit

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)



        setSupportActionBar(findViewById(R.id.action_bar))

        val navController = findNavController(R.id.nav_host_fragment_activity_login)



        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_initial
            )
        )
        setupActionBarWithNavController(navController, appBarConfiguration)


        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setLogo(R.drawable.logo_by_black_2)
        supportActionBar?.setDisplayUseLogoEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)

//        navController.addOnDestinationChangedListener { _, destination, _ ->
//            Log.i("LOGIN", "DESTINATION CHANGED ${destination.id}")
//            when (destination.id) {
//                R.id.navigation_initial -> {
//                    supportActionBar?.hide()
//                }
//                else -> {
//                    supportActionBar?.show()
//                }
//            }
//        }


//        supportActionBar?.setBackgroundDrawable(ColorDrawable(getColor(R.color.p_s_gray)))

        // fixme use a custom view to show up/down statistics and hot linpath spark
        // setCustomView


//        com.bringyour.network.goclient.client.






    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                val navController = findNavController(R.id.nav_host_fragment_activity_login)
                navController.navigateUp()
            }
        }
        return super.onOptionsItemSelected(item)
    }


    fun authClientAndFinish(callback: (String?) -> Unit) {
        val app = app ?: return

        val authArgs = AuthNetworkClientArgs()
        authArgs.description = app.getDeviceDescription()
        authArgs.deviceSpec = app.getDeviceSpec()

        app.byApi?.authNetworkClient(authArgs) { result, err ->
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
                    startActivity(intent)
                    finish()
                } else {
                    callback(getString(R.string.login_client_error))
                }
            }
        }
    }



}