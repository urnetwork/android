package com.bringyour.network.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bringyour.hotspot.HotspotProxy
import com.bringyour.hotspot.HotspotProxyStatus
import com.bringyour.network.HotspotProxyManager
import com.bringyour.sdk.Sub
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Account > Settings > Hotspot proxy: the current device's hotspot proxy
 * (HotspotProxyManager). The sdk owns the choice, the credentials and where
 * the proxy listens; this shows its status and passes the user's actions on,
 * off the main thread, since they bind and close listeners.
 */
@HiltViewModel
class HotspotProxyViewModel @Inject constructor(
    private val hotspotProxyManager: HotspotProxyManager,
) : ViewModel() {

    var status by mutableStateOf(HotspotProxyUi())
        private set

    private var subscribedProxy: HotspotProxy? = null
    private var statusSub: Sub? = null

    init {
        viewModelScope.launch {
            hotspotProxyManager.proxy.collect { proxy -> subscribe(proxy) }
        }
        // the screen opens after the hotspot may have changed
        hotspotProxyManager.refresh()
    }

    private fun subscribe(proxy: HotspotProxy?) {
        statusSub?.close()
        statusSub = null
        subscribedProxy = proxy
        if (proxy == null) {
            status = HotspotProxyUi()
            return
        }
        statusSub = proxy.addStatusChangeListener {
            viewModelScope.launch { load(proxy) }
        }
        viewModelScope.launch { load(proxy) }
    }

    private suspend fun load(proxy: HotspotProxy) {
        val next = withContext(Dispatchers.IO) { hotspotProxyUi(proxy.status) }
        if (subscribedProxy === proxy) {
            status = next
        }
    }

    fun setEnabled(enabled: Boolean) {
        val proxy = subscribedProxy ?: return
        viewModelScope.launch(Dispatchers.IO) { proxy.setEnabled(enabled) }
    }

    /** New credentials, which disconnect the devices signed in with the old ones. */
    fun regenerateCredentials() {
        val proxy = subscribedProxy ?: return
        viewModelScope.launch(Dispatchers.IO) { proxy.regenerateCredentials() }
    }

    override fun onCleared() {
        statusSub?.close()
        statusSub = null
        super.onCleared()
    }
}

/** What the screen shows of the proxy's status. available is false without a device. */
data class HotspotProxyUi(
    val available: Boolean = false,
    val enabled: Boolean = false,
    val listening: Boolean = false,
    val errorId: String = "",
    val username: String = "",
    val password: String = "",
    val endpoints: List<HotspotProxyEndpointUi> = emptyList(),
) {
    /**
     * The details another device needs, as proxy urls with the credentials:
     * the same text in every language, since tools read them.
     */
    val connectionDetails: String
        get() = endpoints.flatMap { listOf(it.httpProxyUrl, it.socksProxyUrl) }.joinToString("\n")
}

data class HotspotProxyEndpointUi(
    val interfaceName: String,
    val host: String,
    val httpPort: Long,
    val socksPort: Long,
    val httpProxyUrl: String,
    val socksProxyUrl: String,
)

private fun hotspotProxyUi(status: HotspotProxyStatus): HotspotProxyUi {
    val endpoints = status.endpoints
    return HotspotProxyUi(
        available = true,
        enabled = status.enabled,
        listening = status.listening,
        errorId = status.errorId,
        username = status.username,
        password = status.password,
        endpoints = (0 until (endpoints?.len() ?: 0L)).map { i ->
            val endpoint = endpoints.get(i)
            HotspotProxyEndpointUi(
                interfaceName = endpoint.interfaceName,
                host = endpoint.host,
                httpPort = endpoint.httpPort,
                socksPort = endpoint.socksPort,
                httpProxyUrl = endpoint.httpProxyUrl,
                socksProxyUrl = endpoint.socksProxyUrl,
            )
        },
    )
}
