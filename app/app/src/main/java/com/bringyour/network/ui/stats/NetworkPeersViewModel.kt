package com.bringyour.network.ui.stats

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bringyour.network.DeviceManager
import com.bringyour.network.ForegroundDeviceControllerOwner
import com.bringyour.sdk.ConnectLocation
import com.bringyour.sdk.ConnectLocationId
import com.bringyour.sdk.DeviceLocal
import com.bringyour.sdk.PeerViewController
import com.bringyour.sdk.ProvideSecretKeyList
import com.bringyour.sdk.Sdk
import com.bringyour.sdk.Sub
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * A connected peer of this device on the network
 */
data class NetworkPeerUi(
    val clientId: String,
    val deviceName: String,
    val deviceSpec: String,
    val provideEnabled: Boolean,
) {
    /**
     * the device name, or the device spec if the name is not available
     */
    val displayName: String
        get() = when {
            deviceName.isNotEmpty() -> deviceName
            deviceSpec.isNotEmpty() -> deviceSpec
            else -> clientId
        }
}

/**
 * Publishes the connected network peers with provide enabled.
 *
 * Uses the change listener plus one initial get — no polling. Devices
 * without a provider have no network peers and the list stays empty.
 */
@HiltViewModel
class NetworkPeersViewModel @Inject constructor(
    private val deviceManager: DeviceManager,
) : ViewModel(), DefaultLifecycleObserver {

    private val subs = mutableListOf<Sub>()
    private var viewControllerDevice: DeviceLocal? = null
    private var peerVc: PeerViewController? = null
    private var removeDeviceChangeListener: (() -> Unit)? = null
    private val processLifecycle = ProcessLifecycleOwner.get().lifecycle
    private val controllerOwner =
        ForegroundDeviceControllerOwner<DeviceLocal, PeerViewController>(
            open = { openPeerViewController(it) },
            close = { device, vc -> closePeerViewController(device, vc) },
        )

    var connectedProvidePeers by mutableStateOf<List<NetworkPeerUi>>(listOf())
        private set

    // ALL connected peers, whether or not they provide — the "You have {n}
    // other devices online" count. Connecting to a peer still requires
    // provide, which is what the filtered list above captures.
    var connectedCount by mutableStateOf(0)
        private set

    var provideEnabled by mutableStateOf(false)
        private set

    // whether the provider currently holds a Network-mode provide key — i.e. this
    // device is providing to same-network peers
    var providerHasNetworkKey by mutableStateOf(false)
        private set

    // this device's editable network name (what peers see), from the network
    // client record. Empty until loaded.
    var deviceName by mutableStateOf("")
        private set

    // true when the device is providing to same-network peers and can accept a
    // peer connection — drives the connect screen's "discoverable as {name}" line.
    // Provide paused deliberately does NOT gate this: pause stops public provide
    // but keeps the Network mode announced and verifiable, so a paused device is
    // still connectable by its network peers.
    val providerDiscoverable: Boolean
        get() = provideEnabled && providerHasNetworkKey

    init {
        processLifecycle.addObserver(this)
        controllerOwner.setForeground(
            processLifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        )
        // the device is (re)created asynchronously (login, network change) and
        // this view model can be created first — wire per device, every time,
        // never once at init (the init-once `device?.let` left the peers
        // pipeline dead for the whole session on cold start)
        removeDeviceChangeListener = deviceManager.addDeviceChangeListener { device ->
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

    private fun setupDevice(device: DeviceLocal?) {
        connectedProvidePeers = listOf()
        connectedCount = 0
        provideEnabled = false
        providerHasNetworkKey = false
        deviceName = ""
        controllerOwner.setDevice(device)
    }

    private fun openPeerViewController(device: DeviceLocal): PeerViewController {
        // the SDK peer view controller carries both the connected count and
        // the connectable (provide-enabled) peers
        val vc = device.openPeerViewController()
        viewControllerDevice = device
        peerVc = vc
        subs.add(vc.addPeersListener {
            viewModelScope.launch {
                update()
            }
        })
        vc.start()

        // discoverability signals for the line under the peers count
        provideEnabled = device.provideEnabled
        providerHasNetworkKey = keysContainNetwork(device.provideSecretKeys)
        subs.add(device.addProvideChangeListener { enabled ->
            viewModelScope.launch {
                if (viewControllerDevice === device) {
                    provideEnabled = enabled
                }
            }
        })
        subs.add(device.addProvideSecretKeysListener { keys ->
            val hasNetworkKey = keysContainNetwork(keys)
            viewModelScope.launch {
                if (viewControllerDevice === device) {
                    providerHasNetworkKey = hasNetworkKey
                }
            }
        })
        fetchDeviceName(device)
        return vc
    }

    private fun closePeerViewController(device: DeviceLocal, vc: PeerViewController) {
        subs.forEach { it.close() }
        subs.clear()
        vc.stop()
        device.closeViewController(vc)
        if (peerVc === vc) {
            peerVc = null
            viewControllerDevice = null
        }
    }

    private fun keysContainNetwork(keys: ProvideSecretKeyList?): Boolean {
        keys ?: return false
        for (i in 0 until keys.len()) {
            val key = keys.get(i) ?: continue
            if (key.provideMode == Sdk.ProvideModeNetwork) {
                return true
            }
        }
        return false
    }

    // this device's editable network name, from its network client record
    private fun fetchDeviceName(device: DeviceLocal) {
        val clientId = device.clientId?.idStr ?: return
        device.api?.getNetworkClients { result, _ ->
            val clients = result?.clients ?: return@getNetworkClients
            for (i in 0 until clients.len()) {
                val info = clients.get(i) ?: continue
                if (info.clientId?.idStr == clientId) {
                    viewModelScope.launch {
                        if (viewControllerDevice === device) {
                            deviceName = info.deviceName.ifEmpty { info.deviceDescription }
                        }
                    }
                    break
                }
            }
        }
    }

    private fun update() {
        val vc = peerVc ?: return
        val peers = mutableListOf<NetworkPeerUi>()
        val list = vc.peers
        if (list != null) {
            val n = list.len()
            for (i in 0 until n) {
                val peer = list.get(i) ?: continue
                val clientId = peer.clientId ?: continue
                peers.add(
                    NetworkPeerUi(
                        clientId = clientId.idStr,
                        deviceName = peer.deviceName,
                        deviceSpec = peer.deviceSpec,
                        provideEnabled = peer.provideEnabled,
                    )
                )
            }
        }
        connectedProvidePeers = peers
        connectedCount = vc.connectedCount.toInt()
    }

    /**
     * a direct connection to this peer device
     */
    fun connectLocationForPeer(peer: NetworkPeerUi): ConnectLocation? {
        val vc = peerVc ?: return null
        // reconstruct the SDK client id (the ConnectLocationId needs the Id object, not its string)
        val list = vc.peers ?: return null
        val n = list.len()
        for (i in 0 until n) {
            val p = list.get(i) ?: continue
            val clientId = p.clientId ?: continue
            if (clientId.idStr == peer.clientId) {
                val location = ConnectLocation()
                val locationId = ConnectLocationId()
                locationId.clientId = clientId
                location.connectLocationId = locationId
                location.name = peer.displayName
                // one of the user's own devices from the peer list — a trusted
                // same-network peer, so it egresses under Network provide mode
                location.networkPeer = true
                return location
            }
        }
        return null
    }

    override fun onCleared() {
        removeDeviceChangeListener?.invoke()
        removeDeviceChangeListener = null
        processLifecycle.removeObserver(this)
        controllerOwner.close()
        super.onCleared()
    }
}
