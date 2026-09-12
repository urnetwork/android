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
 * - `com.bringyour.network.debug.FG_ALLOW_DIRECT` (string extra `mode` =
 *   off|on|clear): the relay-only control, applied to the next connect.
 * - `com.bringyour.network.debug.FG_LANE_RULE` (string extra `mode` =
 *   on|off): the reliable-lane proven-recovery rule, applied to clients
 *   built after the call.
 * - `com.bringyour.network.debug.FG_HEAP_PROFILE` (string extra `name`):
 *   writes a Go heap profile into the app's files directory.
 * - `com.bringyour.network.debug.FG_DEFER_TIMEOUT_RESEND` (string extra
 *   `mode` = on|off): FLIGHTGATEFIX 13.5's deferred whole-window timeout
 *   resend, applied to clients built after the call.
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
            "com.bringyour.network.debug.FG_ALLOW_DIRECT" -> allowDirect(app, intent.getStringExtra("mode"))
            "com.bringyour.network.debug.FG_DEFER_TIMEOUT_RESEND" ->
                deferTimeoutResend(app, intent.getStringExtra("mode"))
            "com.bringyour.network.debug.FG_HEAP_PROFILE" -> heapProfile(app, intent.getStringExtra("name"))
            "com.bringyour.network.debug.FG_LANE_RULE" -> laneRule(app, intent.getStringExtra("mode"))
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

    /**
     * Relay-only control: `mode` = off forces direct (p2p) mode off for the
     * next window, on forces it on, clear restores the normal decision. Takes
     * effect on the next connect.
     */
    private fun allowDirect(app: MainApplication, mode: String?) {
        val device = app.device
        if (device == null) {
            Log.i(TAG, "result action=allow-direct error=no-device")
            return
        }
        when (mode) {
            "off" -> device.setTransferDiagAllowDirect(true, false)
            "on" -> device.setTransferDiagAllowDirect(true, true)
            "clear" -> device.setTransferDiagAllowDirect(false, false)
            else -> {
                Log.i(TAG, "result action=allow-direct ok=false error=bad-mode")
                return
            }
        }
        Log.i(TAG, "result action=allow-direct ok=true mode=$mode")
    }

    /**
     * FLIGHTGATEFIX 13.5: turn the deferred whole-window timeout resend on or
     * off for clients built after this call, so an A/B needs no rebuild.
     */
    private fun deferTimeoutResend(app: MainApplication, mode: String?) {
        val device = app.device
        if (device == null) {
            Log.i(TAG, "result action=defer-timeout-resend error=no-device")
            return
        }
        val enabled = when (mode) {
            "on" -> true
            "off" -> false
            else -> {
                Log.i(TAG, "result action=defer-timeout-resend ok=false error=bad-mode")
                return
            }
        }
        // reflective so this debug build also compiles against an SDK that
        // predates the setting (the merged-tree control of the rig)
        val applied = runCatching {
            device.javaClass
                .getMethod("setTransferDiagDeferTimeoutResend", java.lang.Boolean.TYPE)
                .invoke(device, enabled)
            true
        }.getOrDefault(false)
        Log.i(TAG, "result action=defer-timeout-resend ok=$applied mode=$mode")
    }

    /**
     * Writes a Go heap profile into the app's files directory, where adb can
     * pull it. A forced collection runs first, so the sample after this is not
     * an unperturbed recovery point.
     */
    private fun heapProfile(app: MainApplication, name: String?) {
        val file = java.io.File(app.filesDir, (name ?: "heap") + ".pprof")
        val result = runCatching {
            Class.forName("com.bringyour.sdk.Sdk")
                .getMethod("writeHeapProfileForDiag", String::class.java)
                .invoke(null, file.absolutePath) as String
        }
        val classes = runCatching {
            Class.forName("com.bringyour.sdk.Sdk")
                .getMethod("memoryClassesJsonForDiag")
                .invoke(null) as String
        }.getOrDefault("{}")
        result.fold(
            onSuccess = { Log.i(TAG, "result action=heap-profile ok=true $it classes=$classes") },
            onFailure = { Log.i(TAG, "result action=heap-profile ok=false error=${it.message} classes=$classes") },
        )
    }

    /**
     * The reliable-lane proven-recovery rule, applied to clients built after
     * this call, so the rule is an arm of one build rather than a build.
     */
    private fun laneRule(app: MainApplication, mode: String?) {
        val device = app.device
        if (device == null) {
            Log.i(TAG, "result action=lane-rule error=no-device")
            return
        }
        val enabled = when (mode) {
            "on" -> true
            "off" -> false
            else -> {
                Log.i(TAG, "result action=lane-rule ok=false error=bad-mode")
                return
            }
        }
        val applied = runCatching {
            device.javaClass
                .getMethod("setTransferDiagLaneRule", java.lang.Boolean.TYPE)
                .invoke(device, enabled)
            true
        }.getOrDefault(false)
        Log.i(TAG, "result action=lane-rule ok=$applied mode=$mode")
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
            json.put(
                "lane_rule",
                runCatching {
                    device.javaClass.getMethod("transferDiagLaneRule").invoke(device) as Boolean
                }.getOrDefault(false),
            )
            json.put(
                "defer_timeout_resend",
                runCatching {
                    device.javaClass.getMethod("transferDiagDeferTimeoutResend").invoke(device) as Boolean
                }.getOrDefault(false),
            )
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
