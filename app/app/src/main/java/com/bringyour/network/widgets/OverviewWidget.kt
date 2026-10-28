package com.bringyour.network.widgets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.runtime.Composable
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
import androidx.glance.layout.ColumnScope
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
import com.bringyour.network.QuickConnectActivity
import com.bringyour.network.R
import com.bringyour.network.utils.formatByteCountCompact
import com.bringyour.network.widgets.render.BalanceBarRenderer
import com.bringyour.network.widgets.render.ContractStackRenderer
import com.bringyour.network.widgets.render.GlobeBitmapRenderer
import com.bringyour.network.widgets.render.SparklineRenderer
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * The overview: the dashboard, the provider globe and the contracts in one
 * "super large" widget (apple/DUO.md, "Samsung cover screen (item 16)",
 * option A). The header is the dashboard's: the location and its status
 * line, or the slow internet pill in its place, and the quick connect
 * toggle. The provider globe fills the bottom left, the corner the Flip's
 * cameras cover on its cover screen, so only decoration goes there; three
 * tiles down the right carry the balance, today's traffic and the open
 * contracts. Disconnected, the globe dims and traffic and contracts read
 * "—".
 *
 * Two receivers show it: OverviewWidgetReceiver on the home screen,
 * wherever a launcher fits its 352 x 339 dp minimum (about 4 x 4), growing
 * on tablets and unfolded phones; and CoverWidgetReceiver on the lock screen
 * and Samsung's cover screen (Flex Window), keyguard only as Samsung
 * documents it. While the device is locked, the toggle has the user unlock
 * first (QuickConnectActivity.ACTION_UNLOCK_TOGGLE).
 */
class OverviewWidget : GlanceAppWidget() {

    /** Exact: the globe, the sparkline and the stacks are bitmaps for the widget's real size (see DashboardWidget). */
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // the entry is read in the composition, again on each reload (see DashboardWidget)
        provideContent { OverviewContent(rememberWidgetEntry(context, WidgetKinds.OVERVIEW)) }
    }
}

// the layout, from the mockup at the cover's 352 x 339 dp: the tiles take a
// third of the width, within bounds; the globe is four fifths of the
// shorter side, a fifth of it past the left edge and a tenth past the bottom
private const val TILE_WIDTH_SHARE = 0.34f
private const val TILE_MIN_WIDTH_DP = 120f
private const val TILE_MAX_WIDTH_DP = 200f
private const val GLOBE_SHARE = 0.8f
private const val GLOBE_PAST_LEFT = 0.2f
private const val GLOBE_PAST_BOTTOM = 0.1f
/** Disconnected, the globe shows at this opacity. */
private const val DIMMED_ALPHA = 0.45f

private const val TILE_PADDING_HORIZONTAL_DP = 9f
private const val TILE_PADDING_VERTICAL_DP = 7f
private const val TILE_SPACE_DP = 6f
/** The sparkline's bars: one per hour of the day. */
private const val SPARKLINE_SLOTS = 24
/** The sparkline is drawn this tall, then stretched to its tile (square bars keep). */
private const val SPARKLINE_HEIGHT_DP = 24f
private const val BALANCE_BAR_HEIGHT_DP = 6f

/** A tile's figure while it has none to show. */
private const val NONE = "—"

/**
 * Where the overview's parts go in a widget [widthDp] x [heightDp]: the
 * tiles' width, and the globe's diameter and its top left corner, past the
 * widget's left edge, so the globe bleeds off the bottom left corner.
 */
internal data class OverviewLayout(
    val tileWidthDp: Float,
    val globeDp: Float,
    val globeLeftDp: Float,
    val globeTopDp: Float,
)

internal fun overviewLayout(widthDp: Float, heightDp: Float): OverviewLayout {
    val globeDp = GLOBE_SHARE * minOf(widthDp, heightDp)
    return OverviewLayout(
        tileWidthDp = (TILE_WIDTH_SHARE * widthDp).coerceIn(TILE_MIN_WIDTH_DP, TILE_MAX_WIDTH_DP),
        globeDp = globeDp,
        globeLeftDp = -GLOBE_PAST_LEFT * globeDp,
        globeTopDp = heightDp - (1f - GLOBE_PAST_BOTTOM) * globeDp,
    )
}

/**
 * What the overview shows of an entry. The balance stays whatever the
 * tunnel does (a dash without one); while the tunnel data is hidden
 * (signed out, disconnected) the globe dims with no providers, and traffic
 * and contracts read a dash and draw nothing.
 */
internal data class Overview(
    val providers: List<WidgetProviderSnapshot>,
    val dimmed: Boolean,
    val balance: String,
    val traffic: String,
    /** Today's bytes by hour, for the sparkline; null while hidden. */
    val trafficHours: List<Long>?,
    val contracts: String,
    /** Every peer's send stack in one, then its receive stack, most relevant peer first; null while hidden. */
    val send: List<WidgetContractSnapshot>?,
    val receive: List<WidgetContractSnapshot>?,
    val sendByteCount: Long = 0,
    val receiveByteCount: Long = 0,
)

internal fun overview(entry: WidgetEntry, zone: ZoneId = ZoneId.systemDefault()): Overview {
    val balance = entry.balance?.let { formatByteCountCompact(it.balanceByteCount) } ?: NONE
    if (!entry.showsTunnelData) {
        return Overview(
            providers = emptyList(),
            dimmed = true,
            balance = balance,
            traffic = NONE,
            trafficHours = null,
            contracts = NONE,
            send = null,
            receive = null,
        )
    }
    val today = entry.tunnel.today.asOf(entry.nowMillis, zone)
    val peers = entry.tunnel.contracts
    val send = peers.flatMap { it.send }
    val receive = peers.flatMap { it.receive }
    return Overview(
        providers = entry.tunnel.providers,
        dimmed = false,
        balance = balance,
        traffic = formatByteCountCompact(today.byteCount),
        trafficHours = today.hourlyByteCounts,
        contracts = (send.size + receive.size).toString(),
        send = send,
        receive = receive,
        sendByteCount = peers.sumOf { it.sendByteCount },
        receiveByteCount = peers.sumOf { it.receiveByteCount },
    )
}

/** How many circles of a stack fit [widthPx], at least one: the count says the rest. */
internal fun stackCapacity(widthPx: Int, style: ContractStackRenderer.Style): Int {
    var count = 1
    while (count < MAXIMUM_STACK && ContractStackRenderer.width(count + 1, style) <= widthPx) {
        count += 1
    }
    return count
}

private const val MAXIMUM_STACK = 64

@Composable
internal fun OverviewContent(entry: WidgetEntry) {
    val size = LocalSize.current
    val layout = overviewLayout(size.width.value, size.height.value)
    val overview = overview(entry)
    // no padding at the edge, so the globe bleeds off the bottom left
    // corner; the header and the tiles keep the widgets' 14 dp
    WidgetSurface(entry, QuickConnectActivity.ROUTE_CONNECT, padding = 0.dp) {
        Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.BottomStart) {
            OverviewGlobe(overview, layout)
            Column(modifier = GlanceModifier.fillMaxSize().padding(14.dp)) {
                DashboardHeader(entry, dashboardSlowInternet(entry), compact = false, unlockFirst = true)
                Spacer(GlanceModifier.height(8.dp))
                Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                    Spacer(GlanceModifier.defaultWeight())
                    OverviewTiles(entry, overview, layout.tileWidthDp)
                }
            }
        }
    }
}

/**
 * The globe's part inside the widget, from its bottom left corner: the
 * providers in the stats' green, dimmed without the tunnel data. The
 * device's own place is not known on the device, so there is no "you" and
 * no arcs. Decorative for TalkBack: the header says the provider count.
 */
@Composable
private fun OverviewGlobe(overview: Overview, layout: OverviewLayout) {
    val context = LocalContext.current
    val size = LocalSize.current
    val density = context.resources.displayMetrics.density
    val visibleTopDp = maxOf(0f, layout.globeTopDp)
    val widthDp = minOf(size.width.value, layout.globeLeftDp + layout.globeDp)
    val heightDp = size.height.value - visibleTopDp
    if (widthDp <= 0f || heightDp <= 0f) return
    val bitmap = Bitmap.createBitmap(
        (widthDp * density).roundToInt().coerceAtLeast(1),
        (heightDp * density).roundToInt().coerceAtLeast(1),
        Bitmap.Config.ARGB_8888,
    )
    val canvas = Canvas(bitmap)
    canvas.translate(layout.globeLeftDp * density, (layout.globeTopDp - visibleTopDp) * density)
    val globePx = layout.globeDp * density
    if (overview.dimmed) {
        canvas.saveLayerAlpha(0f, 0f, globePx, globePx, (DIMMED_ALPHA * 255).roundToInt())
    }
    GlobeBitmapRenderer.draw(canvas, context, globePx, globePx, overview.providers, density, dotColor = WidgetTheme.byteSeriesArgb)
    if (overview.dimmed) {
        canvas.restore()
    }
    Image(
        provider = ImageProvider(bitmap),
        contentDescription = null,
        modifier = GlanceModifier.size(widthDp.dp, heightDp.dp),
    )
}

/** The three tiles down the right: the balance and the contracts take what they need, the traffic the rest. */
@Composable
private fun OverviewTiles(entry: WidgetEntry, overview: Overview, widthDp: Float) {
    val context = LocalContext.current
    val density = context.resources.displayMetrics.density
    val innerWidthPx = ((widthDp - 2 * TILE_PADDING_HORIZONTAL_DP) * density).roundToInt()
    Column(modifier = GlanceModifier.width(widthDp.dp).fillMaxHeight()) {
        Tile(context.getString(R.string.balance), overview.balance) {
            Spacer(GlanceModifier.height(5.dp))
            Image(
                provider = ImageProvider(
                    BalanceBarRenderer.render(
                        innerWidthPx,
                        (BALANCE_BAR_HEIGHT_DP * density).roundToInt(),
                        entry.balance,
                        WidgetTheme.balanceUsedArgb,
                        WidgetTheme.balancePendingArgb,
                        WidgetTheme.balanceAvailableArgb,
                        density,
                    )
                ),
                contentDescription = null,
                modifier = GlanceModifier.fillMaxWidth().height(BALANCE_BAR_HEIGHT_DP.dp),
                contentScale = ContentScale.FillBounds,
            )
        }
        Spacer(GlanceModifier.height(TILE_SPACE_DP.dp))
        Tile(context.getString(R.string.widget_traffic_today), overview.traffic, GlanceModifier.defaultWeight()) {
            overview.trafficHours?.let { hours ->
                Spacer(GlanceModifier.height(5.dp))
                Image(
                    provider = ImageProvider(
                        SparklineRenderer.render(
                            innerWidthPx,
                            (SPARKLINE_HEIGHT_DP * density).roundToInt(),
                            hours,
                            SPARKLINE_SLOTS,
                            WidgetTheme.byteSeriesArgb,
                            density,
                        )
                    ),
                    contentDescription = null,
                    modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                    contentScale = ContentScale.FillBounds,
                )
            }
        }
        Spacer(GlanceModifier.height(TILE_SPACE_DP.dp))
        Tile(context.getString(R.string.site_app_contracts), overview.contracts) {
            val send = overview.send
            val receive = overview.receive
            if (send != null && receive != null) {
                // the Contracts widget's identity: the send stack → in green over
                // the receive stack ← in pink, one circle per contract that fits
                val style = ContractStackRenderer.Style(density, compact = true)
                val capacity = stackCapacity(innerWidthPx, style)
                Spacer(GlanceModifier.height(5.dp))
                Image(
                    provider = ImageProvider(
                        ContractStackRenderer.render(send.take(capacity), WidgetTheme.sendStackArgb, pointsRight = true, style = style)
                    ),
                    contentDescription = context.resources.getQuantityString(
                        R.plurals.widget_contract_stack_accessibility, send.size, send.size,
                        formatByteCountCompact(overview.sendByteCount),
                    ),
                )
                Spacer(GlanceModifier.height(2.dp))
                Image(
                    provider = ImageProvider(
                        ContractStackRenderer.render(receive.take(capacity), WidgetTheme.receiveStackArgb, pointsRight = false, style = style)
                    ),
                    contentDescription = context.resources.getQuantityString(
                        R.plurals.widget_contract_stack_accessibility, receive.size, receive.size,
                        formatByteCountCompact(overview.receiveByteCount),
                    ),
                )
            }
        }
    }
}

/** One tile: its name, its figure, then what [content] draws under them. */
@Composable
private fun Tile(
    name: String,
    figure: String,
    modifier: GlanceModifier = GlanceModifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .cornerRadius(12.dp)
            .background(WidgetTheme.tile)
            .padding(horizontal = TILE_PADDING_HORIZONTAL_DP.dp, vertical = TILE_PADDING_VERTICAL_DP.dp),
    ) {
        Text(name, style = WidgetTheme.caption, maxLines = 1)
        Text(figure, style = WidgetTheme.figure, maxLines = 1)
        content()
    }
}
