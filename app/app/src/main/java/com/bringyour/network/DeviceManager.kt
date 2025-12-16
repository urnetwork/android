package com.bringyour.network

import android.util.Log
import com.bringyour.network.ui.shared.models.ProvideControlMode
import com.bringyour.network.ui.shared.models.ProvideNetworkMode
import com.bringyour.sdk.DeviceLocal
import com.bringyour.sdk.NetworkSpace
import com.bringyour.sdk.Sdk
import com.bringyour.sdk.Sub
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceManager @Inject constructor(
    private val jwtManager: JwtManager
) {

    var device: DeviceLocal? = null
        private set

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

    var canPromptIntroFunnel: Boolean
        get() = if (device == null) true else device?.canPromptIntroFunnel!!
        set(it) {
            asyncLocalState?.localState?.setIntroFunnelLastPrompted()
            device?.canPromptIntroFunnel = it
        }

    var provideControlMode: ProvideControlMode
        get() = ProvideControlMode.fromString(device?.provideControlMode!!) ?: ProvideControlMode.AUTO
        set(it) {
            asyncLocalState?.localState?.provideControlMode = ProvideControlMode.toString(it)
            device?.provideControlMode = ProvideControlMode.toString(it)
        }

    var provideNetworkMode: ProvideNetworkMode
        get() = ProvideNetworkMode.fromString(device?.provideNetworkMode!!) ?: ProvideNetworkMode.WIFI
        set(it) {
            asyncLocalState?.localState?.provideNetworkMode = ProvideNetworkMode.toString(it)
            device?.provideNetworkMode = ProvideNetworkMode.toString(it)
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

    fun initDevice(
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
        val defaultLocation = localState.defaultLocation // when user selects location, disconnects, restarts app, we want to persist the location
        val canShowRatingDialog = localState.canShowRatingDialog
        val canPromptIntroFunnel = localState.canPromptIntroFunnel
        val provideControlMode = ProvideControlMode.fromString(localState.provideControlMode) ?: ProvideControlMode.AUTO
        val provideNetworkMode = ProvideNetworkMode.fromString(localState.provideNetworkMode) ?: ProvideNetworkMode.WIFI
        val provideMode = if (provideControlMode == ProvideControlMode.ALWAYS) Sdk.ProvideModePublic else localState.provideMode
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
        device?.defaultLocation = defaultLocation
        device?.canShowRatingDialog = canShowRatingDialog
        device?.provideControlMode = ProvideControlMode.toString(provideControlMode)
        device?.vpnInterfaceWhileOffline = vpnInterfaceWhileOffline
        device?.canRefer = canRefer
        device?.allowForeground = allowForeground
        device?.provideNetworkMode = ProvideNetworkMode.toString(provideNetworkMode)
        device?.canPromptIntroFunnel = canPromptIntroFunnel

        /**
         * set initial jwt on device creation
         */
        val byJwt = localState.parseByJwt()
        jwtManager.updateJwt(byJwt)

        device?.addJwtRefreshListener { jwt ->

            val localState = networkSpace.asyncLocalState.localState
            val byJwt = localState.parseByJwt()

            jwtManager.updateJwt(byJwt)
        }
    }

    fun clearDevice() {
        device?.close()
        device = null
    }

    private fun getAppVersion(): String {
        return "${BuildConfig.VERSION_NAME}-${BuildConfig.VERSION_CODE}"
    }
}