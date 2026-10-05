package com.bringyour.network.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bringyour.network.R
import com.bringyour.network.ui.connect.OutOfBalanceKind
import com.bringyour.network.ui.theme.BlueMedium
import com.bringyour.network.ui.theme.Red
import com.bringyour.network.ui.theme.TextFaint
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.utils.formatBalanceBytes
import kotlinx.coroutines.delay

/**
 * "About your data": what Used, Pending and Available mean, the daily balance
 * and when the free data refreshes. See DataInfo.kt.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataInfoSheet(
    presented: Boolean,
    onDismiss: () -> Unit,
    startBalanceByteCount: Long,
    availableByteCount: Long,
    pendingByteCount: Long,
    showFreeRefresh: Boolean,
) {

    if (!presented) {
        return
    }

    val info = dataInfo(
        startBalanceByteCount = startBalanceByteCount,
        availableByteCount = availableByteCount,
        pendingByteCount = pendingByteCount,
    )

    ModalBottomSheet(
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        onDismissRequest = { onDismiss() },
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {

            Text(
                stringResource(id = R.string.data_info_title),
                style = MaterialTheme.typography.headlineSmall
            )

            Spacer(modifier = Modifier.height(16.dp))

            // the same colors as the usage bar
            DataInfoRow(
                label = stringResource(id = R.string.used_data_key),
                color = BlueMedium,
                amount = info.used,
                explanation = stringResource(id = R.string.data_info_used),
            )

            Spacer(modifier = Modifier.height(12.dp))

            DataInfoRow(
                label = stringResource(id = R.string.pending_data_key),
                color = Red,
                amount = info.pending,
                explanation = stringResource(id = R.string.data_info_pending),
            )

            Spacer(modifier = Modifier.height(12.dp))

            DataInfoRow(
                label = stringResource(id = R.string.available_data_key),
                color = TextFaint,
                amount = info.available,
                explanation = stringResource(id = R.string.data_info_available),
            )

            Spacer(modifier = Modifier.height(16.dp))

            HorizontalDivider()

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    stringResource(id = R.string.daily_data_balance_label),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
                Text(
                    info.daily,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
            }

            if (showFreeRefresh) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    stringResource(id = R.string.data_info_refresh_at, rememberFreeRefreshCountdown()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            URButton(
                onClick = onDismiss,
                style = ButtonStyle.OUTLINE
            ) { buttonTextStyle ->
                Text(
                    stringResource(id = R.string.close),
                    style = buttonTextStyle
                )
            }
        }
    }
}

/** One amount: the usage bar's key and name, the amount, and what it means. */
@Composable
private fun DataInfoRow(
    label: String,
    color: Color,
    amount: String,
    explanation: String,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            ChartKey(
                label = label,
                color = color
            )
            Text(
                amount,
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            explanation,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

/**
 * The time left until the next free data refresh as a compact duration
 * ("5h 12m"), ticking once per displayed minute.
 */
@Composable
fun rememberFreeRefreshCountdown(): String {
    val countdown by produceState(freeRefreshCountdown(System.currentTimeMillis())) {
        while (true) {
            delay(millisUntilCountdownChanges(System.currentTimeMillis()))
            value = freeRefreshCountdown(System.currentTimeMillis())
        }
    }
    val resources = LocalContext.current.resources
    return formatRefreshCountdown(
        countdown,
        hoursAndMinutes = { hours, minutes ->
            resources.getString(R.string.provider_connected_duration_hours, hours.toInt(), minutes.toInt())
        },
        minutesOnly = { minutes ->
            resources.getString(R.string.provider_connected_duration_minutes, minutes.toInt())
        },
    )
}

/**
 * The out-of-balance line that says whether the missing data is reserved by
 * open connections (with the reserved amount) or used up, or null for
 * neither. Shared by the connect drawer and the upgrade screen a blocked
 * connect opens.
 */
@Composable
internal fun outOfBalanceKindText(kind: OutOfBalanceKind, reservedByteCount: Long): String? = when (kind) {
    OutOfBalanceKind.RESERVED ->
        stringResource(id = R.string.insufficient_balance_reserved, formatBalanceBytes(reservedByteCount))
    OutOfBalanceKind.EXHAUSTED -> stringResource(id = R.string.insufficient_balance_exhausted)
    OutOfBalanceKind.UNKNOWN -> null
}
