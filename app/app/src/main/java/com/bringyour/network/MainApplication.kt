package com.bringyour.network

import android.app.ActivityManager
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
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.bringyour.network.ui.shared.models.ProvideNetworkMode
import com.bringyour.sdk.AccountViewController
import com.bringyour.sdk.DevicesViewController
import com.bringyour.sdk.LoginViewController
import com.bringyour.sdk.NetworkSpace
import com.bringyour.sdk.Sdk
import com.bringyour.sdk.Sub
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.lang.ref.WeakReference
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
    // set this to true to use foreground services
    // var allowForeground: Boolean = false

//    var byDevice: BringYourDevice? = null
    var deviceProvideSub: Sub? = null
    var deviceProvidePausedSub: Sub? = null

    var deviceProvideNetworkSub: Sub? = null
    var deviceOfflineSub: Sub? = null
    var deviceConnectSub: Sub? = null
    var deviceRouteLocalSub: Sub? = null
//    var router: Router? = null
    var tunnelChangeSub: Sub? = null
    var contractStatusChangeSub: Sub? = null

    var provideNetworkModeSub: Sub? = null

    var networkCallback: ConnectivityManager.NetworkCallback? = null
    var offlineCallback: ConnectivityManager.NetworkCallback? = null



    // use one set of view controllers across the entire app
    var loginVc: LoginViewController? = null
    var devicesVc: DevicesViewController? = null
    var accountVc: AccountViewController? = null

//    var tunnelRequestStatus: TunnelRequestStatus = TunnelRequestStatus.None

    @Inject
    lateinit var deviceManager: DeviceManager

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

    val device get() = deviceManager.device
//    val vcManager get() = deviceManager.vcManager
    val api get() = networkSpaceManagerProvider.getNetworkSpace()?.api
    val asyncLocalState get() = networkSpaceManagerProvider.getNetworkSpace()?.asyncLocalState
//    val apiUrl get() = networkSpace?.apiUrl
//    val platformUrl get() = networkSpace?.platformUrl


    var service: WeakReference<MainService>? = null
    private var _serviceActive: Boolean = false
    val serviceActiveMonitor = Object()
    var serviceActive: Boolean
        get() = synchronized(serviceActiveMonitor) {
            return _serviceActive
        }
        set(it) = synchronized(serviceActiveMonitor) {
            _serviceActive = it
            serviceActiveMonitor.notifyAll()
        }


    override fun onCreate() {
        super.onCreate()

        val activityManager = getSystemService(ACTIVITY_SERVICE) as ActivityManager?
        val maxMemoryMib = activityManager?.memoryClass?.toLong() ?: 16
        // target 3/4 of the max memory for the sdk
        Sdk.setMemoryLimit((3 * maxMemoryMib * 1024 * 1024) / 4)

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
                runBlocking(Dispatchers.Main.immediate) {
                    Log.i(TAG, "network available device = $network")
                    connectedNetwork = network
                    device?.offline = false
                }
            }

            override fun onUnavailable() {
                runBlocking(Dispatchers.Main.immediate) {
                    device?.offline = true
                }
            }

            override fun onLost(network: Network) {
                runBlocking(Dispatchers.Main.immediate) {
                    if (network == connectedNetwork) {
                        Log.i(TAG, "network lost device = $network")
                        connectedNetwork = null
                        device?.offline = true
                    }
                }
            }
        }


        val networkRequestBuilder = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)

        val networkRequest = networkRequestBuilder.build()

        val connectivityManager =
            getSystemService(ConnectivityManager::class.java) as ConnectivityManager
        connectivityManager.requestNetwork(networkRequest, offlineCallback!!, 100)

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
                runBlocking(Dispatchers.Main.immediate) {
                    Log.i(TAG, "network available device = $network")
                    connectedNetwork = network
                    device?.providePaused = false
                }
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                val internet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                val ethernet = !networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                val wifi = !networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)

                device?.let {
                    val networkReady = if (ProvideNetworkMode.fromString(it.provideNetworkMode) == ProvideNetworkMode.WIFI) {
                        internet && (ethernet || wifi)
                    } else {
                        internet
                    }
                    it.providePaused = !networkReady
                }
            }

            override fun onUnavailable() {
                runBlocking(Dispatchers.Main.immediate) {
                    device?.providePaused = true
                }
            }

            override fun onLost(network: Network) {
                runBlocking(Dispatchers.Main.immediate) {
                    if (network == connectedNetwork) {
                        Log.i(TAG, "network lost device = $network")
                        connectedNetwork = null
                        device?.providePaused = true
                    }
                }
            }
        }


        // see https://developer.android.com/training/monitoring-device-state/connectivity-status-type
        val networkRequestBuilder = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            // 2025-01 drop the non-metered requirement. This appears to limit some networks globally
//            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)

        /**
         * restrict to wifi if provideNetworkMode == wifi or device is null
         */
        device?.let {
            if (ProvideNetworkMode.fromString(it.provideNetworkMode) == ProvideNetworkMode.WIFI) {
                networkRequestBuilder
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
            }
        } ?: run {
            networkRequestBuilder
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
        }

//            .build()


        // note the following capabilities appear to never be satisfied
        // NetworkCapabilities.NET_CAPABILITY_PRIORITIZE_LATENCY
        // NetworkCapabilities.NET_CAPABILITY_PRIORITIZE_BANDWIDTH

        val networkRequest = networkRequestBuilder.build()

        val connectivityManager =
            getSystemService(ConnectivityManager::class.java) as ConnectivityManager
        connectivityManager.requestNetwork(networkRequest, networkCallback!!, 100)

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
        api?.byJwt = byJwt
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
            device?.closeViewController(it)
        }
        devicesVc = null

//        router?.close()
//        router = null
        deviceProvideSub?.close()
        deviceProvideSub = null
        deviceProvidePausedSub?.close()
        deviceProvidePausedSub = null
        deviceProvideNetworkSub?.close()
        deviceProvideNetworkSub = null
        deviceOfflineSub?.close()
        deviceOfflineSub = null
        deviceConnectSub?.close()
        deviceConnectSub = null
        deviceRouteLocalSub?.close()
        deviceRouteLocalSub = null
        tunnelChangeSub?.close()
        tunnelChangeSub = null
        contractStatusChangeSub?.close()
        contractStatusChangeSub = null
        provideNetworkModeSub?.close()
        provideNetworkModeSub = null

//        provideEnabled = false
//        connectEnabled = false

//        byDevice?.close()
//        byDevice = null
        deviceManager.clearDevice()

        accountVc?.close()
        accountVc = null

        loginVc?.close()
        loginVc = null
    }


    private fun initDevice(byClientJwt: String) {
        deviceManager.initDevice(
            networkSpaceManagerProvider.getNetworkSpace(),
            byClientJwt,
            deviceDescription,
            deviceSpec
        )

//        router = Router(device!!) {
//            runBlocking(Dispatchers.Main.immediate) {
//                updateVpnService()
//            }
//        }

        // connectVc = byDevice?.openConnectViewController()
//        devicesVc = byDevice?.openDevicesViewController()
//        accountVc = byDevice?.openAccountViewController()

        devicesVc = device?.openDevicesViewController()
        accountVc = device?.openAccountViewController()

//        byDevice?.providePaused = true
//        byDevice?.routeLocal = routeLocal
//        byDevice?.provideMode = provideMode

//
//        connectLocation?.let {
//            byDeviceManager.connectVc?.connect(it)
//        }





        deviceRouteLocalSub = device?.addRouteLocalChangeListener {
            Handler(Looper.getMainLooper()).post {
                updateVpnService()
            }
        }
        deviceProvideSub = device?.addProvideChangeListener {
            Handler(Looper.getMainLooper()).post {
                updateVpnService()
            }
        }
        deviceProvidePausedSub = device?.addProvidePausedChangeListener {
            Handler(Looper.getMainLooper()).post {
                updateVpnService()
            }
        }
        deviceProvideNetworkSub = device?.addProvideNetworkModeChangeListener {

            Handler(Looper.getMainLooper()).post {

                addNetworkCallback()

                updateVpnService()
            }
        }
        deviceOfflineSub = device?.addOfflineChangeListener { _, _ ->
            Handler(Looper.getMainLooper()).post {
                updateVpnService()
            }
        }
        deviceConnectSub = device?.addConnectChangeListener {
            Handler(Looper.getMainLooper()).post {
                updateVpnService()
            }
        }


        tunnelChangeSub = device?.addTunnelChangeListener { tunnelStarted ->
            Handler(Looper.getMainLooper()).post {
                updateTunnelStarted()

                if (!tunnelStarted) {
                    // the tunnel stopped. Sync with state
//                    tunnelRequestStatus = TunnelRequestStatus.None
                    updateVpnService()
                }
            }
        }
        contractStatusChangeSub = device?.addContractStatusChangeListener {
            Handler(Looper.getMainLooper()).post {
                updateContractStatus()
            }
        }

        provideNetworkModeSub = device?.addProvideNetworkModeChangeListener {
            Handler(Looper.getMainLooper()).post {
                addNetworkCallback()
            }
        }

        addOfflineCallback()
        addNetworkCallback()

        // *important* calling startService for a VpnService in OnCreate will *not* correctly set up the routes
        // we need to delay this after onCreate for the routes to set up correctly (wtf)
        Handler(Looper.getMainLooper()).post {
            updateTunnelStarted()
            updateContractStatus()
            updateVpnService()
        }

        // return byDevice
    }

    private fun updateTunnelStarted() {
        device?.tunnelStarted?.let { tunnelStarted ->
            Log.i(TAG, "[tunnel]started=$tunnelStarted")
        } ?: run {
            Log.i(TAG, "[tunnel]no tunnel")
        }
    }

    private fun updateContractStatus() {
        device?.contractStatus?.let { contractStatus ->
            Log.i(TAG, "[contract]insufficent=${contractStatus.insufficientBalance} nopermission=${contractStatus.noPermission} premium=${contractStatus.premium}")
        } ?: run {
            Log.i(TAG, "[contract]no contract status")
        }
    }

    fun updateVpnService() {
        val device = device ?: return

        val provideEnabled = device.provideEnabled
        val providePaused = device.providePaused
        val connectEnabled = device.connectEnabled
        val routeLocal = device.routeLocal

        if (provideEnabled || connectEnabled || !routeLocal) {
            startVpnService()
            // if provide paused, keep the vpn on but do not keep the locks
            if (provideEnabled && !providePaused) {
                if (wakeLock == null) {
                    wakeLock = (getSystemService(POWER_SERVICE) as PowerManager).run {
                        newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "urnetwork::provide").apply {
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

                        createWifiLock(wifiLockMode, "urnetwork::provide").apply {
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

        // FIXME tunnel request status

    fun startVpnService() {
        val device = device ?: return

        val allowForeground = deviceManager.allowForeground

        // note starting in Android 15, boot completed receivers cannot start foreground services
        // the app will not allow foreground until the activity is explicitly opened
        // see https://developer.android.com/about/versions/15/behavior-changes-15#fgs-boot-completed
        startVpnServiceWithForeground(allowForeground && device.providerEnabled)
    }

    private fun startVpnServiceWithForeground(foreground: Boolean) {
//        if (tunnelRequestStatus == TunnelRequestStatus.Started) {
//            return
//        }


        val device = device ?: return

        if (!serviceActive) {

            val offline = device.offline
            val vpnInterfaceWhileOffline = device.vpnInterfaceWhileOffline

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

                    fun hasForegroundPermissions(): Boolean {
                        if (Build.VERSION_CODES.TIRAMISU <= Build.VERSION.SDK_INT) {
                            val hasForegroundPermissions = ContextCompat.checkSelfPermission(
                                this,
                                android.Manifest.permission.POST_NOTIFICATIONS
                            ) == PackageManager.PERMISSION_GRANTED
                            return hasForegroundPermissions
                        } else {
                            return true
                        }
                    }

                    if (foreground && !hasForegroundPermissions()) {
                        vpnRequestStart = true
                        vpnRequestStartListener?.let { it() }
                    } else {
                        serviceActive = true

//                    tunnelRequestStatus = TunnelRequestStatus.Started
                        vpnRequestStart = false

                        // delaying the tunnel start seems to help with stability
                        Handler(mainLooper).postDelayed({
                            if (this@MainApplication.serviceActive) {
                                if (foreground) {
                                    // use a foreground service to allow notifications
                                    if (Build.VERSION_CODES.TIRAMISU <= Build.VERSION.SDK_INT) {
                                        try {
                                            startForegroundService(vpnIntent)
                                        } catch (e: ForegroundServiceStartNotAllowedException) {
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
                            }
                        }, 200)


                    }
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
    }

    private fun stopVpnService() {
        vpnRequestStart = false

//        val device = device ?: return


//        if (tunnelRequestStatus == TunnelRequestStatus.Stopped) {
//            return
//        }

        // note
        // - using startService with stop intent to stop the service is broken
        //   because stop, start quickly will ignore the second intent and deliver just the stop
        // - using stopService is broken, because it will not close the vpn tunnel fd,
        //   which prevents a new tunnel from starting again correctly
        // - using startService with stop intent, stopService in sequence is broken
        //   because startService in OnCreate will prevent the routes from being set up correctly (wtf)
        //
        // using a weak reference to the service is strangely the cleanest approach


        // TODO stop should return once the tunnel is torn down
//        device?.addTunnelChangeListener {
//            synchronized(serviceActiveMonitor) {
//                serviceActiveMonitor.notifyAll()
//            }
//        }



//        val s = Semaphore(0)
//        var sub: Sub? = null
//        if (!device.tunnelStarted) {
//            s.release()
//        } else {
//            sub = device.addTunnelChangeListener {
//                if (!device.tunnelStarted) {
//                    s.release()
//                }
//            }
//        }

        if (serviceActive) {
            serviceActive = false
            service?.get()?.stop()
            synchronized(serviceActiveMonitor) {
                serviceActiveMonitor.notifyAll()
            }
        }


        // wait for the tunnel to shut down


//        tunnelRequestStatus = TunnelRequestStatus.Stopped


//        val vpnIntent = Intent(this, MainService::class.java)
//        vpnIntent.putExtra("source", "app")
//        vpnIntent.putExtra("stop", true)
//        vpnIntent.putExtra("start", false)
//        vpnIntent.putExtra("foreground", false)
//        stopService(vpnIntent)

//        try {
//            // note this cannot be called in OnCreate, or it will prevent the routes from being set up correctly
//            startService(vpnIntent)
//            stopService(vpnIntent)
//            tunnelRequestStatus = TunnelRequestStatus.Stopped
//        } catch (e: Exception) {
//            Log.i(TAG, "Error trying to communicate with the vpn service to stop: ${e.message}")
//            // ignore
//        }


    }

}

//enum class TunnelRequestStatus {
//    Started, Stopped, None
//}