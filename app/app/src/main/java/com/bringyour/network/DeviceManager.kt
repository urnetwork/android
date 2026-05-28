package com.bringyour.network

import com.bringyour.network.ui.shared.models.ProvideControlMode
import com.bringyour.network.ui.shared.models.ProvideNetworkMode
import com.bringyour.sdk.DeviceLocal
import com.bringyour.sdk.LocalState
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
    private var provideSecretKeysSub: Sub? = null
    private val localStateChangeSubs = mutableListOf<Sub>()

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
    ): Boolean {
        if (networkSpace == null) {
            clearDevice()
            return false
        }
        val localState = networkSpace.asyncLocalState.localState ?: run {
            clearDevice()
            return false
        }
        val instanceId = localState.instanceId ?: run {
            clearDevice()
            return false
        }
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

        val provideSecretKeys = localState.provideSecretKeys
        val keyMaterial = localState.deviceLocalKeyMaterial
        val newDevice = if (keyMaterial == null) {
            createDeviceLocalWithDefaults(
                networkSpace = networkSpace,
                byClientJwt = byClientJwt,
                deviceDescription = deviceDescription,
                deviceSpec = deviceSpec,
                instanceId = instanceId
            )
        } else {
            runCatching {
                Sdk.newDeviceLocalWithKeyMaterial(
                    networkSpace,
                    byClientJwt,
                    deviceDescription,
                    deviceSpec,
                    getAppVersion(),
                    instanceId,
                    false,
                    keyMaterial
                )
            }.getOrNull() ?: run {
                localState.deviceLocalKeyMaterial = null
                createDeviceLocalWithDefaults(
                    networkSpace = networkSpace,
                    byClientJwt = byClientJwt,
                    deviceDescription = deviceDescription,
                    deviceSpec = deviceSpec,
                    instanceId = instanceId
                )
            }
        } ?: run {
            clearDevice()
            return false
        }

        synchronized(deviceLock) {
            closeDeviceSubscriptionsLocked()
            device?.close()
            device = newDevice

            persistDeviceLocalKeyMaterial(localState, newDevice)

            provideSecretKeysSub = newDevice.addProvideSecretKeysListener {
                runCatching {
                    localState.provideSecretKeys = it
                }
                persistDeviceLocalKeyMaterial(localState, newDevice)
            }

            provideSecretKeys?.let {
                newDevice.loadProvideSecretKeys(it)
            } ?: run {
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

            addLocalStateChangeSubscriptionsLocked(localState, newDevice)

            /**
             * set initial jwt on device creation
             */
            runCatching {
                localState.parseByJwt()
            }.getOrNull()?.let { byJwt ->
                jwtManager.updateJwt(byJwt)
            } ?: jwtManager.clearJwt()

            jwtRefreshSub = newDevice.addJwtRefreshListener { _ ->

                val localState = newDevice.networkSpace?.asyncLocalState?.localState ?: return@addJwtRefreshListener
                runCatching {
                    localState.parseByJwt()
                }.getOrNull()?.let { byJwt ->
                    jwtManager.updateJwt(byJwt)
                } ?: jwtManager.clearJwt()
            }
        }
        return true
    }

    private fun createDeviceLocalWithDefaults(
        networkSpace: NetworkSpace,
        byClientJwt: String,
        deviceDescription: String,
        deviceSpec: String,
        instanceId: com.bringyour.sdk.Id
    ): DeviceLocal? {
        return runCatching {
            Sdk.newDeviceLocalWithDefaults(
                networkSpace,
                byClientJwt,
                deviceDescription,
                deviceSpec,
                getAppVersion(),
                instanceId,
                false
            )
        }.getOrNull()
    }

    private fun addLocalStateChangeSubscriptionsLocked(localState: LocalState, device: DeviceLocal) {
        localStateChangeSubs.add(device.addConnectLocationChangeListener { location ->
            runCatching {
                localState.connectLocation = location
            }
        })
        localStateChangeSubs.add(device.addCanShowRatingDialogChangeListener { canShowRatingDialog ->
            runCatching {
                localState.canShowRatingDialog = canShowRatingDialog
            }
        })
        localStateChangeSubs.add(device.addCanPromptIntroFunnelChangeListener { canPromptIntroFunnel ->
            runCatching {
                localState.canPromptIntroFunnel = canPromptIntroFunnel
            }
        })
        localStateChangeSubs.add(device.addAllowForegroundChangeListener { allowForeground ->
            runCatching {
                localState.allowForeground = allowForeground
            }
        })
        localStateChangeSubs.add(device.addCanReferChangeListener { canRefer ->
            runCatching {
                localState.canRefer = canRefer
            }
        })
        localStateChangeSubs.add(device.addProvideModeChangeListener { provideMode ->
            runCatching {
                localState.provideMode = provideMode
            }
        })
        localStateChangeSubs.add(device.addProvideControlModeChangeListener { provideControlMode ->
            if (!provideControlMode.isNullOrEmpty()) {
                runCatching {
                    localState.provideControlMode = provideControlMode
                }
            }
        })
        localStateChangeSubs.add(device.addPerformanceProfileChangeListener { performanceProfile ->
            runCatching {
                localState.performanceProfile = performanceProfile
            }
        })
        localStateChangeSubs.add(device.addRouteLocalChangeListener { routeLocal ->
            runCatching {
                localState.routeLocal = routeLocal
            }
        })
        localStateChangeSubs.add(device.addVpnInterfaceWhileOfflineChangeListener { vpnInterfaceWhileOffline ->
            runCatching {
                localState.vpnInterfaceWhileOffline = vpnInterfaceWhileOffline
            }
        })
        localStateChangeSubs.add(device.addDefaultLocationChangeListener { location ->
            runCatching {
                localState.defaultLocation = location
            }
        })
        localStateChangeSubs.add(device.addProvideNetworkModeChangeListener { provideNetworkMode ->
            if (!provideNetworkMode.isNullOrEmpty()) {
                runCatching {
                    localState.provideNetworkMode = provideNetworkMode
                }
            }
        })
    }

    fun clearDevice() {
        synchronized(deviceLock) {
            closeDeviceSubscriptionsLocked()
            device?.close()
            device = null
            jwtManager.clearJwt()
        }
    }

    private fun closeDeviceSubscriptionsLocked() {
        jwtRefreshSub?.close()
        jwtRefreshSub = null
        provideSecretKeysSub?.close()
        provideSecretKeysSub = null
        localStateChangeSubs.forEach { it.close() }
        localStateChangeSubs.clear()
    }

    private fun persistDeviceLocalKeyMaterial(localState: LocalState, device: DeviceLocal) {
        runCatching {
            localState.deviceLocalKeyMaterial = device.keyMaterial
        }
    }

    private fun getAppVersion(): String {
        return "${BuildConfig.VERSION_NAME}-${BuildConfig.VERSION_CODE}"
    }
}
