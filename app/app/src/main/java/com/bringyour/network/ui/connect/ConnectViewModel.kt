package com.bringyour.network.ui.connect

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bringyour.client.ConnectGrid
import com.bringyour.client.ConnectLocation
import com.bringyour.client.ConnectViewControllerV0
import com.bringyour.client.ProviderGridPoint
import com.bringyour.client.Sub
import com.bringyour.network.ByDeviceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ConnectViewModel @Inject constructor(
    private val byDeviceManager: ByDeviceManager
): ViewModel() {

    private var connectVc: ConnectViewControllerV0? = null

    private val subs = mutableListOf<Sub>()

    private val _connectStatus = MutableStateFlow(ConnectStatus.DISCONNECTED)
    val connectStatus: StateFlow<ConnectStatus> get() = _connectStatus

    var selectedLocation by mutableStateOf<ConnectLocation?>(null)
        private set

    var windowCurrentSize by mutableIntStateOf(0)
        private set

    val providerGridPoints = mutableStateListOf<ProviderGridPoint>()

    var grid by mutableStateOf<ConnectGrid?>(null)
        private set

    val connect: (ConnectLocation?) -> Unit = { location ->
        if (location != null) {
            connectVc?.connect(location)
        } else {
            connectVc?.connectBestAvailable()
        }
    }

    private fun addListener(listener: (ConnectViewControllerV0) -> Sub) {
        connectVc?.let {
            subs.add(listener(it))
        }
    }

    private val addWindowEventSizeListener = {

        addListener { vc ->
            vc.addWindowEventSizeListener {
                viewModelScope.launch {
                    windowCurrentSize = vc.windowCurrentSize
                    grid = vc.grid
                }
            }
        }
    }

    private val addProviderGridPointChangedListener = {
        addListener { vc ->
            vc.addProviderGridPointListener {
                viewModelScope.launch {

                    val providerGridPointList = vc.providerGridPointList
                    val updatedPoints = mutableListOf<ProviderGridPoint>()
                    val n = providerGridPointList.len()

                    providerGridPoints.clear()

                    for (i in 0 until n) {
                        updatedPoints.add(providerGridPointList.get(i))
                    }

                    providerGridPoints.addAll(updatedPoints)

                }
            }
        }
    }

    private fun updateConnectionStatus() {
        connectVc?.let { vc ->
            vc.connectionStatus?.let { status ->
                ConnectStatus.fromString(status)?.let { statusFromStr ->
                    viewModelScope.launch {
                        _connectStatus.value = statusFromStr

                        if (statusFromStr == ConnectStatus.DISCONNECTED) {
                            windowCurrentSize = 0
                        }
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
        connectVc?.cancelConnection()
    }

    init {

        val byDevice = byDeviceManager.getByDevice()
        connectVc = byDevice?.openConnectViewControllerV0()
        connectVc?.start()

        updateConnectionStatus()

        addProviderGridPointChangedListener()
        addConnectionStatusListener()
        addSelectedLocationListener()
        addWindowEventSizeListener()
    }

    override fun onCleared() {
        super.onCleared()

        subs.forEach { sub ->
            sub.close()
        }
        subs.clear()

        connectVc?.let {
            byDeviceManager.getByDevice()?.closeViewController(it)
        }

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