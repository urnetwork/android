package com.bringyour.network

import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.bringyour.network.ui.MainNavHost
import com.bringyour.network.ui.settings.SettingsViewModel
import com.bringyour.network.ui.theme.URNetworkTheme
import com.bringyour.network.ui.wallet.SagaViewModel
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity: AppCompatActivity() {

    var requestPermissionLauncherAndStart : ActivityResultLauncher<String>? = null
    var requestPermissionLauncher : ActivityResultLauncher<String>? = null

    var vpnLauncher : ActivityResultLauncher<Intent>? = null

    private val sagaViewModel: SagaViewModel by viewModels()

    private val settingsViewModel: SettingsViewModel by viewModels()

    private fun prepareVpnService() {
        val app = application as MainApplication
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
                    requestPermissionLauncherAndStart?.launch(android.Manifest.permission.POST_NOTIFICATIONS)
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

        // immutable shadow
        val app = application as MainApplication

        val sender = ActivityResultSender(this)
        sagaViewModel.setSender(sender)

        // used when connecting
        requestPermissionLauncherAndStart =
            registerForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { isGranted ->
                // the vpn service can start with degraded options if not granted
                prepareVpnService()
                settingsViewModel.onPermissionResult(isGranted)
                settingsViewModel.resetPermissionRequest()
            }

        // used in settings
        requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            settingsViewModel.onPermissionResult(isGranted)
            settingsViewModel.resetPermissionRequest()
        }

        vpnLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                app.startVpnService()
            }
        }

        // this is so overlays don't get cut by top bar and bottom drawer
        // WindowCompat.setDecorFitsSystemWindows(window, false)
        setTransparentStatusBar()

        // setStatusBarColor(color = Color.Transparent.toArgb(), false)

        setContent {
            URNetworkTheme {
                MainNavHost(
                    sagaViewModel,
                    settingsViewModel
                )
            }
        }

    }

    override fun onStart() {
        super.onStart()

        val app = application as MainApplication
        val activity = this

        // do this once at start
        lifecycleScope.launch {
            if (app.vpnRequestStart) {
                requestPermissionsThenStartVpnServiceWithRestart()
            }
        }

        app.vpnRequestStartListener = {
            lifecycleScope.launch {
                if (app.vpnRequestStart) {
                    requestPermissionsThenStartVpnServiceWithRestart()
                }
            }
        }

        settingsViewModel.checkPermissionStatus(this)

        // Observe the requestPermission state
        lifecycleScope.launch {
            settingsViewModel.requestPermission.collect { shouldRequest ->
                if (shouldRequest) {
                    // Check if the permission is already granted
                    if (ContextCompat.checkSelfPermission(
                            activity,
                            android.Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        settingsViewModel.onPermissionResult(true)
                    } else {
                        // Request the permission
                        if (Build.VERSION_CODES.TIRAMISU <= Build.VERSION.SDK_INT) {
                            requestPermissionLauncher?.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()

        val app = application as MainApplication

        app.vpnRequestStartListener = null
    }

    private fun setTransparentStatusBar() {
        val window = window
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.Transparent.toArgb()

        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.isAppearanceLightStatusBars = false
    }
}
