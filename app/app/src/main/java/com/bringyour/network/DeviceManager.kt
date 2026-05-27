package com.bringyour.network

import android.util.Log
import com.bringyour.network.ui.shared.models.ProvideControlMode
import com.bringyour.network.ui.shared.models.ProvideNetworkMode
import com.bringyour.sdk.DeviceLocal
import com.bringyour.sdk.NetworkSpace
import com.bringyour.sdk.PerformanceProfile
import com.bringyour.sdk.Sdk
import com.bringyour.sdk.Sub
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceManager @Inject constructor(
    private val jwtManager: JwtManager
) {

    private val deviceLock = Any()

    @Volatile var device: DeviceLocal? = null
        private set

    private var jwtRefreshSub: Sub? = null

    val networkSpace get() = device?.networkSpace
    val asyncLocalState get() = device?.networkSpace?.asyncLocalState

    var routeLocal: Boolean
        get() = synchronized(deviceLock) { device?.routeLocal ?: true }
        set(it) = synchronized(deviceLock) {
            asyncLocalState?.localState?.routeLocal = it
            device?.routeLocal = it
        }

    var canShowRatingDialog: Boolean
        get() = synchronized(deviceLock) { device?.canShowRatingDialog ?: false }
        set(it) = synchronized(deviceLock) {
            asyncLocalState?.localState?.canShowRatingDialog = it
            device?.canShowRatingDialog = it
        }

    var canRefer: Boolean
        get() = synchronized(deviceLock) { device?.canRefer ?: false }
        set(it) = synchronized(deviceLock) {
            asyncLocalState?.localState?.canRefer = it
            device?.canRefer = it
        }

    var canPromptIntroFunnel: Boolean
        get() = synchronized(deviceLock) { device?.canPromptIntroFunnel ?: true }
        set(it) = synchronized(deviceLock) {
            asyncLocalState?.localState?.setIntroFunnelLastPrompted()
            device?.canPromptIntroFunnel = it
        }

    var provideControlMode: ProvideControlMode
        get() = synchronized(deviceLock) { device?.provideControlMode?.let { ProvideControlMode.fromString(it) } ?: ProvideControlMode.AUTO }
        set(it) = synchronized(deviceLock) {
            asyncLocalState?.localState?.provideControlMode = ProvideControlMode.toString(it)
            device?.provideControlMode = ProvideControlMode.toString(it)
        }

    var provideNetworkMode: ProvideNetworkMode
        get() = synchronized(deviceLock) { device?.provideNetworkMode?.let { ProvideNetworkMode.fromString(it) } ?: ProvideNetworkMode.WIFI }
        set(it) = synchronized(deviceLock) {
            asyncLocalState?.localState?.provideNetworkMode = ProvideNetworkMode.toString(it)
            device?.provideNetworkMode = ProvideNetworkMode.toString(it)
        }

    var allowForeground: Boolean
        get() = synchronized(deviceLock) { device?.allowForeground ?: false }
        set(it) = synchronized(deviceLock) {
            asyncLocalState?.localState?.allowForeground = it
            device?.allowForeground = it
        }

    var vpnInterfaceWhileOffline: Boolean
        get() = synchronized(deviceLock) { device?.vpnInterfaceWhileOffline ?: false }
        set(it) = synchronized(deviceLock) {
            asyncLocalState?.localState?.vpnInterfaceWhileOffline = it
            device?.vpnInterfaceWhileOffline = it
        }

    var performanceProfile: PerformanceProfile?
        get() = synchronized(deviceLock) { device?.performanceProfile }
        set(it) = synchronized(deviceLock) {
            asyncLocalState?.localState?.performanceProfile = it
            device?.performanceProfile = it
        }

    fun initDevice(
        networkSpace: NetworkSpace?,
        byClientJwt: String,
        deviceDescription: String,
        deviceSpec: String
    ) {
        if (networkSpace == null) {
            synchronized(deviceLock) {
                jwtRefreshSub?.close()
                jwtRefreshSub = null
                device?.close()
                device = null
            }
            return
        }
        val localState = networkSpace.asyncLocalState.localState ?: return
        val instanceId = localState.instanceId ?: return
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
        val performanceProfile = localState.performanceProfile

        val newDevice = Sdk.newDeviceLocalWithDefaults(
            networkSpace,
            byClientJwt,
            deviceDescription,
            deviceSpec,
            getAppVersion(),
            instanceId,
            false
        ) ?: return

        synchronized(deviceLock) {
            jwtRefreshSub?.close()
            jwtRefreshSub = null
            device?.close()
            device = newDevice

            localState.provideSecretKeys?.let {
                newDevice.loadProvideSecretKeys(it)
            } ?: run {
                var sub: Sub? = null
                sub = newDevice.addProvideSecretKeysListener {
                    localState.provideSecretKeys = it
                    sub?.close()
                }
                newDevice.initProvideSecretKeys()
            }

            newDevice.providePaused = true
            newDevice.routeLocal = routeLocal
            newDevice.provideMode = provideMode
            newDevice.connectLocation = connectLocation
            newDevice.defaultLocation = defaultLocation
            newDevice.canShowRatingDialog = canShowRatingDialog
            newDevice.provideControlMode = ProvideControlMode.toString(provideControlMode)
            newDevice.vpnInterfaceWhileOffline = vpnInterfaceWhileOffline
            newDevice.canRefer = canRefer
            newDevice.allowForeground = allowForeground
            newDevice.provideNetworkMode = ProvideNetworkMode.toString(provideNetworkMode)
            newDevice.canPromptIntroFunnel = canPromptIntroFunnel
            newDevice.performanceProfile = performanceProfile

            /**
             * set initial jwt on device creation
             */
            val byJwt = localState.parseByJwt()
            jwtManager.updateJwt(byJwt)

            jwtRefreshSub = newDevice.addJwtRefreshListener { jwt ->

                val localState = newDevice.networkSpace?.asyncLocalState?.localState ?: return@addJwtRefreshListener
                val byJwt = localState.parseByJwt()

                jwtManager.updateJwt(byJwt)
            }
        }
    }

    fun clearDevice() {
        synchronized(deviceLock) {
            jwtRefreshSub?.close()
            jwtRefreshSub = null
            device?.close()
            device = null
        }
    }

    private fun getAppVersion(): String {
        return "${BuildConfig.VERSION_NAME}-${BuildConfig.VERSION_CODE}"
    }
}