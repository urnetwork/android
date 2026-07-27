package com.bringyour.network.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.bringyour.network.DeviceManager
import com.bringyour.sdk.Exit
import com.bringyour.sdk.ReliabilitySettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Backs the Developer section: the reliability toggles, the exit readout, and
 * the controls that reproduce exit failures on demand.
 *
 * Six reliability fixes ship on by default, each addressing a different way a
 * flow can freeze when its exit misbehaves. Which one matters for any given
 * user is not something the code can decide -- it has to be measured against a
 * live connection, which is what this exists for. Every toggle takes effect on
 * the next packet, so a fix can be switched off and back on *during* a freeze
 * without reconnecting and destroying the thing being observed.
 */
@HiltViewModel
class DeveloperViewModel @Inject constructor(
    private val deviceManager: DeviceManager,
) : ViewModel() {

    var exits by mutableStateOf<List<Exit>>(listOf())
        private set

    /**
     * Null while disconnected -- there is no multi client to read from, so the
     * toggles have nothing to act on and the section shows them disabled
     * rather than reporting defaults that are not in force.
     */
    var reliability by mutableStateOf<ReliabilitySettings?>(null)
        private set

    var lastAction by mutableStateOf<String?>(null)
        private set

    val connected: Boolean get() = reliability != null

    fun refresh() {
        val device = deviceManager.device
        if (device == null) {
            exits = listOf()
            reliability = null
            return
        }

        reliability = device.reliabilitySettings

        val exitList = device.exits
        exits = (0 until exitList.len()).map { exitList.get(it) }
    }

    private fun update(mutate: (ReliabilitySettings) -> Unit) {
        val current = reliability ?: return
        // the binding hands back a copy, so mutate and send the whole struct
        mutate(current)
        deviceManager.device?.reliabilitySettings = current
        refresh()
    }

    val setUdpTeardownSignal: (Boolean) -> Unit = { update { s -> s.udpTeardownSignal = it } }
    val setClusterAffinityFallback: (Boolean) -> Unit = { update { s -> s.clusterAffinityFallback = it } }
    val setServerNameAffinityBridge: (Boolean) -> Unit = { update { s -> s.serverNameAffinityBridge = it } }

    /**
     * The collapse bound is a duration, not a flag. Off means unbounded, which
     * is the pre-fix behaviour: a stalled exit keeps swallowing retransmits
     * until failure detection notices, up to 30s.
     */
    val setTcpCollapseHold: (Boolean) -> Unit = { enabled ->
        update { s -> s.tcpCollapseMaxHoldMillis = if (enabled) TCP_COLLAPSE_HOLD_MILLIS else 0L }
    }

    /**
     * Off collapses the tcp idle bound back onto the shared one, which is the
     * pre-fix behaviour of reaping any flow idle for two minutes.
     */
    val setTcpIdleTimeout: (Boolean) -> Unit = { enabled ->
        update { s -> s.tcpSequenceIdleTimeoutMillis = if (enabled) TCP_IDLE_TIMEOUT_MILLIS else 0L }
    }

    /** Restores everything the app shipped with. */
    val resetReliability: () -> Unit = {
        deviceManager.device?.resetReliabilitySettings()
        lastAction = "Reset to shipped defaults"
        refresh()
    }

    /**
     * Replaces every exit at once. Useful for forcing a full re-selection, but
     * note this is not what a real outage looks like -- see [dropExit].
     */
    val shuffleExits: () -> Unit = {
        deviceManager.device?.shuffleExits()
        lastAction = "Shuffled all exits"
        refresh()
    }

    /**
     * Kills one exit and leaves the rest working. This is the failure the
     * teardown fixes address, and the one to use when reproducing a freeze.
     */
    val dropExit: (Exit) -> Unit = { exit ->
        val dropped = deviceManager.device?.dropExit(exit.clientId) ?: false
        lastAction = if (dropped) "Dropped exit ${exit.clientId.toString().take(8)}" else "Exit already gone"
        refresh()
    }

    /**
     * Makes an exit swallow packets without acknowledging or erroring, so it is
     * neither healthy nor detectably dead. That is the state the tcp collapse
     * bound exists for; without this it only happens when a provider
     * misbehaves at exactly the wrong moment.
     */
    val stallExit: (Exit, Boolean) -> Unit = { exit, stalled ->
        val applied = deviceManager.device?.stallExit(exit.clientId, stalled) ?: false
        lastAction = when {
            !applied -> "Exit already gone"
            stalled -> "Stalled exit ${exit.clientId.toString().take(8)}"
            else -> "Resumed exit ${exit.clientId.toString().take(8)}"
        }
        refresh()
    }

    init {
        refresh()
    }

    companion object {
        // mirrors connect's DefaultMultiClientSettings
        const val TCP_COLLAPSE_HOLD_MILLIS = 1500L
        const val TCP_IDLE_TIMEOUT_MILLIS = 600_000L
    }
}
