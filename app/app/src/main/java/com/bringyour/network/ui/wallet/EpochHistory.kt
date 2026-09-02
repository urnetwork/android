package com.bringyour.network.ui.wallet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bringyour.network.R
import com.bringyour.network.ui.theme.BlueMedium
import com.bringyour.network.ui.theme.Green
import com.bringyour.network.ui.theme.TextFaint
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.URNetworkTheme
import com.bringyour.network.ui.theme.ppNeueBitBold
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Per-epoch history. Points are always shown; the alpha column appears only once a
 * wallet is connected, and only for epochs the vault knows about (not retroactive).
 */
@Composable
fun EpochHistory(
    epochs: List<AccountEpoch>,
    claimsByEpoch: Map<Long, EpochClaim>,
    showAlpha: Boolean,
    loaded: Boolean,
    formatAlpha: (Long) -> String,
    formatShareBps: (Long) -> String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            stringResource(id = R.string.epoch_history),
            style = MaterialTheme.typography.bodyLarge,
            color = TextMuted
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (epochs.isEmpty()) {
            if (loaded) {
                Text(
                    stringResource(id = R.string.no_points_yet),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
            }
            return@Column
        }

        epochs.forEachIndexed { i, epoch ->
            EpochRow(
                epoch = epoch,
                claim = claimsByEpoch[epoch.epoch],
                showAlpha = showAlpha,
                formatAlpha = formatAlpha,
                formatShareBps = formatShareBps
            )
            if (i < epochs.size - 1) {
                HorizontalDivider(color = TextFaint)
            }
        }
    }
}

@Composable
fun EpochRow(
    epoch: AccountEpoch,
    claim: EpochClaim?,
    showAlpha: Boolean,
    formatAlpha: (Long) -> String,
    formatShareBps: (Long) -> String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                stringResource(id = R.string.epoch_row_title, epoch.epoch),
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                epochDateRange(epoch.startMillis, epoch.endMillis),
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )
            if (0 < epoch.shareBps) {
                Text(
                    stringResource(id = R.string.epoch_share_of_block, formatShareBps(epoch.shareBps)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
            }
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                stringResource(id = R.string.points_short, EarningsFormat.points(epoch.points)),
                style = MaterialTheme.typography.bodyLarge.copy(fontFamily = ppNeueBitBold)
            )
            if (showAlpha && claim != null && claim.status != EpochClaimStatus.NOT_FINALIZED) {
                val (label, color) = when (claim.status) {
                    EpochClaimStatus.CLAIMABLE -> stringResource(id = R.string.unclaimed) to BlueMedium
                    EpochClaimStatus.CLAIMED -> stringResource(id = R.string.claim_confirmed) to Green
                    EpochClaimStatus.EXPIRED -> stringResource(id = R.string.claim_expired) to TextMuted
                    else -> null to TextMuted
                }
                if (0 < claim.amountRao) {
                    Text(
                        formatAlpha(claim.amountRao),
                        style = MaterialTheme.typography.bodyMedium,
                        color = color
                    )
                }
                if (label != null) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        color = color
                    )
                }
            }
        }
    }
}

fun epochDateRange(startMillis: Long, endMillis: Long): String {
    val format = SimpleDateFormat("MMM d", Locale.getDefault())
    val start = format.format(Date(startMillis))
    val end = format.format(Date(endMillis))
    return if (start == end) start else "$start – $end"
}

@Preview
@Composable
private fun EpochHistoryPreview() {
    val now = System.currentTimeMillis()
    val day = 24L * 60 * 60 * 1000
    URNetworkTheme {
        EpochHistory(
            epochs = listOf(
                AccountEpoch(1219, now - day, now, 1840.0, 0),
                AccountEpoch(1218, now - 2 * day, now - day, 2210.0, 71),
                AccountEpoch(1217, now - 3 * day, now - 2 * day, 1975.0, 64),
            ),
            claimsByEpoch = mapOf(
                1218L to EpochClaim(1218, 71, 3_241_000_000, EpochClaimStatus.CLAIMABLE, 0, 0, null),
                1217L to EpochClaim(1217, 64, 2_905_500_000, EpochClaimStatus.CLAIMED, 0, 0, "0x1"),
            ),
            showAlpha = true,
            loaded = true,
            formatAlpha = { EarningsFormat.alpha(it) },
            formatShareBps = { EarningsFormat.shareBps(it) },
            modifier = Modifier.padding(16.dp)
        )
    }
}
