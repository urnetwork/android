package com.bringyour.network

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.VpnService
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.ColorInt
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.bringyour.network.databinding.ActivityMainBinding
import com.bringyour.client.support.GLSurfaceViewBinder
import com.bringyour.client.StatusViewController
import com.google.android.material.bottomnavigation.BottomNavigationView


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private var statusVc : StatusViewController? = null


    var requestPermissionLauncher : ActivityResultLauncher<String>? = null
    var vpnLauncher : ActivityResultLauncher<Intent>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)


        setSupportActionBar(findViewById(R.id.action_bar))

        val navView: BottomNavigationView = binding.navView
        // the tint occludes the drawable icons
        navView.itemIconTintList = null


        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_connect,
                R.id.navigation_provide,
                R.id.navigation_devices,
                R.id.navigation_account
            )
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.setLogo(R.drawable.logo_by_white_2)
        supportActionBar?.setDisplayUseLogoEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)


        // fixme use a custom view to show up/down statistics and hot linpath spark
        // setCustomView


//        com.bringyour.network.goclient.client.

        val app = application as MainApplication

        statusVc = app.byDevice?.openStatusViewController()


        // match the action bar background
        val colorPrimaryTypedValue = TypedValue()
        theme.resolveAttribute(R.attr.colorPrimary, colorPrimaryTypedValue, true)
        @ColorInt val colorPrimary = colorPrimaryTypedValue.data
        statusVc?.setBackgroundColor(
            Color.red(colorPrimary) / 255f,
            Color.green(colorPrimary) / 255f,
            Color.blue(colorPrimary) / 255f
        )

        supportActionBar?.setCustomView(R.layout.view_status)
        supportActionBar?.setDisplayShowCustomEnabled(true)
//
        val view = supportActionBar?.customView?.findViewById(R.id.status_surface) as GLSurfaceView
        // overlapping gl surfaces will draw on each other. The status should always be on top
        view.setZOrderOnTop(true)
        GLSurfaceViewBinder.bind("status_surface", view, statusVc!!)


        requestPermissionLauncher =
            registerForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted ->
                prepareVpnService()
            }

        vpnLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            Log.i("Main","ACTIVITY RESULT")
            if (result.resultCode == RESULT_OK) {

                startVpnService()



            }
        }
    }

    fun requestPermissionsThenStartVpnService() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED -> {
                // You can use the API that requires the permission.
                prepareVpnService()
            }
            /*
            shouldShowRequestPermissionRationale(...) -> {
            // In an educational UI, explain to the user why your app requires this
            // permission for a specific feature to behave as expected, and what
            // features are disabled if it's declined. In this UI, include a
            // "cancel" or "no thanks" button that lets the user continue
            // using your app without granting the permission.
            showInContextUI(...)
        }
        */
            else -> {

                // You can directly ask for the permission.
                // The registered ActivityResultCallback gets the result of this request.
                requestPermissionLauncher?.launch(
                    android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // call this on tap connect or tap provide
    private fun prepareVpnService() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            vpnLauncher?.launch(intent)
        } else {
//            onActivityResult(ActivityResult(RESULT_OK, null))
            startVpnService()
        }
    }

    private fun startVpnService() {
        val vpnIntent = Intent(this, MainService::class.java)
        if (Build.VERSION_CODES.O <= Build.VERSION.SDK_INT) {
            startForegroundService(vpnIntent)
        } else {
            startService(vpnIntent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        val app = application as MainApplication
        app.byDevice?.closeViewController(statusVc)
        statusVc = null
//        statusVc?.close()
    }

//    fun onVpnLauncherActivityResult(result: ActivityResult) {
//        Log.i("Main","ACTIVITY RESULT")
//        if (result.resultCode == RESULT_OK) {
//
//
//
//
//        }
//    }

}


