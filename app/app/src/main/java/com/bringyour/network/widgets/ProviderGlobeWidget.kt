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
        val entry = WidgetEntry.load(context)
        provideContent { ProviderGlobeContent(entry) }
    }
}

@Composable
internal fun ProviderGlobeContent(entry: WidgetEntry) {
    val size = LocalSize.current
    val context = LocalContext.current
    val providers = if (entry.showsTunnelData) entry.tunnel.providers else emptyList()
    WidgetSurface(entry, com.bringyour.network.QuickConnectActivity.ROUTE_PROVIDER_LOCATIONS) {
        when {
            size.width < ProviderGlobeWidget.MEDIUM.width && size.height < ProviderGlobeWidget.LARGE.height -> {
                Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                    Globe(providers, (size.width.value - 28f).dp, (size.height.value - 28f).dp)
                    CountBadge(context, providers.size)
                }
            }
            size.height < ProviderGlobeWidget.LARGE.height -> {
                Row(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    val side = (size.height.value - 28f).dp
                    Globe(providers, side, side)
                    Spacer(GlanceModifier.width(12.dp))
                    ProviderList(entry, providers, maximum = 4)
                }
            }
            else -> {
                Column(modifier = GlanceModifier.fillMaxSize()) {
                    val globeHeight = ((size.height.value - 28f) * 0.55f).dp
                    Box(modifier = GlanceModifier.fillMaxWidth().height(globeHeight), contentAlignment = Alignment.Center) {
                        Globe(providers, globeHeight, globeHeight)
                    }
                    Spacer(GlanceModifier.height(8.dp))
                    ProviderList(entry, providers, maximum = 6)
                }
            }
        }
    }
}

@Composable
private fun Globe(providers: List<WidgetProviderSnapshot>, width: androidx.compose.ui.unit.Dp, height: androidx.compose.ui.unit.Dp) {
    val context = LocalContext.current
    val density = context.resources.displayMetrics.density
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

@Composable
private fun ProviderList(entry: WidgetEntry, providers: List<WidgetProviderSnapshot>, maximum: Int) {
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
            providers.take(maximum).forEach { provider ->
                ProviderRow(provider, entry.nowMillis)
                Spacer(GlanceModifier.height(4.dp))
            }
            if (maximum < providers.size) {
                Text(context.getString(R.string.widget_more_providers, providers.size - maximum), style = WidgetTheme.faint)
            }
        }
    }
}

@Composable
private fun ProviderRow(provider: WidgetProviderSnapshot, nowMillis: Long) {
    val context = LocalContext.current
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
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
