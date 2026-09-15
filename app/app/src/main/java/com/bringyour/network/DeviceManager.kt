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

enum class DeviceInitFailure {
    MISSING_NETWORK_SPACE,
    MISSING_LOCAL_STATE,
    MISSING_INSTANCE_ID,
    DEVICE_LOCAL_CREATION_FAILED,
    DEVICE_LOCAL_CONFIGURATION_FAILED,
}

sealed class DeviceInitResult {
    object Ready : DeviceInitResult()
    data class Failed(val failure: DeviceInitFailure) : DeviceInitResult()
}

internal sealed class DeviceCreationResult<out T> {
    data class Created<T>(val value: T) : DeviceCreationResult<T>()
    data class Failed(val failure: DeviceInitFailure) : DeviceCreationResult<Nothing>()
}

internal sealed class DeviceConfigurationResult<out T> {
    data class Configured<T>(val value: T) : DeviceConfigurationResult<T>()
    data class Failed(val failure: DeviceInitFailure) : DeviceConfigurationResult<Nothing>()
}

/** Tries a stored identity once, then clears it before one default-key fallback. */
internal fun <T, K> createDeviceWithStoredKey(
    storedKey: K?,
    clearStoredKey: () -> Unit,
    create: (K?) -> T?,
): DeviceCreationResult<T> {
    if (storedKey == null) {
        return runCatching { create(null) }.getOrNull()?.let { DeviceCreationResult.Created(it) }
            ?: DeviceCreationResult.Failed(DeviceInitFailure.DEVICE_LOCAL_CREATION_FAILED)
    }

    runCatching { create(storedKey) }.getOrNull()?.let {
        return DeviceCreationResult.Created(it)
    }
    if (runCatching(clearStoredKey).isFailure) {
        return DeviceCreationResult.Failed(DeviceInitFailure.DEVICE_LOCAL_CONFIGURATION_FAILED)
    }
    return runCatching { create(null) }.getOrNull()?.let { DeviceCreationResult.Created(it) }
        ?: DeviceCreationResult.Failed(DeviceInitFailure.DEVICE_LOCAL_CREATION_FAILED)
}

/** Converts configuration exceptions into one typed boundary and always closes partial state. */
internal fun <T> configureCreatedDevice(
    configure: () -> T,
    closePartialState: () -> Unit,
): DeviceConfigurationResult<T> = try {
    DeviceConfigurationResult.Configured(configure())
} catch (_: Throwable) {
    runCatching(closePartialState)
    DeviceConfigurationResult.Failed(DeviceInitFailure.DEVICE_LOCAL_CONFIGURATION_FAILED)
}

@Singleton
class DeviceManager @Inject constructor(
    private val jwtManager: JwtManager
) {

    companion object {
        // per-device memory target passed where the device is created: split
        // dns 2 : client 14 : provider 4 by ratio inside the sdk, with the
        // provider share backing the client pair while providing is off. The
        // process-level Sdk.setMemoryLimit (MainApplication) sizes the
        // shared message pools and go soft limit separately.
        //
        // 28 MiB, up from the 24 MiB iOS stand-in, measured on the peer rig
        // with the idle reclaimer working: client quiet p50/p95 20.8/21.0 and
        // 21.4/23.4 MiB across two blocks at this target, unchanged from
        // 20.8/21.1 at 24 MiB. The sdk sizes its mobile caps from this value
        // (7/6 of the 24-MiB calibration) and keeps its reclaim floor at the
        // 24-MiB steady target regardless.
        const val DEVICE_MEMORY_TARGET_BYTE_COUNT = 28L * 1024 * 1024
    }

    private val deviceLock = Any()

    // set by the application; fired when the sdk detects the stored auth is
    // no longer valid on the server (e.g. the client was removed) and has
    // cleared the local auth state. The app must log out and return to the
    // login flow.
    var onAuthLogout: (() -> Unit)? = null

    @Volatile var device: DeviceLocal? = null
        private set

    private var jwtRefreshSub: Sub? = null

    /** The signed-in jwt as it changes (network id and name); null when signed out. */
    val jwtFlow: kotlinx.coroutines.flow.StateFlow<com.bringyour.sdk.ByJwt?>
        get() = jwtManager.jwtFlow
    private var authLogoutSub: Sub? = null
    private var provideSecretKeysSub: Sub? = null
    private val localStateChangeSubs = mutableListOf<Sub>()

    // Device lifecycle listeners: view models wire their SDK subscriptions per
    // device, and the device is (re)created asynchronously (login, network
    // change) — an init-time `device?.let` silently wires NOTHING when the view
    // model is created first. Sequencing prevents a delayed notification for a
    // retired device from arriving after its replacement.
    private val deviceChanges = SequencedValueListeners<DeviceLocal?>(null)

    fun addDeviceChangeListener(listener: (DeviceLocal?) -> Unit): () -> Unit {
        return deviceChanges.add(listener)
    }

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
            // persist the flag itself: the legacy "last prompted" timestamp re-arms
            // the funnel after five days, but onboarding is a one-shot after sign-up
            asyncLocalState?.localState?.canPromptIntroFunnel = it
            device?.canPromptIntroFunnel = it
        }

    var provideControlMode: ProvideControlMode
        get() = synchronized(deviceLock) { device?.provideControlMode?.let { ProvideControlMode.fromString(it) } ?: ProvideControlMode.NEVER }
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
        get() = synchronized(deviceLock) {
            device?.performanceProfile ?: asyncLocalState?.localState?.performanceProfile
        }
        set(it) = synchronized(deviceLock) {
            val localState = asyncLocalState?.localState
            val liveDevice = device
            val plan = performanceProfileWritePlan(
                stored = localState?.let { state ->
                    performanceProfileSnapshot(state.performanceProfile)
                },
                live = liveDevice?.let { currentDevice ->
                    performanceProfileSnapshot(currentDevice.performanceProfile)
                },
                target = performanceProfileSnapshot(it),
            )
            if (plan.persist) {
                localState?.performanceProfile = it
            }
            if (plan.applyLive) {
                liveDevice?.performanceProfile = it
            }
        }

    fun initDevice(
        networkSpace: NetworkSpace?,
        byClientJwt: String,
        deviceDescription: String,
        deviceSpec: String
    ): DeviceInitResult {
        if (networkSpace == null) {
            clearDevice()
            return DeviceInitResult.Failed(DeviceInitFailure.MISSING_NETWORK_SPACE)
        }
        val localState = networkSpace.asyncLocalState.localState ?: run {
            clearDevice()
            return DeviceInitResult.Failed(DeviceInitFailure.MISSING_LOCAL_STATE)
        }
        val instanceId = localState.instanceId ?: run {
            clearDevice()
            return DeviceInitResult.Failed(DeviceInitFailure.MISSING_INSTANCE_ID)
        }
        val routeLocal = localState.routeLocal
        val connectLocation = localState.connectLocation
        val defaultLocation = localState.defaultLocation // when user selects location, disconnects, restarts app, we want to persist the location
        val canShowRatingDialog = localState.canShowRatingDialog
        val canPromptIntroFunnel = localState.canPromptIntroFunnel
        val provideControlMode = ProvideControlMode.fromString(localState.provideControlMode) ?: ProvideControlMode.NEVER
        val provideNetworkMode = ProvideNetworkMode.fromString(localState.provideNetworkMode) ?: ProvideNetworkMode.WIFI
        val provideMode = when (provideControlMode) {
            ProvideControlMode.ALWAYS -> Sdk.ProvideModePublic
            // the private provider: always on, but only for same-network peers
            ProvideControlMode.NETWORK -> Sdk.ProvideModeNetwork
            else -> localState.provideMode
        }
        val vpnInterfaceWhileOffline = localState.vpnInterfaceWhileOffline
        val canRefer = localState.canRefer
        val allowForeground = localState.allowForeground
        val performanceProfile = localState.performanceProfile

        val provideSecretKeys = localState.provideSecretKeys
        val keyMaterial = localState.deviceLocalKeyMaterial
        val creation = createDeviceWithStoredKey(
            storedKey = keyMaterial,
            clearStoredKey = { localState.deviceLocalKeyMaterial = null },
            create = { retainedKeyMaterial ->
                if (retainedKeyMaterial == null) {
                    createDeviceLocalWithDefaults(
                        networkSpace = networkSpace,
                        byClientJwt = byClientJwt,
                        deviceDescription = deviceDescription,
                        deviceSpec = deviceSpec,
                        instanceId = instanceId,
                    )
                } else {
                    Sdk.newDeviceLocalWithMemoryTarget(
                        networkSpace,
                        byClientJwt,
                        deviceDescription,
                        deviceSpec,
                        getAppVersion(),
                        instanceId,
                        false,
                        retainedKeyMaterial,
                        // per-device memory target (split dns 2 : client 14 :
                        // provider 4); the process pools are sized by setMemoryLimit
                        DEVICE_MEMORY_TARGET_BYTE_COUNT,
                    )
                }
            },
        )
        val newDevice = when (creation) {
            is DeviceCreationResult.Created -> creation.value
            is DeviceCreationResult.Failed -> {
                clearDevice()
                return DeviceInitResult.Failed(creation.failure)
            }
        }

        val configuration = configureCreatedDevice(
            configure = {
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

                        val localState = newDevice.networkSpace?.asyncLocalState?.localState
                            ?: return@addJwtRefreshListener
                        runCatching {
                            localState.parseByJwt()
                        }.getOrNull()?.let { byJwt ->
                            jwtManager.updateJwt(byJwt)
                        } ?: jwtManager.clearJwt()
                    }

                    authLogoutSub = newDevice.addAuthLogoutListener {
                        onAuthLogout?.invoke()
                    }
                    deviceChanges.prepareUpdate(newDevice)
                }
            },
            closePartialState = {
                if (device === newDevice) {
                    clearDevice()
                } else {
                    newDevice.close()
                }
            },
        )
        val notifyDeviceChanged = when (configuration) {
            is DeviceConfigurationResult.Configured -> configuration.value
            is DeviceConfigurationResult.Failed -> return DeviceInitResult.Failed(
                configuration.failure,
            )
        }
        notifyDeviceChanged()
        return DeviceInitResult.Ready
    }

    private fun createDeviceLocalWithDefaults(
        networkSpace: NetworkSpace,
        byClientJwt: String,
        deviceDescription: String,
        deviceSpec: String,
        instanceId: com.bringyour.sdk.Id
    ): DeviceLocal? {
        return runCatching {
            Sdk.newDeviceLocalWithMemoryTarget(
                networkSpace,
                byClientJwt,
                deviceDescription,
                deviceSpec,
                getAppVersion(),
                instanceId,
                false,
                null,
                // per-device memory target (split dns 2 : client 14 :
                // provider 4); the process pools are sized by setMemoryLimit
                DEVICE_MEMORY_TARGET_BYTE_COUNT
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
        val notifyDeviceChanged = synchronized(deviceLock) {
            closeDeviceSubscriptionsLocked()
            device?.close()
            device = null
            jwtManager.clearJwt()
            deviceChanges.prepareUpdate(null)
        }
        notifyDeviceChanged()
    }

    private fun closeDeviceSubscriptionsLocked() {
        jwtRefreshSub?.close()
        jwtRefreshSub = null
        authLogoutSub?.close()
        authLogoutSub = null
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
