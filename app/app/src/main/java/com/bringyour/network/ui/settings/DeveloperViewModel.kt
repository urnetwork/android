package com.bringyour.network.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bringyour.network.APP_LOG_PROCESS_NAME
import com.bringyour.network.DeviceManager
import com.bringyour.network.utils.formatByteCountCompact
import com.bringyour.sdk.Exit
import com.bringyour.sdk.ReliabilityMetrics
import com.bringyour.sdk.ReliabilitySettings
import com.bringyour.sdk.Sdk
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    /**
     * What the last export produced, as NUMBERS rather than a finished
     * sentence -- see [DiagnosticExportSummary]. The screen renders it, which
     * is the only place a `<plurals>` resource can be resolved from.
     */
    var lastExport by mutableStateOf<DiagnosticExportSummary?>(null)
        private set

    /**
     * The verbosity the DEVICE reports it is logging at, or null when there is
     * no device to ask (signed out, or before one has been created).
     *
     * Read back from the device rather than remembered from the last tap. The
     * level is process-global state behind a glog flag, so a set can be
     * clamped by the sdk or refused outright (a hosted device) with nothing
     * thrown here. Showing the value the user asked for would then claim a
     * capture is running verbose while it is still at 0 -- and the bundle
     * exported from it would be the empty one this control exists to prevent.
     */
    var logVerbosity by mutableStateOf<Long?>(null)
        private set

    var inventory by mutableStateOf<List<LogRow>>(listOf())
        private set

    var selectedLogNames by mutableStateOf<Set<String>>(setOf())
        private set

    /**
     * Sources the exporter will not be able to read, as "<source>: <reason>",
     * refreshed off the main thread whenever the inventory is.
     *
     * The export ui shows any unavailable source with its reason BEFORE the
     * user commits to an export, not only afterwards in the summary --
     * learning that the logs were unreachable after zipping them is not
     * graceful degradation, it is a report of it.
     */
    var unavailableSources by mutableStateOf<List<String>>(listOf())
        private set

    /**
     * True from the tap that starts an export until its result is on screen.
     *
     * Two things need it. (1) Re-entrancy: [diagnosticBundleFileName] has
     * one-second resolution and only a `-redacted` discriminator, so two
     * exports of the same mode inside one second name the SAME file and the
     * second os.Create truncates the zip the first is still streaming into --
     * the share sheet then hands support a corrupt archive. (2) Feedback: a
     * full export takes many seconds, and with nothing on screen to show for
     * the tap, tapping again is the expected user response.
     */
    var exporting by mutableStateOf(false)
        private set

    /** True while the inventory/availability snapshot is being read. */
    var refreshingDiagnostics by mutableStateOf(false)
        private set

    /**
     * A finished bundle waiting to be handed to the share sheet, consumed by
     * the screen via [consumePendingShare].
     *
     * The export deliberately does NOT take a completion lambda: the one the
     * screen would pass captures the composition's Activity, so a viewmodel
     * that outlives a rotation would call startActivity on a destroyed one and
     * would hold it alive for the whole export. Handing back state instead
     * lets the screen share with whatever context is current.
     */
    var pendingShare by mutableStateOf<File?>(null)
        private set

    val connected: Boolean get() = reliability != null

    /** Total on-disk size of everything the exporter can currently see. */
    val inventoryByteCount: Long get() = inventory.sumOf { it.byteCount }

    /** Total on-disk size of the rows currently checked in the picker. */
    val selectedByteCount: Long
        get() = inventory.filter { selectedLogNames.contains(it.name) }.sumOf { it.byteCount }

    /**
     * Writes a diagnostic bundle into destDir and leaves it in [pendingShare]
     * for the screen to hand to the share sheet; a failure to write it at all
     * is reported in [lastExport]. A source that could not be READ is not a
     * failure: it is recorded inside the bundle, listed in
     * [unavailableSources] before the export, and repeated in [lastExport]
     * after it.
     *
     * The logcat dump is a blocking external-process spawn, and the bundle
     * write walks the full on-disk log inventory and zips it (with a per-line
     * redaction pass when redact is set). Both are blocking i/o; run on the
     * caller's thread they would block Compose's click dispatch -- the main
     * thread -- for however long that inventory takes, risking an ANR past
     * Android's ~5s input-dispatch watchdog on any nontrivial log volume,
     * exactly the volume a diagnostics export exists to handle. So the work
     * happens on Dispatchers.IO and the result comes back as state rather than
     * a return value.
     *
     * A tap arriving while [exporting] is already true is dropped: see that
     * field for why a second concurrent export corrupts the first one's zip.
     */
    fun exportDiagnostics(
        destDir: File,
        redact: Boolean,
        selected: List<String>,
        nowMillis: Long,
    ) {
        if (exporting) {
            return
        }
        exporting = true
        viewModelScope.launch {
            try {
                val outcome = withContext(Dispatchers.IO) {
                    buildDiagnosticBundle(destDir, redact, selected, nowMillis)
                }
                lastExport = outcome.summary
                pendingShare = outcome.file
            } finally {
                exporting = false
            }
        }
    }

    /**
     * The picker's export, and the only caller that may pass a selection.
     *
     * Separate from [exportDiagnostics] because an EMPTY selection must never
     * reach the sdk from this control: empty SelectedNames means "no filter"
     * there, so "Export selected" with nothing checked would produce a
     * complete RAW bundle -- every severity, every rotation, plus the logcat
     * dump and a manifest carrying client_id and instance_id in the clear --
     * under a label promising a narrow subset. The row is disabled in that
     * state as well; this is the second guard, so no future caller can
     * reintroduce the hazard.
     */
    fun exportSelectedDiagnostics(destDir: File, nowMillis: Long) {
        val selected = selectedLogNames.toList()
        if (!canExportSelection(selected)) {
            return
        }
        exportDiagnostics(destDir, redact = false, selected = selected, nowMillis = nowMillis)
    }

    /** Clears a bundle once the screen has handed it to the share sheet. */
    fun consumePendingShare() {
        pendingShare = null
    }

    /**
     * Does the actual (blocking) export work. Called only from
     * [exportDiagnostics] inside a Dispatchers.IO block -- never touches
     * mutableStateOf state directly, so it has no thread requirement of its
     * own beyond "not the main thread".
     */
    private fun buildDiagnosticBundle(
        destDir: File,
        redact: Boolean,
        selected: List<String>,
        nowMillis: Long,
    ): DiagnosticExportOutcome {
        val dest = File(destDir, diagnosticBundleFileName(nowMillis, redact))
        return try {
            destDir.mkdirs()
            // A bundle is a handoff to the share sheet, never storage: at up to
            // 4x16MB of logs each, keeping every past export costs hundreds of
            // MB until the OS reclaims the cache. This also sweeps up a zip
            // orphaned by an export whose coroutine was cancelled (navigating
            // off the screen mid-export) before it could report the file.
            pruneOldBundles(destDir, dest)

            val options = Sdk.newExportOptions()
            options.redact = redact
            options.includeManifest = true
            options.includePlatformLogs = true
            selected.forEach { options.selectedNames.add(it) }

            deviceManager.device?.let { options.setManifestJson(it.diagnosticManifestJson()) }

            // A source that cannot be read is RECORDED as missing, not silently
            // omitted. The sdk cannot do this for us -- LogInventory swallows
            // directory-read failures and ExportDiagnosticBundle only ever
            // learns about per-FILE open/stat errors -- so without this an
            // unreadable log directory yields a bundle with zero log entries,
            // an empty "NOT INCLUDED" section and "Exported 0 log files", and
            // the support engineer is told nothing about why.
            unavailableLogSources().forEach { (source, reason) ->
                options.missingSourceReason(source, reason)
            }

            val logcat = readLogcat()
            val logcatText = logcat.text
            if (logcatText != null) {
                options.addPlatformLog(LOGCAT_LOG_NAME, logcatText)
            } else {
                // Recorded as a missing SOURCE rather than written as the body
                // of platform/logcat.txt: a failure stored inside the file it
                // was supposed to fill never reaches README.txt's NOT INCLUDED
                // list, the ExportResult, or the summary on screen.
                options.missingSourceReason(LOGCAT_LOG_NAME, logcat.failureReason)
            }

            val result = Sdk.exportDiagnosticBundle(dest.absolutePath, options)
            DiagnosticExportOutcome(
                dest,
                DiagnosticExportSummary(
                    // ExportResult.FileCount is a Go `int`, which gobind binds
                    // as a java long; a plurals lookup selects on an int
                    fileCount = result.fileCount.toInt(),
                    byteCount = result.byteCount,
                    missingSources = (0 until result.missingSources.len()).map {
                        result.missingSources.get(it)
                    },
                ),
            )
        } catch (e: Exception) {
            DiagnosticExportOutcome(null, DiagnosticExportSummary.failed(e.message))
        }
    }

    /** Deletes every previously exported bundle in destDir, keeping [keep]. */
    private fun pruneOldBundles(destDir: File, keep: File) {
        destDir.listFiles()?.forEach { file ->
            if (file.isFile && file != keep && isDiagnosticBundleName(file.name)) {
                file.delete()
            }
        }
    }

    /**
     * Every log source the exporter will not be able to read, as
     * (source, reason) pairs. Empty is the normal case.
     */
    private fun unavailableLogSources(): List<Pair<String, String>> {
        val reason = logSourceUnavailableReason(Sdk.getLogRoot(), APP_LOG_PROCESS_NAME)
        return if (reason == null) listOf() else listOf(APP_LOG_PROCESS_NAME to reason)
    }

    /** A logcat dump, or the reason there isn't one. */
    private data class LogcatDump(val text: String?, val failureReason: String)

    private fun readLogcat(): LogcatDump {
        var started: Process? = null
        return try {
            val process = ProcessBuilder(logcatDumpCommand()).redirectErrorStream(true).start()
            started = process
            // Bounded twice: `-t` caps the lines logcat prints, and this caps
            // what is materialised if the buffer was resized (a developer
            // debugging the very fault this feature exists for has usually run
            // `logcat -G 8M`). The dump is held as a kotlin String (2 bytes per
            // char), copied into a Go string and read again by deflate -- three
            // live copies, under a Go soft memory limit set to 3/4 of the app
            // heap.
            val text = process.inputStream.bufferedReader().use { readAtMost(it, LOGCAT_MAX_CHARS) }
            // `logcat -d` dumps and exits, but bound the wait anyway: an
            // unwaited child is left as a zombie, and a logcat that never exits
            // must not pin this thread forever.
            if (!process.waitFor(LOGCAT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroy()
            }
            LogcatDump(text, "")
        } catch (e: Exception) {
            LogcatDump(null, "logcat unavailable: ${e.message}")
        } finally {
            // Never leave the child running: stopping at the cap above can
            // leave logcat with a full pipe and nothing draining it.
            started?.destroy()
        }
    }

    /**
     * Snapshots the log inventory and the unavailable sources, off the main
     * thread.
     *
     * Sdk.logInventory() is an os.ReadDir of the root plus every process
     * directory, with an Info() per entry, all across the gomobile bridge. The
     * rows it returns are Go proxies too, so every source/severity/byteCount
     * read in the picker would be another JNI hop, repeated per row per
     * recomposition. Snapshotting into plain kotlin values here removes both.
     */
    fun refreshDiagnostics() {
        if (refreshingDiagnostics) {
            return
        }
        refreshingDiagnostics = true
        viewModelScope.launch {
            try {
                // one hop, both reads: withContext is not inline, so its
                // result is the way values come back out of it
                val snapshot = withContext(Dispatchers.IO) {
                    readLogInventory() to unavailableLogSources()
                }
                val rows = snapshot.first
                inventory = rows
                unavailableSources = snapshot.second.map { (source, reason) -> "$source: $reason" }
                // A file can rotate away between opening the picker and
                // exporting. The sdk filter silently drops a selected name it
                // no longer finds, so the user would get fewer files than the
                // picker showed with nothing saying so -- reconcile instead.
                selectedLogNames = selectedLogNames.intersect(rows.map { it.name }.toSet())
            } finally {
                refreshingDiagnostics = false
            }
        }
    }

    private fun readLogInventory(): List<LogRow> {
        val list = Sdk.logInventory()
        return (0 until list.len()).map { i ->
            val info = list.get(i)
            LogRow(
                name = info.name,
                source = info.source,
                severity = info.severity,
                byteCount = info.byteCount,
                modifiedMillis = info.modifiedMillis,
            )
        }
    }

    /**
     * Raises or lowers the verbosity of the process that actually writes the
     * logs worth raising it for.
     *
     * Always through the DEVICE, never Sdk.setLogVerbosity: that one reaches
     * only the calling process, and the transport internals, contract
     * accounting and window diagnostics are written by the process the device
     * runs in. Device.SetLogVerbosity is the one that reaches it -- on android
     * the DeviceLocal in this process, and on the other platform binding the
     * same call carries the level across to the extension the transport runs
     * in. Going through the device is what keeps the two honest.
     *
     * The level is then READ BACK from the device rather than assumed. The sdk
     * clamps out-of-range values and a hosted device refuses the call
     * outright, neither of which throws. Reading back is what makes a set that
     * did not take visible.
     */
    val setLogVerbosity: (Long) -> Unit = { level ->
        val device = deviceManager.device
        device?.setLogVerbosity(level)
        logVerbosity = device?.getLogVerbosity()
    }

    fun toggleLogSelection(name: String) {
        selectedLogNames = if (selectedLogNames.contains(name)) {
            selectedLogNames - name
        } else {
            selectedLogNames + name
        }
    }

    fun refresh() {
        val device = deviceManager.device

        // Polled with the rest of the readout rather than cached from the last
        // tap: the flag lives in the device's process and the restore a device
        // runs at construction can move it under us. A stale "Verbose" here is
        // the one lie this control cannot afford. Read BEFORE the null guard
        // below, so signing out clears the level to "Unavailable" instead of
        // leaving the last device's reading on screen.
        logVerbosity = device?.getLogVerbosity()

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

/**
 * The result of building a bundle off the main thread: the file it landed at
 * (null if the export failed outright) and the summary for
 * [DeveloperViewModel.lastExport].
 */
private data class DiagnosticExportOutcome(val file: File?, val summary: DiagnosticExportSummary)

/**
 * What an export produced, carried as numbers.
 *
 * The count is deliberately NOT formatted here. Built as
 * `"Exported ${result.fileCount} log files"` it reads "Exported 1 log files"
 * for the single-file case that a selective export produces most often. Only a
 * `<plurals>` resource can pick the right form, only a composable can resolve
 * one, and a count already baked into a string cannot be pluralised afterwards
 * -- so the count travels as an Int and `R.plurals.dev_export_summary` selects
 * on it at the point of display.
 */
data class DiagnosticExportSummary(
    val fileCount: Int,
    val byteCount: Long,
    val missingSources: List<String>,
    /** Non-null when no bundle was written at all; the reason, verbatim. */
    val failure: String? = null,
) {
    companion object {
        /** An export that produced no bundle. `reason` is an exception message. */
        fun failed(reason: String?): DiagnosticExportSummary = DiagnosticExportSummary(
            fileCount = 0,
            byteCount = 0L,
            missingSources = listOf(),
            failure = reason ?: "unknown error",
        )
    }
}

/**
 * The three glog levels the sdk defines (`Sdk.LogVerbosityDefault`,
 * `LogVerbosityVerbose`, `LogVerbosityTrace`), as the java `long` gobind binds
 * a Go `int` to.
 *
 * Named for what each one BUYS, because the whole point of the control is that
 * the interesting logging is off by default: `connect` gates its contract
 * accounting, transport internals and window diagnostics behind V(1) and V(2),
 * so at level 0 a bundle from a live connected session carries rpc chatter and
 * nothing about contracts or transports at all.
 */
const val LOG_VERBOSITY_DEFAULT = 0L

/** V(1): contract accounting and per-packet block decisions. */
const val LOG_VERBOSITY_VERBOSE = 1L

/** V(2): transport and window internals. */
const val LOG_VERBOSITY_TRACE = 2L

/** What a tap cycles through, in order. */
val LOG_VERBOSITY_PRESETS = listOf(
    LOG_VERBOSITY_DEFAULT,
    LOG_VERBOSITY_VERBOSE,
    LOG_VERBOSITY_TRACE,
)

/**
 * The level a tap moves to, wrapping back to Default after Trace.
 *
 * A level that is not one of the presets lands on the FIRST one, matching the
 * other stepper rows on this screen. That is reachable in practice: the sdk
 * reports whatever the `-v` flag says, including a value set past the sdk's
 * own range by other means, and stepping from an unknown value has to go
 * somewhere predictable rather than throw out of a click handler.
 */
fun nextLogVerbosity(level: Long): Long {
    val index = LOG_VERBOSITY_PRESETS.indexOf(level)
    return LOG_VERBOSITY_PRESETS[(index + 1) % LOG_VERBOSITY_PRESETS.size]
}

/** The three levels this control offers; "no device to ask" is null. */
enum class LogVerbosityLevel { DEFAULT, VERBOSE, TRACE }

/**
 * Which level a raw verbosity is displayed as, or null when there is no device
 * to read one from.
 *
 * "No device" and "level 0" are different claims, and reporting the second for
 * the first is exactly the silent assumption this control exists to avoid.
 *
 * Out-of-range values are folded toward the nearest level rather than dropped:
 * GetLogVerbosity reports the flag itself, so a -v of 5 is a real
 * possibility, and at 5 every V(2) statement fires -- calling that anything
 * but Trace would understate what the logs now contain.
 */
fun logVerbosityLevel(level: Long?): LogVerbosityLevel? {
    if (level == null) {
        return null
    }
    return when {
        level <= LOG_VERBOSITY_DEFAULT -> LogVerbosityLevel.DEFAULT
        level == LOG_VERBOSITY_VERBOSE -> LogVerbosityLevel.VERBOSE
        else -> LogVerbosityLevel.TRACE
    }
}

/**
 * The value shown on the control: the number the device reported, then the
 * name of the level it maps to -- "1 · Verbose".
 *
 * Both, because they answer different questions. The name says what the row
 * means; the number is what the sdk reports, what a support thread compares
 * against a bundle's manifest, and the only place a level outside the sdk's
 * range would ever be visible -- a -v of 7 reads "7 · Trace" rather than
 * being quietly redrawn as 2.
 */
fun logVerbosityValueLabel(level: Long, name: String): String = "$level · $name"

/**
 * True when the level currently in force writes the destination addresses and
 * ports of real traffic into the logs, which is what the persistent warning on
 * the screen is for.
 *
 * Keyed off the level READ BACK from the device, so the warning is never shown
 * for a raise that did not actually take, and never hidden for one that did.
 */
fun logVerbosityRecordsDestinations(level: Long?): Boolean =
    when (logVerbosityLevel(level)) {
        LogVerbosityLevel.VERBOSE, LogVerbosityLevel.TRACE -> true
        else -> false
    }

/**
 * A plain-kotlin snapshot of one row of the log inventory.
 *
 * The sdk's LogFileInfo is a gomobile proxy: every field read is a JNI hop
 * into Go, and Compose reads name/source/severity/byteCount on every
 * recomposition of the picker. Snapshotting once, off the main thread, keeps
 * the bridge out of the frame.
 */
data class LogRow(
    val name: String,
    val source: String,
    val severity: String,
    val byteCount: Long,
    val modifiedMillis: Long,
)

/** platform/<name> the android logcat dump is written to inside the bundle. */
const val LOGCAT_LOG_NAME = "logcat.txt"

/**
 * Line cap on the logcat dump. The stock buffer is 256KB per device, but a
 * developer debugging the fault this feature exists for has usually run
 * `logcat -G 8M` -- and the dump is held as a kotlin String, copied into a Go
 * string and read again by deflate, three live copies at once, under a Go
 * soft memory limit set to 3/4 of the app heap.
 */
const val LOGCAT_MAX_LINES = 20000

/** Hard ceiling on the characters materialised from the dump. */
private const val LOGCAT_MAX_CHARS = 4 * 1024 * 1024

/** How long to wait for `logcat -d` to exit before killing it. */
private const val LOGCAT_TIMEOUT_SECONDS = 10L

/** Every diagnostic bundle this app writes starts with this. */
const val DIAGNOSTIC_BUNDLE_PREFIX = "urnetwork-diagnostics-"

/**
 * Bundle names sort lexically in the same order they were made, so a support
 * thread with several attachments reads in order, and carry the mode so a
 * redacted bundle is never mistaken for a complete one.
 */
fun diagnosticBundleFileName(millis: Long, redacted: Boolean): String {
    val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US)
        .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
        .format(java.util.Date(millis))
    val suffix = if (redacted) "-redacted" else ""
    return "$DIAGNOSTIC_BUNDLE_PREFIX$stamp$suffix.zip"
}

/** True for a file this app wrote as a diagnostic bundle. */
fun isDiagnosticBundleName(name: String): Boolean =
    name.startsWith(DIAGNOSTIC_BUNDLE_PREFIX) && name.endsWith(".zip")

/**
 * `logcat -d` dumps and exits. Since android 4.1 an app reads only its OWN
 * buffer, which is exactly the wanted scope -- no permission is involved and
 * no other app's entries are reachable. `-t` bounds the dump to the most
 * recent lines: an unbounded read of a resized buffer is three live copies of
 * however much the developer asked the device to keep.
 */
fun logcatDumpCommand(): List<String> =
    listOf("logcat", "-d", "-v", "threadtime", "-t", LOGCAT_MAX_LINES.toString())

/**
 * Reads at most maxChars characters, so a dump larger than expected costs a
 * bounded allocation instead of the whole buffer.
 */
fun readAtMost(reader: java.io.Reader, maxChars: Int): String {
    val buffer = CharArray(8 * 1024)
    val out = StringBuilder()
    while (out.length < maxChars) {
        val n = reader.read(buffer, 0, minOf(buffer.size, maxChars - out.length))
        if (n < 0) {
            break
        }
        out.append(buffer, 0, n)
    }
    return out.toString()
}

/**
 * "Export selected" must never fall back to exporting everything: an empty
 * SelectedNames means "no filter" to the sdk ("Empty means every file"), so an
 * unguarded empty selection produces a complete RAW bundle -- every severity,
 * every rotation, the logcat dump and a manifest carrying client_id and
 * instance_id in the clear -- from a control whose label promises a narrow
 * subset.
 */
fun canExportSelection(selected: Collection<String>): Boolean = selected.isNotEmpty()

/**
 * Why the exporter will not be able to read this source's logs, or null when
 * it can.
 *
 * The sdk reports per-file failures but swallows directory-read failures
 * entirely, so this is the only place an unreadable log directory can be
 * noticed on android -- and such a source has to be recorded as missing rather
 * than silently omitted.
 *
 * Deliberately path-free: the reason is copied verbatim into the bundle's
 * README "NOT INCLUDED" list, which the sdk writes without the redaction
 * transform.
 */
fun logSourceUnavailableReason(logRoot: String, source: String): String? {
    if (logRoot.isEmpty()) {
        return "no log directory was recorded at startup"
    }
    val dir = File(logRoot, source)
    return when {
        !dir.exists() -> "no log directory on disk"
        !dir.isDirectory -> "the log path is not a directory"
        !dir.canRead() || dir.list() == null -> "the log directory could not be read"
        else -> null
    }
}

/**
 * What the export would contain, shown before the user commits to it: a
 * bundle is up to 4x16MB per process, which is not a thing to discover
 * afterwards.
 *
 * Sizes go through the app's own formatByteCountCompact rather than
 * `byteCount / 1024`: that integer division renders a freshly rotated
 * 400-byte log as "0 KiB", and in a picker "0" reads as "nothing in this
 * file" rather than as a rounding.
 */
fun inventoryLabel(fileCount: Int, byteCount: Long): String {
    if (fileCount == 0) {
        return "No log files on disk"
    }
    val files = if (fileCount == 1) "log file" else "log files"
    return "$fileCount $files on disk · ${formatByteCountCompact(byteCount)}"
}

/** The same, for the subset currently checked in the picker. */
fun selectionLabel(fileCount: Int, byteCount: Long): String {
    if (fileCount == 0) {
        return "Nothing selected"
    }
    val files = if (fileCount == 1) "file" else "files"
    return "Selected $fileCount $files · ${formatByteCountCompact(byteCount)}"
}

/**
 * When a log was last written, UTC, or "" when unknown. The picker's rows name
 * severity, size AND modified time: which file covers the incident is a
 * question about time, and the glog file name does not answer it legibly.
 */
fun logFileModifiedLabel(modifiedMillis: Long): String {
    if (modifiedMillis <= 0L) {
        return ""
    }
    val stamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US)
        .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
        .format(java.util.Date(modifiedMillis))
    return "${stamp}Z"
}

/** The one-line label for a row in the selective log picker. */
fun logFileRowLabel(
    source: String,
    severity: String,
    byteCount: Long,
    modifiedMillis: Long = 0L,
): String = buildString {
    append(source)
    append(" · ")
    append(severity)
    append(" · ")
    append(formatByteCountCompact(byteCount))
    val modified = logFileModifiedLabel(modifiedMillis)
    if (modified.isNotEmpty()) {
        append(" · ")
        append(modified)
    }
}
