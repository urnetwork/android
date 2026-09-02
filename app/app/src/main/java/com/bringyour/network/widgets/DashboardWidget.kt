package com.bringyour.network.widgets

import android.content.Context
import android.content.ComponentName
import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.ColorFilter
import com.bringyour.network.LoginActivity
import com.bringyour.network.QuickConnectActivity
import com.bringyour.network.R
import com.bringyour.network.utils.formatByteCountCompact
import com.bringyour.network.utils.formatByteRate
import com.bringyour.network.widgets.render.BalanceBarRenderer
import com.bringyour.network.widgets.render.ThroughputChartRenderer
import kotlin.math.roundToInt

/**
 * The Home Screen dashboard: the transfer balance bar, the connected location
 * with its provider count, the quick connect button and, at the tall size,
 * the client and provider throughput for the last hour. Responsive: the
 * launcher picks the largest of the three layouts that fits, as the user
 * resizes.
 */
class DashboardWidget : GlanceAppWidget() {

    companion object {
        /** A single row: mark, location, quick connect. */
        val COMPACT = DpSize(110.dp, 48.dp)
        /** The iOS medium widget: header + balance bar. */
        val SHORT = DpSize(200.dp, 100.dp)
        /** Adds the two charts and the footer. */
        val TALL = DpSize(200.dp, 200.dp)
    }

    /**
     * Exact, not responsive: the charts and the balance bar are bitmaps
     * rendered for the widget's real width (a responsive composition only
     * sees its size bucket, which stretched them on the phone). The three
     * layouts are chosen by thresholds on the exact size.
     */
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entry = WidgetEntry.load(context)
        provideContent { DashboardContent(entry) }
    }
}

@Composable
internal fun DashboardContent(entry: WidgetEntry) {
    val size = LocalSize.current
    val context = LocalContext.current
    WidgetSurface {
        when {
            size.height < DashboardWidget.SHORT.height -> {
                Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                    DashboardHeader(entry, compact = true)
                }
            }
            size.height < DashboardWidget.TALL.height -> {
                Column(
                    modifier = GlanceModifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DashboardHeader(entry, compact = false)
                    Spacer(GlanceModifier.height(10.dp))
                    BalanceBar(entry.balance)
                }
            }
            else -> {
                Column(modifier = GlanceModifier.fillMaxSize()) {
                    DashboardHeader(entry, compact = false)
                    Spacer(GlanceModifier.height(10.dp))
                    BalanceBar(entry.balance)
                    Spacer(GlanceModifier.height(10.dp))
                    val chartHeight = ((size.height.value - 130f) / 2f).coerceIn(40f, 90f).dp
                    ThroughputChart(
                        title = context.getString(R.string.widget_client),
                        entry = entry,
                        provider = false,
                        height = chartHeight,
                    )
                    Spacer(GlanceModifier.height(6.dp))
                    ThroughputChart(
                        title = context.getString(R.string.widget_provider),
                        entry = entry,
                        provider = true,
                        height = chartHeight,
                    )
                    Spacer(GlanceModifier.defaultWeight())
                    Text(footerText(context, entry), style = WidgetTheme.faint, maxLines = 1)
                }
            }
        }
    }
}

/** The widget background: brand black, the launcher's corner radius, edge padding. */
@Composable
internal fun WidgetSurface(content: @Composable () -> Unit) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(WidgetTheme.background)
            .cornerRadius(android.R.dimen.system_app_widget_background_radius)
            .padding(14.dp),
    ) {
        content()
    }
}

@Composable
private fun DashboardHeader(entry: WidgetEntry, compact: Boolean) {
    val context = LocalContext.current
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // the solid connector mark: white when off, the app's connected green when up
        Image(
            provider = ImageProvider(R.drawable.ic_tile_quick_on),
            contentDescription = null,
            modifier = GlanceModifier.size(if (compact) 20.dp else 24.dp),
            colorFilter = ColorFilter.tint(if (entry.isOn) WidgetTheme.connected else WidgetTheme.text),
        )
        Spacer(GlanceModifier.width(10.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(locationTitle(context, entry), style = WidgetTheme.title, maxLines = 1)
            if (!compact) {
                Text(subtitle(context, entry), style = WidgetTheme.caption, maxLines = 1)
            }
        }
        Spacer(GlanceModifier.width(8.dp))
        QuickConnectButton(entry)
    }
}

/**
 * The in-widget quick connect: the connector mark in a capsule, gray when
 * off and the accent pink when on. A tap runs the same trampoline as the
 * launcher shortcuts (QuickConnectActivity, no UI), which toggles and opens
 * the app only when it is needed. The widget re-renders from the device's
 * new state within the same second, so no separate optimistic state is kept.
 */
@Composable
private fun QuickConnectButton(entry: WidgetEntry) {
    val context = LocalContext.current
    val action = if (entry.isConfigured) {
        actionStartActivity(
            ComponentName(context, QuickConnectActivity::class.java),
            actionParametersOf(QuickConnectActivity.ACTION_PARAMETER to QuickConnectActivity.ACTION_TOGGLE),
        )
    } else {
        actionStartActivity(ComponentName(context, LoginActivity::class.java))
    }
    Box(
        modifier = GlanceModifier
            .size(48.dp, 36.dp)
            .cornerRadius(18.dp)
            .background(if (entry.isOn) WidgetTheme.tint else WidgetTheme.card)
            .clickable(action),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_tile_quick_on),
            contentDescription = context.getString(if (entry.isOn) R.string.disconnect else R.string.connect),
            modifier = GlanceModifier.size(18.dp),
            colorFilter = ColorFilter.tint(WidgetTheme.text),
        )
    }
}

/** The three-segment transfer balance bar, as the app's UsageBar draws it (a bitmap: Glance rows only weight equally). */
@Composable
internal fun BalanceBar(balance: WidgetBalanceSnapshot?) {
    val context = LocalContext.current
    val size = LocalSize.current
    val density = context.resources.displayMetrics.density
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(context.getString(R.string.balance), style = WidgetTheme.caption)
            Spacer(GlanceModifier.defaultWeight())
            Text(
                if (balance == null) "—" else
                    "${formatByteCountCompact(balance.balanceByteCount)} / ${formatByteCountCompact(balance.startBalanceByteCount)}",
                style = WidgetTheme.label,
                maxLines = 1,
            )
        }
        Spacer(GlanceModifier.height(4.dp))
        Image(
            provider = ImageProvider(
                BalanceBarRenderer.render(
                    ((size.width.value - 28f) * density).roundToInt(),
                    (8f * density).roundToInt(),
                    balance,
                    WidgetTheme.balanceUsedArgb,
                    WidgetTheme.balancePendingArgb,
                    WidgetTheme.balanceAvailableArgb,
                    density,
                )
            ),
            contentDescription = null,
            modifier = GlanceModifier.fillMaxWidth().height(8.dp),
            contentScale = ContentScale.FillBounds,
        )
    }
}

@Composable
private fun ThroughputChart(title: String, entry: WidgetEntry, provider: Boolean, height: androidx.compose.ui.unit.Dp) {
    val context = LocalContext.current
    val size = LocalSize.current
    val density = context.resources.displayMetrics.density
    val throughput = entry.tunnel.throughput
    val points = throughput.buckets.map {
        if (provider) ThroughputChartRenderer.Point(it.start, it.providerEgress, it.providerIngress)
        else ThroughputChartRenderer.Point(it.start, it.clientEgress, it.clientIngress)
    }
    val peak = ThroughputChartRenderer.peak(points)
    val widthPx = ((size.width.value - 28f) * density).roundToInt()
    val heightPx = ((height.value - 16f) * density).roundToInt()
    Column(modifier = GlanceModifier.fillMaxWidth().height(height)) {
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            Text(title, style = WidgetTheme.caption)
            Spacer(GlanceModifier.defaultWeight())
            Text(
                if (provider && !entry.tunnel.providing && peak == 0L) context.getString(R.string.widget_not_providing)
                else context.getString(R.string.widget_peak_rate, formatByteRate(peak / maxOf(1L, throughput.bucketSeconds))),
                style = WidgetTheme.label,
                maxLines = 1,
            )
        }
        Spacer(GlanceModifier.height(2.dp))
        Image(
            provider = ImageProvider(
                ThroughputChartRenderer.render(
                    widthPx, heightPx, points, throughput.bucketSeconds, entry.nowMillis,
                    if (provider) WidgetTheme.providerSeriesArgb else WidgetTheme.clientSeriesArgb,
                    density,
                )
            ),
            contentDescription = null,
            modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
            contentScale = ContentScale.FillBounds,
        )
    }
}

private fun locationTitle(context: Context, entry: WidgetEntry): String {
    if (!entry.isConfigured) return context.getString(R.string.widget_not_signed_in)
    if (!entry.isOn) return context.getString(R.string.tile_status_disconnected)
    val location = entry.tunnel.location
    if (!entry.showsTunnelData || location == null) return context.getString(R.string.tile_status_connected)
    if (location.bestAvailable) return context.getString(R.string.best_available_provider)
    return location.name.ifEmpty { context.getString(R.string.tile_status_connected) }
}

private fun subtitle(context: Context, entry: WidgetEntry): String {
    if (entry.showsTunnelData) {
        val count = entry.tunnel.providers.size
        return if (entry.tunnel.providing) {
            context.resources.getQuantityString(R.plurals.widget_provider_count_providing, count, count)
        } else {
            context.resources.getQuantityString(R.plurals.widget_provider_count, count, count)
        }
    }
    return if (entry.isConfigured) context.getString(R.string.app_name) else context.getString(R.string.widget_open_to_set_up)
}

private fun footerText(context: Context, entry: WidgetEntry): String {
    val updatedAt = maxOf(entry.tunnel.updatedAtMillis, entry.balance?.updatedAtMillis ?: 0L)
    if (!entry.tunnel.tunnelActive && entry.balance == null) {
        return context.getString(R.string.widget_connect_once)
    }
    if (entry.nowMillis - updatedAt < 60_000L) return context.getString(R.string.widget_updated_just_now)
    val relative = DateUtils.getRelativeTimeSpanString(updatedAt, entry.nowMillis, DateUtils.MINUTE_IN_MILLIS)
    return context.getString(R.string.widget_updated_ago, relative)
}
