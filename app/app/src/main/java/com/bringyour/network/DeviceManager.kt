package com.bringyour.network

import com.bringyour.sdk.DeviceLocal
import com.bringyour.sdk.NetworkSpace
import com.bringyour.sdk.Sdk
import com.bringyour.sdk.Sub
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceManager @Inject constructor() {

    var device: DeviceLocal? = null
        private set

//    var vcManager: ViewControllerManager? = null
//        private set

//    val byDevice = byDeviceManager.getByDevice()
//    var connectVc: ConnectViewControllerV0? = null
//        private set
//        connectVc?.start()

    val networkSpace get() = device?.networkSpace
    val asyncLocalState get() = device?.networkSpace?.asyncLocalState

    var routeLocal: Boolean
        get() = device?.routeLocal!!
        set(it) {
            asyncLocalState?.localState?.routeLocal = it
            device?.routeLocal = it
        }

    var canShowRatingDialog: Boolean
        get() = device?.canShowRatingDialog!!
        set(it) {
            asyncLocalState?.localState?.canShowRatingDialog = it
            device?.canShowRatingDialog = it
        }

    var canRefer: Boolean
        get() = if (device == null) false else device?.canRefer!!
        set(it) {
            asyncLocalState?.localState?.canShowRatingDialog = it
            device?.canShowRatingDialog = it
        }

    var provideWhileDisconnected: Boolean
        get() = device?.provideWhileDisconnected!!
        set(it) {
            asyncLocalState?.localState?.provideWhileDisconnected = it
            device?.provideWhileDisconnected = it
        }

    var allowForeground: Boolean
        get() = device?.allowForeground!!
        set(it) {
            asyncLocalState?.localState?.allowForeground = it
            device?.allowForeground = it
        }

    var vpnInterfaceWhileOffline: Boolean
        get() = device?.vpnInterfaceWhileOffline!!
        set(it) {
            asyncLocalState?.localState?.vpnInterfaceWhileOffline = it
            device?.vpnInterfaceWhileOffline = it
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
        device?.close()  // Ensure old instance is cleaned up

        val localState = networkSpace!!.asyncLocalState.localState!!
        val instanceId = localState.instanceId!!
        val routeLocal = localState.routeLocal
        val connectLocation = localState.connectLocation
        val canShowRatingDialog = localState.canShowRatingDialog
        val provideWhileDisconnected = localState.provideWhileDisconnected
        val provideMode = if (provideWhileDisconnected) Sdk.ProvideModePublic else localState.provideMode
        val vpnInterfaceWhileOffline = localState.vpnInterfaceWhileOffline
        val canRefer = localState.canRefer
        val allowForeground = localState.allowForeground

        device = Sdk.newDeviceLocalWithDefaults(
            networkSpace,
            byClientJwt,
            deviceDescription,
            deviceSpec,
            getAppVersion(),
            instanceId,
            false
        )
//        vcManager = Sdk.newViewControllerManager(device)

        localState.provideSecretKeys?.let {
            device?.loadProvideSecretKeys(it)
        } ?: run {
            var sub: Sub? = null
            sub = device?.addProvideSecretKeysListener {
                localState.provideSecretKeys = it
                sub?.close()
            }
            device?.initProvideSecretKeys()
        }

        device?.providePaused = true
        device?.routeLocal = routeLocal
        device?.provideMode = provideMode
        device?.connectLocation = connectLocation
        device?.canShowRatingDialog = canShowRatingDialog
        device?.provideWhileDisconnected = provideWhileDisconnected
        device?.vpnInterfaceWhileOffline = vpnInterfaceWhileOffline
        device?.canRefer = canRefer
        device?.allowForeground = allowForeground

//        connectVc = byDevice?.openConnectViewControllerV0()
    }

//    fun getByDevice(): BringYourDevice? {
//        return byDevice
//    }

    fun clearDevice() {
//        connectVc?.let {
//            byDevice?.closeViewController(it)
//        }
//        connectVc = null

        device?.close()
        device = null
    }

    private fun getAppVersion(): String {
        return "${BuildConfig.VERSION_NAME}-${BuildConfig.VERSION_CODE}"
    }
}