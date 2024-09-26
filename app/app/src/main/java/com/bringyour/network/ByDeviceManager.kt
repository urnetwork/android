package com.bringyour.network

import com.bringyour.client.BringYourDevice
import com.bringyour.client.Client
import com.bringyour.client.ConnectLocation
import com.bringyour.client.Id
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
        instanceId: Id,
        routeLocal: Boolean,
        provideMode: Long,
        connectLocation: ConnectLocation?,
        deviceDescription: String,
        deviceSpec: String
    ) {
        byDevice?.close()  // Ensure old instance is cleaned up
        byDevice = Client.newBringYourDeviceWithDefaults(
            networkSpace,
            byClientJwt,
            deviceDescription,
            deviceSpec,
            getAppVersion(),
            instanceId
        )
        byDevice?.providePaused = true
        byDevice?.routeLocal = routeLocal
        byDevice?.provideMode = provideMode
        byDevice?.connectLocation = connectLocation

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