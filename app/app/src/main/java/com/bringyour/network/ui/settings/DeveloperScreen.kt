package com.bringyour.network.ui.settings

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.bringyour.network.R
import com.bringyour.network.BuildConfig
import com.bringyour.network.ui.wallet.EarningsDebugFlags
import androidx.compose.runtime.collectAsState
import com.bringyour.network.ui.components.URSwitch
import com.bringyour.network.ui.components.URTextInputLabel
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.BlueMedium
import com.bringyour.network.ui.theme.MainTintedBackgroundBase
import com.bringyour.network.ui.theme.RedDark
import com.bringyour.network.ui.theme.TextDanger
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.TopBarTitleTextStyle
import com.bringyour.network.utils.formatByteCountCompact
import com.bringyour.sdk.Exit
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale

/**
 * Developer tools for diagnosing connection problems.
 *
 * Its own screen rather than a section at the bottom of Settings: these
 * controls act on the live connection while something is going wrong, and the
 * set is expected to grow.
 *
 * The timing controls are values rather than switches because the right value
 * is not knowable in advance -- how long to wait before giving up on an exit
 * trades recovery speed against dropping a slow-but-alive one, and that balance
 * differs per connection. Each cycles through presets on tap, with the first
 * being off (previous behaviour) so any of them can still be A/B'd.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperScreen(
    navController: NavController,
    developerViewModel: DeveloperViewModel = hiltViewModel(),
) {
    // The counters below change while this screen is open -- a stalled exit
    // takes seconds to be detected and removed, and the actions here refresh
    // immediately, which captures the state *before* anything has happened.
    // Without polling a stale zero reads as "no provider failures" rather than
    // "nothing measured yet", which during device testing was very nearly
    // taken as evidence that detection was broken.
    LaunchedEffect(Unit) {
        while (true) {
            delay(DeveloperViewModel.REFRESH_POLL_MILLIS)
            developerViewModel.refresh()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(stringResource(id = R.string.developer), style = TopBarTitleTextStyle)
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Back",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Black),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            DeveloperContent(developerViewModel)
        }
    }
}

@Composable
private fun DeveloperContent(developerViewModel: DeveloperViewModel) {
    val reliability = developerViewModel.reliability

    Text(
        stringResource(id = R.string.developer_description),
        style = MaterialTheme.typography.bodySmall,
        color = TextMuted,
    )

    Spacer(modifier = Modifier.height(16.dp))

    // A diagnostics export is most needed exactly when the connection is
    // broken or the user is signed out -- i.e. exactly when `connected`
    // (reliability != null, which requires a live device) is false. This
    // section therefore renders ABOVE the !connected guard below rather than
    // being gated on the healthy connection it exists to help diagnose the
    // absence of. The export tolerates a null device on its own
    // (deviceManager.device?.let { ... }), which is what makes that safe.
    URTextInputLabel(text = stringResource(id = R.string.dev_section_diagnostics))

    // ABOVE the export rows on purpose: the order of operations is set the
    // level, reproduce the fault, then export. A verbosity control placed
    // under the export actions is found only after the capture it was supposed
    // to widen, and the bundle it produced is the useless one -- close to half
    // the log statements in `connect` (roughly 290 of some 700), every
    // contract, transport and window line among them, sit behind V(1)/V(2) and
    // are simply not written at level 0.
    DeveloperVerbositySetting(
        level = developerViewModel.logVerbosity,
        onSelect = developerViewModel.setLogVerbosity,
    )

    // Beside the verbosity row and, like it, ABOVE the !connected guard below.
    // An address family that fails after connecting is what makes the api
    // unreachable, so this row is reached while signed out or with the tunnel
    // down -- exactly where a row gated on `connected` would not be drawn.
    DeveloperIpFamilySetting(
        policy = developerViewModel.ipFamilyPolicy,
        status = developerViewModel.ipFamilyStatus,
        onSelect = developerViewModel.setIpFamilyPolicy,
    )

    // Debug builds only: drive the Earnings screen from in-memory protocol data so the
    // wallet, unclaimed, claim dialog and Top 200 states can be exercised without a chain.
    if (BuildConfig.DEBUG) {
        EarningsSampleDataSettings()
    }

    // Persistent, not a one-shot toast: it has to be on screen at the moment
    // the user reaches for "Export all logs (raw)", which can be many minutes
    // after the level was raised. This pairing is the point -- raising the
    // verbosity is what makes the redaction stop being decorative, so the
    // warning names the redacted export rather than just describing the risk.
    if (logVerbosityRecordsDestinations(developerViewModel.logVerbosity)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .background(RedDark, RoundedCornerShape(8.dp))
                .padding(12.dp),
        ) {
            Text(
                stringResource(id = R.string.dev_log_verbosity_warning_title),
                style = MaterialTheme.typography.bodyMedium,
                color = TextDanger,
            )
            Text(
                stringResource(id = R.string.dev_log_verbosity_warning),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White,
            )
        }
    }

    val context = LocalContext.current
    val shareBundle: (File) -> Unit = { file ->
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(intent, context.getString(R.string.dev_export_share)),
        )
    }

    // cacheDir/share is already declared to the FileProvider as "share"
    // (res/xml/file_paths.xml), so a bundle written there is shareable with no
    // manifest change. remember{} because a composable body runs on every
    // recomposition -- and this one recomposes on each reliability/metrics
    // poll -- while the directory is created by the exporter itself, on
    // Dispatchers.IO: File.mkdirs() stats the path before it can return, which
    // is a disk hit the ui thread should never take.
    val shareDir = remember(context) { File(context.cacheDir, "share") }

    // The bundle comes back as state rather than through a completion lambda:
    // a lambda declared here captures this composition's Activity, so an
    // export outliving a rotation would call startActivity on a destroyed one,
    // and the viewmodel would hold it alive for the whole export.
    val pendingShare = developerViewModel.pendingShare
    LaunchedEffect(pendingShare) {
        pendingShare?.let { file ->
            shareBundle(file)
            developerViewModel.consumePendingShare()
        }
    }

    // The total size before exporting, and any unavailable source with its
    // reason, are both shown BEFORE the user commits to a bundle of up to
    // 4x16MB per process -- not afterwards in the summary. The read is
    // directory i/o plus a stat per file across the gomobile bridge, so it
    // happens once on entry, off the main thread.
    LaunchedEffect(Unit) {
        developerViewModel.refreshDiagnostics()
    }

    val exporting = developerViewModel.exporting

    Text(
        inventoryLabel(developerViewModel.inventory.size, developerViewModel.inventoryByteCount),
        style = MaterialTheme.typography.bodySmall,
        color = TextMuted,
    )

    developerViewModel.unavailableSources.forEach { unavailable ->
        Text(
            stringResource(id = R.string.dev_export_unavailable, unavailable),
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
        )
    }

    DeveloperAction(
        label = stringResource(id = R.string.dev_export_all_logs),
        enabled = !exporting,
    ) {
        developerViewModel.exportDiagnostics(
            shareDir,
            redact = false,
            selected = emptyList(),
            nowMillis = System.currentTimeMillis(),
        )
    }

    DeveloperAction(
        label = stringResource(id = R.string.dev_export_redacted_logs),
        enabled = !exporting,
    ) {
        developerViewModel.exportDiagnostics(
            shareDir,
            redact = true,
            selected = emptyList(),
            nowMillis = System.currentTimeMillis(),
        )
    }

    var showPicker by remember { mutableStateOf(false) }

    DeveloperAction(
        label = stringResource(id = R.string.dev_choose_logs),
        enabled = !exporting && !developerViewModel.refreshingDiagnostics,
    ) {
        developerViewModel.refreshDiagnostics()
        showPicker = !showPicker
    }

    if (showPicker) {
        developerViewModel.inventory.forEach { info ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { developerViewModel.toggleLogSelection(info.name) }
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    logFileRowLabel(info.source, info.severity, info.byteCount, info.modifiedMillis),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (developerViewModel.selectedLogNames.contains(info.name)) {
                        BlueMedium
                    } else {
                        TextMuted
                    },
                )
            }
        }

        Text(
            selectionLabel(
                developerViewModel.selectedLogNames.size,
                developerViewModel.selectedByteCount,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
        )

        // Disabled -- and a no-op even if it were somehow tapped -- with
        // nothing checked, or while an export is already running: an empty
        // selection means "no filter" to the sdk, which would otherwise export
        // every log file unredacted, the opposite of what this row's label
        // promises.
        DeveloperAction(
            label = stringResource(id = R.string.dev_export_selected),
            enabled = !exporting && canExportSelection(developerViewModel.selectedLogNames),
        ) {
            developerViewModel.exportSelectedDiagnostics(shareDir, System.currentTimeMillis())
        }
    }

    if (exporting) {
        Text(
            stringResource(id = R.string.dev_exporting),
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
        )
    }

    developerViewModel.lastExport?.let { lastExport ->
        // Resolved here rather than in the viewmodel because this is where a
        // <plurals> can be read: "Exported 1 log files" is a count formatted
        // into a fixed English phrase, and a selective export of one file is
        // the common case that produces it.
        val failure = lastExport.failure
        Text(
            if (failure != null) {
                stringResource(id = R.string.dev_export_failed, failure)
            } else {
                pluralStringResource(
                    id = R.plurals.dev_export_summary,
                    count = lastExport.fileCount,
                    lastExport.fileCount,
                    formatByteCountCompact(lastExport.byteCount),
                )
            },
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
        )

        lastExport.missingSources.forEach { missing ->
            Text(
                stringResource(id = R.string.dev_export_not_included, missing),
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
            )
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    if (!developerViewModel.connected) {
        Text(
            stringResource(id = R.string.developer_disconnected),
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted,
        )
        return
    }

    /**
     * Measurements come first because they are what the rest of this screen is
     * for. Reliability changes have been judged on how long a freeze felt,
     * which is why fixes that were correct in isolation changed nothing in
     * use. A candidate that does not move blast radius or recovery time did
     * not work, however good the reasoning behind it was.
     */
    URTextInputLabel(text = stringResource(id = R.string.dev_measurements))

    Text(
        stringResource(id = R.string.dev_measurements_detail),
        style = MaterialTheme.typography.bodySmall,
        color = TextMuted,
    )

    Spacer(modifier = Modifier.height(8.dp))

    val metrics = developerViewModel.metrics

    DeveloperMetric(
        label = stringResource(id = R.string.dev_flows_opened),
        detail = stringResource(id = R.string.dev_flows_opened_detail),
        value = (metrics?.flowsOpened ?: 0L).toString(),
    )

    // dial failures are independent of exit-loss events -- a re-raced flow is
    // one that did *not* cost an exit removal -- so these stay always-shown
    // beside flows opened rather than behind the exit-loss guard below
    DeveloperMetric(
        label = stringResource(id = R.string.dev_dial_failures),
        detail = stringResource(id = R.string.dev_dial_failures_detail),
        value = (metrics?.dialFailuresIntercepted ?: 0L).toString(),
    )
    DeveloperMetric(
        label = stringResource(id = R.string.dev_flows_reraced),
        detail = stringResource(id = R.string.dev_flows_reraced_detail),
        value = (metrics?.flowsReraced ?: 0L).toString(),
    )
    // provider-qualification proof-of-life: sent climbs within seconds of
    // connecting when the sweep is working, answered follows for providers
    // that reach real destinations. Always shown -- a zero is itself the
    // measurement when comparing probe on vs off
    DeveloperMetric(
        label = stringResource(id = R.string.dev_probes),
        detail = stringResource(id = R.string.dev_probes_detail),
        value = stringResource(
            id = R.string.dev_probes_value,
            metrics?.probesSent ?: 0L,
            metrics?.probesAnswered ?: 0L,
        ),
    )
    // proof-of-life for the busy-flow liveness probe: acquitted counts the
    // stalled exits that answered the probe and were kept, the removals it
    // prevented. Always shown -- a zero is itself the measurement when
    // comparing busy probe on vs off
    DeveloperMetric(
        label = stringResource(id = R.string.dev_busy_probes),
        detail = stringResource(id = R.string.dev_busy_probes_detail),
        value = stringResource(
            id = R.string.dev_busy_probes_value,
            metrics?.busyProbesSent ?: 0L,
            metrics?.busyProbesAcquitted ?: 0L,
        ),
    )
    // proof-of-life for the uplink gates: nonzero here during a network
    // change means a false conviction was prevented. Always shown -- a zero
    // is itself the measurement when comparing gate on vs off
    DeveloperMetric(
        label = stringResource(id = R.string.dev_verdicts_held),
        detail = stringResource(id = R.string.dev_verdicts_held_detail),
        value = stringResource(
            id = R.string.dev_verdicts_held_value,
            metrics?.verdictsHeldUplinkStale ?: 0L,
            metrics?.verdictsHeldTransportDown ?: 0L,
        ),
    )
    if ((metrics?.removalsDeferred ?: 0L) > 0L) {
        DeveloperMetric(
            label = stringResource(id = R.string.dev_removals_deferred),
            detail = stringResource(id = R.string.dev_removals_deferred_detail),
            value = (metrics?.removalsDeferred ?: 0L).toString(),
        )
    }
    // host suspends the pause detector caught -- rare and device-specific, so
    // shown only once it has fired, like removals deferred above
    if ((metrics?.schedulerPausesDetected ?: 0L) > 0L) {
        DeveloperMetric(
            label = stringResource(id = R.string.dev_scheduler_pauses),
            detail = stringResource(id = R.string.dev_scheduler_pauses_detail),
            value = (metrics?.schedulerPausesDetected ?: 0L).toString(),
        )
    }
    // the field answer to whether servers accept a quic path change: rebinds
    // split into migrations the server took vs flows the app re-dialed
    if ((metrics?.flowsRebound ?: 0L) > 0L) {
        DeveloperMetric(
            label = stringResource(id = R.string.dev_flows_rebound),
            detail = stringResource(id = R.string.dev_flows_rebound_detail),
            value = stringResource(
                id = R.string.dev_flows_rebound_value,
                metrics?.flowsRebound ?: 0L,
                metrics?.rebindsAccepted ?: 0L,
                metrics?.rebindsRedialed ?: 0L,
            ),
        )
    }

    // the loss numbers are meaningless until something has actually failed,
    // and showing zeros reads as "nothing is wrong" rather than "nothing has
    // been measured yet"
    if (metrics == null || metrics.exitLossEvents == 0L) {
        Text(
            stringResource(id = R.string.dev_measure_none),
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted,
        )
    } else {
        DeveloperMetric(
            label = stringResource(id = R.string.dev_blast_radius),
            detail = stringResource(id = R.string.dev_blast_radius_detail),
            value = stringResource(
                id = R.string.dev_blast_radius_value,
                formatBlastRadius(metrics.meanFlowsLostPerExitLoss),
            ),
        )
        DeveloperMetric(
            label = stringResource(id = R.string.dev_worst_loss),
            detail = stringResource(id = R.string.dev_worst_loss_detail),
            value = stringResource(
                id = R.string.dev_worst_loss_value,
                metrics.maxFlowsLostInOneEvent,
            ),
        )
        DeveloperMetric(
            label = stringResource(id = R.string.dev_recovery),
            detail = stringResource(id = R.string.dev_recovery_detail),
            value = stringResource(
                id = R.string.dev_recovery_value,
                formatDurationMillis(metrics.recoveryMeanMillis),
                formatDurationMillis(metrics.recoveryMaxMillis),
            ),
        )
        // read together with recovery time: a change that abandons flows
        // instead of recovering them makes the average look better while this
        // climbs
        DeveloperMetric(
            label = stringResource(id = R.string.dev_recovery_missed),
            detail = stringResource(id = R.string.dev_recovery_missed_detail),
            value = stringResource(
                id = R.string.dev_recovery_missed_value,
                metrics.recoveryMissed,
                metrics.flowsLostToExit,
            ),
        )
    }

    DeveloperAction(
        label = stringResource(id = R.string.dev_reset_measurements),
        onClick = developerViewModel.resetMetrics,
    )

    Spacer(modifier = Modifier.height(16.dp))

    /**
     * The reliability knobs, grouped by what they act on so the growing list
     * stays scannable. Each timing/count control's first preset reproduces the
     * behaviour that shipped before the fix it controls, so a freeze can still
     * be A/B'd; the middle values are where a per-connection sweet spot is
     * found.
     *
     * Detection: how an exit is judged to be failing, and how fast.
     */
    URTextInputLabel(text = stringResource(id = R.string.dev_section_detection))

    DeveloperDurationSetting(
        label = stringResource(id = R.string.dev_send_stall),
        detail = stringResource(id = R.string.dev_send_stall_detail),
        millis = reliability?.sendStallTimeoutMillis ?: 0L,
        presets = DeveloperViewModel.SEND_STALL_PRESETS,
        onSelect = developerViewModel.setSendStallTimeoutMillis,
    )
    DeveloperToggle(
        label = stringResource(id = R.string.dev_busy_probe),
        detail = stringResource(id = R.string.dev_busy_probe_detail),
        checked = reliability?.busyProbe == true,
        toggle = developerViewModel.setBusyProbe,
    )
    DeveloperDurationSetting(
        label = stringResource(id = R.string.dev_busy_probe_budget),
        detail = stringResource(id = R.string.dev_busy_probe_budget_detail),
        millis = reliability?.busyProbeBudgetMillis ?: 0L,
        presets = DeveloperViewModel.BUSY_PROBE_BUDGET_PRESETS,
        onSelect = developerViewModel.setBusyProbeBudgetMillis,
    )
    DeveloperDurationSetting(
        label = stringResource(id = R.string.dev_scheduler_pause_tolerance),
        detail = stringResource(id = R.string.dev_scheduler_pause_tolerance_detail),
        millis = reliability?.schedulerPauseToleranceMillis ?: 0L,
        presets = DeveloperViewModel.SCHEDULER_PAUSE_TOLERANCE_PRESETS,
        onSelect = developerViewModel.setSchedulerPauseToleranceMillis,
    )
    DeveloperDurationSetting(
        label = stringResource(id = R.string.dev_scheduler_pause_recovery),
        detail = stringResource(id = R.string.dev_scheduler_pause_recovery_detail),
        millis = reliability?.schedulerPauseRecoveryTimeoutMillis ?: 0L,
        presets = DeveloperViewModel.SCHEDULER_PAUSE_RECOVERY_PRESETS,
        onSelect = developerViewModel.setSchedulerPauseRecoveryTimeoutMillis,
    )
    DeveloperDurationSetting(
        label = stringResource(id = R.string.dev_comparative_connect),
        detail = stringResource(id = R.string.dev_comparative_connect_detail),
        millis = reliability?.blackholeConnectComparativeTimeoutMillis ?: 0L,
        presets = DeveloperViewModel.COMPARATIVE_CONNECT_PRESETS,
        onSelect = developerViewModel.setBlackholeConnectComparativeTimeoutMillis,
    )
    DeveloperDurationSetting(
        label = stringResource(id = R.string.dev_blackhole_receive),
        detail = stringResource(id = R.string.dev_blackhole_receive_detail),
        millis = reliability?.blackholeReceiveTimeoutMillis ?: 0L,
        presets = DeveloperViewModel.BLACKHOLE_RECEIVE_PRESETS,
        onSelect = developerViewModel.setBlackholeReceiveTimeoutMillis,
    )
    DeveloperToggle(
        label = stringResource(id = R.string.dev_soft_verdict),
        detail = stringResource(id = R.string.dev_soft_verdict_detail),
        checked = reliability?.softVerdictDemote == true,
        toggle = developerViewModel.setSoftVerdictDemote,
    )

    Spacer(modifier = Modifier.height(16.dp))

    /** Placement: which exit a flow lands on, and how the pool is shaped. */
    URTextInputLabel(text = stringResource(id = R.string.dev_section_placement))

    DeveloperToggle(
        label = stringResource(id = R.string.dev_effective_tier),
        detail = stringResource(id = R.string.dev_effective_tier_detail),
        checked = reliability?.effectiveTierSelection == true,
        toggle = developerViewModel.setEffectiveTierSelection,
    )
    DeveloperCountSetting(
        label = stringResource(id = R.string.dev_max_flows_per_exit),
        detail = stringResource(id = R.string.dev_max_flows_per_exit_detail),
        count = reliability?.maxFlowsPerExit ?: 0,
        presets = DeveloperViewModel.MAX_FLOWS_PER_EXIT_PRESETS,
        onSelect = developerViewModel.setMaxFlowsPerExit,
    )
    DeveloperToggle(
        label = stringResource(id = R.string.dev_affinity_sticky),
        detail = stringResource(id = R.string.dev_affinity_sticky_detail),
        checked = reliability?.affinityStickyPastCap == true,
        toggle = developerViewModel.setAffinityStickyPastCap,
    )
    DeveloperToggle(
        label = stringResource(id = R.string.dev_fresh_flow_affinity),
        detail = stringResource(id = R.string.dev_fresh_flow_affinity_detail),
        checked = reliability?.freshFlowAffinity == true,
        toggle = developerViewModel.setFreshFlowAffinity,
    )
    DeveloperToggle(
        label = stringResource(id = R.string.dev_affinity_performance),
        detail = stringResource(id = R.string.dev_affinity_performance_detail),
        checked = reliability?.performanceAwareAffinity == true,
        toggle = developerViewModel.setPerformanceAwareAffinity,
    )
    DeveloperToggle(
        label = stringResource(id = R.string.dev_group_follow),
        detail = stringResource(id = R.string.dev_group_follow_detail),
        checked = reliability?.quarantineGroupFollow == true,
        toggle = developerViewModel.setQuarantineGroupFollow,
    )
    DeveloperDurationSetting(
        label = stringResource(id = R.string.dev_group_follow_window),
        detail = stringResource(id = R.string.dev_group_follow_window_detail),
        millis = reliability?.groupFollowWindowMillis ?: 0L,
        presets = DeveloperViewModel.GROUP_FOLLOW_WINDOW_PRESETS,
        onSelect = developerViewModel.setGroupFollowWindowMillis,
    )
    DeveloperCountSetting(
        label = stringResource(id = R.string.dev_removal_budget_count),
        detail = stringResource(id = R.string.dev_removal_budget_count_detail),
        count = reliability?.removalBudgetCount ?: 0,
        presets = DeveloperViewModel.REMOVAL_BUDGET_COUNT_PRESETS,
        onSelect = developerViewModel.setRemovalBudgetCount,
        zeroLabel = "Off",
    )
    DeveloperDurationSetting(
        label = stringResource(id = R.string.dev_removal_budget_window),
        detail = stringResource(id = R.string.dev_removal_budget_window_detail),
        millis = reliability?.removalBudgetWindowMillis ?: 0L,
        presets = DeveloperViewModel.REMOVAL_BUDGET_WINDOW_PRESETS,
        onSelect = developerViewModel.setRemovalBudgetWindowMillis,
    )
    DeveloperToggle(
        label = stringResource(id = R.string.dev_standing_reserve),
        detail = stringResource(id = R.string.dev_standing_reserve_detail),
        checked = reliability?.standingReserve == true,
        toggle = developerViewModel.setStandingReserve,
    )
    DeveloperCountSetting(
        label = stringResource(id = R.string.dev_load_corroboration),
        detail = stringResource(id = R.string.dev_load_corroboration_detail),
        count = reliability?.blackholeLoadCorroboration ?: 0,
        presets = DeveloperViewModel.LOAD_CORROBORATION_PRESETS,
        onSelect = developerViewModel.setBlackholeLoadCorroboration,
        zeroLabel = "Off",
    )
    DeveloperCountSetting(
        label = stringResource(id = R.string.dev_min_blackhole_destinations),
        detail = stringResource(id = R.string.dev_min_blackhole_destinations_detail),
        count = reliability?.minBlackholeDestinations ?: 0,
        presets = DeveloperViewModel.MIN_BLACKHOLE_DESTINATIONS_PRESETS,
        onSelect = developerViewModel.setMinBlackholeDestinations,
        zeroLabel = "Off",
    )
    DeveloperToggle(
        label = stringResource(id = R.string.dev_cluster_affinity),
        detail = stringResource(id = R.string.dev_cluster_affinity_detail),
        checked = reliability?.clusterAffinityFallback == true,
        toggle = developerViewModel.setClusterAffinityFallback,
    )
    DeveloperToggle(
        label = stringResource(id = R.string.dev_server_name_bridge),
        detail = stringResource(id = R.string.dev_server_name_bridge_detail),
        checked = reliability?.serverNameAffinityBridge == true,
        toggle = developerViewModel.setServerNameAffinityBridge,
    )

    Spacer(modifier = Modifier.height(16.dp))

    /**
     * Recovery: getting a flow moving again after its exit fails or the phone's
     * own network changes underneath it.
     */
    URTextInputLabel(text = stringResource(id = R.string.dev_section_recovery))

    DeveloperToggle(
        label = stringResource(id = R.string.dev_quic_rebind),
        detail = stringResource(id = R.string.dev_quic_rebind_detail),
        checked = reliability?.quicRebindOnExitLoss == true,
        toggle = developerViewModel.setQuicRebindOnExitLoss,
    )
    DeveloperToggle(
        label = stringResource(id = R.string.dev_dial_failure_rerace),
        detail = stringResource(id = R.string.dev_dial_failure_rerace_detail),
        checked = reliability?.dialFailureRerace == true,
        toggle = developerViewModel.setDialFailureRerace,
    )
    DeveloperToggle(
        label = stringResource(id = R.string.dev_udp_teardown),
        detail = stringResource(id = R.string.dev_udp_teardown_detail),
        checked = reliability?.udpTeardownSignal == true,
        toggle = developerViewModel.setUdpTeardownSignal,
    )
    DeveloperDurationSetting(
        label = stringResource(id = R.string.dev_tcp_collapse_hold),
        detail = stringResource(id = R.string.dev_tcp_collapse_hold_detail),
        millis = reliability?.tcpCollapseMaxHoldMillis ?: 0L,
        presets = DeveloperViewModel.TCP_COLLAPSE_HOLD_PRESETS,
        onSelect = developerViewModel.setTcpCollapseHoldMillis,
    )
    DeveloperDurationSetting(
        label = stringResource(id = R.string.dev_tcp_idle_timeout),
        detail = stringResource(id = R.string.dev_tcp_idle_timeout_detail),
        millis = reliability?.tcpSequenceIdleTimeoutMillis ?: 0L,
        presets = DeveloperViewModel.TCP_IDLE_TIMEOUT_PRESETS,
        onSelect = developerViewModel.setTcpIdleTimeoutMillis,
    )
    DeveloperDurationSetting(
        label = stringResource(id = R.string.dev_udp_idle_timeout),
        detail = stringResource(id = R.string.dev_udp_idle_timeout_detail),
        millis = reliability?.sequenceIdleTimeoutMillis ?: 0L,
        presets = DeveloperViewModel.UDP_IDLE_TIMEOUT_PRESETS,
        onSelect = developerViewModel.setSequenceIdleTimeoutMillis,
    )
    DeveloperDurationSetting(
        label = stringResource(id = R.string.dev_uplink_gate),
        detail = stringResource(id = R.string.dev_uplink_gate_detail),
        millis = reliability?.uplinkStalenessGateMillis ?: 0L,
        presets = DeveloperViewModel.UPLINK_GATE_PRESETS,
        onSelect = developerViewModel.setUplinkGateMillis,
    )
    DeveloperDurationSetting(
        label = stringResource(id = R.string.dev_formation_poll),
        detail = stringResource(id = R.string.dev_formation_poll_detail),
        millis = reliability?.formationPollTimeoutMillis ?: 0L,
        presets = DeveloperViewModel.FORMATION_POLL_PRESETS,
        onSelect = developerViewModel.setFormationPollTimeoutMillis,
    )

    Spacer(modifier = Modifier.height(16.dp))

    /** Probing: proving an exit can actually reach real destinations. */
    URTextInputLabel(text = stringResource(id = R.string.dev_section_probing))

    DeveloperToggle(
        label = stringResource(id = R.string.dev_probe_providers),
        detail = stringResource(id = R.string.dev_probe_providers_detail),
        checked = reliability?.providerProbe == true,
        toggle = developerViewModel.setProviderProbe,
    )
    DeveloperDurationSetting(
        label = stringResource(id = R.string.dev_probe_timeout),
        detail = stringResource(id = R.string.dev_probe_timeout_detail),
        millis = reliability?.probeTimeoutMillis ?: 0L,
        presets = DeveloperViewModel.PROBE_TIMEOUT_PRESETS,
        onSelect = developerViewModel.setProbeTimeoutMillis,
    )
    DeveloperCountSetting(
        label = stringResource(id = R.string.dev_probe_sample_hosts),
        detail = stringResource(id = R.string.dev_probe_sample_hosts_detail),
        count = reliability?.probeSampleHostCount ?: 0,
        presets = DeveloperViewModel.PROBE_SAMPLE_PRESETS,
        onSelect = developerViewModel.setProbeSampleHostCount,
        zeroLabel = "All",
    )
    DeveloperCountSetting(
        label = stringResource(id = R.string.dev_evaluation_pool),
        detail = stringResource(id = R.string.dev_evaluation_pool_detail),
        count = reliability?.evaluationPoolMultiple ?: 0,
        presets = DeveloperViewModel.EVALUATION_POOL_PRESETS,
        onSelect = developerViewModel.setEvaluationPoolMultiple,
    )
    DeveloperAction(
        label = stringResource(id = R.string.dev_probe_all),
        onClick = developerViewModel.probeAllExits,
    )

    Spacer(modifier = Modifier.height(16.dp))

    /** Observability: what the session writes to the log for later forensics. */
    URTextInputLabel(text = stringResource(id = R.string.dev_section_observability))

    DeveloperDurationSetting(
        label = stringResource(id = R.string.dev_heartbeat),
        detail = stringResource(id = R.string.dev_heartbeat_detail),
        millis = reliability?.heartbeatIntervalMillis ?: 0L,
        presets = DeveloperViewModel.HEARTBEAT_PRESETS,
        onSelect = developerViewModel.setHeartbeatIntervalMillis,
    )

    DeveloperAction(
        label = stringResource(id = R.string.dev_reset_defaults),
        onClick = developerViewModel.resetReliability,
    )

    Spacer(modifier = Modifier.height(16.dp))

    /**
     * Exit readout. A site split across exits shows up as flows spread over
     * several rows instead of collected on one.
     */
    URTextInputLabel(text = stringResource(id = R.string.dev_exits))

    if (developerViewModel.exits.isEmpty()) {
        Text(
            stringResource(id = R.string.dev_no_exits),
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted,
        )
    } else {
        developerViewModel.exits.forEach { exit ->
            DeveloperExitRow(
                exit = exit,
                onDrop = { developerViewModel.dropExit(exit) },
                onStall = { developerViewModel.stallExit(exit, true) },
                onMigrate = { developerViewModel.migrateExit(exit) },
            )
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        DeveloperAction(
            label = stringResource(id = R.string.dev_refresh),
            onClick = { developerViewModel.refresh() },
        )
        DeveloperAction(
            label = stringResource(id = R.string.dev_shuffle_exits),
            onClick = developerViewModel.shuffleExits,
        )
    }

    // fires the same process-wide network-change path a real wifi-to-cellular
    // migration triggers, so the uplink-gate storm drill is one tap instead of
    // physically moving between networks
    DeveloperAction(
        label = stringResource(id = R.string.dev_simulate_network_change),
        onClick = developerViewModel.simulateNetworkChange,
    )

    // The probe suite is deliberately not surfaced here. Its dns probe resolves
    // through Go's pure-go resolver, which has no server list on android and
    // falls back to [::1]:53, and its http probes resolve through a tun built
    // without resolver settings -- so every probe times out at 15s regardless
    // of the tunnel's health. A control that always reports failure is worse
    // than no control: it invites reading a harness bug as a tunnel fault.
    // Measurements above are collected from real traffic and are unaffected.
    //
    // The provider-qualification prober (the "Probe providers" toggle and the
    // "Probes" counter above) does not share that trap: it resolves hostnames
    // by querying a public resolver THROUGH the provider channel being probed
    // -- no OS resolver, no tun resolver settings, no [::1]:53 fallback -- so
    // its results are about the provider, not the harness.

    developerViewModel.lastAction?.let { lastAction ->
        Text(lastAction, style = MaterialTheme.typography.bodySmall, color = TextMuted)
    }

    Spacer(modifier = Modifier.height(32.dp))
}

/**
 * The glog verbosity of the process that writes the logs, cycling
 * Default -> Verbose -> Trace on tap.
 *
 * The value shown is the one the DEVICE reports, never the one last asked for.
 * A set can be clamped by the sdk or refused outright by a hosted device, and
 * neither throws -- so a level that failed to apply has to be visible here
 * rather than assumed, or the user reproduces a fault believing they are
 * capturing V(1) contract accounting and exports a bundle that has none.
 *
 * "Unavailable" is not the same as level 0: it means there is no device to ask
 * yet, so the row is inert rather than reporting a default that is not in
 * force.
 */
@Composable
private fun DeveloperVerbositySetting(
    level: Long?,
    onSelect: (Long) -> Unit,
) {
    val named = logVerbosityLevel(level)
    val name = when (named) {
        LogVerbosityLevel.DEFAULT -> R.string.dev_log_verbosity_default
        LogVerbosityLevel.VERBOSE -> R.string.dev_log_verbosity_verbose
        LogVerbosityLevel.TRACE -> R.string.dev_log_verbosity_trace
        null -> R.string.dev_log_verbosity_unavailable
    }
    // the detail line names what THIS level buys, so the cost of the next step
    // is read before it is taken rather than discovered in the bundle
    val detail = when (named) {
        LogVerbosityLevel.DEFAULT -> R.string.dev_log_verbosity_default_detail
        LogVerbosityLevel.VERBOSE -> R.string.dev_log_verbosity_verbose_detail
        LogVerbosityLevel.TRACE -> R.string.dev_log_verbosity_trace_detail
        null -> R.string.dev_log_verbosity_unavailable_detail
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            // inert with no device: there is nothing to set the level on, and
            // a tap that appeared to work would leave the row claiming a level
            // nothing is running at
            .clickable(enabled = level != null) {
                onSelect(nextLogVerbosity(level ?: LOG_VERBOSITY_DEFAULT))
            }
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.fillMaxWidth(0.72f)) {
            Text(
                stringResource(id = R.string.dev_log_verbosity),
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
            )
            Text(
                stringResource(id = detail),
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
            )
        }
        Text(
            // the number as well as the name -- it is what the sdk reports and
            // what a support thread compares against the bundle's manifest,
            // and it is the only place a level outside the sdk's range shows
            if (level == null) {
                stringResource(id = name)
            } else {
                logVerbosityValueLabel(level, stringResource(id = name))
            },
            style = MaterialTheme.typography.bodyLarge,
            // a level that records real destinations is not an ordinary
            // setting value, and must not read as one
            color = when {
                level == null -> TextMuted
                logVerbosityRecordsDestinations(level) -> TextDanger
                else -> BlueMedium
            },
        )
    }
}

/**
 * Which address family the control plane dials over, cycling Automatic ->
 * Force IPv4 -> Force IPv6 on tap.
 *
 * Unlike [DeveloperVerbositySetting] this row is ALWAYS live. That row is
 * inert without a device because there is no process to set a log level on;
 * this policy is process-global sdk state that is always answerable, and the
 * row has to work signed out and with the tunnel down -- those are the states
 * a user is in when the api is unreachable, which is the only reason to reach
 * for it.
 *
 * The value shown is the policy the sdk reports and never a demotion the sdk
 * made on its own: a row that read "Force IPv4" because the heuristic fired
 * could not be set back to Automatic. The demotion is named in the detail
 * line instead, so Automatic does not look identical whether it has fired or
 * not.
 */
@Composable
private fun DeveloperIpFamilySetting(
    policy: Long,
    status: String,
    onSelect: (Long) -> Unit,
) {
    val name = ipFamilyNameResource(policy)
    val detail = ipFamilyDetailResource(policy, status)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect(nextIpFamilyPolicy(policy)) }
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.fillMaxWidth(0.72f)) {
            Text(
                stringResource(id = R.string.dev_ip_family),
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
            )
            Text(
                // the demoted variant is a %1$s format string; the quiet one
                // ignores the argument
                stringResource(id = detail, status),
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
            )
        }
        Text(
            stringResource(id = name),
            style = MaterialTheme.typography.bodyLarge,
            // a forced family is not an ordinary setting value: it overrides
            // the judgement that keeps a user on a working path, and it is
            // what will strand them on the next network that lacks it
            color = if (clampIpFamilyPolicy(policy) == IP_FAMILY_AUTO) BlueMedium else TextDanger,
        )
    }
}

/**
 * A duration that cycles through presets on tap. The first preset is always 0,
 * which restores the behaviour that shipped before the fix it controls.
 */
@Composable
private fun DeveloperDurationSetting(
    label: String,
    detail: String,
    millis: Long,
    presets: List<Long>,
    onSelect: (Long) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                val index = presets.indexOf(millis)
                // an unrecognized current value lands on the first preset
                onSelect(presets[(index + 1) % presets.size])
            }
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.fillMaxWidth(0.72f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = Color.White)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
        Text(
            formatDurationMillis(millis),
            style = MaterialTheme.typography.bodyLarge,
            color = BlueMedium,
        )
    }
}

/**
 * A count that cycles through presets on tap, mirroring
 * [DeveloperDurationSetting]. The label shown for 0 is caller-supplied because
 * what 0 means differs per knob: an unbounded flow cap reads "Unlimited" (0 is
 * the behaviour that shipped, not a feature switched off), while a storm-breaker
 * count or a corroboration minimum reads "Off" (0 disables the check).
 */
@Composable
private fun DeveloperCountSetting(
    label: String,
    detail: String,
    count: Int,
    presets: List<Int>,
    onSelect: (Int) -> Unit,
    zeroLabel: String = "Unlimited",
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                val index = presets.indexOf(count)
                onSelect(presets[(index + 1) % presets.size])
            }
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.fillMaxWidth(0.72f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = Color.White)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
        Text(
            if (count <= 0) zeroLabel else count.toString(),
            style = MaterialTheme.typography.bodyLarge,
            color = BlueMedium,
        )
    }
}

/**
 * A short label that actually distinguishes one exit from another.
 *
 * Client ids are ULIDs, so the leading characters encode creation time. The
 * channels in a window are opened within milliseconds of each other, so a
 * leading substring renders every row identical -- which makes Drop and Stall
 * unusable, since you cannot tell which exit you are acting on. The random
 * component is in the tail.
 */
fun exitLabel(exit: Exit): String {
    val clientId = exit.clientId.toString()
    return clientId.takeLast(8)
}

/** 0 reads as "Off"; sub-second as ms; otherwise seconds or minutes. */
fun formatDurationMillis(millis: Long): String = when {
    millis <= 0L -> "Off"
    millis < 1000L -> "${millis}ms"
    millis < 60_000L -> {
        val seconds = millis / 1000.0
        if (seconds == seconds.toLong().toDouble()) "${seconds.toLong()}s" else "${seconds}s"
    }
    else -> "${millis / 60_000L}m"
}

/**
 * Blast radius is a ratio, not a count -- 4.0 connections per failure is a
 * different claim from 4 -- so it keeps one decimal rather than rounding to an
 * integer that would hide a change between, say, 4.0 and 4.4.
 */
fun formatBlastRadius(value: Double): String = String.format(Locale.US, "%.1f", value)

/** A read-only counter row, matching the toggle rows but with a value in place of the switch. */
@Composable
private fun DeveloperMetric(
    label: String,
    detail: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.fillMaxWidth(0.6f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = Color.White)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
        Text(value, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
    }
}

@Composable
private fun DeveloperToggle(
    label: String,
    detail: String,
    checked: Boolean,
    toggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.fillMaxWidth(0.75f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = Color.White)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
        URSwitch(checked = checked, toggle = { toggle(!checked) })
    }
}

@Composable
private fun DeveloperAction(
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Text(
        label,
        style = MaterialTheme.typography.bodyMedium,
        // a disabled action has to LOOK disabled: an active-looking control
        // that silently does nothing reads as a broken app, and here the
        // "nothing" is deliberate (an empty selection, or an export already
        // running)
        color = if (enabled) BlueMedium else TextMuted,
        modifier = Modifier
            .padding(vertical = 8.dp)
            .clickable(enabled = enabled, onClick = onClick),
    )
}

@Composable
private fun DeveloperExitRow(
    exit: Exit,
    onDrop: () -> Unit,
    onStall: () -> Unit,
    onMigrate: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(MainTintedBackgroundBase, RoundedCornerShape(8.dp))
            .padding(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                // client ids are ULIDs: the leading characters encode creation
                // time, so channels opened in the same millisecond share a
                // prefix and a leading substring makes them indistinguishable.
                // the entropy is in the tail.
                exitLabel(exit),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
            )
            Text(
                stringResource(id = R.string.dev_exit_flows, exit.flowCount),
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
            )
        }

        val state = buildString {
            append(exit.windowType.ifEmpty { "auto" })
            // the platform's rank for this provider. only the best rank present
            // is raced until it is at the flow cap, so a tier above the minimum
            // with 0 flows is a spare, not a failure. effectiveTier is the rank
            // selection actually uses (tier plus live demerits); when it
            // differs the exit is demoted and "tier N→M" makes that visible
            append(" · tier ")
            append(exit.tier)
            if (exit.effectiveTier > exit.tier) {
                append("→")
                append(exit.effectiveTier)
            }
            // the warning state, by name. "benched" is a quarantine (a soft
            // verdict held against a loaded exit -- it stops taking new
            // placements while its flows keep running, and receive progress
            // acquits it); otherwise the resize pass's cause: draining
            // (healthy, retiring), starved (upstream failing dials), or
            // unhealthy (a verdict demoted or deferred). Before the cause
            // existed every one of these displayed as "draining", which made
            // benches read as retirements.
            when {
                exit.quarantined -> append(" · benched")
                exit.warning -> {
                    append(" · ")
                    append(exit.warningCause.ifEmpty { "warned" })
                }
            }
            if (exit.done) append(" · done")
            if (exit.p2pOnly) append(" · p2p")
            // a probe pass (or the exit's own traffic) proved this provider
            // dials real destinations within the qualification window. Absence
            // of the chip is "not yet proven", never "bad" -- the probe design
            // records no negative state to show
            if (exit.proven) append(" · proven")
        }
        Text(state, style = MaterialTheme.typography.bodySmall, color = TextMuted)

        // shown only when the exit has reported upstream dials it could not
        // open in the recent window -- the out-of-capacity signal the re-race
        // acts on
        if (exit.dialFailureCount > 0) {
            Text(
                stringResource(id = R.string.dev_exit_dial_failures, exit.dialFailureCount),
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            DeveloperAction(label = stringResource(id = R.string.dev_drop_exit), onClick = onDrop)
            DeveloperAction(label = stringResource(id = R.string.dev_stall_exit), onClick = onStall)
            DeveloperAction(label = stringResource(id = R.string.dev_migrate_exit), onClick = onMigrate)
        }
    }
}

@Composable
private fun EarningsSampleDataSettings() {
    // observe the flags through their version so the switches redraw on toggle
    val version by EarningsDebugFlags.version.collectAsState()

    Spacer(modifier = Modifier.height(16.dp))
    URTextInputLabel(text = "Earnings (debug)")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Sample protocol data", style = MaterialTheme.typography.bodyMedium)
        URSwitch(
            checked = EarningsDebugFlags.useSampleData,
            toggle = { EarningsDebugFlags.setUseSampleData(!EarningsDebugFlags.useSampleData) },
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Sample gas key unfunded (v$version)", style = MaterialTheme.typography.bodyMedium)
        URSwitch(
            checked = EarningsDebugFlags.sampleGasUnfunded,
            enabled = EarningsDebugFlags.useSampleData,
            toggle = { EarningsDebugFlags.setSampleGasUnfunded(!EarningsDebugFlags.sampleGasUnfunded) },
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Sample starts without a wallet", style = MaterialTheme.typography.bodyMedium)
        URSwitch(
            checked = EarningsDebugFlags.sampleStartDisconnected,
            enabled = EarningsDebugFlags.useSampleData,
            toggle = { EarningsDebugFlags.setSampleStartDisconnected(!EarningsDebugFlags.sampleStartDisconnected) },
        )
    }
}
