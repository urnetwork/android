package com.bringyour.network.widgets

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.Text
import androidx.glance.unit.ColorProvider
import androidx.compose.ui.graphics.Color
import com.bringyour.network.R
import com.bringyour.network.widgets.render.GlobeBitmapRenderer
import com.bringyour.network.widgets.render.WidgetColors
import kotlin.math.roundToInt

/**
 * The provider details globe as a widget: the tunnel's connected providers
 * on the same orthographic globe the app draws, turned to face their
 * centroid, with the provider list beside or below it in the app's order
 * (west to east, unplottable last — the SDK view controller's order). The
 * writer re-renders it as providers join and leave, within a floor.
 */
class ProviderGlobeWidget : GlanceAppWidget() {

    companion object {
        val SMALL = DpSize(110.dp, 110.dp)
        val MEDIUM = DpSize(220.dp, 110.dp)
        val LARGE = DpSize(220.dp, 220.dp)
    }

    /** Exact: the globe bitmap is rendered for the widget's real size (see DashboardWidget). */
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // the entry is read in the composition, again on each reload (see DashboardWidget)
        provideContent { ProviderGlobeContent(rememberWidgetEntry(context, WidgetKinds.PROVIDER_GLOBE)) }
    }
}

@Composable
internal fun ProviderGlobeContent(entry: WidgetEntry) {
    val size = LocalSize.current
    val context = LocalContext.current
    val providers = if (entry.showsTunnelData) entry.tunnel.providers else emptyList()
    val layout = providerGlobeLayout(size.width.value, size.height.value)
    val fontScale = context.resources.configuration.fontScale
    WidgetSurface(entry, com.bringyour.network.QuickConnectActivity.ROUTE_PROVIDER_LOCATIONS) {
        when (layout.arrangement) {
            GlobeArrangement.GlobeOnly -> {
                Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                    Globe(providers, layout.globeWidthDp.dp, layout.globeHeightDp.dp)
                    CountBadge(context, providers.size)
                }
            }
            GlobeArrangement.ListBeside -> {
                Row(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    Globe(providers, layout.globeWidthDp.dp, layout.globeHeightDp.dp)
                    Spacer(GlanceModifier.width(GLOBE_LIST_GAP_DP.dp))
                    ProviderList(entry, providers, rows = providerListRows(layout.listHeightDp, fontScale, providers.size))
                }
            }
            GlobeArrangement.ListBelow -> {
                Column(modifier = GlanceModifier.fillMaxSize()) {
                    Box(
                        modifier = GlanceModifier.fillMaxWidth().height(layout.globeHeightDp.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Globe(providers, layout.globeWidthDp.dp, layout.globeHeightDp.dp)
                    }
                    Spacer(GlanceModifier.height(GLOBE_LIST_BELOW_GAP_DP.dp))
                    ProviderList(entry, providers, rows = providerListRows(layout.listHeightDp, fontScale, providers.size))
                }
            }
        }
    }
}

/** How the globe widget lays out for its size. */
internal enum class GlobeArrangement {
    /** The globe alone, the provider count over it (2x2). */
    GlobeOnly,

    /** The provider list beside the globe (4x2, and a wide widget on a tablet). */
    ListBeside,

    /** The provider list below a larger globe (4x3 and up). */
    ListBelow,
}

/** The globe's box, and the height the provider list gets, in dp. */
internal data class GlobeLayout(
    val arrangement: GlobeArrangement,
    val globeWidthDp: Float,
    val globeHeightDp: Float,
    val listHeightDp: Float,
)

private const val GLOBE_PADDING_DP = 14f
private const val GLOBE_LIST_GAP_DP = 12f
private const val GLOBE_LIST_BELOW_GAP_DP = 8f
/** Below the list, the globe takes this share of the height. */
private const val GLOBE_BELOW_SHARE = 0.55f
/** A widget tall enough for the list below still puts it beside when it gets this much width there. */
private const val LIST_BESIDE_MIN_DP = 200f

/**
 * The globe widget's layout for a [widthDp] x [heightDp] widget. Small,
 * the globe alone. Short, or wide enough for a list [LIST_BESIDE_MIN_DP]
 * wide beside a globe its full height (a tablet), the list beside a globe no
 * wider than half the room, so neither crowds the other. Otherwise the list
 * below a globe that takes 55% of the height and never more than the width,
 * which a tall narrow widget would overflow.
 */
internal fun providerGlobeLayout(widthDp: Float, heightDp: Float): GlobeLayout {
    val innerWidth = widthDp - 2 * GLOBE_PADDING_DP
    val innerHeight = heightDp - 2 * GLOBE_PADDING_DP
    if (widthDp < ProviderGlobeWidget.MEDIUM.width.value && heightDp < ProviderGlobeWidget.LARGE.height.value) {
        return GlobeLayout(GlobeArrangement.GlobeOnly, innerWidth, innerHeight, 0f)
    }
    if (heightDp < ProviderGlobeWidget.LARGE.height.value || LIST_BESIDE_MIN_DP <= innerWidth - GLOBE_LIST_GAP_DP - innerHeight) {
        val side = minOf(innerHeight, (innerWidth - GLOBE_LIST_GAP_DP) / 2f).coerceAtLeast(0f)
        return GlobeLayout(GlobeArrangement.ListBeside, side, side, innerHeight)
    }
    val side = minOf(innerHeight * GLOBE_BELOW_SHARE, innerWidth).coerceAtLeast(0f)
    return GlobeLayout(GlobeArrangement.ListBelow, side, side, innerHeight - side - GLOBE_LIST_BELOW_GAP_DP)
}

/** A provider row, its 13sp line and the 4dp under it, at the default font size. */
private const val PROVIDER_ROW_DP = 22f
/** The list's heading and the space under it. */
private const val PROVIDER_LIST_HEADER_DP = 24f
/** The "+N more" line. */
private const val PROVIDER_MORE_DP = 16f
/** Glance keeps ten children of a column at most and drops the rest. */
internal const val PROVIDER_ROWS_MAX = 10

/**
 * How many of [providerCount] providers a list [listHeightDp] tall shows, at
 * the user's [fontScale]: all that fit, or when not all fit as many as leave
 * room for the "+N more" line, at least one, at most [PROVIDER_ROWS_MAX].
 */
internal fun providerListRows(listHeightDp: Float, fontScale: Float, providerCount: Int): Int {
    val scale = fontScale.coerceAtLeast(1f)
    val row = PROVIDER_ROW_DP * scale
    val room = listHeightDp - PROVIDER_LIST_HEADER_DP * scale
    if (providerCount <= minOf((room / row).toInt(), PROVIDER_ROWS_MAX)) {
        return providerCount
    }
    return ((room - PROVIDER_MORE_DP * scale) / row).toInt().coerceIn(1, PROVIDER_ROWS_MAX)
}

@Composable
private fun Globe(providers: List<WidgetProviderSnapshot>, width: androidx.compose.ui.unit.Dp, height: androidx.compose.ui.unit.Dp) {
    val context = LocalContext.current
    val density = widgetBitmapDensity()
    val bitmap = GlobeBitmapRenderer.render(
        context,
        (width.value * density).roundToInt(),
        (height.value * density).roundToInt(),
        providers,
        density,
    )
    Image(
        provider = ImageProvider(bitmap),
        contentDescription = context.resources.getQuantityString(R.plurals.widget_provider_count, providers.size, providers.size),
        modifier = GlanceModifier.size(width, height),
        contentScale = ContentScale.Fit,
    )
}

@Composable
private fun CountBadge(context: Context, count: Int) {
    Box(
        modifier = GlanceModifier
            .cornerRadius(10.dp)
            .background(WidgetTheme.card)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            if (count == 0) context.getString(R.string.widget_no_providers)
            else context.resources.getQuantityString(R.plurals.widget_provider_count, count, count),
            style = WidgetTheme.caption,
            maxLines = 1,
        )
    }
}

/** The providers, [rows] of them, and a line for the rest. */
@Composable
private fun ProviderList(entry: WidgetEntry, providers: List<WidgetProviderSnapshot>, rows: Int) {
    val context = LocalContext.current
    Column(modifier = GlanceModifier.fillMaxSize()) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(context.getString(R.string.site_app_providers), style = WidgetTheme.title)
            Spacer(GlanceModifier.defaultWeight())
            if (providers.isNotEmpty()) {
                Text(providers.size.toString(), style = WidgetTheme.label)
            }
        }
        Spacer(GlanceModifier.height(4.dp))
        if (providers.isEmpty()) {
            Text(
                context.getString(
                    when {
                        !entry.isConfigured -> R.string.widget_open_to_set_up
                        !entry.isOn -> R.string.provider_locations_unavailable
                        else -> R.string.provider_locations_empty
                    }
                ),
                style = WidgetTheme.faint,
            )
        } else {
            // a column of their own, a child each: Glance drops a column's
            // children past ten, which a row and a spacer each would reach
            // at four providers and lose the "+N more" line
            Column(modifier = GlanceModifier.fillMaxWidth()) {
                providers.take(rows).forEach { provider ->
                    ProviderRow(provider, entry.nowMillis)
                }
            }
            if (rows < providers.size) {
                Text(context.getString(R.string.widget_more_providers, providers.size - rows), style = WidgetTheme.faint)
            }
        }
    }
}

@Composable
private fun ProviderRow(provider: WidgetProviderSnapshot, nowMillis: Long) {
    val context = LocalContext.current
    Row(modifier = GlanceModifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        val dot = WidgetColors.parseHex(provider.colorHex, 0xFF0099FF.toInt())
        Box(
            modifier = GlanceModifier.size(8.dp).cornerRadius(4.dp).background(ColorProvider(Color(dot))),
        ) {}
        Spacer(GlanceModifier.width(8.dp))
        // the place takes whatever is left and is the only thing that truncates
        Text(place(context, provider), style = WidgetTheme.body, maxLines = 1, modifier = GlanceModifier.defaultWeight())
        Spacer(GlanceModifier.width(8.dp))
        Text(connectedDuration(context, provider.connectedSinceMillis, nowMillis), style = WidgetTheme.label, maxLines = 1)
    }
}

/** "City, Country", falling back through region and country, as the app's provider list labels a row. */
private fun place(context: Context, provider: WidgetProviderSnapshot): String {
    val parts = listOf(provider.city, provider.region, provider.country).filter { it.isNotEmpty() }
    if (parts.isEmpty()) return context.getString(R.string.provider_location_unknown)
    if (2 < parts.size) return "${parts.first()}, ${parts.last()}"
    return parts.joinToString(", ")
}

/** The app's compact duration ("3h 24m"), with the same string resources, formatted per update. */
internal fun connectedDuration(context: Context, sinceMillis: Long, nowMillis: Long): String {
    if (sinceMillis <= 0) return ""
    val elapsedSeconds = ((nowMillis - sinceMillis).coerceAtLeast(0)) / 1000
    val hours = elapsedSeconds / 3600
    val minutes = (elapsedSeconds % 3600) / 60
    val seconds = elapsedSeconds % 60
    return when {
        0 < hours -> context.getString(R.string.provider_connected_duration_hours, hours, minutes)
        0 < minutes -> context.getString(R.string.provider_connected_duration_minutes, minutes)
        else -> context.getString(R.string.provider_connected_duration_seconds, seconds)
    }
}
