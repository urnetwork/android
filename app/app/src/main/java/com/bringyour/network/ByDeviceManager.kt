package com.bringyour.network

import android.util.Log
import com.bringyour.client.BringYourDevice
import com.bringyour.client.Client
import javax.inject.Inject
import javax.inject.Singleton
import com.bringyour.client.NetworkSpace

@Singleton
class ByDeviceManager @Inject constructor() {

    var byDevice: BringYourDevice? = null
        private set

//    val byDevice = byDeviceManager.getByDevice()
//    var connectVc: ConnectViewControllerV0? = null
//        private set
//        connectVc?.start()

    val networkSpace get() = byDevice?.networkSpace
    val asyncLocalState get() = byDevice?.networkSpace?.asyncLocalState

    var routeLocal: Boolean
        get() = byDevice?.routeLocal!!
        set(it) {
            asyncLocalState?.localState?.routeLocal = it
            byDevice?.routeLocal = it
        }

    var canShowRatingDialog: Boolean
        get() = byDevice?.canShowRatingDialog!!
        set(it) {
            asyncLocalState?.localState?.canShowRatingDialog = it
            byDevice?.canShowRatingDialog = it
        }

    var provideWhileDisconnected: Boolean
        get() = byDevice?.provideWhileDisconnected!!
        set(it) {
            Log.i("ByDeviceManager", "setting provideWhileDisconnected to $it")
            asyncLocalState?.localState?.provideWhileDisconnected = it
            byDevice?.provideWhileDisconnected = it
        }


//    var provideMode: Long
//        get() = byDevice?.provideMode!!
//        set(it) {
//            asyncLocalState?.localState?.provideMode = it
//            byDevice?.provideMode = it
//        }

    // note that view controllers that set location should also save the state
//    var connectLocation: ConnectLocation?
//        get() = byDevice?.connectLocation
//        set(it) {
//            asyncLocalState?.localState?.connectLocation = connectLocation
//            byDevice?.connectLocation = it
//        }





    fun initDevice(
        // byApi: BringYourApi?,
        networkSpace: NetworkSpace?,
        byClientJwt: String,
        deviceDescription: String,
        deviceSpec: String
    ) {
        byDevice?.close()  // Ensure old instance is cleaned up

        val localState = networkSpace!!.asyncLocalState.localState!!
        val instanceId = localState.instanceId!!
        val routeLocal = localState.routeLocal
        val connectLocation = localState.connectLocation
        val canShowRatingDialog = localState.canShowRatingDialog
        val provideWhileDisconnected = localState.provideWhileDisconnected
        Log.i("ByDeviceManager", "localState.provideWhileDisconnected: ${localState.provideWhileDisconnected}")
        Log.i("ByDeviceManager", "localState.provideMode: ${localState.provideMode}")
        val provideMode = if (provideWhileDisconnected) Client.ProvideModePublic else localState.provideMode

        byDevice = Client.newBringYourDeviceWithDefaults(
            networkSpace,
            byClientJwt,
            deviceDescription,
            deviceSpec,
            getAppVersion(),
            instanceId
        )

        localState.provideSecretKeys?.let {
            byDevice?.loadProvideSecretKeys(it)
        } ?: run {
            byDevice?.initProvideSecretKeys()
            localState.provideSecretKeys = byDevice?.provideSecretKeys
        }

        byDevice?.providePaused = true
        byDevice?.routeLocal = routeLocal
        byDevice?.provideMode = provideMode
        byDevice?.connectLocation = connectLocation
        byDevice?.canShowRatingDialog = canShowRatingDialog
        byDevice?.provideWhileDisconnected = provideWhileDisconnected

//        connectVc = byDevice?.openConnectViewControllerV0()
    }

//    fun getByDevice(): BringYourDevice? {
//        return byDevice
//    }

    fun clearByDevice() {
//        connectVc?.let {
//            byDevice?.closeViewController(it)
//        }
//        connectVc = null

        byDevice?.close()
        byDevice = null
    }

    private fun getAppVersion(): String {
        return BuildConfig.VERSION_NAME
    }
}