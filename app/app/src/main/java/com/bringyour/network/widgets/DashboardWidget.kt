package com.bringyour.network.widgets

import com.bringyour.network.ui.shared.models.ProvideControlMode
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
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
import com.bringyour.network.utils.formatPacketRate
import com.bringyour.network.widgets.render.BalanceBarRenderer
import com.bringyour.network.widgets.render.ThroughputChartRenderer
import com.bringyour.network.widgets.render.WidgetColors
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
    WidgetSurface(entry, QuickConnectActivity.ROUTE_CONNECT) {
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

/**
 * The widget background: brand black, the launcher's corner radius, edge
 * padding. A tap anywhere on it opens the app on the widget's screen
 * (`route`, through the QuickConnectActivity trampoline), or on sign-in when
 * there is no account; the quick connect button's own click wins inside it.
 */
@Composable
internal fun WidgetSurface(entry: WidgetEntry, route: String, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val open = if (entry.isConfigured) {
        actionStartActivity(
            ComponentName(context, QuickConnectActivity::class.java),
            actionParametersOf(
                QuickConnectActivity.ACTION_PARAMETER to QuickConnectActivity.ACTION_OPEN,
                QuickConnectActivity.ROUTE_PARAMETER to route,
            ),
        )
    } else {
        actionStartActivity(ComponentName(context, LoginActivity::class.java))
    }
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(WidgetTheme.background)
            .cornerRadius(android.R.dimen.system_app_widget_background_radius)
            .padding(14.dp)
            .clickable(open),
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
        // the location's country color as a filled disc, the mark the app's
        // location list draws; the palette's unknown-country blue for a location
        // without a country, the faint text color when there is no location at
        // all (off, not configured) so the title keeps its place. A bitmap, so it
        // is round on every Android version (Glance corner radii only apply on 12+).
        val markSize = LOCATION_MARK_SIZE_DP.dp
        Image(
            provider = ImageProvider(
                locationMarkBitmap((markSize.value * context.resources.displayMetrics.density).toInt(), locationMarkColor(entry)),
            ),
            contentDescription = null,
            modifier = GlanceModifier.size(markSize),
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
    val points = throughputPoints(entry, provider)
    val peakLabel = throughputPeakLabel(points, throughput.bucketSeconds)
    val widthPx = ((size.width.value - 28f) * density).roundToInt()
    val heightPx = ((height.value - 16f) * density).roundToInt()
    // the provider block carries the provide mode in its title, and while
    // providing is off it says so instead of drawing a flat line
    val heading = if (provider) providerChartHeading(context, entry) else title
    val providerOff = provider && providerChartOff(entry)
    Column(modifier = GlanceModifier.fillMaxWidth().height(height)) {
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            Text(heading, style = WidgetTheme.caption, maxLines = 1)
            Spacer(GlanceModifier.defaultWeight())
            if (!providerOff) {
                Text(
                    context.getString(R.string.widget_peak_rate, peakLabel),
                    style = WidgetTheme.label,
                    maxLines = 1,
                )
            }
        }
        Spacer(GlanceModifier.height(2.dp))
        if (providerOff) {
            Box(
                modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    context.getString(R.string.widget_provider_stats_when_enabled),
                    style = WidgetTheme.faint,
                    maxLines = 2,
                )
            }
        } else {
            Image(
                provider = ImageProvider(
                    ThroughputChartRenderer.render(
                        widthPx, heightPx, points, throughput.bucketSeconds, entry.nowMillis,
                        WidgetTheme.byteSeriesArgb, WidgetTheme.packetSeriesArgb,
                        density,
                    )
                ),
                contentDescription = null,
                modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                contentScale = ContentScale.FillBounds,
            )
        }
    }
}

/** The chart's points for one side: the client's remote traffic or the provider's local + blocked traffic. */
internal fun throughputPoints(entry: WidgetEntry, provider: Boolean): List<ThroughputChartRenderer.Point> =
    entry.tunnel.throughput.buckets.map {
        if (provider) ThroughputChartRenderer.Point(it.start, it.providerEgress, it.providerIngress, it.providerEgressPackets, it.providerIngressPackets)
        else ThroughputChartRenderer.Point(it.start, it.clientEgress, it.clientIngress, it.clientEgressPackets, it.clientIngressPackets)
    }

/** The byte and packet peaks, in the two line colors' order, as one label. */
internal fun throughputPeakLabel(points: List<ThroughputChartRenderer.Point>, bucketSeconds: Long): String {
    val seconds = maxOf(1L, bucketSeconds)
    return formatByteRate(ThroughputChartRenderer.peak(points) / seconds) + " \u00b7 " +
        formatPacketRate(ThroughputChartRenderer.peakPackets(points) / seconds)
}

/** The provider block's heading: "Provider \u00b7 <mode>" from the snapshot's provide mode, plain "Provider" when the mode is unknown. */
internal fun providerChartHeading(context: Context, entry: WidgetEntry): String {
    val modeLabel = ProvideControlMode.fromString(entry.tunnel.provideMode)
        ?.let { context.getString(ProvideControlMode.toStringResourceId(it)) }
    return if (modeLabel != null) context.getString(R.string.widget_provider_mode_title, modeLabel) else context.getString(R.string.widget_provider)
}

/** While providing is off the provider block says so instead of drawing a flat line. */
internal fun providerChartOff(entry: WidgetEntry): Boolean = !entry.tunnel.providing

/**
 * The color of the disc next to the location name, the same rules as the
 * Apple widget: the location's country color (the SDK palette entry the app's
 * location list uses for its circle); the palette's unknown-country blue when
 * the location has no color to show (best available, no country); the faint
 * text color when there is no location at all (tunnel off, not configured),
 * keeping its size so the title stays aligned.
 */
internal fun locationMarkColor(entry: WidgetEntry): Int {
    val location = entry.tunnel.location
    return locationMarkArgb(entry.showsTunnelData && location != null, location?.colorHex)
}

/** The disc's footprint, shared with the app's widget preview. */
internal const val LOCATION_MARK_SIZE_DP = 22

/** No location: the faint text color (ui.theme.TextFaint). */
internal const val LOCATION_MARK_OFF = 0xFF5A5A5A.toInt()

/** A location without a country color: the palette's unknown-country blue. */
internal const val LOCATION_MARK_UNKNOWN = 0xFF0099FF.toInt()

internal fun locationMarkArgb(hasLocation: Boolean, colorHex: String?): Int {
    if (!hasLocation) return LOCATION_MARK_OFF
    return WidgetColors.parseHex(colorHex ?: "", LOCATION_MARK_UNKNOWN)
}

/** A filled circle of [color], [sizePx] wide, for a Glance image. */
internal fun locationMarkBitmap(sizePx: Int, color: Int): Bitmap {
    val size = sizePx.coerceAtLeast(2)
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }
    Canvas(bitmap).drawCircle(size / 2f, size / 2f, size / 2f, paint)
    return bitmap
}

internal fun locationTitle(context: Context, entry: WidgetEntry): String {
    if (!entry.isConfigured) return context.getString(R.string.widget_not_signed_in)
    if (!entry.isOn) return context.getString(R.string.tile_status_disconnected)
    val location = entry.tunnel.location
    if (!entry.showsTunnelData || location == null) return context.getString(R.string.tile_status_connected)
    if (location.bestAvailable) return context.getString(R.string.best_available_provider)
    return location.name.ifEmpty { context.getString(R.string.tile_status_connected) }
}

internal fun subtitle(context: Context, entry: WidgetEntry): String {
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
