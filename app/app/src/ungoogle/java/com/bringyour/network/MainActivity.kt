package com.bringyour.network

import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.bringyour.network.ui.MainNavHost
import com.bringyour.network.ui.components.overlays.OverlayMode
import com.bringyour.network.ui.settings.SettingsViewModel
import com.bringyour.network.ui.shared.models.BundleStore
import com.bringyour.network.ui.shared.viewmodels.OverlayViewModel
import com.bringyour.network.ui.shared.viewmodels.PlanViewModel
import com.bringyour.network.ui.shared.viewmodels.SubscriptionBalanceViewModel
import com.bringyour.network.ui.theme.URNetworkTheme
import com.bringyour.network.ui.wallet.WalletViewModel
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.solana.mobilewalletadapter.clientlib.MobileWalletAdapter
import com.solana.mobilewalletadapter.clientlib.TransactionResult
import com.solana.publickey.SolanaPublicKey
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity: AppCompatActivity() {

    var requestPermissionLauncherAndStart : ActivityResultLauncher<String>? = null
    var requestPermissionLauncher : ActivityResultLauncher<String>? = null

    var vpnLauncher : ActivityResultLauncher<Intent>? = null

    val activityResultSender = ActivityResultSender(this)

    var subscriptionUpgradeSuccess: Boolean = false

    private val walletViewModel: WalletViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()
    private val planViewModel: PlanViewModel by viewModels()
    private val subscriptionBalanceViewModel: SubscriptionBalanceViewModel by viewModels()
    private val overlayViewModel: OverlayViewModel by viewModels()

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
        val app = application as MainApplication
        if (app.deviceManager.allowForeground) {
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
        } else {
            prepareVpnService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        // immutable shadow
        val app = application as MainApplication

        // allow foreground to be started when the activity is active
        // FIXME does foreground actually help with provider availability?
        // app.allowForeground = false

        val bundleStore = app.device?.networkSpace?.store?.let { BundleStore.fromString(value = it) }

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

        val animateIn = intent.getBooleanExtra("ANIMATE_IN", false)
        val targetUrl = intent.getStringExtra("TARGET_URL")
        val defaultLocation = intent.getStringExtra("DEFAULT_LOCATION")
        subscriptionUpgradeSuccess = intent.getBooleanExtra("UPGRADE_SUBSCRIPTION_SUCCESS", false)


        // disable animation in if mobile or tablet
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
        }

        setContent {
            URNetworkTheme {
                MainNavHost(
                    walletViewModel = walletViewModel,
                    settingsViewModel = settingsViewModel,
                    planViewModel = planViewModel,
                    subscriptionBalanceViewModel = subscriptionBalanceViewModel,
                    overlayViewModel = overlayViewModel,
                    animateIn = animateIn,
                    targetLink = targetUrl,
                    defaultLocation = defaultLocation,
                    activityResultSender = activityResultSender,
                    bundleStore = bundleStore
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

        if (subscriptionUpgradeSuccess) {
            overlayViewModel.launch(OverlayMode.Upgrade)
            subscriptionBalanceViewModel.pollSubscriptionBalance()
        }
    }

    override fun onStop() {
        super.onStop()

        val app = application as MainApplication

        app.vpnRequestStartListener = null
    }

    override fun onDestroy() {
        super.onDestroy()

        // immutable shadow
        // val app = application as MainApplication

        // app.allowForeground = false
    }

    private suspend fun getWalletAddress(walletAdapter: MobileWalletAdapter, activitySender: ActivityResultSender): String? {
        return when (val result = walletAdapter.connect(activitySender)) {
            is TransactionResult.Success -> {
                val pubKey = SolanaPublicKey(result.authResult.publicKey)
                pubKey.base58()
            }
            is TransactionResult.NoWalletFound -> {
                Log.i("SolanaViewModel", "No MWA compatible wallet app found on device.")
                null
            }
            is TransactionResult.Failure -> {
                Log.i("SolanaViewModel", "Error connecting to wallet: ${result.e}")
                null
            }
        }
    }
}
