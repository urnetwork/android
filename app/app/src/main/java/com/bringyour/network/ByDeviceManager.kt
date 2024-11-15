package com.bringyour.network

import com.bringyour.sdk.BringYourDevice
import com.bringyour.sdk.Sdk
import javax.inject.Inject
import javax.inject.Singleton
import com.bringyour.sdk.NetworkSpace

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

    var canRefer: Boolean
        get() = byDevice?.canRefer!!
        set(it) {
            asyncLocalState?.localState?.canShowRatingDialog = it
            byDevice?.canShowRatingDialog = it
        }

    var provideWhileDisconnected: Boolean
        get() = byDevice?.provideWhileDisconnected!!
        set(it) {
            asyncLocalState?.localState?.provideWhileDisconnected = it
            byDevice?.provideWhileDisconnected = it
        }

    var vpnInterfaceWhileOffline: Boolean
        get() = byDevice?.vpnInterfaceWhileOffline!!
        set(it) {
            asyncLocalState?.localState?.vpnInterfaceWhileOffline = it
            byDevice?.vpnInterfaceWhileOffline = it
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
        val provideMode = if (provideWhileDisconnected) Sdk.ProvideModePublic else localState.provideMode
        val vpnInterfaceWhileOffline = localState.vpnInterfaceWhileOffline
        val canRefer = localState.canRefer

        byDevice = Sdk.newBringYourDeviceWithDefaults(
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
        byDevice?.vpnInterfaceWhileOffline = vpnInterfaceWhileOffline
        byDevice?.canRefer = canRefer

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