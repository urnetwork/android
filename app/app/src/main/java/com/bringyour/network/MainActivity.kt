package com.bringyour.network

import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.bringyour.network.ui.MainNavHost
import com.bringyour.network.ui.theme.URNetworkTheme


class MainActivity: ComponentActivity() {
    // private var statusVc : StatusViewController? = null
    private var app : MainApplication? = null

    var requestPermissionLauncher : ActivityResultLauncher<String>? = null
    var vpnLauncher : ActivityResultLauncher<Intent>? = null

    private fun prepareVpnService() {
        val app = app ?: return
        val intent = VpnService.prepare(this)
        if (intent != null) {
            vpnLauncher?.launch(intent)
        } else {
//            onActivityResult(ActivityResult(RESULT_OK, null))
            app.startVpnService()
        }
    }

    fun requestPermissionsThenStartVpnService() {
        requestPermissionsThenStartVpnServiceWithRestart()
    }

    fun requestPermissionsThenStartVpnServiceWithRestart() {
        if (Build.VERSION_CODES.O <= Build.VERSION.SDK_INT) {
            if (Build.VERSION_CODES.TIRAMISU <= Build.VERSION.SDK_INT) {
                val hasForegroundPermissions = ContextCompat.checkSelfPermission(
                        this,
                        android.Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                if (hasForegroundPermissions) {
                    prepareVpnService()
                } else {
                    requestPermissionLauncher?.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            } else {
                prepareVpnService()
            }
        } else {
            prepareVpnService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        app = application as MainApplication

        // immutable shadow
        val app = app ?: return

        requestPermissionLauncher =
            registerForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { _ ->
                // the vpn service can start with degraded options if not granted
                prepareVpnService()
            }

        vpnLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                app.startVpnService()
            }
        }

        // this is so overlays don't get cut by top bar and bottom drawer
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            URNetworkTheme {
                MainNavHost()
            }
        }
    }
}

//class MainActivity : AppCompatActivity() {
//
//    private lateinit var binding: ActivityMainBinding
//
//
//    private var app : MainApplication? = null
//
//    private var statusVc : StatusViewController? = null
//
//
//    var requestPermissionLauncher : ActivityResultLauncher<String>? = null
//    var vpnLauncher : ActivityResultLauncher<Intent>? = null
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//
//        app = application as MainApplication
//
//        // immutable shadow
//        val app = app ?: return
//
//        binding = ActivityMainBinding.inflate(layoutInflater)
//        setContentView(binding.root)
//
//
//        // setSupportActionBar(findViewById(R.id.action_bar))
//
//        val navView: BottomNavigationView = binding.navView
//        // the tint occludes the drawable icons
//        navView.itemIconTintList = null
//
//
//        val navController = findNavController(R.id.nav_host_fragment_activity_main)
//        // Passing each menu ID as a set of Ids because each
//        // menu should be considered as top level destinations.
//        val appBarConfiguration = AppBarConfiguration(
//            setOf(
//                R.id.navigation_connect,
//                R.id.navigation_provide,
//                R.id.navigation_devices,
//                R.id.navigation_account
//            )
//        )
//        // setupActionBarWithNavController(navController, appBarConfiguration)
//        navView.setupWithNavController(navController)
//
////        supportActionBar?.setDisplayShowHomeEnabled(true)
////        // supportActionBar?.setLogo(R.drawable.logo_by_white_2)
////        supportActionBar?.setDisplayUseLogoEnabled(true)
////        supportActionBar?.setDisplayShowTitleEnabled(false)
//
//
//        // fixme use a custom view to show up/down statistics and hot linpath spark
//        // setCustomView
//
//
////        com.bringyour.network.goclient.client.
//
//        statusVc = app.byDevice?.openStatusViewController()
//
//
//        // match the action bar background
//        val colorPrimaryTypedValue = TypedValue()
//        theme.resolveAttribute(R.attr.colorPrimary, colorPrimaryTypedValue, true)
//        @ColorInt val colorPrimary = colorPrimaryTypedValue.data
//        statusVc?.setBackgroundColor(
//            Color.red(colorPrimary) / 255f,
//            Color.green(colorPrimary) / 255f,
//            Color.blue(colorPrimary) / 255f
//        )
//
//        /*
//        supportActionBar?.setCustomView(R.layout.view_status)
//        supportActionBar?.setDisplayShowCustomEnabled(true)
////
//        val view = supportActionBar?.customView?.findViewById(R.id.status_surface) as GLSurfaceView
//        // overlapping gl surfaces will draw on each other. The status should always be on top
//        view.setZOrderOnTop(true)
//        GLSurfaceViewBinder.bind("status_surface", view, statusVc!!)
//*/
//
//        requestPermissionLauncher =
//            registerForActivityResult(
//                ActivityResultContracts.RequestPermission()
//            ) { _ ->
//                // the vpn service can start with degraded options if not granted
//                prepareVpnService()
//            }
//
//        vpnLauncher = registerForActivityResult(
//            ActivityResultContracts.StartActivityForResult()
//        ) { result ->
////            Log.i("Main","ACTIVITY RESULT")
//            if (result.resultCode == RESULT_OK) {
//                app.startVpnService()
//            }
//        }
//    }
//
//    fun requestPermissionsThenStartVpnService() {
//        requestPermissionsThenStartVpnServiceWithRestart()
//    }
//
//    fun requestPermissionsThenStartVpnServiceWithRestart() {
//        if (Build.VERSION_CODES.O <= Build.VERSION.SDK_INT) {
//            if (Build.VERSION_CODES.TIRAMISU <= Build.VERSION.SDK_INT) {
//                val hasForegroundPermissions = ContextCompat.checkSelfPermission(
//                        this,
//                        android.Manifest.permission.POST_NOTIFICATIONS
//                    ) == PackageManager.PERMISSION_GRANTED
//                if (hasForegroundPermissions) {
//                    prepareVpnService()
//                } else {
//                    requestPermissionLauncher?.launch(android.Manifest.permission.POST_NOTIFICATIONS)
//                }
//            } else {
//                prepareVpnService()
//            }
//        } else {
//            prepareVpnService()
//        }
//    }
//
//    // call this on tap connect or tap provide
//    private fun prepareVpnService() {
//        val app = app ?: return
//        val intent = VpnService.prepare(this)
//        if (intent != null) {
//            vpnLauncher?.launch(intent)
//        } else {
////            onActivityResult(ActivityResult(RESULT_OK, null))
//            app.startVpnService()
//        }
//    }
//
///*
//    override fun onResume() {
//        super.onResume()
//
//        val app = app ?: return
//
//        if (app.isVpnRequestStart()) {
//            // user might need to grant permissions
//            requestPermissionsThenStartVpnServiceWithRestart(false)
//        }
//    }
//
// */
//
//    override fun onDestroy() {
//        super.onDestroy()
//
//        val app = app ?: return
//
//        app.byDevice?.closeViewController(statusVc)
//        statusVc = null
////        statusVc?.close()
//    }
//
////    fun onVpnLauncherActivityResult(result: ActivityResult) {
////        Log.i("Main","ACTIVITY RESULT")
////        if (result.resultCode == RESULT_OK) {
////
////
////
////
////        }
////    }
//
//}


