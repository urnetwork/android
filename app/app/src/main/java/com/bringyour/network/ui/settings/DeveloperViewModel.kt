package com.bringyour.network.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bringyour.network.DeviceManager
import com.bringyour.sdk.Exit
import com.bringyour.sdk.ProbeResult
import com.bringyour.sdk.ReliabilityMetrics
import com.bringyour.sdk.ReliabilitySettings
import com.bringyour.sdk.Sdk
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
     * What the toggles are judged against. Every reliability change so far has
     * been evaluated on how long a freeze felt, which is why fixes that tested
     * clean in isolation changed nothing in use. These are the numbers that
     * make a candidate falsifiable: how many connections a provider failure
     * destroys, and how long the sites behind them stay dark.
     */
    var metrics by mutableStateOf<ReliabilityMetrics?>(null)
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
            metrics = null
            return
        }

        reliability = device.reliabilitySettings
        metrics = device.reliabilityMetrics

        val exitList = device.exits
        exits = (0 until exitList.len()).map { exitList.get(it) }
    }

    /**
     * Zeroes the counters so a run starts clean. The A/B cycle is: reset, set
     * the config, drive the same workload, read the numbers back.
     */
    val resetMetrics: () -> Unit = {
        deviceManager.device?.resetReliabilityMetrics()
        lastAction = "Reset measurements"
        refresh()
    }

    /**
     * Probe suite state. Probes run through their own userspace tun pumped into
     * the live device, so they take the same exits as the browser -- an
     * ordinary HTTP client would leave the tunnel entirely, since the app
     * excludes itself from it.
     */
    var probeRunning by mutableStateOf(false)
        private set

    var probeResults by mutableStateOf<List<ProbeResult>>(listOf())
        private set

    private var probePollJob: Job? = null

    /**
     * Starts a run and polls while it lasts. Results are read during the run,
     * not after: the measurement that matters is what the next probe does in
     * the moment right after an exit is stalled.
     */
    val startProbes: () -> Unit = start@{
        val device = deviceManager.device ?: return@start
        val config = Sdk.getDefaultProbeSuiteConfig()
        if (!device.startProbeSuite(config)) {
            lastAction = "A test run is already going"
            return@start
        }
        lastAction = "Running tests"
        probeRunning = true
        pollProbes()
    }

    val stopProbes: () -> Unit = {
        deviceManager.device?.stopProbeSuite()
        lastAction = "Stopped tests"
    }

    private fun pollProbes() {
        probePollJob?.cancel()
        probePollJob = viewModelScope.launch {
            // poll until the run ends, then read once more so the final
            // results are not lost to the last interval
            do {
                readProbeResults()
                delay(PROBE_POLL_MILLIS)
            } while (probeRunning)
            readProbeResults()
        }
    }

    private fun readProbeResults() {
        val device = deviceManager.device
        if (device == null) {
            probeRunning = false
            return
        }
        val results = device.probeResults
        probeResults = (0 until results.len()).map { results.get(it) }
        // a call, not a property: gomobile only emits a Kotlin property for Go
        // methods named Get*, and this one is ProbeSuiteRunning
        probeRunning = device.probeSuiteRunning()
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
     * The timing controls are values, not switches. How long to wait before
     * giving up on an exit trades recovery speed against dropping one that was
     * slow but alive, and the right balance differs per connection -- so these
     * are tuned per user rather than guessed once. 0 always reproduces the
     * behaviour that shipped before the fix it controls.
     */
    val setSendStallTimeoutMillis: (Long) -> Unit = { millis ->
        update { s -> s.sendStallTimeoutMillis = millis }
    }

    val setTcpCollapseHoldMillis: (Long) -> Unit = { millis ->
        update { s -> s.tcpCollapseMaxHoldMillis = millis }
    }

    val setTcpIdleTimeoutMillis: (Long) -> Unit = { millis ->
        update { s -> s.tcpSequenceIdleTimeoutMillis = millis }
    }

    val setSequenceIdleTimeoutMillis: (Long) -> Unit = { millis ->
        update { s -> s.sequenceIdleTimeoutMillis = millis }
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
        lastAction = if (dropped) "Dropped exit ${exitLabel(exit)}" else "Exit already gone"
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
            stalled -> "Stalled exit ${exitLabel(exit)}"
            else -> "Resumed exit ${exitLabel(exit)}"
        }
        refresh()
    }

    init {
        refresh()
    }

    companion object {
        /**
         * Fast enough to watch results land as an exit is stalled, slow enough
         * that polling is not itself load on the connection being measured.
         */
        const val PROBE_POLL_MILLIS = 500L

        // the first entry of every list is 0 -- the behaviour that shipped
        // before the fix -- so each one can still be switched off entirely.
        // the defaults in connect's DefaultMultiClientSettings are marked.

        /** connect default 3s */
        val SEND_STALL_PRESETS = listOf(0L, 1_000L, 2_000L, 3_000L, 5_000L, 10_000L)

        /** connect default 1.5s */
        val TCP_COLLAPSE_HOLD_PRESETS = listOf(0L, 500L, 1_000L, 1_500L, 3_000L, 5_000L)

        /** connect default 600s; 0 falls back to the shared udp bound */
        val TCP_IDLE_TIMEOUT_PRESETS = listOf(0L, 120_000L, 300_000L, 600_000L, 1_800_000L)

        /** connect default 120s, shared by non-tcp flows */
        val UDP_IDLE_TIMEOUT_PRESETS = listOf(0L, 30_000L, 60_000L, 120_000L, 300_000L)
    }
}
