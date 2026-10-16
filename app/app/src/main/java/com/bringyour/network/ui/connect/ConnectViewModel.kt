package com.bringyour.network.ui.connect

import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.VectorConverter
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bringyour.network.DeviceManager
import com.bringyour.network.ForegroundDeviceControllerOwner
import com.bringyour.network.MainApplication
import com.bringyour.network.TAG
import com.bringyour.network.ui.shared.models.ConnectStatus
import com.bringyour.network.ui.theme.BlueLight
import com.bringyour.network.ui.stats.IpFamilyPoint
import com.bringyour.network.ui.theme.Green
import com.bringyour.network.ui.theme.Pink
import com.bringyour.network.ui.theme.Red
import com.bringyour.network.ui.theme.Yellow
import com.bringyour.sdk.ConnectGrid
import com.bringyour.sdk.ConnectLocation
import com.bringyour.sdk.ContractStatus
import com.bringyour.sdk.DeviceLocal
import com.bringyour.sdk.Id
import com.bringyour.sdk.PerformanceProfile
import com.bringyour.sdk.ProviderGridPoint
import com.bringyour.sdk.Sdk
import com.bringyour.sdk.Sub
import com.bringyour.sdk.WindowSizeSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.compose.runtime.mutableStateListOf
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * The connect screen: the presented connect view controller of the current
 * device while the app is in the foreground, its grid, location and status,
 * and the connect and disconnect the screen makes.
 *
 * It reaches the sdk through [ConnectDevice] and [ConnectController], the
 * device changes through `deviceChanges`, the process lifecycle, the start
 * connect gate and the connect command queue through constructor arguments,
 * so tests run it on a fake sdk with a held gate and a held queue
 * (ConnectViewModelTest). The app's are the device manager's device,
 * ProcessLifecycleOwner, MainApplication.checkStartConnect and
 * MainApplication.connectCommands.
 *
 * Its connects and disconnects run on the connect command queue, off the main
 * thread ([ConnectCommands]), and the screen shows each tap's state from the
 * tap until the queue has run it; then it reads the controller's status again.
 * The presented controller and its listeners close on the queue too, after
 * the commands made so far, so a disconnect tapped just before the app goes
 * to the background still reaches the sdk.
 */
@HiltViewModel
class ConnectViewModel
internal constructor(
        private val deviceManager: DeviceManager,
        private val appContext: android.content.Context,
        private val processLifecycle: Lifecycle,
        deviceChanges: (listener: (ConnectDevice?) -> Unit) -> (() -> Unit),
        private val startConnect: StartConnect,
        private val connectCommands: ConnectCommands,
) : ViewModel(), DefaultLifecycleObserver {

    @Inject
    constructor(
        deviceManager: DeviceManager,
        @dagger.hilt.android.qualifiers.ApplicationContext appContext: android.content.Context,
    ) : this(
        deviceManager = deviceManager,
        appContext = appContext,
        processLifecycle = ProcessLifecycleOwner.get().lifecycle,
        deviceChanges = { listener ->
            deviceManager.addDeviceChangeListener { device ->
                listener(device?.let { SdkConnectDevice(it) })
            }
        },
        startConnect = (appContext as? com.bringyour.network.MainApplication)?.startConnect
            ?: StartConnect(check = { it(false) }, render = {}),
        connectCommands = (appContext as? com.bringyour.network.MainApplication)?.connectCommands
            // only a test context has no app: commands run where they are made
            ?: ConnectCommands(execute = { it.run() }, postMain = { it.run() }),
    )

    private var connectVc: ConnectController? = null
    private var viewControllerDevice: ConnectDevice? = null
    // the device last published, also while no controller is presented
    private var currentDevice: ConnectDevice? = null

    private val subs = mutableListOf<Sub>()
    private var removeDeviceChangeListener: (() -> Unit)? = null
    private var removeCommandsListener: (() -> Unit)? = null
    private var removeStartListener: (() -> Unit)? = null
    private val controllerOwner =
        ForegroundDeviceControllerOwner<ConnectDevice, ConnectController>(
            open = { openConnectViewController(it) },
            close = { device, vc -> closeConnectViewController(device, vc) },
        )

    private val _connectStatus = MutableStateFlow(ConnectStatus.DISCONNECTED)
    val connectStatus: StateFlow<ConnectStatus> = _connectStatus

    var selectedLocation by mutableStateOf<ConnectLocation?>(null)
        private set

    var windowCurrentSize by mutableIntStateOf(0)
        private set

    var providerGridPoints by mutableStateOf<Map<Id, ProviderGridPoint>>(mapOf())
        private set

    // the same providers as plain data for the connect drawer's IP family
    // status row (the sdk state and category), derived with the grid so the
    // two views always describe one snapshot
    var ipFamilyPoints by mutableStateOf<List<IpFamilyPoint>>(listOf())
        private set

    var grid by mutableStateOf<ConnectGrid?>(null)
        private set

    // last published grid signature; skip redundant recomposition when the SDK
    // re-emits a logically-unchanged grid (its Id/ProviderGridPoint values are
    // compared by reference, so an unconditional assign always recomposes)
    private var lastGridSignature: String = ""

    // var grid by mutableStateOf<ConnectGrid?>(null)
    private var tunnelConnected = false

    // A failed connect attempt (the sdk's CONNECT_FAILED, or no provider in the
    // window past the time bound, while the user wants to be connected) is
    // handed to the whitelist probe, which decides whether to run (P052).
    private val connectFailureMonitor = ConnectFailureMonitor()
    private var connectFailureCheckJob: Job? = null
    private var connectFailureCheckAtMillis: Long? = null

    var displayReconnectTunnel by mutableStateOf(false)
        private set

    val setDisplayReconnectTunnel: (Boolean) -> Unit = { display ->
        displayReconnectTunnel = display
    }

    var fixedIpSize by mutableStateOf(false)
        private set

    val toggleFixedIp: () -> Unit = {
        val fixed = this.fixedIpSize
        setFixedIpSize(!fixed)
    }

    var allowDirect by mutableStateOf(false)
        private set

    val setAllowDirect: (Boolean) -> Unit = {
        this.allowDirect = it
        updatePerformanceProfile()
    }

    val toggleAllowDirect: () -> Unit = {
        val allow = this.allowDirect
        setAllowDirect(!allow)
    }

    var postQuantumEncryption by mutableStateOf(false)
        private set

    val setPostQuantumEncryption: (Boolean) -> Unit = {
        this.postQuantumEncryption = it
        updatePerformanceProfile()
    }

    val togglePostQuantumEncryption: () -> Unit = {
        val enabled = this.postQuantumEncryption
        setPostQuantumEncryption(!enabled)
    }

    val setFixedIpSize: (Boolean) -> Unit = {
        fixedIpSize = it
        updatePerformanceProfile()
    }

    var selectedWindowType by mutableStateOf<WindowType>(WindowType.AUTO)
        private set

    val setSelectedWindowType: (WindowType) -> Unit = {
        selectedWindowType = it

        if (selectedWindowType == WindowType.AUTO) {
            /**
             * disable "fixed IP size"
             * this will trigger updatePerformanceProfile so we don't need to call it
             */

            setFixedIpSize(false)

        } else {
            updatePerformanceProfile()
        }

    }

    private val _contractStatus = MutableStateFlow<ContractStatus?>(null)
    val contractStatus: StateFlow<ContractStatus?> = _contractStatus

    private val successPoints = mutableListOf<AnimatedSuccessPoint>()

    val canvasSize = 248.dp

    val shuffledSuccessPoints = mutableStateListOf<AnimatedSuccessPoint>()

    val device: DeviceLocal?
        get() = (viewControllerDevice as? SdkConnectDevice)?.device ?: deviceManager.device

    val initSuccessPoints: (Float) -> Unit = { canvasSizePx ->

        successPoints.clear()

        successPoints.addAll(
                listOf(
                        AnimatedSuccessPoint(
                                initialOffset =
                                        Offset(
                                                -canvasSizePx.times(1.1f),
                                                canvasSizePx.times(0.95f)
                                        ),
                                targetOffset =
                                        Offset(canvasSizePx.times(.4f), canvasSizePx.times(0.95f)),
                                color = Red,
                                radius = canvasSizePx
                        ),
                        AnimatedSuccessPoint(
                                initialOffset =
                                        Offset(canvasSizePx.times(2.55f), canvasSizePx.times(3.3f)),
                                targetOffset = Offset(canvasSizePx.times(2), canvasSizePx.times(2)),
                                color = Pink,
                                radius = canvasSizePx
                        ),
                        AnimatedSuccessPoint(
                                initialOffset =
                                        Offset(canvasSizePx.times(3.5f), canvasSizePx.times(0.3f)),
                                targetOffset =
                                        Offset(canvasSizePx.times(2.25f), canvasSizePx.times(0.8f)),
                                color = Green,
                                radius = canvasSizePx
                        ),
                        AnimatedSuccessPoint(
                                initialOffset =
                                        Offset(canvasSizePx.times(0.8f), -canvasSizePx.times(1.5f)),
                                targetOffset =
                                        Offset(canvasSizePx.times(0.8f), canvasSizePx.times(0.05f)),
                                color = Yellow,
                                radius = canvasSizePx
                        ),
                        AnimatedSuccessPoint(
                                initialOffset =
                                        Offset(-canvasSizePx.times(1.5f), canvasSizePx.times(3f)),
                                targetOffset =
                                        Offset(canvasSizePx.times(.75f), canvasSizePx.times(2.25f)),
                                color = BlueLight,
                                radius = canvasSizePx
                        ),
                )
        )
    }

    val shuffleSuccessPoints: () -> Unit = {
        shuffledSuccessPoints.clear()
        shuffledSuccessPoints.addAll(successPoints.shuffled())
    }

    // the start connect gate for the connect screen, and the connecting
    // state it shows while the gate fetches the balance
    private val connectDisplay = ConnectStatusDisplay()

    /**
     * What the connect screen shows: the connecting state while a start waits
     * on its balance, else [connectStatus] (StartConnectAdmission).
     */
    val displayConnectStatus: StateFlow<ConnectStatus> = connectDisplay.displayStatus

    /**
     * Every in-app connect (the connect button and drawer, a location pick, a
     * link) passes the start connect gate: out of balance it opens the
     * upgrade screen instead of starting the tunnel, and the connect waits on
     * the balance to be retried by itself (MainApplication.startConnectBlocked).
     * The gate may first fetch a fresh account balance, so the connect starts
     * from its callback, and the screen shows the connecting state meanwhile.
     */
    val connect: (ConnectLocation?) -> Unit = { location ->
        val app = appContext as? com.bringyour.network.MainApplication
        val device = currentDevice
        if (device != null) {
            startConnect.request(
                connectEnabled = connectCommands.connectRequested(device),
                connect = true,
                owner = this,
                apply = { blocked ->
                    if (currentDevice?.identity === device.identity) {
                        if (blocked) {
                            Log.i(TAG, "[connect]blocked: insufficient balance")
                            app?.startConnectBlocked(location)
                        } else {
                            app?.clearBalanceRecovery()
                            runCommand(connect = true) { vc ->
                                if (location != null) vc.connect(location) else vc.connectBestAvailable()
                            }
                        }
                    }
                },
                waiting = {},
                ended = {},
                onResult = {},
            )
        }
    }



    val updatePerformanceProfile: () -> Unit = {

        // always a profile, even for window type auto, so the orthogonal
        // settings (allow direct, post quantum encryption) persist and apply
        // in every mode
        val performanceProfile = PerformanceProfile()
        performanceProfile.allowDirect = allowDirect
        performanceProfile.postQuantumEncryption = postQuantumEncryption

        when (selectedWindowType) {
            WindowType.AUTO -> {
                // no fixed window type or size
                performanceProfile.windowType = Sdk.WindowTypeAuto
            }
            WindowType.QUALITY, WindowType.SPEED -> {
                performanceProfile.windowType =
                        if (selectedWindowType == WindowType.QUALITY) Sdk.WindowTypeQuality
                        else Sdk.WindowTypeSpeed

                val windowSizeSettings = WindowSizeSettings()
                windowSizeSettings.windowSizeMin = if (this.fixedIpSize) 1 else 2
                windowSizeSettings.windowSizeMax = if (this.fixedIpSize) 1 else 4
                performanceProfile.windowSize = windowSizeSettings
            }
        }

        deviceManager.performanceProfile = performanceProfile

    }

    private fun addListener(listener: (ConnectController) -> Sub) {
        connectVc?.let { subs.add(listener(it)) }
    }

    private val addGridListener = {
        addListener { vc -> vc.addGridListener { updateGrid() } }
    }

    val refreshContractStatus = {
        _contractStatus.value = viewControllerDevice?.contractStatus
    }

    private val addContractStatusListener = {

        // initialize contract status
        refreshContractStatus()

        val device = viewControllerDevice
        val sub = device?.addContractStatusChangeListener {
            viewModelScope.launch {
                if (viewControllerDevice === device) {
                    // out of balance never disconnects: connect stays requested so
                    // nothing leaves outside the tunnel. InsufficientBalanceMonitor
                    // alerts the user, who can upgrade or disconnect
                    refreshContractStatus()
                }
            }
        }
        sub?.let { subs.add(it) }
    }

    private fun updateGrid() {
        val grid = connectVc?.grid
        val newWindowCurrentSize: Int
        val newProviderGridPoints: Map<Id, ProviderGridPoint>

        val signature = StringBuilder()
        if (grid != null) {
            newWindowCurrentSize = grid.windowCurrentSize

            val updateProviderGridPointsList = grid.providerGridPointList
            val updateProviderGridPoints = mutableMapOf<Id, ProviderGridPoint>()
            for (i in 0 until updateProviderGridPointsList.len()) {
                val point = updateProviderGridPointsList.get(i)
                updateProviderGridPoints[point.clientId] = point
            }
            newProviderGridPoints = updateProviderGridPoints
            signature.append(newWindowCurrentSize).append(';')
            // the family is in the signature: a local downgrade changes a
            // provider's category without changing its state or position, and
            // the status row must follow it. the extender colors are in it for
            // the same reason: a transport migration changes the rings on a
            // dot that has not otherwise moved (K2)
            newProviderGridPoints.values
                .map {
                    "${it.clientId.idStr}:${it.state}:${it.x}:${it.y}:${it.ipFamily}" +
                            ":${it.extenderColorHexes}"
                }
                .sorted()
                .forEach { signature.append(it).append('|') }
        } else {
            newWindowCurrentSize = 0
            newProviderGridPoints = mapOf()
        }

        // skip the state assignment (and the GridCanvas recomposition + point
        // animation relaunch it triggers) when the grid is logically unchanged.
        // the SDK re-emits fresh Id/ProviderGridPoint proxies each callback, so
        // an unconditional assign would recompose on every event even when
        // nothing moved.
        val newSignature = signature.toString()
        if (newSignature == lastGridSignature) {
            return
        }
        lastGridSignature = newSignature

        val newIpFamilyPoints = newProviderGridPoints.values.map { point ->
            IpFamilyPoint(
                state = point.state,
                ipFamily = point.ipFamily,
            )
        }

        viewModelScope.launch {
            this@ConnectViewModel.grid = grid
            windowCurrentSize = newWindowCurrentSize
            providerGridPoints = newProviderGridPoints
            ipFamilyPoints = newIpFamilyPoints
            checkConnectFailure()
        }
    }

    val getStateColor: (ProviderPointState?) -> Color = { state ->
        when (state) {
            ProviderPointState.IN_EVALUATION -> Yellow
            ProviderPointState.EVALUATION_FAILED -> Red
            ProviderPointState.NOT_ADDED -> Red
            ProviderPointState.ADDED -> Green
            ProviderPointState.REMOVED -> Red
            else -> Color.Transparent
        }
    }

    /**
     * Folds the view controller's raw status and window into the connect failure
     * monitor and hands a failed attempt to MainApplication's whitelist probe,
     * whose own trigger (cellular, network country, cool-down) decides whether
     * it runs. It reads the raw status, so the pure policy needs no ui model.
     * Main thread.
     */
    private fun checkConnectFailure() {
        val failed = connectFailureMonitor.observe(
            connectRequested = viewControllerDevice?.connectEnabled == true,
            connectionStatus = connectVc?.connectionStatus,
            windowProviderCount = windowCurrentSize,
            nowMillis = System.currentTimeMillis(),
        )
        if (failed) {
            (appContext.applicationContext as? MainApplication)
                ?.maybeRunWhitelistProbe(connectFailed = true)
        }
        scheduleConnectFailureCheck()
    }

    // The time bound has to be checked even when no status or window event
    // arrives, so re-check when the pending attempt's bound elapses.
    private fun scheduleConnectFailureCheck() {
        val timeoutAtMillis = connectFailureMonitor.timeoutAtMillis()
        if (timeoutAtMillis == connectFailureCheckAtMillis) {
            return
        }
        connectFailureCheckJob?.cancel()
        connectFailureCheckJob = null
        connectFailureCheckAtMillis = timeoutAtMillis
        if (timeoutAtMillis == null) {
            return
        }
        connectFailureCheckJob = viewModelScope.launch {
            delay(maxOf(0L, timeoutAtMillis - System.currentTimeMillis()))
            connectFailureCheckJob = null
            connectFailureCheckAtMillis = null
            checkConnectFailure()
        }
    }

    /** Forgets the current attempt and its pending re-check: the device or view controller changed. */
    private fun resetConnectFailureMonitor() {
        connectFailureMonitor.reset()
        connectFailureCheckJob?.cancel()
        connectFailureCheckJob = null
        connectFailureCheckAtMillis = null
    }

    private fun updateConnectionStatus() {
        viewModelScope.launch { checkConnectFailure() }
        connectVc?.let { vc ->
            vc.connectionStatus?.let { status ->
                ConnectStatus.fromString(status)?.let { statusFromStr ->
                    viewModelScope.launch {
                        val wasConnected = _connectStatus.value == ConnectStatus.CONNECTED
                        _connectStatus.value = statusFromStr
                        connectDisplay.reported(statusFromStr)
                        updateDisplayReconnectTunnel()
                        if (statusFromStr == ConnectStatus.CONNECTED && !wasConnected) {
                            // the activation mark, once per network (the facade dedups)
                            com.bringyour.network.analytics.ClientEvents.connectFirst(
                                deviceManager.jwtFlow.value?.networkId?.toString()
                            )
                        }
                    }
                }
            }
        }
    }

    val addConnectionStatusListener = {
        // the sdk reports on its own thread, the queue's for a command's own
        // status: read on the main thread, with the rest of the view model
        addListener { vc -> vc.addConnectionStatusListener { viewModelScope.launch { updateConnectionStatus() } } }
    }

    private fun updateSelectedLocation() {
        connectVc?.let {
            val location = it.selectedLocation
            viewModelScope.launch { selectedLocation = location }
        }
    }

    val addSelectedLocationListener = {
        addListener { vc -> vc.addSelectedLocationListener { updateSelectedLocation() } }
    }

    val disconnect: () -> Unit = {
        // the user's disconnect: nothing is reconnected by itself after it,
        // and a start still waiting on its balance does not connect
        (appContext as? com.bringyour.network.MainApplication)?.clearBalanceRecovery()
        startConnect.cancel()
        runCommand(connect = false) { it.disconnect() }
    }

    /**
     * Queues `command`, a connect (`connect`) or a disconnect, on the connect
     * command queue, off the main thread ([ConnectCommands]). It runs on the
     * presented controller or, without one, on a controller opened for it on
     * the current device and closed after, so a connect or disconnect always
     * reaches the sdk. The presented controller is closed in the background,
     * and a start the gate admits after the app left the foreground found none
     * and was lost. The quick connect surfaces and the balance recovery connect
     * the same way, as the Apple app does without a presented controller
     * (ConnectViewModel.performOnSdk). The presented controller closes on the
     * queue after this command (closeConnectViewController). Main thread.
     */
    private fun runCommand(connect: Boolean, command: (ConnectController) -> Unit) {
        val presented = connectVc
        val device = (if (presented != null) viewControllerDevice else null) ?: currentDevice ?: return
        connectCommands.run(device = device.identity, connect = connect) {
            if (presented != null) {
                command(presented)
            } else {
                val vc = device.openConnectViewController()
                try {
                    command(vc)
                } finally {
                    device.closeViewController(vc)
                }
            }
        }
    }

    /**
     * The queue made or finished a command: until the last one made on this
     * device has run, the newest one's own first status stands in for the
     * sdk's, and then the controller's status is read again. A device change
     * drops the previous device's commands from the status.
     */
    private fun commandsChanged() {
        val pending = connectCommands.pending(currentDevice?.identity)
        connectDisplay.commandPending(
            pending?.let { connect -> if (connect) ConnectStatus.DESTINATION_SET else ConnectStatus.DISCONNECTED }
        )
        if (pending == null) {
            // the sdk has every command now
            updateConnectionStatus()
        }
    }

    val addTunnelListener: () -> Unit = {
        val device = viewControllerDevice
        val tunnelStarted = device?.tunnelStarted

        if (tunnelStarted == true) {
            this.tunnelConnected = true
            updateDisplayReconnectTunnel()
        }

        val sub =
                device?.addTunnelChangeListener { tunnelConnected ->
                    viewModelScope.launch {
                        if (viewControllerDevice === device) {
                            this@ConnectViewModel.tunnelConnected = tunnelConnected
                            updateDisplayReconnectTunnel()
                        }
                    }
                }

        // unwrap sub
        if (sub == null) {
            this.tunnelConnected = false
            updateDisplayReconnectTunnel()
        } else {
            this.subs.add(sub)
        }
    }

    val updateDisplayReconnectTunnel: () -> Unit = {
        if (this.connectStatus.value == ConnectStatus.CONNECTED && !this.tunnelConnected) {
            this.setDisplayReconnectTunnel(true)
        } else {
            this.setDisplayReconnectTunnel(false)
        }
    }

    init {
        loadPerformanceProfile()

        processLifecycle.addObserver(this)
        controllerOwner.setForeground(
            processLifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        )
        removeCommandsListener = connectCommands.addListener { commandsChanged() }
        removeStartListener = startConnect.addListener {
            connectDisplay.startWaiting(startConnect.waiting)
        }
        connectDisplay.startWaiting(startConnect.waiting)
        removeDeviceChangeListener = deviceChanges { device ->
            viewModelScope.launch {
                setupDevice(device)
            }
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        controllerOwner.setForeground(true)
    }

    override fun onStop(owner: LifecycleOwner) {
        controllerOwner.setForeground(false)
    }

    private fun loadPerformanceProfile() {
        // A null profile and window type auto mean the same thing; the
        // orthogonal settings are read off the profile with false defaults.
        val performanceProfile = deviceManager.performanceProfile
        selectedWindowType =
            WindowType.fromRawValueOrDefault(performanceProfile?.windowType.orEmpty())
        allowDirect = performanceProfile?.allowDirect ?: false
        postQuantumEncryption = performanceProfile?.postQuantumEncryption ?: false
        val windowSize = performanceProfile?.windowSize
        fixedIpSize = windowSize != null &&
            windowSize.windowSizeMin.toInt() == 1 &&
            windowSize.windowSizeMax.toInt() == 1
    }

    private fun setupDevice(device: ConnectDevice?) {
        selectedLocation = null
        grid = null
        providerGridPoints = mapOf()
        windowCurrentSize = 0
        lastGridSignature = ""
        tunnelConnected = false
        displayReconnectTunnel = false
        _contractStatus.value = null
        resetConnectFailureMonitor()
        // a start waiting on its balance belonged to the previous device
        if (currentDevice != null && currentDevice?.identity !== device?.identity) {
            startConnect.cancel()
        }
        currentDevice = device
        if (device == null) {
            _connectStatus.value = ConnectStatus.DISCONNECTED
            connectDisplay.reported(ConnectStatus.DISCONNECTED)
        } else {
            loadPerformanceProfile()
        }
        controllerOwner.setDevice(device)
        // the previous device's commands no longer show
        commandsChanged()
    }

    private fun openConnectViewController(device: ConnectDevice): ConnectController {
        val vc = device.openConnectViewController()
        viewControllerDevice = device
        connectVc = vc
        addTunnelListener()
        addSelectedLocationListener()
        addGridListener()
        addConnectionStatusListener()
        addContractStatusListener()
        vc.start()
        update()
        return vc
    }

    private fun closeConnectViewController(device: ConnectDevice, vc: ConnectController) {
        // on the queue, after the commands made so far, which may still run on
        // this controller: a closed controller refuses a disconnect, so one
        // tapped just before the app left the foreground was lost. The closes
        // also take the device's locks, which a command holds while it runs.
        // A listener reporting until then reads the current controller, or
        // checks its device
        val closingSubs = subs.toList()
        subs.clear()
        connectCommands.afterCommands {
            closingSubs.forEach { sub -> sub.close() }
            vc.stop()
            device.closeViewController(vc)
        }
        if (connectVc === vc) {
            connectVc = null
            viewControllerDevice = null
            grid = null
            providerGridPoints = mapOf()
            windowCurrentSize = 0
            lastGridSignature = ""
            resetConnectFailureMonitor()
        }
    }

    fun update() {
        updateSelectedLocation()
        updateGrid()
        updateConnectionStatus()
    }

    override fun onCleared() {
        removeDeviceChangeListener?.invoke()
        removeDeviceChangeListener = null
        removeCommandsListener?.invoke()
        removeCommandsListener = null
        removeStartListener?.invoke()
        removeStartListener = null
        processLifecycle.removeObserver(this)
        // a start still waiting on its balance never connects once the
        // screen is gone, as the Apple app's is dropped with its view model
        startConnect.cancel(this)
        currentDevice = null
        controllerOwner.close()
        super.onCleared()
    }
}

enum class ProviderPointState {
    IN_EVALUATION,
    EVALUATION_FAILED,
    NOT_ADDED,
    ADDED,
    REMOVED;

    companion object {
        fun fromString(value: String): ProviderPointState? {
            return when (value) {
                "InEvaluation" -> IN_EVALUATION
                "EvaluationFailed" -> EVALUATION_FAILED
                "NotAdded" -> NOT_ADDED
                "Added" -> ADDED
                "Removed" -> REMOVED
                else -> null
            }
        }
    }
}

data class AnimatedSuccessPoint(
        val initialOffset: Offset,
        val targetOffset: Offset,
        val center: Animatable<Offset, AnimationVector2D> =
                Animatable(Offset(-500f, 0f), Offset.VectorConverter),
        val color: Color,
        val radius: Float
)

enum class WindowType(val rawValue: String) {
    AUTO("auto"),
    QUALITY("quality"),
    SPEED("speed");

    // display names are localized at the UI layer (window_type_* store keys,
    // see WindowTypeButtonText)

    companion object {
        val default = AUTO

        fun fromRawValue(value: String): WindowType? {
            return entries.find { it.rawValue == value }
        }
        
        fun fromRawValueOrDefault(value: String): WindowType {
            return entries.find { it.rawValue == value } ?: default
        }
    }
}
