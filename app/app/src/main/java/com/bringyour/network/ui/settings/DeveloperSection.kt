package com.bringyour.network.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bringyour.network.R
import com.bringyour.network.ui.components.URSwitch
import com.bringyour.network.ui.components.URTextInputLabel
import com.bringyour.network.ui.theme.BlueMedium
import com.bringyour.network.ui.theme.MainTintedBackgroundBase
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.sdk.Exit

/**
 * Developer tools for diagnosing the multi-exit freeze.
 *
 * Deliberately a plain Settings section rather than a hidden gesture: these
 * controls exist to be used against a live connection while something is
 * wrong, and hunting for a secret tap target at that moment is the wrong
 * experience. The destructive ones are labelled for what they actually do to
 * the connection.
 */
@Composable
fun DeveloperSection(
    developerViewModel: DeveloperViewModel = hiltViewModel(),
) {
    val reliability = developerViewModel.reliability

    URTextInputLabel(text = stringResource(id = R.string.developer))

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
        Spacer(modifier = Modifier.height(24.dp))
        return
    }

    /**
     * Reliability fixes. Each one addresses a different freeze; turning one off
     * restores exactly the behaviour that shipped before it, so a freeze can be
     * A/B'd while it is happening.
     */
    DeveloperToggle(
        label = stringResource(id = R.string.dev_udp_teardown),
        detail = stringResource(id = R.string.dev_udp_teardown_detail),
        checked = reliability?.udpTeardownSignal == true,
        toggle = developerViewModel.setUdpTeardownSignal,
    )
    DeveloperToggle(
        label = stringResource(id = R.string.dev_tcp_collapse_hold),
        detail = stringResource(id = R.string.dev_tcp_collapse_hold_detail),
        checked = (reliability?.tcpCollapseMaxHoldMillis ?: 0L) > 0L,
        toggle = developerViewModel.setTcpCollapseHold,
    )
    DeveloperToggle(
        label = stringResource(id = R.string.dev_send_stall),
        detail = stringResource(id = R.string.dev_send_stall_detail),
        checked = (reliability?.sendStallTimeoutMillis ?: 0L) > 0L,
        toggle = developerViewModel.setSendStallTimeout,
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
    DeveloperToggle(
        label = stringResource(id = R.string.dev_tcp_idle_timeout),
        detail = stringResource(id = R.string.dev_tcp_idle_timeout_detail),
        checked = (reliability?.tcpSequenceIdleTimeoutMillis ?: 0L) > 0L,
        toggle = developerViewModel.setTcpIdleTimeout,
    )

    Spacer(modifier = Modifier.height(8.dp))

    DeveloperAction(
        label = stringResource(id = R.string.dev_reset_defaults),
        onClick = developerViewModel.resetReliability,
    )

    Spacer(modifier = Modifier.height(24.dp))

    /**
     * Exit readout. A site split across exits shows up here as flows spread
     * over several rows instead of collected on one.
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

    Spacer(modifier = Modifier.height(8.dp))

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
        Spacer(modifier = Modifier.height(8.dp))
        Text(lastAction, style = MaterialTheme.typography.bodySmall, color = TextMuted)
    }

    Spacer(modifier = Modifier.height(24.dp))
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

        Spacer(modifier = Modifier.height(4.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            DeveloperAction(label = stringResource(id = R.string.dev_drop_exit), onClick = onDrop)
            DeveloperAction(label = stringResource(id = R.string.dev_stall_exit), onClick = onStall)
        }
    }
}
