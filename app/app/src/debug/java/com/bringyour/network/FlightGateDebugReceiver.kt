package com.bringyour.network

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.util.Log
import com.bringyour.sdk.ConnectLocation
import com.bringyour.sdk.ConnectLocationId
import com.bringyour.network.ui.shared.models.ProvideControlMode
import com.bringyour.network.ui.shared.models.ProvideNetworkMode
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Debug-only hooks for the physical peer-to-peer rig (connect/FLIGHTGATEFIX.md
 * §10 Phase 4, connect/tools/flightgate-devices). Driven from adb with
 * `am broadcast -a <action> -n com.bringyour.network/.FlightGateDebugReceiver`:
 *
 * - `com.bringyour.network.debug.FG_LOGIN`: signs the network in with the
 *   credentials in `/data/local/tmp/flightgate-login` (line 1 user auth,
 *   line 2 password), so no secret ever appears on a command line or in a log.
 * - `com.bringyour.network.debug.FG_PROVIDE` (string extras `control` =
 *   never|network|always, `network` = wifi|all): provide settings, persisted
 *   through DeviceManager exactly like the settings screen.
 * - `com.bringyour.network.debug.FG_CONNECT_PEER` (string extra `name`, a
 *   case-insensitive substring of the peer's device name): connects to that
 *   network peer as a trusted same-network destination.
 * - `com.bringyour.network.debug.FG_DISCONNECT`.
 * - `com.bringyour.network.debug.FG_STATUS`: logs one JSON line of the
 *   sign-in, connect and provide state and the peer list.
 *
 * Every outcome is one `FlightGate` logcat line; nothing is returned to adb.
 */
class FlightGateDebugReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? MainApplication
        if (app == null) {
            Log.i(TAG, "result action=${intent.action} error=no-application")
            return
        }
        when (intent.action) {
            "com.bringyour.network.debug.FG_LOGIN" -> login(app)
            "com.bringyour.network.debug.FG_PROVIDE" -> provide(
                app,
                intent.getStringExtra("control"),
                intent.getStringExtra("network"),
            )
            "com.bringyour.network.debug.FG_CONNECT_PEER" -> connectPeer(app, intent.getStringExtra("name"))
            "com.bringyour.network.debug.FG_DISCONNECT" -> disconnect(app)
            "com.bringyour.network.debug.FG_STATUS" -> status(app)
            else -> Log.i(TAG, "result action=${intent.action} error=unknown-action")
        }
    }

    private fun login(app: MainApplication) {
        val lines = runCatching { File(LOGIN_FILE).readLines() }.getOrNull()
        if (lines == null || lines.size < 2 || lines[0].isBlank() || lines[1].isBlank()) {
            Log.i(TAG, "result action=login error=missing-credentials-file")
            return
        }
        if (app.device != null) {
            Log.i(TAG, "result action=login ok=true already=true")
            return
        }
        app.authenticateWithPassword(lines[0].trim(), lines[1].trim()) { completion ->
            when (completion) {
                is PasswordLoginCompletion.Ready ->
                    Log.i(TAG, "result action=login ok=true")
                is PasswordLoginCompletion.VerificationRequired ->
                    Log.i(TAG, "result action=login ok=false error=verification-required")
                is PasswordLoginCompletion.Failed ->
                    Log.i(TAG, "result action=login ok=false error=${completion.failure} message=${completion.message}")
            }
        }
    }

    private fun provide(app: MainApplication, control: String?, network: String?) {
        if (app.device == null) {
            Log.i(TAG, "result action=provide error=no-device")
            return
        }
        val manager = app.deviceManager
        control?.let { value ->
            ProvideControlMode.fromString(value)?.let { manager.provideControlMode = it }
        }
        network?.let { value ->
            ProvideNetworkMode.fromString(value)?.let { manager.provideNetworkMode = it }
        }
        Log.i(
            TAG,
            "result action=provide ok=true control=${ProvideControlMode.toString(manager.provideControlMode)} " +
                "network=${ProvideNetworkMode.toString(manager.provideNetworkMode)} mode=${app.device?.provideMode}",
        )
    }

    private fun connectPeer(app: MainApplication, name: String?) {
        val device = app.device
        if (device == null || name.isNullOrBlank()) {
            Log.i(TAG, "result action=connect-peer error=no-device-or-name")
            return
        }
        val peers = device.networkPeers?.connected
        var location: ConnectLocation? = null
        if (peers != null) {
            for (i in 0 until peers.len()) {
                val peer = peers.get(i) ?: continue
                val clientId = peer.clientId ?: continue
                if (!peer.deviceName.contains(name, ignoreCase = true)) {
                    continue
                }
                location = ConnectLocation().also { l ->
                    l.connectLocationId = ConnectLocationId().also { id -> id.clientId = clientId }
                    l.name = peer.deviceName
                    // one of the user's own devices: a trusted same-network
                    // peer, egressing under Network provide mode
                    l.networkPeer = true
                }
                break
            }
        }
        if (location == null) {
            Log.i(TAG, "result action=connect-peer ok=false error=peer-not-found")
            return
        }
        val vc = device.openConnectViewController()
        if (vc == null) {
            Log.i(TAG, "result action=connect-peer ok=false error=no-view-controller")
            return
        }
        try {
            vc.connect(location)
        } finally {
            device.closeViewController(vc)
        }
        val needsConsent = VpnService.prepare(app) != null
        Log.i(TAG, "result action=connect-peer ok=true needs_consent=$needsConsent")
    }

    private fun disconnect(app: MainApplication) {
        val device = app.device
        if (device == null) {
            Log.i(TAG, "result action=disconnect error=no-device")
            return
        }
        val vc = device.openConnectViewController()
        if (vc == null) {
            Log.i(TAG, "result action=disconnect ok=false error=no-view-controller")
            return
        }
        try {
            vc.disconnect()
        } finally {
            device.closeViewController(vc)
        }
        Log.i(TAG, "result action=disconnect ok=true")
    }

    private fun status(app: MainApplication) {
        val json = JSONObject()
        val device = app.device
        json.put("signed_in", device != null)
        if (device != null) {
            json.put("connect_enabled", device.connectEnabled)
            json.put("provide_mode", device.provideMode)
            json.put("provide_control_mode", device.provideControlMode)
            json.put("provide_network_mode", device.provideNetworkMode)
            json.put("needs_consent", VpnService.prepare(app) != null)
            device.connectLocation?.let { location ->
                json.put("location_name", location.name)
                json.put("location_network_peer", location.networkPeer)
            }
            val peers = JSONArray()
            device.networkPeers?.connected?.let { list ->
                for (i in 0 until list.len()) {
                    val peer = list.get(i) ?: continue
                    peers.put(
                        JSONObject()
                            .put("device_name", peer.deviceName)
                            .put("provide_enabled", peer.provideEnabled),
                    )
                }
            }
            json.put("peers", peers)
            json.put("disconnected_peer_count", device.networkPeers?.disconnectedCount ?: 0)
        }
        Log.i(TAG, "status $json")
    }

    companion object {
        private const val TAG = "FlightGate"
        private const val LOGIN_FILE = "/data/local/tmp/flightgate-login"
    }
}
