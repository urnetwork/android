package com.bringyour.network

import android.app.Application
import com.bringyour.client.AsyncLocalState
import com.bringyour.client.BringYourApi
import com.bringyour.client.BringYourDevice
import com.bringyour.client.Client
import com.bringyour.client.ConnectViewController
import com.bringyour.client.DevicesViewController
import com.bringyour.client.LoginViewController
import go.Universe
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


    override fun onCreate() {
        super.onCreate()

        // use sync mode for the local state
        asyncLocalState = Client.newAsyncLocalState(filesDir.absolutePath)
        byApi = Client.newBringYourApi(apiUrl)

        loginVc = Client.newLoginViewController(byApi)

        try {
            val byJwt = asyncLocalState?.localState()?.byJwt
            val byClientJwt = asyncLocalState?.localState()?.byClientJwt

            byApi?.setByJwt(byJwt)

            val instanceId = asyncLocalState?.localState()?.instanceId
            byDevice = Client.newBringYourDevice(byClientJwt, platformUrl, apiUrl, instanceId)
            router = Router(byDevice!!)

            connectVc = byDevice?.openConnectViewController()
            devicesVc = byDevice?.openDevicesViewController()

        } catch (e: Throwable) {
            if (e is error) {
                // this was an error raised as a second value
                logout()
            } else {
                throw(e)
            }
        }
    }


    fun login(byJwt : String) {
        asyncLocalState?.localState()?.setByJwt(byJwt)
        byApi?.setByJwt(byJwt)
    }

    fun loginClient(byClientJwt : String) {
        asyncLocalState?.localState()?.byClientJwt = byClientJwt

        val instanceId = asyncLocalState?.localState()?.instanceId
        byDevice = Client.newBringYourDevice(byClientJwt, platformUrl, apiUrl, instanceId)
        router = Router(byDevice!!)
    }

    fun logout() {
        asyncLocalState?.localState()?.logout()
        router?.close()
        router = null
        byDevice?.close()
        byDevice = null
    }


}
