package com.bringyour.network.ui.stats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.bringyour.network.R
import com.bringyour.network.ui.Route
import com.bringyour.network.ui.theme.Red
import com.bringyour.network.ui.theme.MutedCoral
import com.bringyour.network.ui.theme.TextFaint
import com.bringyour.network.ui.theme.TextMuted

/**
 * Provider statistics: local and blocked traffic relayed for remote
 * clients. Tap to open the provider contract details.
 */
@Composable
fun ProviderStatsSection(
    navController: NavController,
    throughputViewModel: ThroughputViewModel = hiltViewModel(),
) {

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = throughputViewModel.hasProviderStats) {
                navController.navigate(Route.ContractStats(provider = true))
            }
    ) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(id = R.string.provider_statistics),
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )
            if (throughputViewModel.hasProviderStats) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = TextFaint,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (throughputViewModel.hasProviderStats) {

            TransferChart(
                points = throughputViewModel.providerPoints,
                route = ThroughputRoute.LOCAL,
                title = stringResource(id = R.string.local),
                windowSeconds = throughputViewModel.windowSeconds,
            )

            Spacer(modifier = Modifier.height(12.dp))

            /**
             * The relayed traffic of the window by the transport this
             * device used to carry it, under the provider plot. Tap to
             * open the provider transport settings. The child tap wins over
             * the section's tap.
             */
            TransportDistributionBar(
                distribution = throughputViewModel.providerTransportDistribution,
                onClick = {
                    navController.navigate(Route.TransportSettings(provider = true))
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            TransferChart(
                points = throughputViewModel.providerPoints,
                route = ThroughputRoute.BLOCK,
                title = stringResource(id = R.string.blocked),
                windowSeconds = throughputViewModel.windowSeconds,
                byteColor = Red,
                packetColor = MutedCoral,
            )

        } else {

            Text(
                stringResource(id = R.string.providing_disabled),
                style = MaterialTheme.typography.bodyMedium,
                color = TextFaint,
                modifier = Modifier.padding(bottom = 8.dp)
            )

        }

    }
}
