package com.bringyour.network

import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bringyour.network.ui.MainNavHost
import com.bringyour.network.ui.components.overlays.OverlayMode
import com.bringyour.network.ui.settings.SettingsViewModel
import com.bringyour.network.ui.shared.models.BundleStore
import com.bringyour.network.ui.shared.viewmodels.OverlayViewModel
import com.bringyour.network.ui.shared.viewmodels.PlanViewModel
import com.bringyour.network.ui.shared.viewmodels.SubscriptionBalanceViewModel
import com.bringyour.network.ui.theme.URNetworkTheme
import com.bringyour.network.ui.wallet.EarningsViewModel
import com.bringyour.network.ui.wallet.SnWalletConnectExtras
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity: AppCompatActivity() {

    @Inject lateinit var jwtManager: JwtManager

    var requestPermissionLauncher : ActivityResultLauncher<String>? = null

    var vpnLauncher : ActivityResultLauncher<Intent>? = null

    val activityResultSender = ActivityResultSender(this)

    var subscriptionUpgradeSuccess: Boolean = false

    private val earningsViewModel: EarningsViewModel by viewModels()
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
        // Notification permission controls drawer visibility only. It is not a
        // prerequisite for starting the mandatory VPN foreground service.
        prepareVpnService()
    }

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        // immutable shadow
        val app = application as MainApplication

        val bundleStore = app.device?.networkSpace?.store?.let { BundleStore.fromString(value = it) }

        // used in settings
        requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            settingsViewModel.onPermissionResult(isGranted, this)
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

        // the ur.io bridge signed a wallet-connect challenge for the earnings screen
        // (forwarded by the login activity, which receives the ur:// redirect)
        intent.getStringExtra(SnWalletConnectExtras.ADDRESS)?.let { address ->
            val signature = intent.getStringExtra(SnWalletConnectExtras.SIGNATURE) ?: ""
            val message = intent.getStringExtra(SnWalletConnectExtras.MESSAGE) ?: ""
            intent.removeExtra(SnWalletConnectExtras.ADDRESS)
            earningsViewModel.onWalletSigned(address, signature, message)
        }
        intent.getStringExtra(SnWalletConnectExtras.ERROR)?.let { error ->
            intent.removeExtra(SnWalletConnectExtras.ERROR)
            earningsViewModel.onWalletSignFailed(error)
        }
        subscriptionUpgradeSuccess = intent.getBooleanExtra("UPGRADE_SUBSCRIPTION_SUCCESS", false)


        // disable animation in if mobile or tablet
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
        }

        setContent {

            val jwt by jwtManager.jwtFlow.collectAsState(initial = null)
            val isPro = jwt?.pro == true

            URNetworkTheme {
                MainNavHost(
                    earningsViewModel = earningsViewModel,
                    settingsViewModel = settingsViewModel,
                    planViewModel = planViewModel,
                    subscriptionBalanceViewModel = subscriptionBalanceViewModel,
                    overlayViewModel = overlayViewModel,
                    animateIn = animateIn,
                    targetLink = targetUrl,
                    defaultLocation = defaultLocation,
                    activityResultSender = activityResultSender,
                    bundleStore = bundleStore,
                    isPro = isPro
                )
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    settingsViewModel.requestPermission.collect { shouldRequest ->
                        requestNotificationPermissionIfNeeded(shouldRequest)
                    }
                }
            }
        }

        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onPause(owner: LifecycleOwner) {
                Log.i("Lifecycle", "Activity onPause")
                subscriptionBalanceViewModel.stopBackgroundPolling()
            }
            override fun onResume(owner: LifecycleOwner) {
                Log.i("Lifecycle", "Activity onResume")
                subscriptionBalanceViewModel.setErrorReachingSubscriptionBalance(false)
                subscriptionBalanceViewModel.createBackgroundPollingJob()
            }
        })
    }

    override fun onStart() {
        super.onStart()

        val app = application as MainApplication

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

        if (subscriptionUpgradeSuccess) {
            subscriptionUpgradeSuccess = false
            intent.removeExtra("UPGRADE_SUBSCRIPTION_SUCCESS")
            overlayViewModel.launch(OverlayMode.Upgrade)
            subscriptionBalanceViewModel.pollSubscriptionBalance()
        }
    }

    override fun onStop() {
        super.onStop()

        val app = application as MainApplication

        app.vpnRequestStartListener = null
    }

    private fun requestNotificationPermissionIfNeeded(shouldRequest: Boolean) {
        if (!shouldRequest) {
            return
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            settingsViewModel.onPermissionResult(true)
            settingsViewModel.resetPermissionRequest()
            return
        }

        if (ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            settingsViewModel.onPermissionResult(true)
            settingsViewModel.resetPermissionRequest()
        } else {
            requestPermissionLauncher?.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
