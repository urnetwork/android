package com.bringyour.network

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import androidx.core.content.ContextCompat
import com.bringyour.hotspot.Hotspot
import com.bringyour.hotspot.HotspotInterfaceList
import com.bringyour.hotspot.HotspotProxy
import com.bringyour.sdk.DeviceLocal
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.NetworkInterface
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The hotspot proxy of the current device (the sdk's hotspot package) and the
 * report of this phone's tethering interfaces that it picks its addresses from.
 *
 * The sdk decides everything that keeps the proxy safe: it is off until the
 * user turns it on, it listens only on a tethering downstream's private IPv4
 * address (never on the phone's own cellular, wi-fi or vpn network), it needs
 * the generated credentials, and it dials only through the tunnel. This class
 * only reports facts and follows the device:
 *  - one proxy per DeviceLocal, closed when the device is replaced; the next
 *    device's proxy reads the space's stored choice;
 *  - the tethering downstreams are the active tether list of the sticky
 *    TETHER_STATE_CHANGED broadcast, their addresses come from
 *    NetworkInterface, and the interfaces of the phone's own networks
 *    (ConnectivityManager) are reported as upstream so the sdk refuses them.
 *
 * Its state and every sdk call stay on one handler thread, since an sdk call
 * can bind or close listeners.
 */
@Singleton
class HotspotProxyManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val deviceManager: DeviceManager,
) {

    companion object {
        private const val TAG = "HotspotProxyManager"

        // The values of ConnectivityManager.ACTION_TETHER_STATE_CHANGED and
        // EXTRA_ACTIVE_TETHER (TetheringManager's since Android 11), which the
        // public SDK hides. A protected system broadcast, kept sticky, so
        // registering returns the current state.
        private const val ACTION_TETHER_STATE_CHANGED = "android.net.conn.TETHER_STATE_CHANGED"
        private const val EXTRA_ACTIVE_TETHER = "tetherArray"

        // a tethering interface gets its address a moment after its state
        // changes, so the report repeats once that has settled
        private const val SETTLED_REPORT_DELAY_MILLIS = 3_000L
    }

    private val handlerThread = HandlerThread("hotspot_proxy").apply { start() }
    private val handler = Handler(handlerThread.looper)

    private val _proxy = MutableStateFlow<HotspotProxy?>(null)

    /** The current device's proxy, or null while there is no device. */
    val proxy: StateFlow<HotspotProxy?> = _proxy.asStateFlow()

    // confined to the handler thread
    private var started = false
    private var device: DeviceLocal? = null
    private var tetheredInterfaceNames: List<String> = emptyList()
    private val settledReport = Runnable { report() }

    /** Follows the device and the tethering state for the app's lifetime. */
    fun start() {
        handler.post {
            if (started) {
                return@post
            }
            started = true
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    // delivered on the handler thread
                    onTetheredInterfaces(activeTetheredInterfaceNames(intent))
                }
            }
            val current = ContextCompat.registerReceiver(
                context,
                receiver,
                IntentFilter(ACTION_TETHER_STATE_CHANGED),
                null,
                handler,
                // a protected system broadcast: an exported context receiver
                // accepts the framework sender and no app can send it
                ContextCompat.RECEIVER_EXPORTED,
            )
            tetheredInterfaceNames = activeTetheredInterfaceNames(current)
            deviceManager.addDeviceChangeListener { next ->
                handler.post { onDevice(next) }
            }
        }
    }

    /** Reports the interfaces again, for a screen that shows the proxy. */
    fun refresh() {
        handler.post { report() }
    }

    private fun activeTetheredInterfaceNames(intent: Intent?): List<String> =
        intent?.getStringArrayListExtra(EXTRA_ACTIVE_TETHER)?.filterNotNull()?.distinct() ?: emptyList()

    private fun onTetheredInterfaces(names: List<String>) {
        tetheredInterfaceNames = names
        Log.i(TAG, "tethered interfaces: ${names.size}")
        report()
        handler.removeCallbacks(settledReport)
        handler.postDelayed(settledReport, SETTLED_REPORT_DELAY_MILLIS)
    }

    private fun onDevice(next: DeviceLocal?) {
        if (next === device) {
            return
        }
        device = next
        // the old proxy frees its ports before the new one listens on them
        _proxy.value?.let { previous ->
            _proxy.value = null
            runCatching { previous.close() }
        }
        _proxy.value = next?.let { Hotspot.newHotspotProxyWithDefaults(it) }
        report()
    }

    private fun report() {
        val proxy = _proxy.value ?: return
        runCatching { proxy.setInterfaces(interfaceReport()) }
            .onFailure { Log.w(TAG, "interface report failed", it) }
    }

    /** The tethered interfaces with their addresses, in prefix form. */
    private fun interfaceReport(): HotspotInterfaceList {
        val interfaceList = Hotspot.newHotspotInterfaceList()
        val upstreamNames = upstreamInterfaceNames()
        for (name in tetheredInterfaceNames) {
            val hotspotInterface = Hotspot.newHotspotInterface()
            hotspotInterface.name = name
            hotspotInterface.tethered = true
            hotspotInterface.upstream = name in upstreamNames
            val networkInterface = runCatching { NetworkInterface.getByName(name) }.getOrNull()
            networkInterface?.interfaceAddresses?.forEach { interfaceAddress ->
                val hostAddress = interfaceAddress.address?.hostAddress ?: return@forEach
                hotspotInterface.addresses.add("$hostAddress/${interfaceAddress.networkPrefixLength}")
            }
            interfaceList.add(hotspotInterface)
        }
        return interfaceList
    }

    /** The interfaces of the phone's own networks: cellular, wi-fi, ethernet, vpn. */
    @Suppress("DEPRECATION")
    private fun upstreamInterfaceNames(): Set<String> {
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
            ?: return emptySet()
        return runCatching {
            connectivityManager.allNetworks.mapNotNullTo(mutableSetOf()) { network ->
                connectivityManager.getLinkProperties(network)?.interfaceName
            }
        }.getOrDefault(emptySet())
    }
}
