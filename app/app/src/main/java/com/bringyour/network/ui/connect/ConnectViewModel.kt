package com.bringyour.network.ui.connect

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bringyour.client.ConnectLocation
import com.bringyour.client.ConnectViewControllerV0
import com.bringyour.client.Sub
import com.bringyour.network.ByDeviceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ConnectStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    CANCELING;

    companion object {
        fun fromString(value: String): ConnectStatus? {
            return when (value.uppercase()) {
                "DISCONNECTED" -> DISCONNECTED
                "CONNECTING" -> CONNECTING
                "CONNECTED" -> CONNECTED
                "CANCELING" ->CANCELING
                else -> null
            }
        }
    }
}

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

    var connectedProviderCount by mutableIntStateOf(0)
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

    val addConnectedProviderCountListener = {

        addListener { vc ->
            vc.addConnectedProviderCountListener { count ->
                viewModelScope.launch {
                    connectedProviderCount = count
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

        updateConnectionStatus()

        addConnectionStatusListener()
        addConnectedProviderCountListener()
        addSelectedLocationListener()
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
