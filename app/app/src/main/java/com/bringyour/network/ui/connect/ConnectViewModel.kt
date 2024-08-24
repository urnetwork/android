package com.bringyour.network.ui.connect

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bringyour.client.BringYourDevice
import com.bringyour.client.ConnectLocation
import com.bringyour.client.Sub
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
    private val byDevice: BringYourDevice?,
): ViewModel() {

    private val connectVm = byDevice?.openConnectViewModel()

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

    private val getSelectedLocation = {
        selectedLocation = connectVm?.selectedLocation
    }

    private val getConnectionStatus = {
        val status = connectVm?.connectionStatus
        if (status != null) {
            val statusFromStr = ConnectStatus.fromString(status)
            if (statusFromStr != null) {
                viewModelScope.launch {
                    _connectStatus.value = statusFromStr
                }
            }
        }
    }

    val addConnectedProviderCountListener = {
        if (connectVm != null) {
            subs.add(connectVm.addConnectedProviderCountListener { count ->
                viewModelScope.launch {
                    connectedProviderCount = count
                }
            })
        }
    }

    val addConnectionStatusListener = {
        if (connectVm != null) {
            subs.add(connectVm.addConnectionStatusListener {
                getConnectionStatus()
            })
        }
    }

    val addSelectedLocationListener = {
        if (connectVm != null) {
            subs.add(connectVm.addSelectedLocationListener {
                getSelectedLocation()
            })
        }
    }

    val disconnect = {
        connectVm?.disconnect()
    }

    val cancelConnection = {
        connectVm?.cancelConnection()
    }

    init {
        getConnectionStatus()
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
            byDevice?.closeViewController(it)
        }

        viewModelScope.cancel()
    }

}
