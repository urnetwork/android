package com.bringyour.network.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.bringyour.network.R
import com.bringyour.network.ui.theme.MainTintedBackgroundBase
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.utils.formatByteCountCompact
import com.bringyour.sdk.ProvideShareLimits
import com.bringyour.sdk.ProvideShareStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private const val BYTES_PER_SECOND_PER_MBPS = 125_000L
private const val BYTES_PER_GIB = 1024L * 1024 * 1024

// the speeds offered, in Mbps, and the monthly data offered, in GiB; 0 is no limit
private val provideShareSpeedMbps = listOf(0L, 1L, 2L, 5L, 10L, 20L, 50L, 100L)
private val provideShareMonthlyGib = listOf(0L, 5L, 10L, 25L, 50L, 100L, 250L, 500L, 1024L)

/**
 * The owner's limits on how much this device shares with others (the sdk's
 * provide share limits, which the device keeps and applies): the sharing speed
 * and the monthly data, each with no limit first. A value set elsewhere (the
 * CLI) that is not offered shows as one more option. Under them, this month's
 * sharing as the device reports it, and what a limit costs.
 */
@Composable
fun ProvideShareLimitsPicker(
    provideShareLimits: ProvideShareLimits?,
    provideShareStatus: ProvideShareStatus?,
    // writes both limits; a change passes the other one as it is
    setProvideShareLimits: (maxBytesPerSecond: Long, monthlyByteCount: Long) -> Unit,
) {
    val maxBytesPerSecond = provideShareLimits?.maxBytesPerSecond ?: 0L
    val monthlyByteCount = provideShareLimits?.monthlyByteCount ?: 0L

    val speedOptions = provideShareOptions(
        provideShareSpeedMbps.map { it * BYTES_PER_SECOND_PER_MBPS },
        maxBytesPerSecond,
    ).map { bytesPerSecond ->
        val optionLabel = if (bytesPerSecond <= 0L) {
            stringResource(id = R.string.provide_share_no_limit)
        } else {
            stringResource(
                id = R.string.provide_share_speed_mbps,
                Math.round(bytesPerSecond.toDouble() / BYTES_PER_SECOND_PER_MBPS)
            )
        }
        bytesPerSecond to optionLabel
    }

    val monthlyOptions = provideShareOptions(
        provideShareMonthlyGib.map { it * BYTES_PER_GIB },
        monthlyByteCount,
    ).map { byteCount ->
        val optionLabel = if (byteCount <= 0L) {
            stringResource(id = R.string.provide_share_no_limit)
        } else {
            formatByteCountCompact(byteCount)
        }
        byteCount to optionLabel
    }

    Column(
        modifier = Modifier
            .fillMaxWidth(),
    ) {

        Text(
            stringResource(id = R.string.provide_share_limits),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White
        )

        ProvideShareLimitRow(
            label = stringResource(id = R.string.provide_share_max_speed),
            options = speedOptions,
            selected = maxBytesPerSecond,
            onSelect = { setProvideShareLimits(it, monthlyByteCount) }
        )

        ProvideShareLimitRow(
            label = stringResource(id = R.string.provide_share_monthly_limit),
            options = monthlyOptions,
            selected = monthlyByteCount,
            onSelect = { setProvideShareLimits(maxBytesPerSecond, it) }
        )

        if (provideShareStatus != null) {
            Text(
                provideShareStatusText(provideShareStatus),
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )

            Spacer(modifier = Modifier.height(4.dp))
        }

        Text(
            stringResource(id = R.string.provide_share_limits_help),
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted
        )
    }
}

/**
 * One limit: its label and the option in force, which opens a menu of the
 * options, as the VLESS settings' pickers do.
 */
@Composable
private fun ProvideShareLimitRow(
    label: String,
    // each value with its label, in order
    options: List<Pair<Long, String>>,
    selected: Long,
    onSelect: (Long) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    onClickLabel = label,
                    role = Role.DropdownList,
                ) {
                    expanded = true
                }
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                options.firstOrNull { it.first == selected }?.second ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )

            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = TextMuted
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = MainTintedBackgroundBase,
        ) {
            options.forEach { (value, optionLabel) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            optionLabel,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelect(value)
                    },
                )
            }
        }
    }
}

/**
 * The values a limit offers, with the one in force put in order among them
 * when it is not one of them.
 */
private fun provideShareOptions(offered: List<Long>, current: Long): List<Long> {
    return if (current in offered) offered else (offered + current).sorted()
}

/**
 * This month's sharing: the budget reached with the day sharing with others
 * resumes, else the data shared against the monthly limit, else the data shared.
 */
@Composable
private fun provideShareStatusText(status: ProvideShareStatus): String {
    return when {
        status.budgetReached -> stringResource(
            id = R.string.provide_share_limit_reached,
            provideShareResumeDate(status.periodEndMillis)
        )
        0L < status.monthlyByteCount -> stringResource(
            id = R.string.provide_share_used_of_limit,
            formatByteCountCompact(status.byteCount),
            formatByteCountCompact(status.monthlyByteCount)
        )
        else -> stringResource(
            id = R.string.provide_share_used,
            formatByteCountCompact(status.byteCount)
        )
    }
}

/** The day the month ends, in the reader's own form and time zone. */
@Composable
private fun provideShareResumeDate(millis: Long): String {
    val locale = LocalConfiguration.current.locales[0]
    return DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(locale)
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(millis))
}
