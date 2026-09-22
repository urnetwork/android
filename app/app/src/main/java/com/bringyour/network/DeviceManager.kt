package com.bringyour.network

import com.bringyour.network.ui.shared.models.ProvideControlMode
import com.bringyour.network.ui.shared.models.ProvideNetworkMode
import com.bringyour.sdk.BlockActionOverrideList
import com.bringyour.sdk.DeviceLocal
import com.bringyour.sdk.DnsResolverSettings
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
    private val jwtManager: JwtManager,
    private val networkSpaceManagerProvider: NetworkSpaceManagerProvider,
) {

    companion object {
        // Per-device target passed at construction: DNS 2 parts, one shared
        // 13-part transfer/topology root with overlapping client/provider/NAT
        // children, and 5 parts for platform carriers. Process-level
        // Sdk.setMemoryLimit (MainApplication) separately sizes the shared
        // message pools, carrier root, and Go soft limit.
        //
        // Ordinary Android keeps 28 MiB. The explicit debug iOS proxy follows
        // apple/app/extension/TunnelMemoryBounds.swift: 20 MiB admission, with
        // the separate observed runtime constrained to a hard 24-MiB cap.
        internal fun deviceMemoryTargetByteCount(profile: String): Long =
            (if (profile == MainApplication.IOS_MEMORY_AUDIT_PROFILE) 20L else 28L) * 1024 * 1024
        val DEVICE_MEMORY_TARGET_BYTE_COUNT: Long
            get() = deviceMemoryTargetByteCount(MainApplication.MEMORY_PROFILE_NAME)
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

    val networkSpace get() = synchronized(deviceLock) {
        device?.networkSpace ?: networkSpaceManagerProvider.getNetworkSpace()
    }
    val asyncLocalState get() = networkSpace?.asyncLocalState

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

    var blockerEnabled: Boolean
        get() = synchronized(deviceLock) {
            SplitRulePersistencePolicy.resolveEffectiveBlocker(
                liveBlocker = device?.blockerEnabled,
                storedBlocker = asyncLocalState?.localState?.blockerEnabled,
            )
        }
        set(it) = synchronized(deviceLock) {
            val plan = SplitRulePersistencePolicy.planWrite(isDeviceConnected = device != null)
            if (plan.applyLive) {
                device?.blockerEnabled = it
            }
            if (plan.persistToStorage) {
                asyncLocalState?.localState?.let { localState ->
                    runCatching { localState.blockerEnabled = it }
                }
            }
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

    /**
     * The effective overrides: the live device's when there is a device (an
     * empty list is an answer, not a reason to fall back), else the persisted
     * list (signed out, or before the device is created)
     */
    val blockActionOverrides: BlockActionOverrideList?
        get() = synchronized(deviceLock) {
            SplitRulePersistencePolicy.resolveEffective(
                livePresent = device != null,
                live = device?.blockActionOverrides,
                stored = asyncLocalState?.localState?.blockActionOverrides,
            )
        }

    /**
     * Applies an overrides edit to exactly one place. A live device persists
     * its own overrides on the serial local state queue, so a second,
     * synchronous write from here races that queue: a read-modify-write over
     * a file the queue has not flushed yet duplicates or resurrects rules.
     * Without a device the persisted list is edited directly. Holding
     * [deviceLock] serializes an edit with [initDevice] handing the persisted
     * list to a new device, so an edit made while connecting is not lost.
     *
     * @return false when there was nowhere to apply the edit
     */
    fun editBlockActionOverrides(
        live: (DeviceLocal) -> Unit,
        persisted: (BlockActionOverrideList?) -> BlockActionOverrideList?,
    ): Boolean {
        synchronized(deviceLock) {
            val plan = SplitRulePersistencePolicy.planWrite(isDeviceConnected = device != null)
            when {
                plan.applyLive -> {
                    val liveDevice = device ?: return false
                    live(liveDevice)
                    return true
                }
                else -> {
                    val localState = asyncLocalState?.localState ?: return false
                    val next = persisted(localState.blockActionOverrides) ?: return true
                    return runCatching { localState.blockActionOverrides = next }.isSuccess
                }
            }
        }
    }

    /**
     * The effective dns resolver settings: the live device's, else the
     * persisted settings. The device reports none while its dns upgrade mux is
     * disabled, and then the persisted settings show through
     */
    val dnsResolverSettings: DnsResolverSettings?
        get() = synchronized(deviceLock) {
            SplitRulePersistencePolicy.resolveEffective(
                livePresent = device?.dnsResolverSettings != null,
                live = device?.dnsResolverSettings,
                stored = asyncLocalState?.localState?.dnsResolverSettings,
            )
        }

    /**
     * Applies dns resolver settings to exactly one place; see
     * [editBlockActionOverrides]. The device persists the settings only when
     * it accepts them, and with its dns upgrade mux disabled it accepts none,
     * so in that case they are persisted here for the next device.
     *
     * @return false when there was nowhere to apply the settings
     */
    fun applyDnsResolverSettings(settings: DnsResolverSettings): Boolean {
        synchronized(deviceLock) {
            val plan = SplitRulePersistencePolicy.planWrite(isDeviceConnected = device != null)
            when {
                plan.applyLive -> {
                    val liveDevice = device ?: return false
                    liveDevice.dnsResolverSettings = settings
                    if (liveDevice.dnsResolverSettings != null) {
                        return true
                    }
                    // device declined (e.g. dns upgrade mux disabled) — fall
                    // through to persist for the next device
                }
                else -> {}
            }
            val localState = asyncLocalState?.localState ?: return false
            return runCatching { localState.dnsResolverSettings = settings }.isSuccess
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
                        // Per-device DNS 2 + shared transfer/topology 13 +
                        // platform carriers 5 target; process pools and the
                        // shared carrier root are sized by setMemoryLimit.
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
                    newDevice.blockerEnabled = localState.blockerEnabled
                    // read under the lock, not with the other snapshots above:
                    // an edit made without a device (editBlockActionOverrides,
                    // applyDnsResolverSettings) can land while the device is
                    // being created, and a stale snapshot applied here would
                    // be persisted by the device over that edit. Re-applying
                    // through the setters also dedupes by override id and
                    // installs the resolver ignore hosts, which the sdk's
                    // creation-time load does not
                    localState.blockActionOverrides?.let { newDevice.blockActionOverrides = it }
                    localState.dnsResolverSettings?.let { newDevice.dnsResolverSettings = it }

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
                // Per-device DNS 2 + shared transfer/topology 13 + platform
                // carriers 5 target; process pools and the shared carrier root
                // are sized by setMemoryLimit.
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
        // block action overrides and dns resolver settings are deliberately
        // absent: the device persists both on its own serial local state queue,
        // and a synchronous write from a change listener races that queue
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
