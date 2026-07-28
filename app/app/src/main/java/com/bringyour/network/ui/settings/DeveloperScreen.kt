package com.bringyour.network.ui.settings

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.bringyour.network.R
import com.bringyour.network.ui.components.URSwitch
import com.bringyour.network.ui.components.URTextInputLabel
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.BlueMedium
import com.bringyour.network.ui.theme.MainTintedBackgroundBase
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.TopBarTitleTextStyle
import com.bringyour.sdk.Exit

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

    if (!developerViewModel.connected) {
        Text(
            stringResource(id = R.string.developer_disconnected),
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted,
        )
        return
    }

    /**
     * Timing. Each fix's "off" value reproduces the behaviour that shipped
     * before it, so a freeze can still be A/B'd -- but the middle values are
     * where a per-connection sweet spot is found.
     */
    URTextInputLabel(text = stringResource(id = R.string.dev_timing))

    DeveloperDurationSetting(
        label = stringResource(id = R.string.dev_send_stall),
        detail = stringResource(id = R.string.dev_send_stall_detail),
        millis = reliability?.sendStallTimeoutMillis ?: 0L,
        presets = DeveloperViewModel.SEND_STALL_PRESETS,
        onSelect = developerViewModel.setSendStallTimeoutMillis,
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

    Spacer(modifier = Modifier.height(16.dp))

    URTextInputLabel(text = stringResource(id = R.string.dev_behaviour))

    DeveloperToggle(
        label = stringResource(id = R.string.dev_udp_teardown),
        detail = stringResource(id = R.string.dev_udp_teardown_detail),
        checked = reliability?.udpTeardownSignal == true,
        toggle = developerViewModel.setUdpTeardownSignal,
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

    developerViewModel.lastAction?.let { lastAction ->
        Text(lastAction, style = MaterialTheme.typography.bodySmall, color = TextMuted)
    }

    Spacer(modifier = Modifier.height(32.dp))
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
    onClick: () -> Unit,
) {
    Text(
        label,
        style = MaterialTheme.typography.bodyMedium,
        color = BlueMedium,
        modifier = Modifier
            .padding(vertical = 8.dp)
            .clickable(onClick = onClick),
    )
}

@Composable
private fun DeveloperExitRow(
    exit: Exit,
    onDrop: () -> Unit,
    onStall: () -> Unit,
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
                exit.clientId.toString().take(8),
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
            if (exit.warning) append(" · draining")
            if (exit.done) append(" · done")
            if (exit.p2pOnly) append(" · p2p")
        }
        Text(state, style = MaterialTheme.typography.bodySmall, color = TextMuted)

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            DeveloperAction(label = stringResource(id = R.string.dev_drop_exit), onClick = onDrop)
            DeveloperAction(label = stringResource(id = R.string.dev_stall_exit), onClick = onStall)
        }
    }
}
