package com.bringyour.network

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.VpnService
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
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

        try {
            asyncLocalState?.localState()?.byJwt?.let { byJwt ->
                byApi?.setByJwt(byJwt)
            }

            asyncLocalState?.localState()?.byClientJwt?.let { byClientJwt ->
                val instanceId = asyncLocalState?.localState()?.instanceId!!
                val provideMode = asyncLocalState?.localState()?.provideMode!!
                initDevice(byClientJwt, instanceId, provideMode)
            }

        } catch (e: Throwable) {
            if (e is error) {
                // this was an error raised as a second value
                logout()
            } else {
                throw(e)
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


    private fun initDevice(byClientJwt: String, instanceId: Id, provideMode: Long) {
        byDevice = Client.newBringYourDeviceWithDefaults(
            byClientJwt,
            platformUrl,
            apiUrl,
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

        byDevice?.provideMode = provideMode

        connectVc = byDevice?.openConnectViewController()
        devicesVc = byDevice?.openDevicesViewController()
        accountVc = byDevice?.openAccountViewController()
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
        val vpnIntent = Intent(this, MainService::class.java)
        vpnIntent.putExtra("stop", false)
        vpnIntent.putExtra("start", true)
        sendVpnServiceIntent(vpnIntent)



//        startService(vpnIntent)

        vpnRequestStart = true
    }

    fun stopVpnService() {
        val vpnIntent = Intent(this, MainService::class.java)
        vpnIntent.putExtra("stop", true)
        vpnIntent.putExtra("start", false)
        sendVpnServiceIntent(vpnIntent)


        vpnRequestStart = false
    }

    private fun sendVpnServiceIntent(vpnIntent: Intent) {
        if (VpnService.prepare(this) == null) {
            // important: start the vpn service in the application context

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
                        createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "bringyour::provide").apply {
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
        byDevice?.setProvideMode(provideMode)

    }

    fun getProvideMode(): Long {
        return asyncLocalState?.localState()?.provideMode!!
    }

    fun isVpnRequestStart(): Boolean {
        return vpnRequestStart
    }




}
