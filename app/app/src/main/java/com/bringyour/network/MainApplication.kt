package com.bringyour.network

import android.app.Application
import android.content.Intent
import android.os.Build
import androidx.biometric.BiometricManager
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
import go.error

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
    var byApi: BringYourApi? = null
    var asyncLocalState: AsyncLocalState? = null
    var router: Router? = null


    // use one set of view controllers across the entire app
    var loginVc: LoginViewController? = null
    var connectVc: ConnectViewController? = null
    var devicesVc: DevicesViewController? = null
    var accountVc: AccountViewController? = null

    var hasBiometric: Boolean = false


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
                initDevice(byClientJwt, instanceId)
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

        val instanceId = asyncLocalState?.localState()?.instanceId!!
        initDevice(byClientJwt, instanceId)
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
        byDevice?.close()
        byDevice = null
        accountVc?.close()
        accountVc = null
    }


    private fun initDevice(byClientJwt: String, instanceId: Id) {
        byDevice = Client.newBringYourDeviceWithDefaults(
            byClientJwt,
            platformUrl,
            apiUrl,
            getDeviceDescription(),
            getDeviceSpec(),
            getAppVersion(),
            instanceId
        )
        router = Router(byDevice!!)

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


    fun startVpnService() {
        // important: start the vpn service in the application context
        val vpnIntent = Intent(this, MainService::class.java)
        if (Build.VERSION_CODES.O <= Build.VERSION.SDK_INT) {
            startForegroundService(vpnIntent)
        } else {
            startService(vpnIntent)
        }
    }

    fun stopVpnService() {
        val vpnIntent = Intent(this, MainService::class.java)
        stopService(vpnIntent)
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
}
