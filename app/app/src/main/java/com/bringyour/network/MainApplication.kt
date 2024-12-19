package com.bringyour.network

import android.app.Application
import android.app.ForegroundServiceStartNotAllowedException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.core.content.ContextCompat
import circle.programmablewallet.sdk.presentation.SettingsManagement
import com.bringyour.sdk.AccountViewController
import com.bringyour.sdk.Sdk
import com.bringyour.sdk.DevicesViewController
import com.bringyour.sdk.LoginViewController
import com.bringyour.network.ui.account.CircleLayoutProvider
import com.bringyour.network.ui.account.CircleViewSetterProvider
import dagger.hilt.android.HiltAndroidApp
import com.bringyour.sdk.NetworkSpace
import com.bringyour.sdk.Sub
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@HiltAndroidApp
class MainApplication : Application() {

    // FIXME
    // device
    // api
    // asynclocalstorage

    // launcher activity that shows login or mainactivity

//    var byClient : BringYourClient? = null
//    var endpoints : Endpoints? = null

    // fixme should be set on the first screen
//    val platformUrl = "wss://connect.bringyour.com"
//    val apiUrl = "https://api.bringyour.com"


    val deviceDescription = "New device"

    val deviceSpec get() = if (32 <= Build.VERSION.SDK_INT) {
        "${Build.VERSION.RELEASE_OR_CODENAME} ${Build.FINGERPRINT}"
    } else {
        "${Build.VERSION.RELEASE} ${Build.FINGERPRINT}"
    }

    var networkSpaceSub: Sub? = null

//    var byDevice: BringYourDevice? = null
    var deviceProvideSub: Sub? = null
    var deviceProvidePausedSub: Sub? = null
    var deviceOfflineSub: Sub? = null
    var deviceConnectSub: Sub? = null
    var deviceRouteLocalSub: Sub? = null
    var router: Router? = null

    var networkCallback: ConnectivityManager.NetworkCallback? = null
    var offlineCallback: ConnectivityManager.NetworkCallback? = null


    // use one set of view controllers across the entire app
    var loginVc: LoginViewController? = null
    var devicesVc: DevicesViewController? = null
    var accountVc: AccountViewController? = null

    var hasBiometric: Boolean = false

    @Inject
    lateinit var byDeviceManager: ByDeviceManager

    @Inject
    lateinit var circleWalletManager: CircleWalletManager

    @Inject
    lateinit var networkSpaceManagerProvider: NetworkSpaceManagerProvider

    var vpnRequestStart: Boolean = false
        private set

    var vpnRequestStartListener: (() -> Unit)? = null

    // FIXME remove these bools and just query the device directly
//    private var provideEnabled: Boolean = false
//    private var connectEnabled: Boolean = false

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    val byDevice get() = byDeviceManager.byDevice
    val api get() = networkSpaceManagerProvider.getNetworkSpace()?.api
    val asyncLocalState get() = networkSpaceManagerProvider.getNetworkSpace()?.asyncLocalState
//    val apiUrl get() = networkSpace?.apiUrl
//    val platformUrl get() = networkSpace?.platformUrl





    override fun onCreate() {
        super.onCreate()

        networkSpaceManagerProvider.init(filesDir.absolutePath)

        val networkSpaceManager = networkSpaceManagerProvider.getNetworkSpaceManager()

        val key = Sdk.newNetworkSpaceKey(BuildConfig.BRINGYOUR_BUNDLE_HOST_NAME, BuildConfig.BRINGYOUR_BUNDLE_ENV_NAME)
        val bundleNetworkSpaceExists = networkSpaceManager?.getNetworkSpace(key) != null
        val bundleNetworkSpace = networkSpaceManager?.updateNetworkSpace(key) { values ->
            // migrate specific bundled fields to the latest from the build
            values.envSecret = BuildConfig.BRINGYOUR_BUNDLE_ENV_SECRET
            values.bundled = true
            // security settings
            // more security can mean fewer connectivity options and slower connectivity in some regions
            values.netExposeServerIps = BuildConfig.BRINGYOUR_BUNDLE_NET_EXPOSE_SERVER_IPS
            values.netExposeServerHostNames = BuildConfig.BRINGYOUR_BUNDLE_NET_EXPOSE_SERVER_HOST_NAMES
            // server settings
            values.linkHostName = BuildConfig.BRINGYOUR_BUNDLE_LINK_HOST_NAME
            values.migrationHostName = BuildConfig.BRINGYOUR_BUNDLE_MIGRATION_HOST_NAME
            // third party settings
            // TODO sso settings
            values.store = BuildConfig.BRINGYOUR_BUNDLE_STORE
            values.wallet = BuildConfig.BRINGYOUR_BUNDLE_WALLET
            values.ssoGoogle = BuildConfig.BRINGYOUR_BUNDLE_SSO_GOOGLE
        }

        if (!bundleNetworkSpaceExists || networkSpaceManager?.activeNetworkSpace == null) {
            // switch to the bundled network space when first created
            // this is important when migrating from an older bundle to a newer bundle
            networkSpaceManager?.activeNetworkSpace = bundleNetworkSpace
        }

        networkSpaceSub = networkSpaceManager?.addActiveNetworkSpaceChangeListener { networkSpace ->
            runBlocking(Dispatchers.Main.immediate) {
                updateActiveNetworkSpace(networkSpace)

                // launch the initial activity
                val intent = Intent(applicationContext, LoginActivity::class.java)
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK.or(Intent.FLAG_ACTIVITY_TASK_ON_HOME))
                startActivity(intent)
            }
        }

        updateActiveNetworkSpace(networkSpaceManager?.activeNetworkSpace!!)
    }


    private fun updateActiveNetworkSpace(networkSpace: NetworkSpace) {
        stop()

        networkSpaceManagerProvider.setNetworkSpace(networkSpace)

        loginVc = Sdk.newLoginViewController(api)

        if (networkSpace.wallet == "circle" || networkSpace.wallet == "all") {
            initCircleWallet()
        }

        asyncLocalState?.localState?.let { localState ->
            localState.byClientJwt?.let { byClientJwt ->
                if (byClientJwt == "") {
                    // missing one or both of jwt or client jwt
                    // clean up the local state
                    localState.logout()
                } else {
                    // the device wraps the api and sets the jwt
                    initDevice(byClientJwt)
                }
            }
        }


    }


    private fun addOfflineCallback() {
        removeOfflineCallback()

        offlineCallback = object : ConnectivityManager.NetworkCallback() {
            var connectedNetwork: Network? = null

            override fun onAvailable(network: Network) {
                super.onAvailable(network)

                Log.i(TAG, "network available device = $network")
                connectedNetwork = network
                byDevice?.offline = false
            }

            override fun onLost(network: Network) {
                super.onLost(network)

                if (network == connectedNetwork) {
                    Log.i(TAG, "network lost device = $network")
                    connectedNetwork = null
                    byDevice?.offline = true
                }
            }
        }


        val networkRequestBuilder = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)

        val networkRequest = networkRequestBuilder.build()

        val connectivityManager =
            getSystemService(ConnectivityManager::class.java) as ConnectivityManager
        connectivityManager.requestNetwork(networkRequest, offlineCallback!!)

    }

    fun removeOfflineCallback() {
        offlineCallback?.let {
            val connectivityManager =
                getSystemService(ConnectivityManager::class.java) as ConnectivityManager
            connectivityManager.unregisterNetworkCallback(it)
        }
        offlineCallback = null
    }

    private fun addNetworkCallback() {
        removeNetworkCallback()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            var connectedNetwork: Network? = null

            override fun onAvailable(network: Network) {
                super.onAvailable(network)

                Log.i(TAG, "network available device = $network")
                connectedNetwork = network
                byDevice?.providePaused = false
            }

//            override fun onCapabilitiesChanged(
//                network: Network,
//                networkCapabilities: NetworkCapabilities
//            ) {
//                super.onCapabilitiesChanged(network, networkCapabilities)
//
////                val metered = !networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
////                byDevice?.providePaused = metered
//            }

            override fun onLost(network: Network) {
                super.onLost(network)

                if (network == connectedNetwork) {
                    Log.i(TAG, "network lost device = $network")
                    connectedNetwork = null
                    byDevice?.providePaused = true
                }
            }
        }


        // see https://developer.android.com/training/monitoring-device-state/connectivity-status-type
        val networkRequestBuilder = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)

            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
//            .build()


        // note the following capabilities appear to never be satisfied
        // NetworkCapabilities.NET_CAPABILITY_PRIORITIZE_LATENCY
        // NetworkCapabilities.NET_CAPABILITY_PRIORITIZE_BANDWIDTH

        val networkRequest = networkRequestBuilder.build()

        val connectivityManager =
            getSystemService(ConnectivityManager::class.java) as ConnectivityManager
        connectivityManager.requestNetwork(networkRequest, networkCallback!!)

    }

    fun removeNetworkCallback() {
        networkCallback?.let {
            val connectivityManager =
                getSystemService(ConnectivityManager::class.java) as ConnectivityManager
            connectivityManager.unregisterNetworkCallback(it)
        }
        networkCallback = null
    }


    fun login(byJwt: String) {
        asyncLocalState?.localState?.byJwt = byJwt
        api?.setByJwt(byJwt)
    }

    fun loginClient(byClientJwt: String) {
        asyncLocalState?.localState?.let { localState ->
            localState.byClientJwt = byClientJwt
            initDevice(byClientJwt)
        }
    }

    fun logout() {
        stop()

        // note this clears the clientJwt also
        asyncLocalState?.localState?.logout()
        api?.byJwt = null
    }

    fun stop() {
        stopVpnService()

        removeNetworkCallback()
        removeOfflineCallback()

        devicesVc?.let {
            byDevice?.closeViewController(it)
        }
        devicesVc = null

        router?.close()
        router = null
        deviceProvideSub?.close()
        deviceProvideSub = null
        deviceProvidePausedSub?.close()
        deviceProvidePausedSub = null
        deviceOfflineSub?.close()
        deviceOfflineSub = null
        deviceConnectSub?.close()
        deviceConnectSub = null

//        provideEnabled = false
//        connectEnabled = false

//        byDevice?.close()
//        byDevice = null
        byDeviceManager.clearByDevice()

        accountVc?.close()
        accountVc = null

        loginVc?.close()
        loginVc = null
    }


    private fun initDevice(byClientJwt: String) {
        byDeviceManager.initDevice(
            networkSpaceManagerProvider.getNetworkSpace(),
            byClientJwt,
            deviceDescription,
            deviceSpec
        )

        router = Router(byDevice!!) {
            runBlocking(Dispatchers.Main.immediate) {
                updateVpnService()
            }
        }

        // connectVc = byDevice?.openConnectViewController()
//        devicesVc = byDevice?.openDevicesViewController()
//        accountVc = byDevice?.openAccountViewController()

        devicesVc = byDevice?.openDevicesViewController()
        accountVc = byDevice?.openAccountViewController()

//        byDevice?.providePaused = true
//        byDevice?.routeLocal = routeLocal
//        byDevice?.provideMode = provideMode

//
//        connectLocation?.let {
//            byDeviceManager.connectVc?.connect(it)
//        }





        deviceRouteLocalSub = byDevice?.addRouteLocalChangeListener {
            runBlocking(Dispatchers.Main.immediate) {
//                this@MainApplication.connectEnabled = connectEnabled
                updateVpnService()
            }
        }
        deviceProvideSub = byDevice?.addProvideChangeListener {
            runBlocking(Dispatchers.Main.immediate) {
//                this@MainApplication.provideEnabled = provideEnabled
                updateVpnService()
            }
        }
        deviceProvidePausedSub = byDevice?.addProvidePausedChangeListener {
            runBlocking(Dispatchers.Main.immediate) {
                updateVpnService()
            }
        }
        deviceOfflineSub = byDevice?.addOfflineChangeListener { _, _ ->
            runBlocking(Dispatchers.Main.immediate) {
                updateVpnService()
            }
        }
        deviceConnectSub = byDevice?.addConnectChangeListener {
            runBlocking(Dispatchers.Main.immediate) {
//                this@MainApplication.connectEnabled = connectEnabled
                updateVpnService()
            }
        }

        router = Router(byDevice!!)

        addOfflineCallback()
        addNetworkCallback()

        updateVpnService()

        // return byDevice
    }



    private fun initCircleWallet() {
        val applicationContext = applicationContext ?: return

        val addId = applicationContext.getString(R.string.circle_app_id)

        val settingsManagement = SettingsManagement()

        val fingerprintManager = BiometricManager.from(applicationContext)

        hasBiometric = BiometricManager.BIOMETRIC_SUCCESS == fingerprintManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
                    BiometricManager.Authenticators.BIOMETRIC_STRONG)

        settingsManagement.isEnableBiometricsPin = hasBiometric //Set "true" to enable, "false" to disable
        val layoutProvider = CircleLayoutProvider(applicationContext)
        val viewSetterProvider = CircleViewSetterProvider(applicationContext)

        circleWalletManager.init(
            applicationContext,
            addId,
            settingsManagement,
            layoutProvider,
            viewSetterProvider
        )

    }

    private fun updateVpnService() {
        val byDevice = byDevice ?: return

        val provideEnabled = byDevice.provideEnabled
        val providePaused = byDevice.providePaused
        val connectEnabled = byDevice.connectEnabled
        val routeLocal = byDevice.routeLocal

        if (provideEnabled || connectEnabled || !routeLocal) {
            startVpnService()
            // if provide paused, keep the vpn on but do not keep the locks
            if (provideEnabled && !providePaused) {
                if (wakeLock == null) {
                    wakeLock = (getSystemService(POWER_SERVICE) as PowerManager).run {
                        newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "bringyour::provide").apply {
                            acquire()
                        }
                    }

                }
                if (wifiLock == null) {

                    wifiLock = (getSystemService(WIFI_SERVICE) as WifiManager).run {
                        val wifiLockMode: Int
                        if (Build.VERSION_CODES.UPSIDE_DOWN_CAKE <= Build.VERSION.SDK_INT) {
                            wifiLockMode = WifiManager.WIFI_MODE_FULL_LOW_LATENCY
                        } else {
                            wifiLockMode = WifiManager.WIFI_MODE_FULL_HIGH_PERF
                        }

                        createWifiLock(wifiLockMode, "bringyour::provide").apply {
                            acquire()
                        }
                    }
                }
                // make sure the wake lock and wifi lock are on
            } else {
                // turn off any wake lock or wifi lock

                wakeLock?.release()
                wakeLock = null
                wifiLock?.release()
                wifiLock = null
            }
        } else {
            stopVpnService()
            // turn off any wake lock or wifi lock

            wakeLock?.release()
            wakeLock = null
            wifiLock?.release()
            wifiLock = null
        }
    }

    fun startVpnService() {
        startVpnServiceWithForeground(true)
    }

    fun startVpnServiceWithForeground(foreground: Boolean) {
        val byDevice = byDevice ?: return


        val offline = byDevice.offline
        val vpnInterfaceWhileOffline = byDevice.vpnInterfaceWhileOffline

        val vpnIntent = Intent(this, MainService::class.java)
        vpnIntent.putExtra("source", "app")
        vpnIntent.putExtra("stop", false)
        vpnIntent.putExtra("start", true)
        vpnIntent.putExtra("foreground", foreground)
        vpnIntent.putExtra("offline", offline && !vpnInterfaceWhileOffline)

        try {
            if (VpnService.prepare(this) != null) {
                // prepare returns an intent when the user must grant additional permissions
                // the ui will check `vpnRequestStart` and start again when the permissions have been set up
                vpnRequestStart = true
                vpnRequestStartListener?.let { it() }
            } else {
                // important: start the vpn service in the application context

                if (foreground) {
                    // use a foreground service to allow notifications
                    if (Build.VERSION_CODES.TIRAMISU <= Build.VERSION.SDK_INT) {
                        val hasForegroundPermissions = ContextCompat.checkSelfPermission(
                            this,
                            android.Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasForegroundPermissions) {
                            try {
                                startForegroundService(vpnIntent)
                            } catch (e: ForegroundServiceStartNotAllowedException) {
                                startService(vpnIntent)
                            }
                        } else {
                            startService(vpnIntent)
                        }
                    } else if (Build.VERSION_CODES.S <= Build.VERSION.SDK_INT) {
                        try {
                            ContextCompat.startForegroundService(this, vpnIntent)
                        } catch (e: ForegroundServiceStartNotAllowedException) {
                            startService(vpnIntent)
                        }
                    } else {
                        ContextCompat.startForegroundService(this, vpnIntent)
                    }
                } else {
                    startService(vpnIntent)
                }

                vpnRequestStart = false
            }
        } catch (e: Exception) {
            Log.i(
                TAG,
                "Error trying to communicate with the vpn service to start: ${e.message}"
            )
            vpnRequestStart = true
            // do not request start here
            // that could lead to a loop
        }
    }

    private fun stopVpnService() {
        vpnRequestStart = false

        val vpnIntent = Intent(this, MainService::class.java)
        vpnIntent.putExtra("source", "app")
        vpnIntent.putExtra("stop", true)
        vpnIntent.putExtra("start", false)
        vpnIntent.putExtra("foreground", false)
        try {
            stopService(vpnIntent)
        } catch (e: Exception) {
            Log.i(TAG, "Error trying to communicate with the vpn service to stop: ${e.message}")
            // ignore
        }


    }
}
