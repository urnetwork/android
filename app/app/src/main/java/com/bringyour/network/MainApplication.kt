package com.bringyour.network

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.core.content.ContextCompat
import circle.programmablewallet.sdk.WalletSdk
import circle.programmablewallet.sdk.presentation.SecurityQuestion
import circle.programmablewallet.sdk.presentation.SettingsManagement
import com.bringyour.client.AccountViewController
import com.bringyour.client.AsyncLocalState
import com.bringyour.client.BringYourApi
import com.bringyour.client.BringYourDevice
import com.bringyour.client.Client
import com.bringyour.client.DevicesViewController
import com.bringyour.client.Id
import com.bringyour.client.LoginViewController
import com.bringyour.network.ui.account.CircleLayoutProvider
import com.bringyour.network.ui.account.CircleViewSetterProvider
import com.bringyour.client.Client.ProvideModeNone
import com.bringyour.client.OverlayViewController
import com.bringyour.client.Sub
import dagger.hilt.android.HiltAndroidApp
import go.error
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
    val platformUrl = "wss://connect.bringyour.com"
    val apiUrl = "https://api.bringyour.com"

    var byDevice: BringYourDevice? = null
    var deviceProvideSub: Sub? = null
    var deviceConnectSub: Sub? = null
    var byApi: BringYourApi? = null
    var asyncLocalState: AsyncLocalState? = null
    var router: Router? = null


    // use one set of view controllers across the entire app
    var loginVc: LoginViewController? = null
    var devicesVc: DevicesViewController? = null
    var accountVc: AccountViewController? = null
    var overlayVc: OverlayViewController? = null

    var hasBiometric: Boolean = false

    @Inject
    lateinit var byDeviceManager: ByDeviceManager

    @Inject
    lateinit var circleWalletManager: CircleWalletManager

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
            try {
                localState.byClientJwt?.let { byClientJwt ->
                    // the device wraps the api and sets the jwt
                    val instanceId = asyncLocalState?.localState()?.instanceId!!
                    val provideMode = asyncLocalState?.localState()?.provideMode!!
                    initDevice(byClientJwt, instanceId, provideMode)
                }
            } catch (e: Throwable) {
                if (e is error) {
                    // missing one or both of jwt or client jwt
                    localState.logout()
                } else {
                    throw(e)
                }
            }
        }

        initCircleWallet()
    }


    fun login(byJwt: String) {
        asyncLocalState?.localState()?.byJwt = byJwt
        byApi?.setByJwt(byJwt)
    }

    fun loginClient(byClientJwt: String) {
        asyncLocalState?.localState()?.byClientJwt = byClientJwt
        val provideMode = ProvideModeNone
        asyncLocalState?.localState()?.provideMode = provideMode

        val instanceId = asyncLocalState?.localState()?.instanceId!!
        initDevice(byClientJwt, instanceId, provideMode)
    }

    fun logout() {
        stopVpnService()

        asyncLocalState?.localState()?.logout()

        devicesVc?.let {
            byDevice?.closeViewController(it)
        }
        devicesVc = null

        overlayVc?.let {
            byDevice?.closeViewController(it)
        }
        overlayVc = null

        router?.close()
        router = null
        deviceProvideSub?.close()
        deviceProvideSub = null
        deviceConnectSub?.close()
        deviceConnectSub = null

//        provideEnabled = false
//        connectEnabled = false

        byDevice?.close()
        byDevice = null
        byDeviceManager.clearByDevice()

        accountVc?.close()
        accountVc = null

        byApi?.setByJwt(null)
    }


    private fun initDevice(byClientJwt: String, instanceId: Id, provideMode: Long): BringYourDevice? {

        byDeviceManager.initDevice(
            byApi,
            byClientJwt,
            instanceId,
            provideMode,
            platformUrl,
            getDeviceDescription(),
            getDeviceSpec()
        )

        byDevice = byDeviceManager.getByDevice()

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

        devicesVc = byDevice?.openDevicesViewController()
        accountVc = byDevice?.openAccountViewController()
        overlayVc = byDevice?.openOverlayViewController()

        return byDevice
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


    fun requestStartVpnService() {
        vpnRequestStart = true
    }

    fun startVpnService(onStart: (() -> Unit) = {}) {
        val vpnIntent = Intent(this, MainService::class.java)
        vpnIntent.putExtra("stop", false)
        vpnIntent.putExtra("start", true)
        vpnIntent.putExtra("foreground", true)

        try {
            sendVpnServiceIntent(
                vpnIntent,
                onStart = onStart
            )
        } catch (e: Exception) {
            Log.i(TAG, "Could not start vpn service: ${e.message}")
            // ignore
        }

//        startService(vpnIntent)

        vpnRequestStart = true
    }

    fun stopVpnService() {
        val vpnIntent = Intent(this, MainService::class.java)
        vpnIntent.putExtra("stop", true)
        vpnIntent.putExtra("start", false)
        vpnIntent.putExtra("foreground", false)
        try {
            sendVpnServiceIntent(vpnIntent)
        } catch (e: Exception) {
            Log.i(TAG, "Could not start vpn service: ${e.message}")
            // ignore
        }


        vpnRequestStart = false
    }

    private fun sendVpnServiceIntent(vpnIntent: Intent, onStart: (() -> Unit) = {}) {
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

                onStart()
            }
        }
    }


    private fun updateVpnService() {
        val byDevice = byDevice ?: return

        val provideEnabled = byDevice.isProvideEnabled
        val connectEnabled = byDevice.isConnectEnabled

        if (provideEnabled || connectEnabled) {
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

    fun setProvideMode(provideMode: Long) {
        // store the setting in local storage
        asyncLocalState?.localState()?.provideMode = provideMode
        byDevice?.setProvideMode(provideMode)

    }

    fun getProvideMode(): Long {
        return asyncLocalState?.localState()?.provideMode!!
    }

    fun isVpnRequestStart(): Boolean {
        return vpnRequestStart
    }

}

class ApplicationPreviewParameterProvider : PreviewParameterProvider<Application> {
    override val values: Sequence<Application>
        get() = sequenceOf(MainApplication())
}