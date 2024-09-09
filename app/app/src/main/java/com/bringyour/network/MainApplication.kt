package com.bringyour.network

import android.app.Application
import android.app.BackgroundServiceStartNotAllowedException
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.core.content.ContextCompat
import circle.programmablewallet.sdk.WalletSdk
import circle.programmablewallet.sdk.api.ExecuteEvent
import circle.programmablewallet.sdk.presentation.SecurityQuestion
import circle.programmablewallet.sdk.presentation.SettingsManagement
import com.bringyour.client.AccountViewController
import com.bringyour.client.AsyncLocalState
import com.bringyour.client.BringYourApi
import com.bringyour.client.BringYourDevice
import com.bringyour.client.Client
import com.bringyour.client.ConnectViewController
import com.bringyour.client.DevicesViewController
import com.bringyour.client.Id
import com.bringyour.client.LoginViewController
import com.bringyour.network.ui.account.CircleLayoutProvider
import com.bringyour.network.ui.account.CircleViewSetterProvider
import circle.programmablewallet.sdk.presentation.EventListener
import com.bringyour.client.Client.ProvideModeNone
import com.bringyour.client.ConnectLocation
import com.bringyour.client.NetworkSpace
import com.bringyour.client.Sub
import go.error
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

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

    val networkSpaceManager = Client.newNetworkSpaceManager(filesDir.absolutePath)
    var networkSpaceSub: Sub? = null
    var networkSpace: NetworkSpace? = null

    var byDevice: BringYourDevice? = null
    var deviceProvideSub: Sub? = null
    var deviceConnectSub: Sub? = null
    var deviceRouteLocalSub: Sub? = null
//    var byApi: BringYourApi? = null
//    var asyncLocalState: AsyncLocalState? = null
    var router: Router? = null

    var networkCallback: ConnectivityManager.NetworkCallback? = null


    // use one set of view controllers across the entire app
    var loginVc: LoginViewController? = null
    var connectVc: ConnectViewController? = null
    var devicesVc: DevicesViewController? = null
    var accountVc: AccountViewController? = null

    var hasBiometric: Boolean = false

    private var vpnRequestStart: Boolean = false
    // FIXME remove these bools and just query the device directly
//    private var provideEnabled: Boolean = false
//    private var connectEnabled: Boolean = false


    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null


    val byApi get() = networkSpace?.api
    val asyncLocalState get() = networkSpace?.asyncLocalState
    val apiUrl get() = networkSpace?.apiUrl
    val platformUrl get() = networkSpace?.platformUrl


    override fun onCreate() {
        super.onCreate()

        val bundleNetworkSpaceExists = networkSpaceManager.getNetworkSpace(BuildConfig.BRINGYOUR_BUNDLE_HOST_NAME, BuildConfig.BRINGYOUR_BUNDLE_ENV) != null
        val bundleNetworkSpace = networkSpaceManager.updateNetworkSpace(BuildConfig.BRINGYOUR_BUNDLE_HOST_NAME, BuildConfig.BRINGYOUR_BUNDLE_ENV) { values ->
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
        }

        if (!bundleNetworkSpaceExists || networkSpaceManager.activeNetworkSpace == null) {
            // switch to the bundled network space when first created
            // this is important when migrating from an older bundle to a newer bundle
            networkSpaceManager.activeNetworkSpace = bundleNetworkSpace
        }

        networkSpaceSub = networkSpaceManager.addNetworkSpaceListener { networkSpace ->
            runBlocking(Dispatchers.Main.immediate) {
                updateActiveNetworkSpace(networkSpace)

                // launch the initial activity
                val intent = Intent(applicationContext, LoginActivity::class.java)
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK.or(Intent.FLAG_ACTIVITY_TASK_ON_HOME))
                startActivity(intent)
            }
        }

        updateActiveNetworkSpace(networkSpaceManager.activeNetworkSpace!!)
    }


    fun updateActiveNetworkSpace(networkSpace: NetworkSpace) {
        stop()

        this.networkSpace = networkSpace
        // use sync mode for the local state
//        asyncLocalState = networkSpace.asyncLocalState
//        byApi = networkSpace.byApi

        loginVc = Client.newLoginViewController(byApi)

        if (networkSpace.wallet == "circle" || networkSpace.wallet == "all") {
            initCircleWallet()
        }

        asyncLocalState?.localState()?.let { localState ->
            localState.byClientJwt?.let { byClientJwt ->
                if (byClientJwt == "") {
                    // missing one or both of jwt or client jwt
                    // clean up the local state
                    localState.logout()
                } else {
                    // the device wraps the api and sets the jwt
                    val instanceId = localState.instanceId!!
                    val routeLocal = localState.routeLocal
                    val provideMode = localState.provideMode
                    val connectLocation = localState.connectLocation
                    initDevice(byClientJwt, instanceId, routeLocal, provideMode, connectLocation)
                }
            }
        }


    }

    fun addNetworkCallback() {
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
        asyncLocalState?.localState()?.byJwt = byJwt
        byApi?.setByJwt(byJwt)
    }

    fun loginClient(byClientJwt: String) {
        asyncLocalState?.localState()?.let { localState ->
            localState.byClientJwt = byClientJwt

            val instanceId = localState.instanceId!!
            val routeLocal = localState.routeLocal
            val provideMode = localState.provideMode
            val connectLocation = localState.connectLocation

            initDevice(byClientJwt, instanceId, routeLocal, provideMode, connectLocation)
        }
    }

    fun logout() {
        stop()

        // note this clears the clientJwt also
        asyncLocalState?.localState()?.byJwt = ""
        byApi?.setByJwt(null)
    }

    fun stop() {
        stopVpnService()

        removeNetworkCallback()

        asyncLocalState?.localState()?.logout()

        connectVc?.let {
            byDevice?.closeViewController(it)
        }
        connectVc = null
        devicesVc?.let {
            byDevice?.closeViewController(it)
        }
        devicesVc = null


        router?.close()
        router = null
        deviceProvideSub?.close()
        deviceProvideSub = null
        deviceConnectSub?.close()
        deviceConnectSub = null
        byDevice?.close()
        byDevice = null
//        provideEnabled = false
//        connectEnabled = false
        accountVc?.close()
        accountVc = null

        loginVc?.close()
        loginVc = null
    }


    private fun initDevice(byClientJwt: String, instanceId: Id, routeLocal: Boolean, provideMode: Long, connectLocation: ConnectLocation?) {
        byDevice = Client.newBringYourDeviceWithDefaults(
            networkSpace,
            byClientJwt,
//            platformUrl,
            getDeviceDescription(),
            getDeviceSpec(),
            getAppVersion(),
            instanceId
        )
//        provideEnabled = false
//        connectEnabled = false



        router = Router(byDevice!!)

        connectVc = byDevice?.openConnectViewController()
        devicesVc = byDevice?.openDevicesViewController()
        accountVc = byDevice?.openAccountViewController()


        byDevice?.providePaused = true
        byDevice?.routeLocal = routeLocal
        byDevice?.provideMode = provideMode


        connectLocation?.let {
            connectVc?.connect(it)
        }


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
        deviceConnectSub = byDevice?.addConnectChangeListener {
            runBlocking(Dispatchers.Main.immediate) {
//                this@MainApplication.connectEnabled = connectEnabled
                updateVpnService()
            }
        }


        addNetworkCallback()

        updateVpnService()
    }



    private fun initCircleWallet() {
        val applicationContext = applicationContext ?: return

        val endpoint = applicationContext.getString(R.string.circle_endpoint)
        val addId = applicationContext.getString(R.string.circle_app_id)

        val settingsManagement = SettingsManagement()

        val fingerprintManager = BiometricManager.from(applicationContext)

        hasBiometric = BiometricManager.BIOMETRIC_SUCCESS == fingerprintManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
                    BiometricManager.Authenticators.BIOMETRIC_STRONG)

        settingsManagement.isEnableBiometricsPin = hasBiometric //Set "true" to enable, "false" to disable

        WalletSdk.init(
            applicationContext,
            WalletSdk.Configuration(endpoint, addId, settingsManagement)
        )

        WalletSdk.setSecurityQuestions(
            arrayOf(
                SecurityQuestion("What is your favorite color?"),
                SecurityQuestion("What is your favorite shape?"),
                SecurityQuestion("What is your favorite animal?"),
                SecurityQuestion("What is your favorite place?"),
                SecurityQuestion("What is your favorite material?"),
                SecurityQuestion("What is your favorite sound?"),
                SecurityQuestion("What would you explore in space?"),
                SecurityQuestion("Pick a word, any word."),
                SecurityQuestion("Pick a date, any date.", SecurityQuestion.InputType.datePicker),
            ))

        /*
        WalletSdk.addEventListener { event: ExecuteEvent ->
            // FIXME show a toast with the message
        }
         */

        WalletSdk.setLayoutProvider(CircleLayoutProvider(applicationContext))
        WalletSdk.setViewSetterProvider(CircleViewSetterProvider(applicationContext))
    }


//    fun requestStartVpnService() {
//        vpnRequestStart = true
//    }

    fun startVpnService() {

        val vpnIntent = Intent(this, MainService::class.java)
        vpnIntent.putExtra("source", "app")
        vpnIntent.putExtra("stop", false)
        vpnIntent.putExtra("start", true)
        vpnIntent.putExtra("foreground", true)
        try {
            sendVpnServiceIntent(vpnIntent)
            vpnRequestStart = false
        } catch (e: Exception) {
            Log.i(TAG, "Could not start vpn service: ${e.message}")
            // set the `vpnRequestStart` flag on failure
            // the app will call `startVpnService` once the permissions are ready
            vpnRequestStart = true
        }

//        startService(vpnIntent)

    }

    fun stopVpnService() {
        vpnRequestStart = false

        val vpnIntent = Intent(this, MainService::class.java)
        vpnIntent.putExtra("source", "app")
        vpnIntent.putExtra("stop", true)
        vpnIntent.putExtra("start", false)
        vpnIntent.putExtra("foreground", false)
        try {
            sendVpnServiceIntent(vpnIntent)
        } catch (e: Exception) {
            Log.i(TAG, "Could not start vpn service: ${e.message}")
            // ignore
        }


    }

    private fun sendVpnServiceIntent(vpnIntent: Intent) {
        if (VpnService.prepare(this) == null) {
            // important: start the vpn service in the application context

            vpnIntent.getBooleanExtra("foreground", false).let { foreground ->
                if (foreground) {
                    // use a foreground service to allow notifications
                    if (Build.VERSION_CODES.TIRAMISU <= Build.VERSION.SDK_INT) {
                        val hasForegroundPermissions = ContextCompat.checkSelfPermission(
                            this,
                            android.Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasForegroundPermissions) {
                            startForegroundService(vpnIntent)
                        } else {
                            startService(vpnIntent)
                        }
                    } else {
                        ContextCompat.startForegroundService(this, vpnIntent)
                    }
                } else {
                    startService(vpnIntent)
                }
            }
        }
    }


    private fun updateVpnService() {
        val byDevice = byDevice ?: return

        val provideEnabled = byDevice.provideEnabled
        val connectEnabled = byDevice.connectEnabled
        val routeLocal = byDevice.routeLocal

        if (provideEnabled || connectEnabled || !routeLocal) {
            startVpnService()
            if (provideEnabled) {
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
                        if (Build.VERSION_CODES.Q <= Build.VERSION.SDK_INT) {
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




    fun getDeviceDescription(): String {
        return "New device"
    }

    fun getDeviceSpec(): String {
        if (32 <= Build.VERSION.SDK_INT) {
            return "${Build.VERSION.RELEASE_OR_CODENAME} ${Build.FINGERPRINT}"
        } else {
            return "${Build.VERSION.RELEASE} ${Build.FINGERPRINT}"
        }
    }

    fun getAppVersion(): String {
        return BuildConfig.VERSION_NAME
    }

    fun setProvideMode(provideMode: Long) {
        // store the setting in local storage
        asyncLocalState?.localState()?.provideMode = provideMode
        byDevice?.provideMode = provideMode

    }

    fun getProvideMode(): Long {
        return asyncLocalState?.localState()?.provideMode!!
    }

    fun setRouteLocal(routeLocal: Boolean) {
        // store the setting in local storage
        asyncLocalState?.localState()?.routeLocal = routeLocal
        byDevice?.routeLocal = routeLocal
    }

    fun isRouteLocal(): Boolean {
        return asyncLocalState?.localState()?.routeLocal!!
    }

    fun isVpnRequestStart(): Boolean {
        return vpnRequestStart
    }

    fun setConnectLocation(connectLocation: ConnectLocation?) {
        // save connect location
        asyncLocalState?.localState()?.connectLocation = connectLocation
        if (connectLocation == null) {
            connectVc?.disconnect()
        } else {
            connectVc?.connect(connectLocation)
        }
    }

    fun getConnectLocation(): ConnectLocation? {
        return connectVc?.activeLocation
    }



}
