package com.bringyour.network.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.bringyour.network.R
import com.bringyour.network.ui.Route
import com.bringyour.network.ui.components.URSwitch
import com.bringyour.network.ui.theme.Green
import com.bringyour.network.ui.theme.MainTintedBackgroundBase
import com.bringyour.network.ui.theme.MutedCoral
import com.bringyour.network.ui.theme.Red
import com.bringyour.network.ui.theme.TextMuted

/**
 * The connect statistics in two columns, for the stacked connect arrangement
 * at medium width (apple/DUO.md): remote and blocked traffic, local traffic
 * with the split rules, and the blocker with dns over https. Each tile opens
 * what its full card in the drawer opens; the blocked plot, like the full
 * card's, opens nothing.
 */
@Composable
fun ConnectStatsTiles(
    navController: NavController,
    throughputViewModel: ThroughputViewModel,
    blockActionsViewModel: BlockActionsViewModel,
    dnsSettingsViewModel: DnsSettingsViewModel,
    blockerViewModel: BlockerViewModel,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(TileSpacing),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(TileSpacing),
        ) {
            StatsTile(
                onClick = { navController.navigate(Route.ContractStats(provider = false)) },
                modifier = Modifier.weight(1f),
            ) {
                TransferChart(
                    points = throughputViewModel.clientPoints,
                    route = ThroughputRoute.REMOTE,
                    title = stringResource(id = R.string.remote),
                    windowSeconds = throughputViewModel.windowSeconds,
                    height = TileChartHeight,
                )

                Spacer(modifier = Modifier.height(8.dp))

                // the remote traffic by transport, as under the full card's plot;
                // its own tap opens the transport settings
                TransportDistributionBar(
                    distribution = throughputViewModel.clientTransportDistribution,
                    onClick = {
                        navController.navigate(Route.TransportSettings(provider = false))
                    }
                )
            }

            StatsTile(
                onClick = null,
                modifier = Modifier.weight(1f),
            ) {
                TransferChart(
                    points = throughputViewModel.clientPoints,
                    route = ThroughputRoute.BLOCK,
                    title = stringResource(id = R.string.blocked),
                    windowSeconds = throughputViewModel.windowSeconds,
                    byteColor = Red,
                    packetColor = MutedCoral,
                    height = TileChartHeight,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(TileSpacing),
        ) {
            StatsTile(
                onClick = { navController.navigate(Route.SplitRules) },
                modifier = Modifier.weight(1f),
            ) {
                TransferChart(
                    points = throughputViewModel.clientPoints,
                    route = ThroughputRoute.LOCAL,
                    title = stringResource(id = R.string.local),
                    windowSeconds = throughputViewModel.windowSeconds,
                    height = TileChartHeight,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    pluralStringResource(
                        id = R.plurals.split_rule_count,
                        count = blockActionsViewModel.splitRules.size,
                        blockActionsViewModel.splitRules.size,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
                )
            }

            StatsTile(
                onClick = null,
                modifier = Modifier.weight(1f),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(id = R.string.block_ads_and_trackers),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    URSwitch(
                        checked = blockerViewModel.blockerEnabled,
                        toggle = {
                            blockerViewModel.setBlockerEnabled(!blockerViewModel.blockerEnabled)
                        }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // the first of the custom dns card's rows; the row opens the dns
                // settings, as the card does
                val dohEnabled = dnsSettingsViewModel.settings?.dohEnabled
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { navController.navigate(Route.DnsSettings) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(id = R.string.dns_over_https),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )

                    if (dohEnabled != null) {
                        Text(
                            stringResource(id = if (dohEnabled) R.string.on else R.string.off),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (dohEnabled) Green else TextMuted
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsTile(
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MainTintedBackgroundBase)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(12.dp),
        content = content,
    )
}

private val TileSpacing = 8.dp

/** The tiles' plots, shorter than the full cards' 128dp. */
private val TileChartHeight = 72.dp
