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
    val setDialFailureRerace: (Boolean) -> Unit = { update { s -> s.dialFailureRerace = it } }
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
    /**
     * Keeps a site's new flows on the exit its earlier flows already use, even
     * past the flow cap -- the cap then only gates which exits collect NEW
     * sites. This is what holds a busy site (video especially) to one egress
     * ip. Off restores the cap veto, the A/B point.
     */
    val setAffinityStickyPastCap: (Boolean) -> Unit = { sticky ->
        update { s -> s.affinityStickyPastCap = sticky }
    }

    /** Restores legacy hard IP/domain inheritance for controlled A/B runs. */
    val setFreshFlowAffinity: (Boolean) -> Unit = { enabled ->
        update { s -> s.freshFlowAffinity = enabled }
    }

    /**
     * Weights a fresh TLS provider race using peak acknowledged-byte progress
     * relative to each provider's advertised bandwidth prior.
     */
    val setPerformanceAwareAffinity: (Boolean) -> Unit = { enabled ->
        update { s -> s.performanceAwareAffinity = enabled }
    }

    /**
     * Lets a quarantined exit keep inheriting new flows from sites already on
     * it (through the early part of the bench, when the verdict is least
     * proven), so a bench does not split the site's egress IP. Off restores
     * the scatter, the A/B point.
     */
    val setQuarantineGroupFollow: (Boolean) -> Unit = { follow ->
        update { s -> s.quarantineGroupFollow = follow }
    }

    /**
     * How long into a quarantine episode a site's new connections keep
     * following their benched exit. Early benches are usually false alarms;
     * one that sustains is trending toward removal and stops collecting
     * flows first. 0 disables the follow.
     */
    val setGroupFollowWindowMillis: (Long) -> Unit = { millis ->
        update { s -> s.groupFollowWindowMillis = millis }
    }

    /**
     * Widens the silent-destination corroboration the soft no-receive verdict
     * needs as an exit's flow count grows: effective minimum =
     * max(min destinations, flows/this). 0 keeps the flat minimum, the A/B
     * point.
     */
    val setBlackholeLoadCorroboration: (Int) -> Unit = { perFlows ->
        update { s -> s.blackholeLoadCorroboration = perFlows }
    }

    val setMaxFlowsPerExit: (Int) -> Unit = { maxFlows ->
        update { s -> s.maxFlowsPerExit = maxFlows }
    }

    /**
     * How long the whole tunnel may receive nothing from any provider before
     * the ambiguous blackhole verdicts are held as inadmissible. Tunnel-wide
     * silence convicts the phone's own uplink, not the providers: one wifi
     * migration executed 7 exits in 79 seconds, every verdict no-receive-ack
     * with nothing received anywhere.
     */
    val setUplinkGateMillis: (Long) -> Unit = { millis ->
        update { s -> s.uplinkStalenessGateMillis = millis }
    }

    /**
     * The flows-are-sacred invariant: ambiguous verdicts bench an exit (no
     * new flows, established flows keep running) instead of executing it.
     * Removal then needs an empty exit or the evidence sustained past the
     * 60s bound. Off restores execute-on-first-verdict for A/B.
     */
    val setSoftVerdictDemote: (Boolean) -> Unit = { update { s -> s.softVerdictDemote = it } }

    /**
     * Re-pin established QUIC flows to a warm exit inside the removal of a
     * dying one. QUIC keys the connection on its connection id, so the server
     * path-validates the new address and the flow survives -- recovery in one
     * packet interval instead of a re-race. Off restores teardown for all.
     */
    val setQuicRebindOnExitLoss: (Boolean) -> Unit = { update { s -> s.quicRebindOnExitLoss = it } }

    /**
     * Rank providers by live evidence, not just the platform's static tier:
     * failing dials or a survived verdict drop a provider within a second;
     * promotion back requires clean minutes plus a proven connect. Off
     * restores static-tier selection for A/B.
     */
    val setEffectiveTierSelection: (Boolean) -> Unit = { update { s -> s.effectiveTierSelection = it } }

    /**
     * Qualify providers by dialing real sites through them. An answered probe
     * proves the exit completes upstream connects (the "proven" chip on its
     * card); an unanswered one proves nothing and never counts against it.
     * Off removes the mechanism entirely for A/B.
     */
    val setProviderProbe: (Boolean) -> Unit = { update { s -> s.providerProbe = it } }

    /**
     * Ask before convicting a stalled exit: when the send-stall bar trips, fire
     * one control ping through the exit and let an ack acquit it. A congested
     * but alive exit answers and keeps its flows; a dead one is still removed.
     * Off convicts immediately on the bar, the pre-port behaviour, for A/B.
     */
    val setBusyProbe: (Boolean) -> Unit = { update { s -> s.busyProbe = it } }

    /**
     * Size each window one spare exit beyond its target so a failed exit's
     * replacement is already connected. Off restores exact-target sizing, where
     * backfill only starts after a loss -- measured at about 45s.
     */
    val setStandingReserve: (Boolean) -> Unit = { update { s -> s.standingReserve = it } }

    /**
     * How long a busy probe waits for its ack before the stalled exit is
     * convicted. 0 derives max(1s, send-stall/2). Only matters while the busy
     * probe is on.
     */
    val setBusyProbeBudgetMillis: (Long) -> Unit = { millis ->
        update { s -> s.busyProbeBudgetMillis = millis }
    }

    /**
     * How much later than armed a timer may fire before the gap is read as a
     * host suspend rather than a stall, holding verdicts collected across it so
     * a just-resumed phone does not convict every exit at once. 0 disables the
     * suspend detector.
     */
    val setSchedulerPauseToleranceMillis: (Long) -> Unit = { millis ->
        update { s -> s.schedulerPauseToleranceMillis = millis }
    }

    /**
     * How long after a detected suspend the hold stays in effect, giving the
     * transports time to re-register before convictions resume. 0 falls back to
     * the built-in 5s.
     */
    val setSchedulerPauseRecoveryTimeoutMillis: (Long) -> Unit = { millis ->
        update { s -> s.schedulerPauseRecoveryTimeoutMillis = millis }
    }

    /**
     * The shorter connect bar the no-receive-syn branch fires at while two
     * sibling exits are demonstrably receiving -- an exit that has established
     * nothing while the pool works is cut ~20s sooner. 0 restores the single
     * 30s bar for A/B.
     */
    val setBlackholeConnectComparativeTimeoutMillis: (Long) -> Unit = { millis ->
        update { s -> s.blackholeConnectComparativeTimeoutMillis = millis }
    }

    /**
     * How often a flow with no candidate exits re-checks its forming window, so
     * the first DNS+SYN leaves moments after the first exit lands instead of
     * waiting out the 2s send-retry pace. 0 falls back to that pace.
     */
    val setFormationPollTimeoutMillis: (Long) -> Unit = { millis ->
        update { s -> s.formationPollTimeoutMillis = millis }
    }

    /**
     * How often the one-line state heartbeat is logged for logcat forensics. 0
     * silences it; a shorter interval spots a transition, a longer one keeps
     * more buffer for a capture. Takes effect without a reconnect.
     */
    val setHeartbeatIntervalMillis: (Long) -> Unit = { millis ->
        update { s -> s.heartbeatIntervalMillis = millis }
    }

    /**
     * How long one provider-qualification probe pass waits for positive
     * evidence. 0 falls back to the built-in 4s. It only ever bounds how long
     * an answer is waited for; it never produces a verdict.
     */
    val setProbeTimeoutMillis: (Long) -> Unit = { millis ->
        update { s -> s.probeTimeoutMillis = millis }
    }

    /**
     * How many health hosts one qualification pass asks about. 0 means the
     * entire embedded table (the shipped default); a positive value narrows
     * the pass to a rotating block of that many hosts.
     */
    val setProbeSampleHostCount: (Int) -> Unit = { count ->
        update { s -> s.probeSampleHostCount = count }
    }

    /**
     * The window the removal-budget storm breaker counts removals over. 0 (or a
     * count of 0) turns the breaker off.
     */
    val setRemovalBudgetWindowMillis: (Long) -> Unit = { millis ->
        update { s -> s.removalBudgetWindowMillis = millis }
    }

    /**
     * How many verdict-driven removals are allowed per window before the rest
     * are deferred -- a removal storm is more likely one local cause than that
     * many independent provider failures. 0 turns the breaker off.
     */
    val setRemovalBudgetCount: (Int) -> Unit = { count ->
        update { s -> s.removalBudgetCount = count }
    }

    /**
     * How many candidates a window expansion evaluates per slot it needs,
     * keeping the best and cancelling the flowless surplus. 1 restores
     * exact-count evaluation, the A/B point; 2 is the shipped default.
     */
    val setEvaluationPoolMultiple: (Int) -> Unit = { count ->
        update { s -> s.evaluationPoolMultiple = count }
    }

    /**
     * How many distinct destinations must be silent before the no-receive-ack
     * blackhole verdict can fire, so one dead website cannot convict an exit
     * that is demonstrably alive. 0 or 1 restores the single-destination
     * behaviour for A/B.
     */
    val setMinBlackholeDestinations: (Int) -> Unit = { count ->
        update { s -> s.minBlackholeDestinations = count }
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
     * Hands one exit's movable (established QUIC) flows to live replacements
     * now, while the exit stays alive -- the drain-time hand-off on demand.
     * Nothing is killed: TCP and anything unplaceable keeps working where it
     * is. The G-3 drill.
     */
    val migrateExit: (Exit) -> Unit = { exit ->
        val moved = deviceManager.device?.migrateExit(exit.clientId) ?: -1
        lastAction = if (moved >= 0) "Migrated $moved flows" else "Exit not in window"
        refresh()
    }

    /**
     * Fires a qualification probe pass at every exit right now instead of
     * waiting for the background sweep. Non-blocking; the "Probes" counter above
     * moves as the passes complete. No-op when provider probing is off.
     */
    val probeAllExits: () -> Unit = {
        val scheduled = deviceManager.device?.probeAllExits() ?: 0
        lastAction = if (scheduled > 0) "Probing $scheduled exits" else "No exits to probe"
        refresh()
    }

    /**
     * Fires the platform network-change path on demand -- the uplink epoch reset
     * and the transport kick a real wifi-to-cellular migration triggers -- so
     * the storm drill the uplink gate exists for is one tap instead of
     * physically moving between networks.
     */
    val simulateNetworkChange: () -> Unit = {
        deviceManager.device?.simulateNetworkChange()
        lastAction = "Simulated network change"
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

        /**
         * connect default 5s. The gate holds ambiguous verdicts while the
         * whole tunnel is silent; 0 disables it, the pre-fix comparison
         * point. Values are bounded below the 20s receive verdict or the
         * gate could never engage before the verdict it exists to hold.
         */
        val UPLINK_GATE_PRESETS = listOf(0L, 3_000L, 5_000L, 10_000L)

        /**
         * connect default 200ms. 0 falls back to the 2s send-retry pace, the
         * pre-change behaviour -- note 0 here is "slow poll", not "off": the
         * window still forms, just at the old cadence.
         */
        val FORMATION_POLL_PRESETS = listOf(0L, 100L, 200L, 500L)

        /**
         * connect default 0, which derives max(1s, send-stall/2) = 1.5s at the
         * shipped 3s bar. The others set an explicit ack budget for the busy
         * probe. Only used while the busy probe is on.
         */
        val BUSY_PROBE_BUDGET_PRESETS = listOf(0L, 1_000L, 1_500L, 2_000L, 3_000L)

        /**
         * connect default 2s. How much timer overshoot reads as a host suspend.
         * 0 disables the suspend detector, the pre-port comparison point.
         */
        val SCHEDULER_PAUSE_TOLERANCE_PRESETS = listOf(0L, 1_000L, 2_000L, 5_000L)

        /**
         * connect default 5s. The grace window after a detected suspend during
         * which verdicts stay held. 0 falls back to the built-in 5s.
         */
        val SCHEDULER_PAUSE_RECOVERY_PRESETS = listOf(0L, 3_000L, 5_000L, 10_000L)

        /**
         * connect default 10s. The shorter connect bar used while two siblings
         * are receiving. 0 restores the single 30s bar, the A/B point. Values
         * at or above 30s are a no-op for the same reason.
         */
        val COMPARATIVE_CONNECT_PRESETS = listOf(0L, 5_000L, 10_000L, 15_000L)

        /**
         * connect default 60s. One state line per interval for logcat
         * forensics. 0 silences the heartbeat; a shorter interval spots a
         * transition, a longer one keeps more buffer.
         */
        val HEARTBEAT_PRESETS = listOf(0L, 15_000L, 30_000L, 60_000L, 120_000L)

        /**
         * connect default 4s. Bounds one provider-qualification probe pass. 0
         * falls back to the built-in 4s. It only bounds how long positive
         * evidence is waited for, never a timer that convicts.
         */
        val PROBE_TIMEOUT_PRESETS = listOf(0L, 2_000L, 4_000L, 8_000L)

        /**
         * connect default 0 = the ENTIRE health-host table every pass. A
         * positive value narrows a pass to a rotating block of that many
         * hosts (4 was the old compact width). Width costs bytes, never wall
         * time -- a pass's probes are all in flight together.
         */
        val PROBE_SAMPLE_PRESETS = listOf(0, 4, 16, 64)

        /**
         * connect default 45s. How long into a bench a site keeps following
         * its exit; 0 turns the follow off. 45s covers the observed
         * false-positive bench range (every field acquittal landed inside
         * ~50s) while stopping before the ~60s drain-to-conviction zone.
         */
        val GROUP_FOLLOW_WINDOW_PRESETS = listOf(0L, 15_000L, 45_000L, 90_000L)

        /**
         * connect default 8 flows per extra required silent destination. 0
         * keeps the flat MinBlackholeDestinations bar, the A/B point.
         */
        val LOAD_CORROBORATION_PRESETS = listOf(0, 4, 8, 16)

        /**
         * connect default 2 per window. The storm breaker admits this many
         * verdict-driven removals per window and defers the rest. 0 turns the
         * breaker off, the pre-fix comparison point.
         */
        val REMOVAL_BUDGET_COUNT_PRESETS = listOf(0, 2, 4, 8)

        /**
         * connect default 30s. The window the removal budget is counted over. 0
         * (like a count of 0) turns the breaker off.
         */
        val REMOVAL_BUDGET_WINDOW_PRESETS = listOf(0L, 15_000L, 30_000L, 60_000L)

        /**
         * connect default 2. Candidates evaluated per window slot. 1 restores
         * exact-count evaluation, the A/B point; there is no 0 -- evaluating
         * zero candidates is not a behaviour.
         */
        val EVALUATION_POOL_PRESETS = listOf(1, 2, 3)

        /**
         * connect default 2. Distinct silent destinations required before the
         * no-receive-ack verdict can fire. 0 or 1 restores the
         * single-destination behaviour, the A/B point.
         */
        val MIN_BLACKHOLE_DESTINATIONS_PRESETS = listOf(0, 1, 2, 3)
    }
}
