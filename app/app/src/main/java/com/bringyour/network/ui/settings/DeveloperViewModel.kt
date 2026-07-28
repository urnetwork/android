package com.bringyour.network.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.bringyour.network.DeviceManager
import com.bringyour.sdk.Exit
import com.bringyour.sdk.ReliabilityMetrics
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

    /**
     * How long a provider that is still acknowledging our sends may return no
     * destination data before it is dropped.
     *
     * This is the weaker of the two blackhole signals and the one responsible
     * for the churn: at 5s it removed a provider roughly every 18s under real
     * load, and every one of those providers was still acknowledging traffic
     * -- some as much as 602 sends. Since removing an exit destroys every flow
     * pinned to it, that is a destructive action taken on very little
     * evidence. Off leaves only the unambiguous signal, a provider that
     * acknowledges nothing at all.
     */
    val setBlackholeReceiveTimeoutMillis: (Long) -> Unit = { millis ->
        update { s -> s.blackholeReceiveTimeoutMillis = millis }
    }

    /**
     * How many live flows one exit may carry.
     *
     * Providers are split-TCP, so removing an exit destroys every flow pinned
     * to it. Over 40 minutes of real use, 25 removals destroyed 821 flows --
     * but four of them accounted for 756, the worst being 484 connections in a
     * single event. Removal rate is not what a user feels; concentration is.
     *
     * The cost is that a site's flows can end up split across exits, so it
     * sees more than one egress IP. Unlimited restores the previous behaviour.
     */
    val setMaxFlowsPerExit: (Int) -> Unit = { maxFlows ->
        update { s -> s.maxFlowsPerExit = maxFlows }
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
         * How often the developer screen re-reads the counters while open.
         * Slow enough to be free, fast enough that a stall detected a few
         * seconds after the button press is visible without a manual refresh.
         */
        const val REFRESH_POLL_MILLIS = 2_000L

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

        /**
         * connect default 20s. 5s is what shipped and is the churn being
         * measured; 0 disables the check entirely, which is the comparison
         * point for how much of the churn it accounts for.
         *
         * Nothing above ~30s appears here on purpose. The bound is compared
         * against an age derived from stat buckets that are dropped once they
         * pass the 30s stats window, so a larger value never fires and is
         * silently identical to off -- it would present as "even more grace"
         * while measuring nothing.
         */
        val BLACKHOLE_RECEIVE_PRESETS = listOf(0L, 5_000L, 10_000L, 20_000L, 25_000L)

        /**
         * connect default 16. 0 is unlimited, the behaviour that shipped and
         * the one that produced a 484-connection teardown.
         *
         * 16 is set from measured recovery: teardowns of 4-6 flows recovered
         * in about 3-5s, while 44 took about 15s and 484 about 35s. The median
         * teardown observed was 36 flows, so the higher presets barely move
         * the common case -- 64 would not have touched any of the 15s stalls.
         * They are here to A/B the affinity cost, which is that a site's flows
         * split across exits and it sees more than one egress IP.
         */
        val MAX_FLOWS_PER_EXIT_PRESETS = listOf(0, 16, 32, 64, 128)
    }
}
