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
    val platformUrl = "wss://connect.bringyour.com"
    val apiUrl = "https://api.bringyour.com"

    var byDevice: BringYourDevice? = null
    var deviceProvideSub: Sub? = null
    var deviceConnectSub: Sub? = null
    var byApi: BringYourApi? = null
    var asyncLocalState: AsyncLocalState? = null
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




    override fun onCreate() {
        super.onCreate()

        // use sync mode for the local state
        asyncLocalState = Client.newAsyncLocalState(filesDir.absolutePath)
        byApi = Client.newBringYourApi(apiUrl)

        loginVc = Client.newLoginViewController(byApi)

        asyncLocalState?.localState()?.let { localState ->
            localState.byClientJwt?.let { byClientJwt ->
                if (byClientJwt == "") {
                    // missing one or both of jwt or client jwt
                    localState.logout()
                } else {
                    // the device wraps the api and sets the jwt
                    val instanceId = localState.instanceId!!
                    val provideMode = localState.provideMode
                    val connectLocation = localState.connectLocation
                    initDevice(byClientJwt, instanceId, provideMode, connectLocation)
                }
            }
        }

        initCircleWallet()
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
        asyncLocalState?.localState()?.byClientJwt = byClientJwt
        val provideMode = ProvideModeNone
        val connectLocation: ConnectLocation? = null
        asyncLocalState?.localState()?.provideMode = provideMode
        asyncLocalState?.localState()?.connectLocation = connectLocation

        val instanceId = asyncLocalState?.localState()?.instanceId!!
        initDevice(byClientJwt, instanceId, provideMode, connectLocation)
    }

    fun logout() {
        stop()

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
    }


    private fun initDevice(byClientJwt: String, instanceId: Id, provideMode: Long, connectLocation: ConnectLocation?) {
        byDevice = Client.newBringYourDeviceWithDefaults(
            byApi,
            byClientJwt,
            platformUrl,
            getDeviceDescription(),
            getDeviceSpec(),
            getAppVersion(),
            instanceId
        )
//        provideEnabled = false
//        connectEnabled = false
        deviceProvideSub = byDevice?.addProvideChangeListener { provideEnabled ->
            runBlocking(Dispatchers.Main.immediate) {
//                this@MainApplication.provideEnabled = provideEnabled
                updateVpnService()
            }
        }
        deviceConnectSub = byDevice?.addConnectChangeListener { connectEnabled ->
            runBlocking(Dispatchers.Main.immediate) {
//                this@MainApplication.connectEnabled = connectEnabled
                updateVpnService()
            }
        }


        router = Router(byDevice!!)

        connectVc = byDevice?.openConnectViewController()
        devicesVc = byDevice?.openDevicesViewController()
        accountVc = byDevice?.openAccountViewController()


        byDevice?.providePaused = true
        byDevice?.provideMode = provideMode

        connectLocation?.let {
            connectVc?.connect(it)
        }

        addNetworkCallback()
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


    fun requestStartVpnService() {
        vpnRequestStart = true
    }

    fun startVpnService() {
        vpnRequestStart = true

        val vpnIntent = Intent(this, MainService::class.java)
//        vpnIntent.putExtra("managed", true)
        vpnIntent.putExtra("stop", false)
        vpnIntent.putExtra("start", true)
        vpnIntent.putExtra("route-local", isRouteLocal())
        vpnIntent.putExtra("foreground", true)
        try {
            sendVpnServiceIntent(vpnIntent)
        } catch (e: Exception) {
            Log.i(TAG, "Could not start vpn service: ${e.message}")
            // ignore
        }

//        startService(vpnIntent)

    }

    fun stopVpnService() {
        vpnRequestStart = false

        val vpnIntent = Intent(this, MainService::class.java)
//        vpnIntent.putExtra("managed", true)
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

        val provideEnabled = byDevice.isProvideEnabled
        val connectEnabled = byDevice.isConnectEnabled

        if (provideEnabled || connectEnabled) {
            // note the app will call `startVpnService` once it determines the permissions are ready
            requestStartVpnService()
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
