package com.bringyour.network

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.Application
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Build
import android.os.Handler
import android.os.PowerManager
import android.os.SystemClock
import android.os.ext.SdkExtensions
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
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
        // Stable platform capability id since API 30. The framework exposes it
        // to system code only, but public hasCapability(Int) reports it to VPN
        // apps as part of ordinary NetworkCapabilities callbacks.
        const val NET_CAPABILITY_PARTIAL_CONNECTIVITY_COMPAT = 24
        // Public in API 36 and Android 14 extension 16. Use the stable id so an
        // extension-capable API 34/35 device can report it without linking an
        // API-36-only field on older releases.
        const val NET_CAPABILITY_NOT_BANDWIDTH_CONSTRAINED_COMPAT = 37
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
    var networkPolicyReceiver: BroadcastReceiver? = null
    var thermalStatusListener: PowerManager.OnThermalStatusChangedListener? = null

    // main-looper confined; latest thermal status composed into
    // setPerformanceDegraded alongside battery saver (see
    // updatePerformanceDegraded / addThermalStatusListener)
    private var thermalDegraded = false
    private var dataSaverDegraded = false
    private var defaultNetworkDegraded = false
    private var performanceDegradedDevice: DeviceLocal? = null
    private var performanceDegradedApplied: Boolean? = null

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
    private var vpnStartAttemptGeneration: Long = 0
    private var vpnStartAdoptionWatchdog: Runnable? = null
    private var vpnStartRetryRunnable: Runnable? = null
    private var vpnStartFailureCount: Int = 0
    // SDK state changes arrive in bursts for one logical connect transition.
    // Coalesce them on the main looper and make the service start idempotent.
    private var vpnServiceUpdatePosted: Boolean = false
    private var vpnServiceUpdateGeneration: Long = 0
    @Volatile
    private var systemAlwaysOnVpn: Boolean = false
    private var alwaysOnConnectRequestedDevice: DeviceLocal? = null

    var vpnRequestStartListener: (() -> Unit)? = null

    // FIXME remove these bools and just query the device directly
//    private var provideEnabled: Boolean = false
//    private var connectEnabled: Boolean = false

    private val mainHandler by lazy(LazyThreadSafetyMode.NONE) { Handler(mainLooper) }
    private val tunnelRecoveryPolicy = TunnelRecoveryPolicy()
    private var pendingTransportRecovery: Runnable? = null
    private var pendingWakeStart: Runnable? = null
    private var pendingWakeHealthAudit: Runnable? = null
    private var physicalNetworkAvailable = false
    private var sleepStartedAtMillis: Long? = null
    private var tunnelLifecycleReceiver: BroadcastReceiver? = null
    private var processLifecycleObserver: DefaultLifecycleObserver? = null
    private var userUnlockReceiver: BroadcastReceiver? = null
    private val directBootState by lazy(LazyThreadSafetyMode.NONE) { DirectBootState(this) }
    @Volatile
    private var applicationStateInitialized = false
    private var applicationStateInitializing = false

    val device get() = deviceManager.device

    /**
     * Publishes the Home Screen widgets' snapshot (location, providers,
     * throughput, contracts, balance) and re-renders them; created with the
     * application state and alive for the life of the process.
     */
    var widgetSnapshotWriter: com.bringyour.network.widgets.WidgetSnapshotWriter? = null
        private set

    /**
     * The screen a Home Screen widget tap asked for (see QuickConnectActivity):
     * "connect", "provider_locations" or "contract_stats". MainNavHost observes
     * it, navigates, and clears it -- whether the app was cold-started for the
     * tap or was already running.
     */
    val widgetRoute = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
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

        // A cold VPN-service process deliberately has not loaded gomobile yet;
        // a memory callback must not defeat the early-promotion startup path.
        if (applicationStateInitialized) Sdk.freeMemory()
    }


    override fun onCreate() {
        super.onCreate()

        if (!isCredentialStorageUnlocked()) {
            enterDirectBootMode("application-create")
            return
        }
        if (processStartedForVpnService()) {
            // A cold process must reach MainService.onCreate/startForeground
            // before gomobile loading, storage migration, device restore, or
            // callback registration can consume Android's FGS deadline.
            // MainService initializes the same state immediately after it has
            // promoted, before command admission or TUN construction.
            Log.i(TAG, "Deferring cold application initialization until VPN foreground promotion")
            return
        }
        ensureApplicationStateInitialized()
    }

    /**
     * Keep pre-unlock work deliberately tiny. The SDK store under [filesDir]
     * is credential encrypted and must not be opened until ACTION_USER_UNLOCKED.
     */
    private fun enterDirectBootMode(source: String?) {
        directBootState.markCredentialRestorePending()
        addUserUnlockReceiver()
        Log.i(TAG, "Direct Boot active; deferring credential state source=$source")
    }

    fun handleLockedBootCompleted(source: String?) {
        if (android.os.Looper.myLooper() != mainLooper) {
            mainHandler.post { handleLockedBootCompleted(source) }
            return
        }
        if (isCredentialStorageUnlocked()) {
            restoreVpnServiceFromSystemEvent(source)
            return
        }
        enterDirectBootMode(source)
        // A configured Always-on VPN is started by Android itself. Starting an
        // app-owned FGS here would both duplicate that authority and risk an
        // ineligible systemExempted promotion when Always-on is not selected.
    }

    private fun addUserUnlockReceiver() {
        if (userUnlockReceiver != null || isCredentialStorageUnlocked()) return
        userUnlockReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_USER_UNLOCKED) {
                    // Return from the broadcast before credential-state restore;
                    // the existing main-looper initialization can be substantial
                    // and must not consume the receiver execution deadline.
                    mainHandler.post {
                        completeDirectBootRestore(Intent.ACTION_USER_UNLOCKED)
                    }
                }
            }
        }
        ContextCompat.registerReceiver(
            this,
            userUnlockReceiver,
            IntentFilter(Intent.ACTION_USER_UNLOCKED),
            ContextCompat.RECEIVER_EXPORTED,
        )
    }

    private fun removeUserUnlockReceiver() {
        userUnlockReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (_: IllegalArgumentException) {
            }
        }
        userUnlockReceiver = null
    }

    private fun completeDirectBootRestore(source: String?) {
        if (android.os.Looper.myLooper() != mainLooper) {
            mainHandler.post { completeDirectBootRestore(source) }
            return
        }
        if (!isCredentialStorageUnlocked()) return
        removeUserUnlockReceiver()
        ensureApplicationStateInitialized()
        directBootState.markCredentialRestoreComplete()
        restoreVpnServiceFromSystemEvent(source)
        service?.get()?.onDeviceAvailable()
        Log.i(TAG, "Direct Boot credential restore complete source=$source")
    }

    private fun processStartedForVpnService(): Boolean {
        val activityManager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val startIntentClassName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            runCatching {
                activityManager.getHistoricalProcessStartReasons(1)
                    .firstOrNull()
                    ?.intent
                    ?.component
                    ?.className
            }.getOrNull()
        } else {
            null
        }
        @Suppress("DEPRECATION")
        val runningServiceClassNames = runCatching {
            activityManager.getRunningServices(32)
                .mapTo(linkedSetOf()) { it.service.className }
        }.getOrDefault(emptySet())
        return vpnServiceOwnsColdProcessStart(
            startIntentClassName = startIntentClassName,
            runningServiceClassNames = runningServiceClassNames,
            vpnServiceClassName = VPN_SERVICE_CLASS_NAME,
        )
    }

    /** Main-looper initialization deferred only for a cold VPN-service start. */
    fun ensureApplicationStateInitialized() {
        if (applicationStateInitialized || applicationStateInitializing) return
        check(isCredentialStorageUnlocked()) {
            "Credential encrypted application state is unavailable during Direct Boot"
        }
        check(android.os.Looper.myLooper() == mainLooper) {
            "Application state must be initialized on the main looper"
        }
        applicationStateInitializing = true
        try {
            initializeApplicationState()
            applicationStateInitialized = true
            if (directBootState.credentialRestorePending) {
                directBootState.markCredentialRestoreComplete()
            }
        } finally {
            applicationStateInitializing = false
        }
    }

    private fun initializeApplicationState() {
        addTunnelLifecycleObservers()

        if (widgetSnapshotWriter == null) {
            widgetSnapshotWriter = com.bringyour.network.widgets.WidgetSnapshotWriter(
                this,
                deviceManager,
                com.bringyour.network.widgets.GlanceWidgetRefresh(this),
            ).also { it.start() }
        }
        // the Quick Settings tile is active: render it once per process start
        QuickConnectTileService.requestUpdate(this)

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

    /**
     * Android has no VpnService sleep/wake callback. Combine the physical
     * screen/idle signals with process foreground as a fallback: SCREEN_ON can
     * be deferred for cached processes on recent Android releases, while the
     * process lifecycle only describes activities. Neither signal is complete
     * alone; all are coalesced by [TunnelRecoveryPolicy].
     */
    private fun addTunnelLifecycleObservers() {
        if (tunnelLifecycleReceiver != null) return

        val powerManager = getSystemService(PowerManager::class.java)
        if (!powerManager.isInteractive) {
            sleepStartedAtMillis = SystemClock.elapsedRealtime()
        }
        tunnelLifecycleReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_OFF -> recordTunnelSleep("screen-off")
                    Intent.ACTION_SCREEN_ON -> requestWakeHealthAudit("screen-on")
                    Intent.ACTION_USER_PRESENT -> requestWakeHealthAudit("user-present")
                    PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED -> {
                        if (powerManager.isInteractive && !powerManager.isDeviceIdleMode) {
                            requestWakeHealthAudit("device-idle-exit")
                        }
                    }
                    PowerManager.ACTION_DEVICE_LIGHT_IDLE_MODE_CHANGED -> {
                        if (
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            powerManager.isInteractive &&
                            !powerManager.isDeviceLightIdleMode
                        ) {
                            requestWakeHealthAudit("light-idle-exit")
                        }
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                addAction(PowerManager.ACTION_DEVICE_LIGHT_IDLE_MODE_CHANGED)
            }
        }
        ContextCompat.registerReceiver(
            this,
            tunnelLifecycleReceiver,
            filter,
            // These are protected system broadcasts. Android recommends an
            // exported context receiver for system-originated actions; using
            // NOT_EXPORTED can exclude privileged framework senders on OEMs.
            ContextCompat.RECEIVER_EXPORTED,
        )

        processLifecycleObserver = object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                requestWakeHealthAudit("process-foreground")
            }
        }.also { ProcessLifecycleOwner.get().lifecycle.addObserver(it) }
    }

    private fun recordTunnelSleep(reason: String) {
        if (android.os.Looper.myLooper() != mainLooper) {
            mainHandler.post { recordTunnelSleep(reason) }
            return
        }
        sleepStartedAtMillis = SystemClock.elapsedRealtime()
        pendingWakeStart?.let(mainHandler::removeCallbacks)
        pendingWakeStart = null
        pendingWakeHealthAudit?.let(mainHandler::removeCallbacks)
        pendingWakeHealthAudit = null
        tunnelRecoveryPolicy.cancelWakeAudits()
        Log.i(TAG, "[tunnel-lifecycle] sleep reason=$reason")
    }

    private fun requestWakeHealthAudit(reason: String) {
        if (android.os.Looper.myLooper() != mainLooper) {
            mainHandler.post { requestWakeHealthAudit(reason) }
            return
        }
        val scheduled = tunnelRecoveryPolicy.requestWakeAudit(reason)
        pendingWakeStart?.let(mainHandler::removeCallbacks)
        val runnable = Runnable {
            pendingWakeStart = null
            val audit = tunnelRecoveryPolicy.beginWakeAudit(
                scheduled.generation,
                SystemClock.elapsedRealtime(),
            ) ?: return@Runnable
            val wakeDevice = device ?: return@Runnable
            val sleptMillis = sleepStartedAtMillis?.let {
                (audit.startedAtMillis - it).coerceAtLeast(0L)
            }
            sleepStartedAtMillis = null

            // First repair the Android-side service/TUN state, then ask the SDK
            // to actively qualify every existing exit during the grace period.
            updateVpnService()
            service?.get()?.requestTunnelReconcile("wake:${audit.reasons.joinToString(",")}")
            val probePasses = if (wakeDevice.connectEnabled) {
                runCatching { wakeDevice.probeAllExits() }
                    .onFailure { Log.i(TAG, "[tunnel-lifecycle] wake probe failed: ${it.message}") }
                    .getOrDefault(0)
            } else {
                0
            }
            Log.i(
                TAG,
                "[tunnel-lifecycle] wake reasons=${audit.reasons} sleptMillis=$sleptMillis " +
                    "probePasses=$probePasses",
            )

            pendingWakeHealthAudit?.let(mainHandler::removeCallbacks)
            val healthRunnable = Runnable {
                pendingWakeHealthAudit = null
                if (device !== wakeDevice) return@Runnable
                val providerCount = (wakeDevice.windowStatus?.providerStateAdded ?: 0L)
                    .coerceAtMost(Int.MAX_VALUE.toLong())
                    .toInt()
                val decision = tunnelRecoveryPolicy.evaluateWakeAudit(
                    audit,
                    WakeHealthFacts(
                        connectRequested = wakeDevice.connectEnabled,
                        physicalNetworkAvailable = physicalNetworkAvailable && !wakeDevice.offline,
                        providerCount = providerCount,
                    ),
                )
                Log.i(
                    TAG,
                    "[tunnel-lifecycle] wake health decision=$decision providers=$providerCount " +
                        "networkAvailable=$physicalNetworkAvailable",
                )
                if (decision == WakeHealthDecision.RECOVER_TRANSPORTS) {
                    requestTransportRecovery("wake-unhealthy", physicalPathChange = false)
                }
            }
            pendingWakeHealthAudit = healthRunnable
            mainHandler.postDelayed(healthRunnable, audit.delayMillis)
        }
        pendingWakeStart = runnable
        mainHandler.postDelayed(runnable, scheduled.delayMillis)
    }

    private fun requestTransportRecovery(reason: String, physicalPathChange: Boolean = true) {
        if (android.os.Looper.myLooper() != mainLooper) {
            mainHandler.post { requestTransportRecovery(reason, physicalPathChange) }
            return
        }
        val recoveryDevice = device ?: return
        val scheduled = tunnelRecoveryPolicy.requestTransportRecovery(
            nowMillis = SystemClock.elapsedRealtime(),
            reason = reason,
            physicalPathChange = physicalPathChange,
        )
        pendingTransportRecovery?.let(mainHandler::removeCallbacks)
        val runnable = Runnable {
            pendingTransportRecovery = null
            if (device !== recoveryDevice) return@Runnable
            val recovery = tunnelRecoveryPolicy.consumeTransportRecovery(
                scheduled.generation,
                SystemClock.elapsedRealtime(),
            ) ?: return@Runnable
            Log.i(
                TAG,
                "[tunnel-lifecycle] transport recovery reasons=${recovery.reasons} " +
                    "physical=${recovery.physicalPathChange}",
            )
            runCatching { recoveryDevice.networkChanged() }
                .onFailure { Log.e(TAG, "transport recovery failed: ${it.message}", it) }
            service?.get()?.requestTunnelReconcile("transport-recovery")
        }
        pendingTransportRecovery = runnable
        mainHandler.postDelayed(runnable, scheduled.delayMillis)
    }

    private fun resetTunnelRecoveryState() {
        pendingTransportRecovery?.let(mainHandler::removeCallbacks)
        pendingWakeStart?.let(mainHandler::removeCallbacks)
        pendingWakeHealthAudit?.let(mainHandler::removeCallbacks)
        pendingTransportRecovery = null
        pendingWakeStart = null
        pendingWakeHealthAudit = null
        physicalNetworkAvailable = false
        tunnelRecoveryPolicy.reset()
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


    private fun physicalNetworkCapabilitiesSnapshot(
        capabilities: NetworkCapabilities,
    ): PhysicalNetworkCapabilitiesSnapshot {
        val transports = linkedSetOf<Int>()
        listOf(
            NetworkCapabilities.TRANSPORT_CELLULAR,
            NetworkCapabilities.TRANSPORT_WIFI,
            NetworkCapabilities.TRANSPORT_BLUETOOTH,
            NetworkCapabilities.TRANSPORT_ETHERNET,
            NetworkCapabilities.TRANSPORT_WIFI_AWARE,
        ).filterTo(transports, capabilities::hasTransport)
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 &&
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_LOWPAN)
        ) {
            transports.add(NetworkCapabilities.TRANSPORT_LOWPAN)
        }
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_USB)
        ) {
            transports.add(NetworkCapabilities.TRANSPORT_USB)
        }
        return PhysicalNetworkCapabilitiesSnapshot(
            validated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
            notSuspended =
                Build.VERSION.SDK_INT < Build.VERSION_CODES.P ||
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED),
            captivePortal = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL),
            partialConnectivity = hasPartialConnectivity(capabilities),
            transports = transports,
        )
    }

    @SuppressLint("WrongConstant")
    private fun hasPartialConnectivity(capabilities: NetworkCapabilities): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            capabilities.hasCapability(NET_CAPABILITY_PARTIAL_CONNECTIVITY_COMPAT)
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
                physicalNetworkAvailable = change.available
                callbackDevice?.offline = false
                if (change.recoveryRequired) {
                    // Existing sockets may be bound to the previous physical path:
                    // re-dial platform transports and re-warm tunnel DoH now instead
                    // of waiting for ping timeouts.
                    requestTransportRecovery("physical-network-available")
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
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        linkProperties.mtu.toString()
                    } else {
                        "0"
                    },
                ).joinToString("|")
                if (availableNetworks.onLinkPropertiesChanged(network, fingerprint)) {
                    Log.i(TAG, "network link changed device = $network")
                    requestTransportRecovery("link-properties-changed")
                }
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities,
            ) {
                if (offlineCallback !== this || device !== callbackDevice) return
                val change = availableNetworks.onCapabilitiesChanged(
                    network,
                    physicalNetworkCapabilitiesSnapshot(networkCapabilities),
                )
                if (!change.changed) return
                Log.i(
                    TAG,
                    "network capabilities changed device=$network transports=${change.transportChanged} " +
                        "recovered=${change.recovered} degraded=${change.degraded}",
                )
                if (change.transportChanged) {
                    requestTransportRecovery("network-transport-changed")
                }
                if (change.recovered) {
                    requestWakeHealthAudit("network-capability-restored")
                }
            }

            override fun onBlockedStatusChanged(network: Network, blocked: Boolean) {
                if (offlineCallback !== this || device !== callbackDevice) return
                val change = availableNetworks.onBlockedStatusChanged(network, blocked)
                if (!change.changed) return
                Log.i(TAG, "network blocked changed device=$network blocked=$blocked")
                if (change.recovered) {
                    requestWakeHealthAudit("network-unblocked")
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
                physicalNetworkAvailable = change.available
                callbackDevice?.offline = !change.available
                if (change.recoveryRequired) {
                    // Another physical path remains. It may now become the route
                    // for transport sockets even though the device stayed online.
                    requestTransportRecovery("physical-network-lost")
                }
            }
        }

        // Build from the least restrictive capability set supported by this OS
        // so cellular and future constrained physical paths remain eligible.
        // Explicit NOT_VPN prevents the tunnel satisfying its own observer.
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
        physicalNetworkAvailable = false
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
    @SuppressLint("WrongConstant")
    private fun defaultNetworkPressure(
        capabilities: NetworkCapabilities,
    ): DefaultNetworkPressure {
        val notCongested =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.P ||
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_CONGESTED)
        val uExtensionVersion = if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            Build.VERSION.SDK_INT < BANDWIDTH_CONSTRAINT_CAPABILITY_API
        ) {
            runCatching {
                SdkExtensions.getExtensionVersion(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            }.getOrDefault(0)
        } else {
            0
        }
        val bandwidthCapabilitySupported = supportsBandwidthConstraintCapability(
            sdkInt = Build.VERSION.SDK_INT,
            uExtensionVersion = uExtensionVersion,
        )
        val notBandwidthConstrained =
            !bandwidthCapabilitySupported ||
                capabilities.hasCapability(NET_CAPABILITY_NOT_BANDWIDTH_CONSTRAINED_COMPAT)
        return DefaultNetworkPressure(
            notCongested = notCongested,
            notBandwidthConstrained = notBandwidthConstrained,
        )
    }

    private fun setDefaultNetworkDegraded(degraded: Boolean) {
        if (defaultNetworkDegraded == degraded) return
        defaultNetworkDegraded = degraded
        Log.i(TAG, "default network pressure degraded=$degraded")
        updatePerformanceDegraded()
    }

    private fun addDefaultNetworkCallback() {
        removeDefaultNetworkCallback()

        val callbackDevice = device
        val tracker = DefaultNetworkTracker<Network>()
        val pressureTracker = DefaultNetworkPressureTracker<Network>()
        defaultNetworkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (defaultNetworkCallback !== this || device !== callbackDevice) {
                    return
                }
                if (pressureTracker.onAvailable(network)) {
                    setDefaultNetworkDegraded(pressureTracker.degraded)
                }
                if (tracker.onAvailable(network)) {
                    Log.i(TAG, "network default changed to $network")
                    requestTransportRecovery("default-network-changed")
                }
                updateDataSaverDegraded()
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities,
            ) {
                if (defaultNetworkCallback !== this || device !== callbackDevice) return
                if (
                    pressureTracker.onCapabilitiesChanged(
                        network,
                        defaultNetworkPressure(networkCapabilities),
                    )
                ) {
                    setDefaultNetworkDegraded(pressureTracker.degraded)
                }
                // Meteredness can change with capabilities without a Data Saver
                // preference broadcast (for example Wi-Fi policy changes).
                updateDataSaverDegraded()
            }

            override fun onLost(network: Network) {
                if (defaultNetworkCallback !== this || device !== callbackDevice) {
                    return
                }
                // A loss->replacement with the same Network identity is still
                // a dead-path crossing and is detected by the tracker.
                tracker.onLost(network)
                if (pressureTracker.onLost(network)) {
                    setDefaultNetworkDegraded(pressureTracker.degraded)
                }
                updateDataSaverDegraded()
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
        setDefaultNetworkDegraded(false)
    }

    private fun updatePerformanceDegraded() {
        // Battery saver, thermal throttling, Data Saver, congestion and an OS
        // bandwidth constraint all make control traffic legitimately slower.
        // Ease SDK liveness and idle keepalive timing instead of forcing a
        // reconnect: slow must not be misread as a dead peer. This mirrors the
        // Apple extension's low-power / thermal / constrained-path composition.
        val currentDevice = device
        if (currentDevice == null) {
            performanceDegradedDevice = null
            performanceDegradedApplied = null
            return
        }
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val degraded = hostPerformanceDegraded(
            HostPerformanceFacts(
                powerSave = powerManager.isPowerSaveMode,
                thermalDegraded = thermalDegraded,
                dataSaverDegraded = dataSaverDegraded,
                defaultNetworkDegraded = defaultNetworkDegraded,
            ),
        )
        if (
            performanceDegradedDevice === currentDevice &&
            performanceDegradedApplied == degraded
        ) {
            return
        }
        performanceDegradedDevice = currentDevice
        performanceDegradedApplied = degraded
        currentDevice.setPerformanceDegraded(degraded)
        Log.i(
            TAG,
            "performance degraded=$degraded powerSave=${powerManager.isPowerSaveMode} " +
                "thermal=$thermalDegraded dataSaver=$dataSaverDegraded " +
                "networkPressure=$defaultNetworkDegraded",
        )
    }

    private fun updateDataSaverDegraded() {
        val connectivityManager = getSystemService(ConnectivityManager::class.java)
        val next = runCatching {
            val restriction = when (connectivityManager.restrictBackgroundStatus) {
                ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED ->
                    BackgroundDataRestriction.ENABLED
                ConnectivityManager.RESTRICT_BACKGROUND_STATUS_WHITELISTED ->
                    BackgroundDataRestriction.ALLOWLISTED
                else -> BackgroundDataRestriction.DISABLED
            }
            dataSaverDegradesPerformance(
                activeNetworkMetered = connectivityManager.isActiveNetworkMetered,
                restriction = restriction,
            )
        }.onFailure {
            Log.i(TAG, "unable to read Data Saver policy: ${it.message}")
        }.getOrNull() ?: return
        if (dataSaverDegraded == next) return
        dataSaverDegraded = next
        Log.i(TAG, "Data Saver network pressure degraded=$next")
        updatePerformanceDegraded()
    }

    private fun addNetworkPolicyReceiver() {
        removeNetworkPolicyReceiver()
        networkPolicyReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == ConnectivityManager.ACTION_RESTRICT_BACKGROUND_CHANGED) {
                    updateDataSaverDegraded()
                }
            }
        }
        ContextCompat.registerReceiver(
            this,
            networkPolicyReceiver,
            IntentFilter(ConnectivityManager.ACTION_RESTRICT_BACKGROUND_CHANGED),
            // The platform only delivers this action to dynamically registered
            // receivers. It is a protected system policy signal.
            ContextCompat.RECEIVER_EXPORTED,
        )
        updateDataSaverDegraded()
    }

    fun removeNetworkPolicyReceiver() {
        networkPolicyReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (_: IllegalArgumentException) {
            }
        }
        networkPolicyReceiver = null
        if (dataSaverDegraded) {
            dataSaverDegraded = false
            updatePerformanceDegraded()
        }
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
        ContextCompat.registerReceiver(
            this,
            powerSaveReceiver,
            IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
            // This is a protected system broadcast. Exported context
            // receivers reliably accept privileged framework senders across
            // OEM builds while still rejecting non-system spoofing.
            ContextCompat.RECEIVER_EXPORTED,
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


    /**
     * Signs the network in. The post-login onboarding flow is for a network
     * that was just created (`newNetwork`), never for an existing account
     * signing in: the SDK reads a missing flag as "may prompt", so the flag
     * is written explicitly on every login, before the device reads it.
     */
    fun login(byJwt: String, newNetwork: Boolean = false) {
        asyncLocalState?.localState?.let { localState ->
            localState.byJwt = byJwt
            localState.canPromptIntroFunnel = newNetwork
        }
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
        widgetSnapshotWriter?.clear()

        // note this clears the clientJwt also
        asyncLocalState?.localState?.logout()
        api?.byJwt = null
    }

    fun stop() {
        // Invalidate a reconcile already queued by a listener before tearing
        // down the device; it must not restart the service after logout.
        vpnServiceUpdateGeneration += 1
        vpnServiceUpdatePosted = false
        resetTunnelRecoveryState()
        stopVpnService()

        removeNetworkCallback()
        removeOfflineCallback()
        removeDefaultNetworkCallback()
        removePowerSaveReceiver()
        removeNetworkPolicyReceiver()
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

        // A callback queued for a former DeviceLocal must never reset the new
        // device's transports or judge its initial provider window unhealthy.
        resetTunnelRecoveryState()

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
                // an active Quick Settings tile is only bound when the app asks
                QuickConnectTileService.requestUpdate(this)
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
        addNetworkPolicyReceiver()
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
        val connectEnabled = device.connectEnabled
        val routeLocal = device.routeLocal

        if (systemAlwaysOnVpn || vpnServiceRequired(provideEnabled, connectEnabled, routeLocal)) {
            startVpnService()
        } else {
            stopVpnService()
        }
    }

        // FIXME tunnel request status

    fun startVpnService() {
        if (android.os.Looper.myLooper() != mainLooper) {
            mainHandler.post { startVpnService() }
            return
        }
        if (device == null) return

        try {
            val alreadyStartingOrRunning = serviceActive || vpnStartPending
            when (
                decideVpnServiceLaunch(
                    serviceActive = serviceActive,
                    startPending = vpnStartPending,
                    vpnPermissionRequired =
                        !alreadyStartingOrRunning && VpnService.prepare(this) != null,
                )
            ) {
                VpnServiceLaunchDecision.ALREADY_RUNNING -> {
                    cancelVpnStartRetry(resetFailureCount = service?.get() != null)
                    service?.get()?.onDeviceAvailable()
                }
                VpnServiceLaunchDecision.REQUEST_VPN_PERMISSION -> {
                    cancelVpnStartRetry(resetFailureCount = false)
                    vpnRequestStart = true
                    vpnRequestStartListener?.invoke()
                }
                VpnServiceLaunchDecision.START_FOREGROUND -> {
                    // Every live VPN is an FGS on API 26+. POST_NOTIFICATIONS
                    // controls drawer visibility only and must never gate this.
                    cancelVpnStartRetry(resetFailureCount = false)
                    serviceActive = true
                    vpnRequestStart = false
                    vpnStartPending = true
                    val vpnIntent = Intent(this, MainService::class.java).apply {
                        putExtra("source", "app")
                        putExtra("command_version", VPN_SERVICE_COMMAND_VERSION)
                        putExtra("stop", false)
                        putExtra("start", true)
                        // Retained for redelivery compatibility with older APKs.
                        putExtra("foreground", true)
                    }
                    try {
                        // Start inside the Activity/BroadcastReceiver eligibility
                        // window. Posting this call can lose Android's temporary
                        // background-start exemption before the service request.
                        ContextCompat.startForegroundService(this, vpnIntent)
                        scheduleVpnStartAdoptionWatchdog()
                    } catch (e: Exception) {
                        // A background-start restriction must not fall back to
                        // startService: that creates the exact five-second
                        // foreground-promotion crash this path prevents.
                        Log.e(TAG, "Unable to start VPN foreground service: ${e.message}", e)
                        vpnServiceDidNotStart(null)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unable to prepare VPN service start: ${e.message}", e)
            vpnStartPending = false
            vpnRequestStart = true
            scheduleVpnStartRetry()
        }
    }

    private fun scheduleVpnStartAdoptionWatchdog() {
        vpnStartAdoptionWatchdog?.let(mainHandler::removeCallbacks)
        val generation = ++vpnStartAttemptGeneration
        lateinit var watchdog: Runnable
        watchdog = Runnable {
            if (vpnStartAdoptionWatchdog !== watchdog) return@Runnable
            vpnStartAdoptionWatchdog = null
            if (
                !vpnServiceStartAttemptTimedOut(
                    expectedGeneration = generation,
                    currentGeneration = vpnStartAttemptGeneration,
                    serviceActive = serviceActive,
                    serviceAdopted = service?.get() != null,
                )
            ) {
                return@Runnable
            }
            Log.e(TAG, "VPN foreground service start was not delivered within the adoption timeout")
            vpnServiceDidNotStart(null)
        }
        vpnStartAdoptionWatchdog = watchdog
        mainHandler.postDelayed(watchdog, VPN_SERVICE_START_ADOPTION_TIMEOUT_MILLIS)
    }

    private fun cancelVpnStartAdoptionWatchdog() {
        vpnStartAdoptionWatchdog?.let(mainHandler::removeCallbacks)
        vpnStartAdoptionWatchdog = null
        vpnStartAttemptGeneration += 1
    }

    /**
     * Recover a dropped/rejected foreground-service launch while the app still
     * has a started Activity. If the app is backgrounded, retain
     * [vpnRequestStart] and let MainActivity.onStart use the next real
     * user-visible eligibility window instead of repeatedly violating Android's
     * background-start restriction.
     */
    private fun scheduleVpnStartRetry() {
        vpnStartRetryRunnable?.let(mainHandler::removeCallbacks)
        val delayMillis = vpnServiceStartRetryDelayMillis(vpnStartFailureCount)
        if (vpnStartFailureCount < Int.MAX_VALUE) vpnStartFailureCount += 1
        lateinit var retry: Runnable
        retry = Runnable {
            if (vpnStartRetryRunnable !== retry) return@Runnable
            vpnStartRetryRunnable = null
            val retryListener = vpnRequestStartListener
            if (
                !vpnServiceStartRetryEligible(
                    retryRequested = vpnRequestStart,
                    vpnRequired = systemAlwaysOnVpn || restoredVpnServiceRequired(),
                    serviceActive = serviceActive,
                    startPending = vpnStartPending,
                    foregroundActivityAvailable = retryListener != null,
                )
            ) {
                return@Runnable
            }
            Log.i(
                TAG,
                "Retrying VPN foreground service after failure delayMillis=$delayMillis " +
                    "failureCount=$vpnStartFailureCount",
            )
            runCatching { retryListener?.invoke() }
                .onFailure {
                    Log.e(TAG, "Unable to request VPN foreground retry: ${it.message}", it)
                    scheduleVpnStartRetry()
                }
        }
        vpnStartRetryRunnable = retry
        mainHandler.postDelayed(retry, delayMillis)
    }

    private fun cancelVpnStartRetry(resetFailureCount: Boolean = true) {
        vpnStartRetryRunnable?.let(mainHandler::removeCallbacks)
        vpnStartRetryRunnable = null
        if (resetFailureCount) vpnStartFailureCount = 0
    }

    /** Called from the earliest service callback, before command admission. */
    fun vpnServiceDidEnterForeground() {
        if (android.os.Looper.myLooper() != mainLooper) {
            mainHandler.post { vpnServiceDidEnterForeground() }
            return
        }
        cancelVpnStartAdoptionWatchdog()
        cancelVpnStartRetry(resetFailureCount = false)
        vpnStartPending = false
    }

    /**
     * Release an app-owned optimistic start after promotion/admission failure.
     * Do not disturb a different service instance that won a start race.
     */
    fun vpnServiceDidNotStart(failedService: MainService?, retryRequested: Boolean = true) {
        if (android.os.Looper.myLooper() != mainLooper) {
            mainHandler.post { vpnServiceDidNotStart(failedService, retryRequested) }
            return
        }
        val adoptedService = service?.get()
        if (adoptedService != null && adoptedService !== failedService) return
        if (adoptedService === failedService) service = null
        cancelVpnStartAdoptionWatchdog()
        serviceActive = false
        vpnStartPending = false
        vpnRequestStart = retryRequested
        if (retryRequested) {
            scheduleVpnStartRetry()
        } else {
            cancelVpnStartRetry()
        }
    }

    /**
     * Commit the service instance Android actually delivered. This is also the
     * cold-process redelivery adoption path: in-memory optimistic state is gone,
     * but the restored SDK device has already proven the VPN is still desired.
     */
    fun vpnServiceDidStart(
        startedService: MainService,
        systemAlwaysOn: Boolean,
    ) {
        cancelVpnStartAdoptionWatchdog()
        cancelVpnStartRetry()
        service = WeakReference(startedService)
        serviceActive = true
        systemAlwaysOnVpn = systemAlwaysOnVpn || systemAlwaysOn
        vpnStartPending = false
        vpnRequestStart = false
        ensureAlwaysOnConnected()
    }

    /**
     * Adopt Android's pre-unlock Always-on service without consulting the SDK
     * device or credential encrypted desired state.
     */
    fun vpnServiceDidStartDirectBoot(startedService: MainService) {
        cancelVpnStartAdoptionWatchdog()
        cancelVpnStartRetry()
        service = WeakReference(startedService)
        serviceActive = true
        systemAlwaysOnVpn = true
        vpnStartPending = false
        vpnRequestStart = false
        directBootState.markCredentialRestorePending()
    }

    fun isSystemAlwaysOnVpnActive(): Boolean = systemAlwaysOnVpn

    /**
     * Disconnect through the SDK controller so a notification action follows
     * the same state transition as the connect screen and Quick Settings tile.
     * System Always-on owns its own reconnect policy, so it must not be
     * presented as a disconnectable state.
     */
    fun disconnectVpnConnection(source: String) {
        if (android.os.Looper.myLooper() != mainLooper) {
            mainHandler.post { disconnectVpnConnection(source) }
            return
        }
        if (systemAlwaysOnVpn) {
            Log.i(TAG, "Ignoring VPN disconnect from $source while system Always-on is active")
            return
        }
        val current = device ?: return
        if (!current.connectEnabled) return

        val vc = current.openConnectViewController() ?: run {
            Log.i(TAG, "Unable to open connect controller for VPN disconnect from $source")
            return
        }
        try {
            Log.i(TAG, "Disconnecting VPN connection from $source")
            vc.disconnect()
        } catch (e: Exception) {
            Log.e(TAG, "VPN disconnect from $source failed: ${e.message}", e)
        } finally {
            runCatching { current.closeViewController(vc) }
                .onFailure { Log.i(TAG, "Connect controller close failed: ${it.message}") }
        }
        updateVpnService()
    }

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

    fun restoreVpnServiceFromSystemEvent(source: String?) {
        if (android.os.Looper.myLooper() != mainLooper) {
            mainHandler.post { restoreVpnServiceFromSystemEvent(source) }
            return
        }
        if (!isCredentialStorageUnlocked()) {
            enterDirectBootMode(source)
            return
        }
        ensureApplicationStateInitialized()
        directBootState.markCredentialRestoreComplete()
        if (restoredVpnServiceRequired()) {
            Log.i(TAG, "restoring VPN service after system event=$source")
            startVpnService()
        } else {
            updateVpnService()
        }
    }

    private fun stopVpnService() {
        cancelVpnStartAdoptionWatchdog()
        cancelVpnStartRetry()
        vpnRequestStart = false
        vpnStartPending = false

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
        // Capture durable app intent before clearing the adopted instance. An
        // OEM can destroy an adopted service before a PacketFlow ever flips
        // tunnelStarted, so relying only on that SDK listener leaves no event
        // to recover the foreground service while the activity stays open.
        val restartRequired = restoredVpnServiceRequired()
        service = null
        cancelVpnStartAdoptionWatchdog()
        serviceActive = false
        systemAlwaysOnVpn = false
        alwaysOnConnectRequestedDevice = null
        vpnStartPending = false
        vpnRequestStart = restartRequired
        if (restartRequired) {
            Log.i(TAG, "VPN service stopped while still required; scheduling foreground retry")
            scheduleVpnStartRetry()
        } else {
            cancelVpnStartRetry()
        }
    }

    fun forceStopVpnService() {
        val vpnIntent = Intent(this, MainService::class.java)
        vpnIntent.putExtra("source", "app")
        vpnIntent.putExtra("command_version", VPN_SERVICE_COMMAND_VERSION)
        vpnIntent.putExtra("stop", true)
        vpnIntent.putExtra("start", false)
        stopService(vpnIntent)

        stopVpnService()
    }
}
