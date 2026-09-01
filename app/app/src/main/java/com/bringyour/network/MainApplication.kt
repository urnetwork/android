package com.bringyour.network

import android.app.ActivityManager
import android.app.Application
import android.app.ForegroundServiceStartNotAllowedException
import android.content.Intent
import android.content.pm.PackageManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.VpnService
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.WorkManager
import com.bringyour.network.location.MockLocationController
import com.bringyour.network.ui.shared.models.ProvideNetworkMode
import com.bringyour.sdk.DeviceLocal
import com.bringyour.sdk.LocalState
import com.bringyour.sdk.LoginViewController
import com.bringyour.sdk.NetworkSpace
import com.bringyour.sdk.Sdk
import com.bringyour.sdk.Sub
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import java.lang.ref.WeakReference
import javax.inject.Inject
import kotlin.math.min


@HiltAndroidApp
class MainApplication : Application() {
    private companion object {
        const val VPN_STATE_BURST_COALESCE_MILLIS = 20L
        // Match the iOS packet-tunnel process budget so Android physical runs
        // expose the same SDK pressure/failure boundary. DeviceManager already
        // passes the matching iOS per-device steady target (24 MiB).
        const val SDK_PROCESS_MEMORY_LIMIT_MIB = 32L
    }

    // The initial device name defaults to the human model name — e.g.
    // "Samsung Galaxy S24 Ultra", "Pixel 8 Pro" — never "New device". Users can
    // still rename their device in settings (a separate, server-side
    // device_name); this is only the default a brand-new device registers with.
    val deviceDescription: String get() = deviceModelName

    // concise, human-readable spec shown in the peers list: "<os> <make model>",
    // e.g. "17.1 Pixel 8 Pro", "16 Samsung Galaxy S24 Ultra". Reuses the same
    // model name as `deviceDescription` so the name and spec agree; the full
    // Build.FINGERPRINT was unreadably long in the ui.
    val deviceSpec: String get() = "$osVersion $deviceModelName"

    // the retail model name, brand-prefixed unless it is already
    // self-identifying: the marketing name comes from the bundled catalog
    // (`DeviceNames`, e.g. "Galaxy S24 Ultra" for "SM-S928U1"); a Samsung gets
    // the "Samsung" prefix, while Google's Pixel names ("Pixel 8 Pro") and any
    // marketing name that already leads with the manufacturer stand alone.
    private val deviceModelName: String get() {
        val model = DeviceNames.marketingName(this)
        return if (
            model.startsWith(Build.MANUFACTURER, ignoreCase = true) ||
            Build.MANUFACTURER.equals("Google", ignoreCase = true)
        ) {
            model
        } else {
            val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
            "$manufacturer $model"
        }
    }

    // the exact os version, always NUMERIC ("17", "16.1"), never a dev
    // codename. RELEASE is major-only on modern android ("16"); older builds
    // carried the point ("8.1.0"). From android 16 (api 36) the minor os
    // revision is exposed through the full sdk version — append it so the spec
    // reads "16.1" like the ios side's point versions.
    //
    // NOTE: use RELEASE, not RELEASE_OR_CODENAME. On a preview/beta build the
    // latter returns the dev codename (e.g. "CinnamonBun" on the Android 17
    // preview), producing nonsense like "CinnamonBun.1"; RELEASE stays numeric
    // ("17") on those builds. If RELEASE is ever non-numeric (very early dev
    // images), fall back to the numeric version derived from the api level.
    private val osVersion: String get() {
        var release = Build.VERSION.RELEASE
        if (release.isEmpty() || !release[0].isDigit()) {
            release = androidMajorVersionForSdk(Build.VERSION.SDK_INT) ?: release
        }
        if (36 <= Build.VERSION.SDK_INT && !release.contains('.')) {
            val minor = Build.getMinorSdkVersion(Build.VERSION.SDK_INT_FULL)
            if (0 < minor) {
                return "$release.$minor"
            }
        }
        return release
    }

    // numeric android major version for an api level, used only when
    // Build.VERSION.RELEASE is unavailable/non-numeric on a preview image.
    private fun androidMajorVersionForSdk(sdkInt: Int): String? = when {
        sdkInt >= 36 -> (sdkInt - 20).toString() // 36->16, 37->17, forward-compatible
        sdkInt == 35 -> "15"
        sdkInt == 34 -> "14"
        sdkInt == 33 -> "13"
        sdkInt == 32 || sdkInt == 31 -> "12"
        sdkInt == 30 -> "11"
        sdkInt == 29 -> "10"
        sdkInt == 28 -> "9"
        sdkInt >= 26 -> "8"
        else -> null
    }

    var networkSpaceSub: Sub? = null

//    var byDevice: BringYourDevice? = null
    var deviceProvideSub: Sub? = null
    var deviceProvidePausedSub: Sub? = null

    var deviceProvideNetworkSub: Sub? = null
//    var deviceOfflineSub: Sub? = null
    var deviceConnectSub: Sub? = null
    var deviceRouteLocalSub: Sub? = null
//    var router: Router? = null
    var tunnelChangeSub: Sub? = null
    var contractStatusChangeSub: Sub? = null

    var networkCallback: ConnectivityManager.NetworkCallback? = null
    var offlineCallback: ConnectivityManager.NetworkCallback? = null
    var defaultNetworkCallback: ConnectivityManager.NetworkCallback? = null
    var powerSaveReceiver: BroadcastReceiver? = null
    var thermalStatusListener: PowerManager.OnThermalStatusChangedListener? = null

    // main-looper confined; latest thermal status composed into
    // setPerformanceDegraded alongside battery saver (see
    // updatePerformanceDegraded / addThermalStatusListener)
    private var thermalDegraded = false

    var loginVc: LoginViewController? = null

    @Inject
    lateinit var deviceManager: DeviceManager

    @Inject
    lateinit var networkSpaceManagerProvider: NetworkSpaceManagerProvider

    @Inject
    lateinit var mockLocationController: MockLocationController

    var vpnRequestStart: Boolean = false
        private set

    @Volatile
    private var vpnStartPending: Boolean = false
    // SDK state changes arrive in bursts for one logical connect transition.
    // Coalesce them on the main looper and make the service start idempotent.
    private var vpnServiceUpdatePosted: Boolean = false
    private var vpnServiceUpdateGeneration: Long = 0
    private var activeVpnForeground: Boolean? = null
    @Volatile
    private var systemAlwaysOnVpn: Boolean = false
    private var alwaysOnConnectRequestedDevice: DeviceLocal? = null

    var vpnRequestStartListener: (() -> Unit)? = null

    // FIXME remove these bools and just query the device directly
//    private var provideEnabled: Boolean = false
//    private var connectEnabled: Boolean = false

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    val device get() = deviceManager.device
//    val vcManager get() = deviceManager.vcManager
    val api get() = networkSpaceManagerProvider.getNetworkSpace()?.api
    val asyncLocalState get() = networkSpaceManagerProvider.getNetworkSpace()?.asyncLocalState
//    val apiUrl get() = networkSpace?.apiUrl
//    val platformUrl get() = networkSpace?.platformUrl


    var service: WeakReference<MainService>? = null
    private var _serviceActive: Boolean = false
    val serviceActiveMonitor = Object()
    var serviceActive: Boolean
        get() = synchronized(serviceActiveMonitor) {
            return _serviceActive
        }
        set(it) = synchronized(serviceActiveMonitor) {
            _serviceActive = it
            serviceActiveMonitor.notifyAll()
        }


//    override fun onTrimMemory(level: Int) {
//        super.onTrimMemory(level)
//
//        if (TRIM_MEMORY_BACKGROUND <= level) {
//            Sdk.freeMemory()
//        }
//    }

    override fun onLowMemory() {
        super.onLowMemory()

        Sdk.freeMemory()
    }


    override fun onCreate() {
        super.onCreate()

        if (0 < BuildConfig.URNETWORK_MEMORY_PROFILE_RATE_BYTES) {
            // Diagnostic profile AARs are linked with this same startup rate;
            // repeat it here before the workload to make the app/build contract
            // explicit. Production AARs start at zero before libgojni loads.
            Sdk.setMemoryProfileRate(BuildConfig.URNETWORK_MEMORY_PROFILE_RATE_BYTES)
        }

        // One subdirectory per writing process, under a shared root. Android is
        // single-process (no android:process in the manifest), so there is only
        // ever "app" here -- but the sdk prunes per directory (it keeps the 4
        // newest files in whichever one glog is pointed at), so the per-process
        // layout is what stops two writers from deleting each other's history,
        // and it keeps this call identical to the one iOS makes.
        val logRoot = logRootDir(applicationContext.filesDir)
        val processLogDir = File(logRoot, APP_LOG_PROCESS_NAME)

        // Pre-upgrade builds wrote glog files straight into filesDir. Nothing
        // ever prunes or reads that directory again once the root moves, so
        // the old files are both dead storage and unreachable evidence. Run the
        // migration BEFORE pointing glog at the new directory: the sdk's
        // retention pass then treats the migrated files as part of the app's
        // own history and keeps only the newest four of the merged set.
        val migratedLogCount = try {
            migrateLegacyLogFiles(applicationContext.filesDir, processLogDir)
        } catch (e: Exception) {
            Log.e(TAG, "could not migrate pre-upgrade log files: ${e.message}", e)
            0
        }
        if (0 < migratedLogCount) {
            Log.i(TAG, "migrated $migratedLogCount pre-upgrade log files into $processLogDir")
        }

        // gomobile binds Go's `error` return as a checked java exception, and
        // kotlin does not enforce checked exceptions -- so an unguarded call
        // compiles and then propagates out of Application.onCreate as an
        // unhandled crash on EVERY launch. The sdk's own contract is the
        // opposite ("logging must never be what breaks a launch"), but the
        // fallback meant to make the error unreachable cannot do that here: it
        // targets os.TempDir(), which on android resolves to /data/local/tmp
        // (Go's own android case in os.tempDir, since app processes have no
        // TMPDIR set) -- shell-owned and not writable by an app uid. So if
        // <filesDir>/logs cannot be created (storage full, quota, EIO) the
        // fallback fails too and the error really is returned.
        // Catching it turns an unbootable app back into a logging problem:
        // glog keeps whatever destination it already had, and Sdk.getLogDir()
        // keeps naming that destination for the feedback screen's log buttons.
        try {
            Sdk.setLogDirForProcess(logRoot.absolutePath, APP_LOG_PROCESS_NAME)
        } catch (e: Exception) {
            Log.e(TAG, "could not point logging at $logRoot: ${e.message}", e)
        }

        val activityManager = getSystemService(ACTIVITY_SERVICE) as ActivityManager?
        val maxMemoryMib = activityManager?.memoryClass?.toLong() ?: 32
        // Target 3/4 of the app heap, capped to the iOS packet-tunnel budget.
        val sdkMemoryMib = min((3 * maxMemoryMib) / 4, SDK_PROCESS_MEMORY_LIMIT_MIB)
        Sdk.setMemoryLimit(sdkMemoryMib * 1024 * 1024)

        // Nothing removes location test providers when a process dies — not a
        // crash, not force-stop, not uninstall. Start the controller here (not
        // from the feature UI) so a previous process's leftovers are cleared
        // even when the user never opens the provider locations sheet.
        mockLocationController.start()

        networkSpaceManagerProvider.init(filesDir.absolutePath)

        val networkSpaceManager = networkSpaceManagerProvider.getNetworkSpaceManager()

        val key = Sdk.newNetworkSpaceKey(BuildConfig.BRINGYOUR_BUNDLE_HOST_NAME, BuildConfig.BRINGYOUR_BUNDLE_ENV_NAME)
        val bundleNetworkSpaceExists = networkSpaceManager?.getNetworkSpace(key) != null
        val bundleNetworkSpace = networkSpaceManager?.updateNetworkSpace(key) { values ->
            // migrate specific bundled fields to the latest from the build
            values.envSecret = BuildConfig.BRINGYOUR_BUNDLE_ENV_SECRET
            values.bundled = true
            // security settings
            // more security can mean fewer connectivity options and slower connectivity in some regions
            values.netExposeServerIps = BuildConfig.BRINGYOUR_BUNDLE_NET_EXPOSE_SERVER_IPS
            values.netExposeServerHostNames = BuildConfig.BRINGYOUR_BUNDLE_NET_EXPOSE_SERVER_HOST_NAMES
            // server settings
            values.linkHostName = BuildConfig.BRINGYOUR_BUNDLE_LINK_HOST_NAME
            values.migrationHostName = BuildConfig.BRINGYOUR_BUNDLE_MIGRATION_HOST_NAME
            // third party settings
            // TODO sso settings
            values.store = BuildConfig.BRINGYOUR_BUNDLE_STORE
            values.wallet = BuildConfig.BRINGYOUR_BUNDLE_WALLET
            values.ssoGoogle = BuildConfig.BRINGYOUR_BUNDLE_SSO_GOOGLE
        }

        if (!bundleNetworkSpaceExists || networkSpaceManager?.activeNetworkSpace == null) {
            // switch to the bundled network space when first created
            // this is important when migrating from an older bundle to a newer bundle
            networkSpaceManager?.activeNetworkSpace = bundleNetworkSpace
        }

        networkSpaceSub = networkSpaceManager?.addActiveNetworkSpaceChangeListener { networkSpace ->
            Handler(mainLooper).post {
                updateActiveNetworkSpace(networkSpace)

                val intent = Intent(applicationContext, LoginActivity::class.java)
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK.or(Intent.FLAG_ACTIVITY_TASK_ON_HOME))
                startActivity(intent)
            }
        }

        networkSpaceManager?.activeNetworkSpace?.let { updateActiveNetworkSpace(it) }

        Handler(mainLooper).post {
            // Older builds installed a 15-minute periodic worker whose body
            // became empty. Merely leaving it registered still wakes the app
            // indefinitely, so remove the persisted work during migration.
            WorkManager.getInstance(this).cancelUniqueWork("background_update")
        }
    }

    // Retained while installed jobs from older versions can still instantiate
    // BackgroundUpdateWorker before the cancellation above is committed.
    fun backgroundUpdate() {
    }


    private fun updateActiveNetworkSpace(networkSpace: NetworkSpace) {
        stop()

        networkSpaceManagerProvider.setNetworkSpace(networkSpace)

        loginVc = Sdk.newLoginViewController(api)

        asyncLocalState?.localState?.let { localState ->
            val byClientJwt = localState.byClientJwt
            val hasByJwt = !localState.byJwt.isNullOrEmpty()

            if (byClientJwt.isNullOrEmpty()) {
                if (hasByJwt) {
                    // missing client jwt after saving byJwt; clean up partial auth state
                    logoutStaleLocalState(localState)
                    api?.byJwt = null
                }
            } else {
                val hasValidByJwt = runCatching {
                    localState.parseByJwt()
                }.getOrNull() != null

                if (!hasValidByJwt) {
                    // missing one or both of jwt or client jwt
                    // clean up the local state
                    logoutStaleLocalState(localState)
                    api?.byJwt = null
                } else {
                    // the device wraps the api and sets the jwt
                    if (!initDevice(byClientJwt)) {
                        logoutStaleLocalState(localState)
                        api?.byJwt = null
                    }
                }
            }
        }


    }


    private fun addOfflineCallback() {
        removeOfflineCallback()

        val callbackDevice = device
        val availableNetworks = AvailableNetworkTracker<Network>()
        offlineCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (offlineCallback !== this || device !== callbackDevice) {
                    return
                }
                val change = availableNetworks.onAvailable(network)
                Log.i(TAG, "network available device = $network count=${availableNetworks.size}")
                callbackDevice?.offline = false
                if (change.topologyChanged) {
                    // Existing sockets may be bound to the previous physical path:
                    // re-dial platform transports and re-warm tunnel DoH now instead
                    // of waiting for ping timeouts.
                    callbackDevice?.networkChanged()
                }
            }

            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                if (offlineCallback !== this || device !== callbackDevice) {
                    return
                }
                // Same network, new addressing (DHCP renew, IPv6 renumbering,
                // AP roam, DNS or route update): old sockets may be stale too.
                val fingerprint = listOf(
                    linkProperties.interfaceName.orEmpty(),
                    linkProperties.linkAddresses.map { it.toString() }.sorted().joinToString(","),
                    linkProperties.routes.map { it.toString() }.sorted().joinToString(","),
                    linkProperties.dnsServers.map { it.toString() }.sorted().joinToString(","),
                    linkProperties.domains.orEmpty(),
                    linkProperties.mtu.toString(),
                ).joinToString("|")
                if (availableNetworks.onLinkPropertiesChanged(network, fingerprint)) {
                    Log.i(TAG, "network link changed device = $network")
                    callbackDevice?.networkChanged()
                }
            }

            override fun onLost(network: Network) {
                if (offlineCallback !== this || device !== callbackDevice) {
                    return
                }
                val change = availableNetworks.onLost(network)
                if (!change.topologyChanged) {
                    return
                }
                Log.i(TAG, "network lost device = $network count=${availableNetworks.size}")
                callbackDevice?.offline = !change.available
                if (change.available) {
                    // Another physical path remains. It may now become the route
                    // for transport sockets even though the device stayed online.
                    callbackDevice?.networkChanged()
                }
            }
        }

        // Android 15+ adds NOT_METERED to a new NetworkRequest by default. Build
        // from an empty capability set so cellular and future constrained
        // physical paths are not silently excluded. Explicit NOT_VPN prevents
        // the tunnel from satisfying its own underlying-network observer.
        val networkRequest = physicalInternetNetworkRequestBuilder().build()

        val connectivityManager =
            getSystemService(ConnectivityManager::class.java) as ConnectivityManager
        connectivityManager.registerNetworkCallback(
            networkRequest,
            offlineCallback!!,
            Handler(mainLooper),
        )
    }

    fun removeOfflineCallback() {
        offlineCallback?.let {
            try {
                val connectivityManager =
                    getSystemService(ConnectivityManager::class.java) as ConnectivityManager
                connectivityManager.unregisterNetworkCallback(it)
            } catch (_: IllegalArgumentException) {
            }
        }
        offlineCallback = null
    }

    /**
     * The membership callback above tracks the SET of physical networks, so it
     * cannot see a default-preference flip between two still-attached networks
     * (bad-wifi avoidance moving the default to cell while wifi stays
     * associated, or the reverse). Existing transport sockets do not migrate on
     * such a flip; they linger on the old path until a ping timeout. Track the
     * per-app default network's identity and kick the transports the moment it
     * changes. This app never routes through its own tunnel (see MainService
     * updatePfd's app rules for every mode), so the per-app default here is
     * always a physical network, never the tunnel itself.
     */
    private fun addDefaultNetworkCallback() {
        removeDefaultNetworkCallback()

        val callbackDevice = device
        defaultNetworkCallback = object : ConnectivityManager.NetworkCallback() {
            // main-looper confined (registered with a main handler)
            var lastDefaultNetwork: Network? = null

            override fun onAvailable(network: Network) {
                if (defaultNetworkCallback !== this || device !== callbackDevice) {
                    return
                }
                val previous = lastDefaultNetwork
                lastDefaultNetwork = network
                if (previous != null && previous != network) {
                    Log.i(TAG, "network default changed $previous -> $network")
                    callbackDevice?.networkChanged()
                }
            }

            override fun onLost(network: Network) {
                if (defaultNetworkCallback !== this || device !== callbackDevice) {
                    return
                }
                // No default remains. A replacement arrives as onAvailable; a
                // true loss is also a membership loss, so the offline state and
                // the reconnect kick stay owned by the membership callback.
                if (lastDefaultNetwork == network) {
                    lastDefaultNetwork = null
                }
            }
        }

        val connectivityManager =
            getSystemService(ConnectivityManager::class.java) as ConnectivityManager
        connectivityManager.registerDefaultNetworkCallback(
            defaultNetworkCallback!!,
            Handler(mainLooper),
        )
    }

    fun removeDefaultNetworkCallback() {
        defaultNetworkCallback?.let {
            try {
                val connectivityManager =
                    getSystemService(ConnectivityManager::class.java) as ConnectivityManager
                connectivityManager.unregisterNetworkCallback(it)
            } catch (_: IllegalArgumentException) {
            }
        }
        defaultNetworkCallback = null
    }

    private fun updatePerformanceDegraded() {
        // battery saver throttles cpu/network and severe thermal throttling
        // slows the whole host: the device answers control pings slowly, so
        // ease the sdk's liveness probe timings — slow must not be misread as
        // a dead peer. Mirrors the apple extension's composition (low power
        // mode / thermal state).
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        device?.setPerformanceDegraded(powerManager.isPowerSaveMode || thermalDegraded)
    }

    private fun addPowerSaveReceiver() {
        removePowerSaveReceiver()

        powerSaveReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                Handler(mainLooper).post {
                    updatePerformanceDegraded()
                }
            }
        }
        registerReceiver(
            powerSaveReceiver,
            IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        )
        updatePerformanceDegraded()
    }

    fun removePowerSaveReceiver() {
        powerSaveReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (_: IllegalArgumentException) {
            }
        }
        powerSaveReceiver = null
    }

    private fun addThermalStatusListener() {
        removeThermalStatusListener()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return
        }

        // SEVERE matches the apple extension's .serious/.critical threshold:
        // the point where the OS throttles enough that control pings answer
        // slowly. The listener also fires once with the current status at
        // registration, which initializes thermalDegraded.
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val listener = object : PowerManager.OnThermalStatusChangedListener {
            override fun onThermalStatusChanged(status: Int) {
                if (thermalStatusListener !== this) {
                    return
                }
                thermalDegraded = status >= PowerManager.THERMAL_STATUS_SEVERE
                updatePerformanceDegraded()
            }
        }
        thermalStatusListener = listener
        powerManager.addThermalStatusListener(mainExecutor, listener)
    }

    fun removeThermalStatusListener() {
        thermalStatusListener?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                try {
                    powerManager.removeThermalStatusListener(it)
                } catch (_: IllegalArgumentException) {
                }
            }
        }
        thermalStatusListener = null
        thermalDegraded = false
    }

    private fun addNetworkCallback() {
        removeNetworkCallback()

        val callbackDevice = device
        val availableNetworks = AvailableNetworkTracker<Network>()
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (networkCallback !== this || device !== callbackDevice) {
                    return
                }
                availableNetworks.onAvailable(network)
                Log.i(TAG, "network available provider = $network count=${availableNetworks.size}")
                callbackDevice?.providePaused = false
            }

            override fun onLost(network: Network) {
                if (networkCallback !== this || device !== callbackDevice) {
                    return
                }
                val change = availableNetworks.onLost(network)
                if (change.topologyChanged) {
                    Log.i(TAG, "network lost provider = $network count=${availableNetworks.size}")
                    callbackDevice?.providePaused = !change.available
                }
            }
        }

        // see https://developer.android.com/training/monitoring-device-state/connectivity-status-type
        val networkRequestBuilder = physicalInternetNetworkRequestBuilder()

        /**
         * restrict to wifi if provideNetworkMode == wifi or device is null
         */
        device?.let {
            if (ProvideNetworkMode.fromString(it.provideNetworkMode) == ProvideNetworkMode.WIFI) {
                networkRequestBuilder
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
            }
        } ?: run {
            networkRequestBuilder
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
        }

        val networkRequest = networkRequestBuilder.build()

        val connectivityManager =
            getSystemService(ConnectivityManager::class.java) as ConnectivityManager
        // Until the passive callback reports a matching path, do not expose the
        // device as a provider on a stale path from the previous configuration.
        callbackDevice?.providePaused = true
        connectivityManager.registerNetworkCallback(
            networkRequest,
            networkCallback!!,
            Handler(mainLooper),
        )
    }

    fun removeNetworkCallback() {
        networkCallback?.let {
            try {
                val connectivityManager =
                    getSystemService(ConnectivityManager::class.java) as ConnectivityManager
                connectivityManager.unregisterNetworkCallback(it)
            } catch (_: IllegalArgumentException) {
            }
        }
        networkCallback = null
    }


    fun login(byJwt: String){
        asyncLocalState?.localState?.byJwt = byJwt
        api?.byJwt = byJwt
    }

    fun loginClient(byClientJwt: String): Boolean {
        return asyncLocalState?.localState?.let { localState ->
            localState.byClientJwt = byClientJwt
            if (initDevice(byClientJwt)) {
                true
            } else {
                logoutStaleLocalState(localState)
                api?.byJwt = null
                false
            }
        } ?: false
    }

    // Clear a stale or partial auth state WITHOUT rotating the device
    // identity. The identity key material is device-scoped, not
    // session-scoped: auth staleness (token rotation, partial auth state,
    // re-login) must not change the key peers use to verify this device, so
    // re-persist the material across the localState.logout() wipe (which
    // clears the whole local storage dir). Only an explicit user logout()
    // deliberately severs and rotates the identity.
    private fun logoutStaleLocalState(localState: LocalState) {
        val keyMaterial = runCatching { localState.deviceLocalKeyMaterial }.getOrNull()
        localState.logout()
        keyMaterial?.let {
            runCatching { localState.deviceLocalKeyMaterial = it }
        }
    }

    fun logout() {
        stop()

        // note this clears the clientJwt also
        asyncLocalState?.localState?.logout()
        api?.byJwt = null
    }

    fun stop() {
        // Invalidate a reconcile already queued by a listener before tearing
        // down the device; it must not restart the service after logout.
        vpnServiceUpdateGeneration += 1
        vpnServiceUpdatePosted = false
        stopVpnService()

        if (wakeLock?.isHeld == true) wakeLock?.release()
        wakeLock = null
        if (wifiLock?.isHeld == true) wifiLock?.release()
        wifiLock = null

        removeNetworkCallback()
        removeOfflineCallback()
        removeDefaultNetworkCallback()
        removePowerSaveReceiver()
        removeThermalStatusListener()

//        router?.close()
//        router = null
        deviceProvideSub?.close()
        deviceProvideSub = null
        deviceProvidePausedSub?.close()
        deviceProvidePausedSub = null
        deviceProvideNetworkSub?.close()
        deviceProvideNetworkSub = null
//        deviceOfflineSub?.close()
//        deviceOfflineSub = null
        deviceConnectSub?.close()
        deviceConnectSub = null
        deviceRouteLocalSub?.close()
        deviceRouteLocalSub = null
        tunnelChangeSub?.close()
        tunnelChangeSub = null
        contractStatusChangeSub?.close()
        contractStatusChangeSub = null

//        provideEnabled = false
//        connectEnabled = false

//        byDevice?.close()
//        byDevice = null
        deviceManager.clearDevice()

        loginVc?.close()
        loginVc = null
    }


    private fun initDevice(byClientJwt: String): Boolean {
        // the sdk fires this when the jwt refresh finds the client no longer
        // exists on the server (e.g. the client was removed): the sdk has
        // already cleared its local auth state; log the user out and return
        // to the login flow
        deviceManager.onAuthLogout = {
            Handler(mainLooper).post {
                logout()
                val intent = Intent(applicationContext, LoginActivity::class.java)
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK.or(Intent.FLAG_ACTIVITY_TASK_ON_HOME))
                startActivity(intent)
            }
        }
        if (!deviceManager.initDevice(
            networkSpaceManagerProvider.getNetworkSpace(),
            byClientJwt,
            deviceDescription,
            deviceSpec
        )) {
            return false
        }

//        router = Router(device!!) {
//            runBlocking(Dispatchers.Main.immediate) {
//                updateVpnService()
//            }
//        }

//        byDevice?.providePaused = true
//        byDevice?.routeLocal = routeLocal
//        byDevice?.provideMode = provideMode

//
//        connectLocation?.let {
//            byDeviceManager.connectVc?.connect(it)
//        }



        deviceRouteLocalSub = device?.addRouteLocalChangeListener {
            Handler(mainLooper).post {
                updateVpnService()
            }
        }
        deviceProvideSub = device?.addProvideChangeListener {
            Handler(mainLooper).post {
                updateVpnService()
            }
        }
        deviceProvidePausedSub = device?.addProvidePausedChangeListener {
            Handler(mainLooper).post {
                updateVpnService()
            }
        }
        deviceProvideNetworkSub = device?.addProvideNetworkModeChangeListener {

            Handler(mainLooper).post {

                addNetworkCallback()

                updateVpnService()
            }
        }
//            deviceOfflineSub = device?.addOfflineChangeListener { _, _ ->
//                Handler(mainLooper).post {
//                    updateVpnService()
//                }
//            }
        deviceConnectSub = device?.addConnectChangeListener {
            Handler(mainLooper).post {
                updateVpnService()
            }
        }


        tunnelChangeSub = device?.addTunnelChangeListener { tunnelStarted ->
            Handler(mainLooper).post {
                updateTunnelStarted()

                if (!tunnelStarted) {
                    // the tunnel stopped. Sync with state
//                    tunnelRequestStatus = TunnelRequestStatus.None
                    updateVpnService()
                }
            }
        }
        contractStatusChangeSub = device?.addContractStatusChangeListener {
            Handler(mainLooper).post {
                updateContractStatus()
            }
        }

        addOfflineCallback()
        addDefaultNetworkCallback()
        addNetworkCallback()
        addPowerSaveReceiver()
        addThermalStatusListener()

        updateTunnelStarted()
        updateContractStatus()
        service?.get()?.onDeviceAvailable()
        updateVpnService()

        return true
    }

    private fun updateTunnelStarted() {
        device?.tunnelStarted?.let { tunnelStarted ->
            Log.i(TAG, "[tunnel]started=$tunnelStarted")
        } ?: run {
            Log.i(TAG, "[tunnel]no tunnel")
        }
    }

    private fun updateContractStatus() {
        device?.contractStatus?.let { contractStatus ->
            Log.i(TAG, "[contract]insufficent=${contractStatus.insufficientBalance} nopermission=${contractStatus.noPermission} premium=${contractStatus.premium}")
        } ?: run {
            Log.i(TAG, "[contract]no contract status")
        }
    }


    fun updateVpnService() {
        if (android.os.Looper.myLooper() != mainLooper) {
            Handler(mainLooper).post {
                updateVpnService()
            }
            return
        }
        if (vpnServiceUpdatePosted) {
            return
        }

        vpnServiceUpdatePosted = true
        val generation = vpnServiceUpdateGeneration
        Handler(mainLooper).postDelayed({
            if (generation != vpnServiceUpdateGeneration) {
                return@postDelayed
            }
            vpnServiceUpdatePosted = false
            reconcileVpnService()
        }, VPN_STATE_BURST_COALESCE_MILLIS)
    }

    private fun reconcileVpnService() {
        val device = device ?: return

        if (systemAlwaysOnVpn) {
            ensureAlwaysOnConnected()
        }

        // the vpn service is the packet router: it must run whenever the device
        // is connected, providing (any mode — including Network, which relays
        // for same-network peers), or routing remotely
        val provideEnabled = device.provideEnabled
        val providePaused = device.providePaused
        val connectEnabled = device.connectEnabled
        val routeLocal = device.routeLocal

        if (systemAlwaysOnVpn || vpnServiceRequired(provideEnabled, connectEnabled, routeLocal)) {
            startVpnService()
            // if provide paused, keep the vpn on but do not keep the locks
            if (provideEnabled && !providePaused) {
                if (wakeLock == null) {
                    wakeLock = (getSystemService(POWER_SERVICE) as PowerManager).run {
                        newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "urnetwork::provide").apply {
                            acquire()
                        }
                    }

                }
                if (wifiLock == null) {

                    wifiLock = (getSystemService(WIFI_SERVICE) as WifiManager).run {
                        val wifiLockMode: Int
                        if (Build.VERSION_CODES.UPSIDE_DOWN_CAKE <= Build.VERSION.SDK_INT) {
                            wifiLockMode = WifiManager.WIFI_MODE_FULL_LOW_LATENCY
                        } else {
                            wifiLockMode = WifiManager.WIFI_MODE_FULL_HIGH_PERF
                        }

                        createWifiLock(wifiLockMode, "urnetwork::provide").apply {
                            acquire()
                        }
                    }
                }
                // make sure the wake lock and wifi lock are on
            } else {
                // turn off any wake lock or wifi lock

                if (wakeLock?.isHeld == true) wakeLock?.release()
                wakeLock = null
                if (wifiLock?.isHeld == true) wifiLock?.release()
                wifiLock = null
            }
        } else {
            stopVpnService()
            // turn off any wake lock or wifi lock

            if (wakeLock?.isHeld == true) wakeLock?.release()
            wakeLock = null
            if (wifiLock?.isHeld == true) wifiLock?.release()
            wifiLock = null
        }
    }

        // FIXME tunnel request status

    fun startVpnService() {
        val device = device ?: return

        val allowForeground = deviceManager.allowForeground

        // note starting in Android 15, boot completed receivers cannot start foreground services
        // the app will not allow foreground until the activity is explicitly opened
        // see https://developer.android.com/about/versions/15/behavior-changes-15#fgs-boot-completed
        startVpnServiceWithForeground(allowForeground && device.provideEnabled)
    }

    private fun startVpnServiceWithForeground(foreground: Boolean) {
        // Listener echoes for the same logical state are no-ops. MainService
        // already rebuilds the TUN descriptor on material window/DNS/split
        // changes, so stop/start here only creates a traffic pause.
        if ((serviceActive || vpnStartPending) && activeVpnForeground == foreground) {
            service?.get()?.onDeviceAvailable()
            return
        }

        // Foreground policy does not change the TUN. Promote/demote the live
        // service in place so a provide<->connect handoff does not close the
        // descriptor and pause traffic.
        if (serviceActive && !vpnStartPending) {
            service?.get()?.let { activeService ->
                if (activeService.setForegroundEnabled(foreground || systemAlwaysOnVpn)) {
                    activeVpnForeground = foreground
                    return
                }
            }
        }

        // If the service has not reached onStartCommand yet, or Android
        // rejected the in-place policy change, replace that incomplete start.
        if (serviceActive || vpnStartPending) {
            stopVpnService()
        }

        if (!serviceActive && !vpnStartPending) {
            try {
                if (VpnService.prepare(this) != null) {
                    // prepare returns an intent when the user must grant additional permissions
                    // the ui will check `vpnRequestStart` and start again when the permissions have been set up
                    vpnRequestStart = true
                    vpnRequestStartListener?.let { it() }
                } else {
                    // important: start the vpn service in the application context

                    fun hasForegroundPermissions(): Boolean {
                        if (Build.VERSION_CODES.TIRAMISU <= Build.VERSION.SDK_INT) {
                            val hasForegroundPermissions = ContextCompat.checkSelfPermission(
                                this,
                                android.Manifest.permission.POST_NOTIFICATIONS
                            ) == PackageManager.PERMISSION_GRANTED
                            return hasForegroundPermissions
                        } else {
                            return true
                        }
                    }

                    if (foreground && !hasForegroundPermissions()) {
                        vpnRequestStart = true
                        vpnRequestStartListener?.let { it() }
                    } else {
                        serviceActive = true
                        activeVpnForeground = foreground
                        vpnRequestStart = false
                        vpnStartPending = true

                        // *important* calling startService for a VpnService in OnCreate will *not* correctly set up the routes
                        // we need to delay this after onCreate for the routes to set up correctly (wtf)
                        Handler(mainLooper).post {
                            vpnStartPending = false
                            if (this@MainApplication.serviceActive) {
                                val vpnIntent = Intent(this, MainService::class.java)
                                vpnIntent.putExtra("source", "app")
                                vpnIntent.putExtra("command_version", VPN_SERVICE_COMMAND_VERSION)
                                vpnIntent.putExtra("stop", false)
                                vpnIntent.putExtra("start", true)
                                vpnIntent.putExtra("foreground", foreground)
//                                vpnIntent.putExtra("offline", offline && !vpnInterfaceWhileOffline)

                                try {
                                    if (foreground) {
                                        // use a foreground service to allow notifications
                                        if (Build.VERSION_CODES.TIRAMISU <= Build.VERSION.SDK_INT) {
                                            try {
                                                startForegroundService(vpnIntent)
                                            } catch (e: ForegroundServiceStartNotAllowedException) {
                                                startService(vpnIntent)
                                            }
                                        } else if (Build.VERSION_CODES.S <= Build.VERSION.SDK_INT) {
                                            try {
                                                ContextCompat.startForegroundService(
                                                    this,
                                                    vpnIntent
                                                )
                                            } catch (e: ForegroundServiceStartNotAllowedException) {
                                                startService(vpnIntent)
                                            }
                                        } else {
                                            ContextCompat.startForegroundService(this, vpnIntent)
                                        }
                                    } else {
                                        startService(vpnIntent)
                                    }
                                } catch (e: Exception) {
                                    Log.i(
                                        TAG,
                                        "Error trying to start the vpn service: ${e.message}"
                                    )
                                    serviceActive = false
                                    activeVpnForeground = null
                                    vpnRequestStart = true
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.i(
                    TAG,
                    "Error trying to communicate with the vpn service to start: ${e.message}"
                )
                vpnStartPending = false
                activeVpnForeground = null
                vpnRequestStart = true
                // do not request start here
                // that could lead to a loop
            }
        }
    }

    /**
     * Commit the service instance Android actually delivered. This is also the
     * cold-process redelivery adoption path: in-memory optimistic state is gone,
     * but the restored SDK device has already proven the VPN is still desired.
     */
    fun vpnServiceDidStart(
        startedService: MainService,
        foreground: Boolean,
        systemAlwaysOn: Boolean,
    ) {
        service = WeakReference(startedService)
        serviceActive = true
        systemAlwaysOnVpn = systemAlwaysOnVpn || systemAlwaysOn
        vpnStartPending = false
        activeVpnForeground = foreground
        vpnRequestStart = false
        ensureAlwaysOnConnected()
    }

    fun isSystemAlwaysOnVpnActive(): Boolean = systemAlwaysOnVpn

    /**
     * System Always-on owns the disconnect policy. Keep the user's selected
     * location when there is one, otherwise reconnect to the best provider.
     * A bounded-delay retry covers a transient SDK restore/network race while
     * avoiding multiple simultaneous controllers for the same device.
     */
    private fun ensureAlwaysOnConnected() {
        if (!systemAlwaysOnVpn) {
            alwaysOnConnectRequestedDevice = null
            return
        }
        val current = device ?: return
        if (current.connectEnabled) {
            alwaysOnConnectRequestedDevice = null
            return
        }
        if (alwaysOnConnectRequestedDevice === current) return
        alwaysOnConnectRequestedDevice = current
        val vc = current.openConnectViewController() ?: run {
            alwaysOnConnectRequestedDevice = null
            return
        }
        try {
            current.connectLocation?.let(vc::connect) ?: vc.connectBestAvailable()
        } catch (e: Exception) {
            Log.i(TAG, "Always-on reconnect request failed: ${e.message}")
            alwaysOnConnectRequestedDevice = null
        } finally {
            runCatching { current.closeViewController(vc) }
                .onFailure { Log.i(TAG, "Always-on controller close failed: ${it.message}") }
        }
        Handler(mainLooper).postDelayed({
            if (systemAlwaysOnVpn && device === current && !current.connectEnabled) {
                alwaysOnConnectRequestedDevice = null
                ensureAlwaysOnConnected()
            }
        }, 30_000L)
    }

    fun restoredVpnServiceRequired(): Boolean {
        val current = device ?: return false
        return vpnServiceRequired(
            provideEnabled = current.provideEnabled,
            connectEnabled = current.connectEnabled,
            routeLocal = current.routeLocal,
        )
    }

    fun restoredVpnForegroundDesired(): Boolean {
        val current = device ?: return false
        return deviceManager.allowForeground && current.provideEnabled
    }

    private fun stopVpnService() {
        vpnRequestStart = false
        vpnStartPending = false
        activeVpnForeground = null

        if (serviceActive && systemAlwaysOnVpn) {
            val activeService = service?.get()
            if (activeService != null && activeService.retainAlwaysOnWithoutDevice()) {
                serviceActive = true
                return
            }
        }

        // note
        // - using startService with stop intent to stop the service is broken
        //   because stop, start quickly will ignore the second intent and deliver just the stop
        // - using stopService is broken, because it will not close the vpn tunnel fd,
        //   which prevents a new tunnel from starting again correctly
        // - using startService with stop intent, stopService in sequence is broken
        //   because startService in OnCreate will prevent the routes from being set up correctly (wtf)
        //
        // using a weak reference to the service is strangely the cleanest approach

        if (serviceActive) {
            serviceActive = false
            service?.get()?.stop()
            synchronized(serviceActiveMonitor) {
                serviceActiveMonitor.notifyAll()
            }
        }
    }

    /**
     * MainService calls this when Android (or an internal tunnel failure)
     * destroys the active instance. Clear optimistic start state so the
     * tunnel-change listener can actually restart it.
     */
    fun vpnServiceDidStop(stoppedService: MainService) {
        if (service?.get() != stoppedService) {
            return
        }
        service = null
        serviceActive = false
        systemAlwaysOnVpn = false
        alwaysOnConnectRequestedDevice = null
        vpnStartPending = false
        activeVpnForeground = null
    }

    fun forceStopVpnService() {
        val vpnIntent = Intent(this, MainService::class.java)
        vpnIntent.putExtra("source", "app")
        vpnIntent.putExtra("command_version", VPN_SERVICE_COMMAND_VERSION)
        vpnIntent.putExtra("stop", true)
        vpnIntent.putExtra("start", false)
        vpnIntent.putExtra("foreground", false)
        stopService(vpnIntent)

        stopVpnService()
    }
}
