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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bringyour.network.DeviceManager
import com.bringyour.network.TAG
import com.bringyour.network.ui.shared.models.ConnectStatus
import com.bringyour.network.ui.theme.BlueLight
import com.bringyour.network.ui.theme.Green
import com.bringyour.network.ui.theme.Pink
import com.bringyour.network.ui.theme.Red
import com.bringyour.network.ui.theme.Yellow
import com.bringyour.sdk.ConnectGrid
import com.bringyour.sdk.ConnectLocation
import com.bringyour.sdk.ConnectViewController
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

@HiltViewModel
class ConnectViewModel
@Inject
constructor(
        private val deviceManager: DeviceManager,
) : ViewModel() {

    private var connectVc: ConnectViewController? = null

    private val subs = mutableListOf<Sub>()

    private val _connectStatus = MutableStateFlow(ConnectStatus.DISCONNECTED)
    val connectStatus: StateFlow<ConnectStatus> = _connectStatus

    var selectedLocation by mutableStateOf<ConnectLocation?>(null)
        private set

    var windowCurrentSize by mutableIntStateOf(0)
        private set

    var providerGridPoints by mutableStateOf<Map<Id, ProviderGridPoint>>(mapOf())
        private set

    var grid by mutableStateOf<ConnectGrid?>(null)
        private set

    // var grid by mutableStateOf<ConnectGrid?>(null)
    private var tunnelConnected = false

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
        get() = this.deviceManager.device

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

    val connect: (ConnectLocation?) -> Unit = { location ->
        if (location != null) {
            connectVc?.connect(location)
        } else {
            connectVc?.connectBestAvailable()
        }
    }



    val updatePerformanceProfile: () -> Unit = {

        val windowType = when (selectedWindowType) {
            WindowType.AUTO -> null
            WindowType.QUALITY -> Sdk.WindowTypeQuality
            WindowType.SPEED -> Sdk.WindowTypeSpeed
        }

        if (windowType == null) {
            deviceManager.performanceProfile = null
        } else {
            val performanceProfile = PerformanceProfile()
            performanceProfile.windowType = windowType
            performanceProfile.allowDirect = allowDirect

            val windowSizeSettings = WindowSizeSettings()
            windowSizeSettings.windowSizeMin = if (this.fixedIpSize) 1 else 2
            windowSizeSettings.windowSizeMax = if (this.fixedIpSize) 1 else 4
            performanceProfile.windowSize = windowSizeSettings

            deviceManager.performanceProfile = performanceProfile
        }

    }

    private fun addListener(listener: (ConnectViewController) -> Sub) {
        connectVc?.let { subs.add(listener(it)) }
    }

    private val addGridListener = {
        addListener { vc -> vc.addGridListener { updateGrid() } }
    }

    val refreshContractStatus = { _contractStatus.value = deviceManager.device?.contractStatus }

    private val addContractStatusListener = {

        // initialize contract status
        refreshContractStatus()

        val sub = deviceManager.device?.addContractStatusChangeListener {
            viewModelScope.launch {
                refreshContractStatus()

                if (_contractStatus.value?.insufficientBalance == true &&
                                _connectStatus.value != ConnectStatus.DISCONNECTED
                ) {
                    disconnect()
                }
            }
        }
        sub?.let { subs.add(it) }
    }

    private fun updateGrid() {
        val grid = connectVc?.grid
        val newWindowCurrentSize: Int
        val newProviderGridPoints: Map<Id, ProviderGridPoint>

        if (grid != null) {
            newWindowCurrentSize = grid.windowCurrentSize

            val updateProviderGridPointsList = grid.providerGridPointList
            val updateProviderGridPoints = mutableMapOf<Id, ProviderGridPoint>()
            for (i in 0 until updateProviderGridPointsList.len()) {
                val point = updateProviderGridPointsList.get(i)
                updateProviderGridPoints[point.clientId] = point
            }
            newProviderGridPoints = updateProviderGridPoints
        } else {
            newWindowCurrentSize = 0
            newProviderGridPoints = mapOf()
        }

        viewModelScope.launch {
            this@ConnectViewModel.grid = grid
            windowCurrentSize = newWindowCurrentSize
            providerGridPoints = newProviderGridPoints
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

    private fun updateConnectionStatus() {
        connectVc?.let { vc ->
            vc.connectionStatus?.let { status ->
                ConnectStatus.fromString(status)?.let { statusFromStr ->
                    viewModelScope.launch {
                        _connectStatus.value = statusFromStr
                        updateDisplayReconnectTunnel()
                    }
                }
            }
        }
    }

    val addConnectionStatusListener = {
        addListener { vc -> vc.addConnectionStatusListener { updateConnectionStatus() } }
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

    val disconnect: () -> Unit = { connectVc?.disconnect() }

    val addTunnelListener: () -> Unit = {
        val tunnelStarted = deviceManager.device?.tunnelStarted

        if (tunnelStarted == true) {
            this.tunnelConnected = true
            updateDisplayReconnectTunnel()
        }

        val sub =
                deviceManager.device?.addTunnelChangeListener { tunnelConnected ->
                    viewModelScope.launch {
                        this@ConnectViewModel.tunnelConnected = tunnelConnected
                        updateDisplayReconnectTunnel()
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

        connectVc = deviceManager.device?.openConnectViewController()

        addTunnelListener()

        addSelectedLocationListener()
        addGridListener()
        addConnectionStatusListener()
        addContractStatusListener()

        val performanceProfile = deviceManager.performanceProfile
        if (performanceProfile != null) {
            fixedIpSize = performanceProfile.windowSize.windowSizeMin.toInt() == 1 &&
                    performanceProfile.windowSize.windowSizeMax.toInt() == 1
            selectedWindowType = WindowType.fromRawValueOrDefault(performanceProfile.windowType)
            allowDirect = performanceProfile.allowDirect
            updatePerformanceProfile()
        }

        update()
    }

    fun update() {
        updateSelectedLocation()
        updateGrid()
        updateConnectionStatus()
    }

    override fun onCleared() {
        super.onCleared()

        subs.forEach { sub -> sub.close() }
        subs.clear()

        connectVc?.let { deviceManager.device?.closeViewController(it) }
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

    val displayName: String
        get() = when (this) {
            AUTO -> "Auto"
            QUALITY -> "Web"
            SPEED -> "Streaming"
        }

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
