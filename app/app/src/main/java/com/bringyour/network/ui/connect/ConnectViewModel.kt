package com.bringyour.network.ui.connect

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
import com.bringyour.client.ConnectGrid
import com.bringyour.client.ConnectLocation
import com.bringyour.client.ConnectViewController
import com.bringyour.client.Id
import com.bringyour.client.ProviderGridPoint
import com.bringyour.client.Sub
import com.bringyour.network.ByDeviceManager
import com.bringyour.network.NetworkSpaceManagerProvider
import com.bringyour.network.ui.theme.BlueLight
import com.bringyour.network.ui.theme.Green
import com.bringyour.network.ui.theme.Pink
import com.bringyour.network.ui.theme.Red
import com.bringyour.network.ui.theme.Yellow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ConnectViewModel @Inject constructor(
    private val byDeviceManager: ByDeviceManager,
    private val networkSpaceManagerProvider: NetworkSpaceManagerProvider
): ViewModel() {

    private var connectVc: ConnectViewController? = null

    private val subs = mutableListOf<Sub>()

    private val _connectStatus = MutableStateFlow(ConnectStatus.DISCONNECTED)
    val connectStatus: StateFlow<ConnectStatus> get() = _connectStatus

    var selectedLocation by mutableStateOf<ConnectLocation?>(null)
        private set

    var windowCurrentSize by mutableIntStateOf(0)
        private set
    
    var providerGridPoints by mutableStateOf<Map<Id, ProviderGridPoint>>(mapOf())
        private set

    var grid by mutableStateOf<ConnectGrid?>(null)
        private set

    private val successPoints = mutableListOf<AnimatedSuccessPoint>()

    val canvasSize = 248.dp

    val shuffledSuccessPoints = mutableListOf<AnimatedSuccessPoint>()

    val initSuccessPoints: (Float) -> Unit = { canvasSizePx ->
        successPoints.addAll(
            listOf(
                AnimatedSuccessPoint(
                    initialOffset = Offset(
                        -canvasSizePx.times(1.1f),
                        canvasSizePx.times(0.95f)
                    ),
                    targetOffset = Offset(
                        canvasSizePx.times(.4f),
                        canvasSizePx.times(0.95f)
                    ),
                    color = Red,
                    radius = canvasSizePx
                ),
                AnimatedSuccessPoint(
                    initialOffset = Offset(
                        canvasSizePx.times(2.5f),
                        canvasSizePx.times(3.25f)
                    )
                    ,
                    targetOffset = Offset(
                        canvasSizePx.times(2),
                        canvasSizePx.times(2)
                    ),
                    color = Pink,
                    radius = canvasSizePx
                ),
                AnimatedSuccessPoint(
                    initialOffset = Offset(
                        canvasSizePx.times(3.5f),
                        canvasSizePx.times(0.3f)
                    ),
                    targetOffset = Offset(
                        canvasSizePx.times(2.25f),
                        canvasSizePx.times(0.8f)
                    ),
                    color = Green,
                    radius = canvasSizePx
                ),
                AnimatedSuccessPoint(
                    initialOffset = Offset(
                        canvasSizePx.times(0.8f),
                        -canvasSizePx.times(1.5f)
                    ),
                    targetOffset = Offset(
                        canvasSizePx.times(0.8f),
                        canvasSizePx.times(0.05f)
                    ),
                    color = Yellow,
                    radius = canvasSizePx
                ),
                AnimatedSuccessPoint(
                    initialOffset = Offset(
                        -canvasSizePx.times(1.5f),
                        canvasSizePx.times(3f)
                    ),
                    targetOffset = Offset(
                        canvasSizePx.times(.75f),
                        canvasSizePx.times(2.25f)
                    ),
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

    private fun addListener(listener: (ConnectViewController) -> Sub) {
        connectVc?.let {
            subs.add(listener(it))
        }
    }

//    private val addWindowEventSizeListener = {
//
//        addListener { vc ->
//            vc.addWindowSizeListener {
//                viewModelScope.launch {
//                    windowCurrentSize = vc.grid?.windowCurrentSize ?: 0
//                }
//            }
//        }
//    }

    private val addGridListener = {
        addListener { vc ->
            vc.addGridListener {
                viewModelScope.launch {
                    updateGrid()

                }
            }
        }
    }

    private fun updateGrid() {
        val grid = connectVc?.grid
        this.grid = grid
        grid?.let {
            windowCurrentSize = it.windowCurrentSize

            val updateProviderGridPointsList = it.providerGridPointList
            val updateProviderGridPoints = mutableMapOf<Id, ProviderGridPoint>()
            for (i in 0 until updateProviderGridPointsList.len()) {
                val point = updateProviderGridPointsList.get(i)
                updateProviderGridPoints[point.clientId] = point
            }
            providerGridPoints = updateProviderGridPoints
        } ?: run {
            windowCurrentSize = 0
            providerGridPoints = mapOf()
        }
    }

    private fun updateConnectionStatus() {
        connectVc?.let { vc ->
            vc.connectionStatus?.let { status ->
                ConnectStatus.fromString(status)?.let { statusFromStr ->
                    viewModelScope.launch {
                        _connectStatus.value = statusFromStr

//                        if (statusFromStr == ConnectStatus.DISCONNECTED) {
//                            windowCurrentSize = 0
//                        }
                    }
                }
            }
        }
    }

    val addConnectionStatusListener = {
        addListener { vc ->
            vc.addConnectionStatusListener {
                updateConnectionStatus()
            }
        }
    }

    private fun updateSelectedLocation() {

        connectVc?.let {
            selectedLocation = it.selectedLocation
        }
    }

    val addSelectedLocationListener = {
        addListener { vc ->
            vc.addSelectedLocationListener {
                updateSelectedLocation()
            }
        }
    }

    val disconnect: () -> Unit = {
        connectVc?.disconnect()
    }

    val cancelConnection: () -> Unit = {
        connectVc?.disconnect()
    }

    init {

        val byDevice = byDeviceManager.byDevice
        connectVc = byDevice?.openConnectViewController()


//        addProviderGridPointChangedListener()

        addSelectedLocationListener()
        addGridListener()
        addConnectionStatusListener()
//        addWindowEventSizeListener()


        updateSelectedLocation()
        updateGrid()
        updateConnectionStatus()
    }

    override fun onCleared() {
        super.onCleared()

        subs.forEach { sub ->
            sub.close()
        }
        subs.clear()

//        connectVc?.let {
//            byDeviceManager.getByDevice()?.closeViewController(it)
//        }

        viewModelScope.cancel()
    }
}

enum class ConnectStatus {
    DISCONNECTED,
    CONNECTING,
    DESTINATION_SET,
    CONNECTED,
    CANCELING;

    companion object {
        fun fromString(value: String): ConnectStatus? {
            return when (value.uppercase()) {
                "DISCONNECTED" -> DISCONNECTED
                "CONNECTING" -> CONNECTING
                "DESTINATION_SET" -> DESTINATION_SET
                "CONNECTED" -> CONNECTED
                "CANCELING" -> CANCELING
                else -> null
            }
        }

        fun toString(value: ConnectStatus): String {
            return when (value) {
                DISCONNECTED -> "DISCONNECTED"
                CONNECTING -> "CONNECTING"
                DESTINATION_SET -> "DESTINATION_SET"
                CONNECTED -> "CONNECTED"
                CANCELING -> "CANCELING"
            }
        }
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
    val center: Animatable<Offset, AnimationVector2D> = Animatable(Offset(-500f, 0f), Offset.VectorConverter),
    val color: Color,
    val radius: Float
)