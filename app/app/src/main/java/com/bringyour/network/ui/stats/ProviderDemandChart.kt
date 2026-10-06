package com.bringyour.network.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bringyour.network.R
import com.bringyour.network.ui.components.expandableRow
import com.bringyour.network.ui.theme.Amber
import com.bringyour.network.ui.theme.BlueMedium
import com.bringyour.network.ui.theme.Green
import com.bringyour.network.ui.theme.MainBorderBase
import com.bringyour.network.ui.theme.TextFaint
import com.bringyour.network.ui.theme.TextMuted
import kotlin.math.max
import kotlin.math.min

private val DEMAND_CHART_HEIGHT = 64.dp

/**
 * The line under the provide mode row saying why the provider may get no
 * traffic, with a "Change" action that opens the provide settings. It has its
 * own click, which wins over the provider card's.
 */
@Composable
fun ProviderStatusLineRow(
    line: ProviderStatusLine,
    onChange: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onChange)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            when (line) {
                is ProviderStatusLine.Resource -> stringResource(id = line.id)
                is ProviderStatusLine.Text -> line.text
            },
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
            modifier = Modifier.weight(1f)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            stringResource(id = R.string.change),
            style = MaterialTheme.typography.bodySmall,
            color = BlueMedium
        )
    }
}

/**
 * The "Demand" plot under the other provider plots: how often the network
 * offered this device to clients in each minute of the last hour, as 60 bars
 * with the current minute on the right, and an expandable "Why?" with the
 * numbers the device is ranked by. Before or without a histogram the chart
 * area says loading or unavailable, so the layout does not jump.
 */
@Composable
fun ProviderDemandChart(
    status: ProviderStatusUi,
) {
    val display = providerStatusDisplay(status)
    val histogram = remember(status.appearancesPerMinute) {
        ProviderDemandHistogram.fromCounts(status.appearancesPerMinute)
    }
    val caption = stringResource(id = R.string.provider_status_histogram_title)
    val chartShown = display.demand == ProviderDemandState.EMPTY ||
        display.demand == ProviderDemandState.BARS

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            // styled like the other provider plot titles (TransferChart)
            Text(
                stringResource(id = R.string.provider_status_demand),
                style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium),
                color = TextMuted
            )

            Spacer(modifier = Modifier.weight(1f))

            if (display.demand == ProviderDemandState.BARS) {
                val total = histogram.total.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                Text(
                    pluralStringResource(
                        id = R.plurals.provider_status_histogram_total,
                        count = total,
                        total,
                    ),
                    style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Medium),
                    color = TextMuted
                )
            }
        }

        Text(
            caption,
            style = TextStyle(fontSize = 10.sp),
            color = TextMuted
        )

        Spacer(modifier = Modifier.height(6.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(DEMAND_CHART_HEIGHT),
            contentAlignment = Alignment.Center
        ) {
            when (display.demand) {
                ProviderDemandState.LOADING -> DemandChartNote(stringResource(id = R.string.loading))
                ProviderDemandState.UNAVAILABLE -> DemandChartNote(
                    stringResource(id = R.string.provider_status_unavailable)
                )
                ProviderDemandState.EMPTY, ProviderDemandState.BARS -> {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .semantics { contentDescription = caption }
                    ) {
                        drawDemandBars(histogram)
                    }
                    if (display.demand == ProviderDemandState.EMPTY) {
                        DemandChartNote(stringResource(id = R.string.provider_status_histogram_empty))
                    }
                }
            }
        }

        if (chartShown) {
            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    stringResource(id = R.string.provider_status_histogram_start),
                    style = TextStyle(fontSize = 10.sp),
                    color = TextMuted
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    stringResource(id = R.string.provider_status_histogram_end),
                    style = TextStyle(fontSize = 10.sp),
                    color = TextMuted
                )
            }
        }

        if (display.why) {
            val rows = remember(status) { providerStatusRows(status) }
            if (rows.isNotEmpty()) {
                ProviderStatusWhy(rows = rows)
            }
        }
    }
}

/** A muted line in the chart's box: loading, unavailable, or not offered in the last hour. */
@Composable
private fun DemandChartNote(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = TextMuted,
        textAlign = TextAlign.Center
    )
}

/**
 * The bars in the provider green, each `count / max(max count, 1)` of the
 * chart height, over a baseline that a zero leaves bare. The current, partial
 * minute is drawn lighter.
 */
private fun DrawScope.drawDemandBars(histogram: ProviderDemandHistogram) {
    val baselineWidth = 1.dp.toPx()
    val baselineY = size.height - baselineWidth / 2f
    drawLine(
        color = MainBorderBase,
        start = Offset(0f, baselineY),
        end = Offset(size.width, baselineY),
        strokeWidth = baselineWidth
    )

    val barCount = histogram.fractions.size
    if (barCount == 0 || size.width <= 0f) {
        return
    }
    val slotWidth = size.width / barCount
    val gap = min(slotWidth * 0.25f, 2.dp.toPx())
    val barWidth = max(slotWidth - gap, 1f)
    val plotHeight = size.height - baselineWidth
    histogram.fractions.forEachIndexed { index, fraction ->
        if (fraction <= 0f) {
            return@forEachIndexed
        }
        val barHeight = max(plotHeight * fraction, 1f)
        val alpha = if (index == barCount - 1) 0.5f else 0.9f
        drawRect(
            color = Green.copy(alpha = alpha),
            topLeft = Offset(index * slotWidth + gap / 2f, plotHeight - barHeight),
            size = Size(barWidth, barHeight)
        )
    }
}

/**
 * "Why?", collapsed by default: one row per ranking number in the server's
 * order, then the country. A value that holds the device back is amber.
 */
@Composable
private fun ProviderStatusWhy(rows: List<ProviderStatusRow>) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val locale = configuration.locales[0]
    val strings = remember(context, configuration) {
        object : ProviderStatusValueStrings {
            override fun withMinimum(value: String, minimum: String): String =
                context.getString(R.string.provider_status_value_with_minimum, value, minimum)

            override fun withMaximum(value: String, maximum: String): String =
                context.getString(R.string.provider_status_value_with_maximum, value, maximum)

            override fun countOfTotal(count: Long, total: Long): String =
                context.getString(R.string.provider_status_value_count_of_total, count, total)

            override val notYet: String = context.getString(R.string.provider_status_value_not_yet)
            override val noHistory: String = context.getString(R.string.provider_status_value_no_history)
            override val notInPool: String = context.getString(R.string.provider_status_value_not_in_pool)
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // its own click wins over the provider card's
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .expandableRow(expanded = expanded) { expanded = !expanded }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(id = R.string.provider_status_why),
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                if (expanded) {
                    Icons.Filled.KeyboardArrowUp
                } else {
                    Icons.Filled.KeyboardArrowDown
                },
                contentDescription = null,
                tint = TextFaint,
                modifier = Modifier.size(16.dp)
            )
        }

        if (expanded) {
            rows.forEach { row ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            stringResource(id = row.labelResourceId),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            row.value.format(strings, locale),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (row.passes) Color.Unspecified else Amber,
                            textAlign = TextAlign.End
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        stringResource(id = row.helpResourceId),
                        style = TextStyle(fontSize = 11.sp),
                        color = TextMuted
                    )
                }
            }
        }
    }
}
