package com.bringyour.network.ui.connect

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bringyour.client.ConnectLocation
import com.bringyour.client.ConnectViewModel
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
                else -> null // or throw IllegalArgumentException("Unknown ProvideMode: $value")
            }
        }
    }
}

@HiltViewModel
class ConnectViewModel @Inject constructor(
    private val byDeviceManager: ByDeviceManager
): ViewModel() {

    private var connectVm: ConnectViewModel? = null

    private val subs = mutableListOf<Sub>()

    private val _connectStatus = MutableStateFlow(ConnectStatus.DISCONNECTED)
    val connectStatus: StateFlow<ConnectStatus> get() = _connectStatus

    var selectedLocation by mutableStateOf<ConnectLocation?>(null)
        private set

    var connectedProviderCount by mutableIntStateOf(0)
        private set

    val connect: (ConnectLocation?) -> Unit = { location ->
        if (location != null) {
            connectVm?.connect(location)
        } else {
            connectVm?.connectBestAvailable()
        }
    }

    private fun addListener(listener: (ConnectViewModel) -> Sub) {
        connectVm?.let {
            subs.add(listener(it))
        }
    }

    val addConnectedProviderCountListener = {

        addListener { vm ->
            vm.addConnectedProviderCountListener { count ->
                viewModelScope.launch {
                    connectedProviderCount = count
                }
            }
        }
    }

    private fun updateConnectionStatus() {
        connectVm?.let { vm ->
            vm.connectionStatus?.let { status ->
                ConnectStatus.fromString(status)?.let { statusFromStr ->
                    viewModelScope.launch {
                        _connectStatus.value = statusFromStr
                    }
                }
            }
        }
    }

    val addConnectionStatusListener = {
        addListener { vm ->
            vm.addConnectionStatusListener {
                updateConnectionStatus()
            }
        }
    }

    private fun updateSelectedLocation() {
        connectVm?.let {
            selectedLocation = it.selectedLocation
        }
    }

    val addSelectedLocationListener = {
        addListener { vm ->
            vm.addSelectedLocationListener {
                updateSelectedLocation()
            }
        }
    }

    val disconnect = {
        connectVm?.disconnect()
    }

    val cancelConnection = {
        connectVm?.cancelConnection()
    }

    init {

        val byDevice = byDeviceManager.getByDevice()
        connectVm = byDevice?.openConnectViewModel()
        
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

        connectVm?.let {
            byDeviceManager.getByDevice()?.closeViewController(it)
        }

        viewModelScope.cancel()
    }

}
