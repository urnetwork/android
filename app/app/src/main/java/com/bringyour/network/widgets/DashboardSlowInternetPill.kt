package com.bringyour.network.widgets

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.text.style.TextAppearanceSpan
import android.util.DisplayMetrics
import android.util.TypedValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.bringyour.network.R
import com.bringyour.network.TransportDelayUi
import com.bringyour.network.ui.components.tunnelLevel
import com.bringyour.network.ui.components.tunnelLevelIcon
import com.bringyour.network.ui.connect.QuickConnectStatus
import com.bringyour.network.ui.connect.SlowInternetPillContent
import com.bringyour.network.ui.connect.textId
import com.bringyour.network.ui.theme.Amber

/*
 * The dashboard's slow internet pill (apple/DELAY.md §5.7, §7.4). While the
 * VPN notification shows its slow content, the connect screen's pill, scaled
 * to the widget, takes the provider count's place under the location: the
 * tunnel at the level, "Poor signal" or "Fallback", and each direction's
 * number, the worse one amber (SlowInternetPillContent). The compact widget
 * has no room for words: the tunnel and the worse number. What it shows is
 * decided by the pure functions here (DashboardSlowInternetPillTest); the
 * header draws it with DashboardSlowInternetPill.
 */

// the in-app pill's sizes scaled to the widget: dp, the copy in sp. The
// vertical padding makes the box about 22 dp tall (20 compact) around the
// copy's line, and lets it grow with the font scale
private const val PILL_TUNNEL_DP = 14f
private const val PILL_COMPACT_TUNNEL_DP = 12f
private const val PILL_VERTICAL_PADDING_DP = 4f
private const val PILL_COMPACT_VERTICAL_PADDING_DP = 3f
private const val PILL_START_PADDING_DP = 5f
private const val PILL_END_PADDING_DP = 8f
/** Between the tunnel, the label and each direction. */
private const val PILL_GAP_DP = 6f
/** Between a direction's arrow and its number. */
private const val PILL_ARROW_GAP_DP = 1f
private const val PILL_TEXT_SP = 11f
private const val PILL_ARROW_SP = 8f

/** Between the title and the pill. */
internal const val PILL_SPACE_DP = 2f

/**
 * How much taller the header is with the pill than with the provider count,
 * about: the title's line, a 2 dp space and the pill, less the 36 dp the
 * quick connect button gave it. The tall layout's charts give it up.
 */
internal const val PILL_HEADER_GROWTH_DP = 8f

// the short dashboard's parts around the pill's header, as DashboardWidget
// lays them out: the title's size (WidgetTheme.title), the quick connect
// button's height, the space under the header, and the balance's space
// under its label row and its bar
private const val TITLE_SP = 14f
private const val QUICK_CONNECT_HEIGHT_DP = 36f
private const val SHORT_HEADER_SPACE_DP = 10f
private const val BALANCE_LABEL_SPACE_DP = 4f
private const val BALANCE_BAR_DP = 8f

/** What the pill shows: the tunnel at [level], 1 to 4 (ic_tunnel_<level>), and what it says. */
internal data class DashboardSlowInternet(
    val level: Int,
    val content: SlowInternetPillContent,
)

/**
 * The pill for [entry], null for the provider count. It follows the
 * notification's slow content (WidgetEntry.slowInternet) and goes with the
 * location it describes: with the rest of the tunnel data (signed out,
 * disconnected), and while the title says anything but connected, as the
 * notification keeps its slow content for a connected status.
 */
internal fun dashboardSlowInternet(entry: WidgetEntry): DashboardSlowInternet? {
    val reading = entry.slowInternet ?: return null
    if (!entry.showsTunnelData || entry.status != QuickConnectStatus.CONNECTED) {
        return null
    }
    return DashboardSlowInternet(slowInternetLevel(reading), SlowInternetPillContent(reading))
}

/** The tunnel's level for a slow reading, the notification's icon's: a zero one still gets the first. */
private fun slowInternetLevel(reading: TransportDelayUi): Int = tunnelLevel(reading.quality).coerceAtLeast(1)

/**
 * Whether a newly published reading re-renders the dashboard: the pill
 * comes or goes, or its tunnel or label changes. New numbers alone wait for
 * the next re-render: widget updates have a budget, and the sdk's
 * hysteresis keeps the level's changes rare.
 */
internal fun dashboardSlowInternetChanged(previous: TransportDelayUi?, next: TransportDelayUi?): Boolean {
    if (previous == null || next == null) {
        return (previous == null) != (next == null)
    }
    return slowInternetLevel(previous) != slowInternetLevel(next) || previous.fallback != next.fallback
}

/**
 * What the pill says in the room it has, the in-app pill's choice
 * (SlowInternetPill): the label with both numbers, with the worse one, then
 * alone. The compact widget has no room for words: the worse number, then
 * the tunnel alone. [fits] says whether a choice fits.
 */
internal fun DashboardSlowInternet.fitted(
    compact: Boolean,
    fits: (SlowInternetPillContent) -> Boolean,
): SlowInternetPillContent {
    val choices = if (compact) listOf(content.worseOnly) else listOf(content, content.worseOnly)
    return choices.firstOrNull(fits) ?: content.labelOnly
}

/**
 * The pill's width in dp as the widget draws [content]: the padding and the
 * tunnel, then after a gap each part that shows, the [label] (not in the
 * compact widget) and each direction's arrow and number. [textWidth]
 * measures a text at a size in sp, in dp.
 */
internal fun slowInternetPillWidth(
    content: SlowInternetPillContent,
    label: String,
    compact: Boolean,
    textWidth: (text: String, sizeSp: Float) -> Float,
): Float {
    var width = PILL_START_PADDING_DP + (if (compact) PILL_COMPACT_TUNNEL_DP else PILL_TUNNEL_DP) + PILL_END_PADDING_DP
    if (!compact) {
        width += PILL_GAP_DP + textWidth(label, PILL_TEXT_SP)
    }
    for ((direction, number) in content.numbers()) {
        width += PILL_GAP_DP + textWidth(direction.arrow, PILL_ARROW_SP) + PILL_ARROW_GAP_DP + textWidth(number, PILL_TEXT_SP)
    }
    return width
}

/**
 * What TalkBack reads for the header while the pill shows, one element: the
 * title, the label, then each direction that shows as the in-app pill reads
 * it ("Germany. Poor signal. Upload delay 800 ms, Download delay 1.9 s").
 * Every number, whatever the room on screen.
 */
internal fun dashboardSlowInternetDescription(
    title: String,
    label: String,
    content: SlowInternetPillContent,
    uploadDelay: String,
    downloadDelay: String,
): String {
    val numbers = content.numbers().joinToString(", ") { (direction, number) ->
        val name = if (direction == SlowInternetPillContent.Direction.UP) uploadDelay else downloadDelay
        "$name $number"
    }
    return listOf(title, label, numbers).filter { it.isNotEmpty() }.joinToString(". ")
}

/**
 * The short dashboard's height with the pill, in dp: the title's line
 * [titleDp] over the space and the pill (or the quick connect button, where
 * taller), the space under the header, the balance's label row when
 * [balanceLabel], and the bar. [lineDp] is the 11 sp line of the pill's copy
 * and the label row, as the launcher sets them.
 */
internal fun dashboardShortHeightDp(titleDp: Float, lineDp: Float, balanceLabel: Boolean): Float {
    val pillDp = maxOf(PILL_TUNNEL_DP, lineDp) + 2 * PILL_VERTICAL_PADDING_DP
    val headerDp = maxOf(QUICK_CONNECT_HEIGHT_DP, titleDp + PILL_SPACE_DP + pillDp)
    val labelDp = if (balanceLabel) lineDp + BALANCE_LABEL_SPACE_DP else 0f
    return headerDp + SHORT_HEADER_SPACE_DP + labelDp + BALANCE_BAR_DP
}

/**
 * Whether the short dashboard keeps the balance's label row under the
 * pill's taller header, in [roomDp], its height inside the padding. At the
 * shortest heights the row gives way and the bar stays, so nothing clips;
 * the short size has no footer to go first. Asked only while the pill
 * shows: with the provider count the row always shows.
 */
internal fun dashboardShortKeepsBalanceLabel(roomDp: Float, titleDp: Float, lineDp: Float): Boolean =
    dashboardShortHeightDp(titleDp, lineDp, balanceLabel = true) <= roomDp

/**
 * [dashboardShortKeepsBalanceLabel] for [pill] under [title], the lines
 * measured as the launcher sets them: the title's, and the 11 sp line of the
 * pill's label and the balance's caption.
 */
internal fun dashboardShortKeepsBalanceLabel(
    context: Context,
    pill: DashboardSlowInternet,
    title: String,
    roomDp: Float,
): Boolean {
    val paint = pillTextPaint(context)
    val metrics = context.resources.displayMetrics
    val lineDp = maxOf(
        paint.lineDp(context.getString(pill.content.label.textId), PILL_TEXT_SP, metrics),
        paint.lineDp(context.getString(R.string.balance), PILL_TEXT_SP, metrics),
    )
    return dashboardShortKeepsBalanceLabel(roomDp, paint.lineDp(title, TITLE_SP, metrics), lineDp)
}

/** The directions that show, upload first, each with its number. */
private fun SlowInternetPillContent.numbers(): List<Pair<SlowInternetPillContent.Direction, String>> =
    listOfNotNull(
        up?.let { SlowInternetPillContent.Direction.UP to it },
        down?.let { SlowInternetPillContent.Direction.DOWN to it },
    )

private val SlowInternetPillContent.Direction.arrow: String
    get() = when (this) {
        SlowInternetPillContent.Direction.UP -> "↑"
        SlowInternetPillContent.Direction.DOWN -> "↓"
    }

/**
 * The pill under the title, in [roomDp] of the header: the box is a shape
 * drawable (Glance draws no borders) and the tunnel the notification's
 * static ic_tunnel_<level> tinted amber (Glance has no canvas), then the
 * copy that fits ([fitted]), measured as the launcher sets it. The digits
 * are not tabular: Glance has no font features. The header carries the
 * description.
 */
@Composable
internal fun DashboardSlowInternetPill(pill: DashboardSlowInternet, compact: Boolean, roomDp: Float) {
    val context = LocalContext.current
    val label = context.getString(pill.content.label.textId)
    val metrics = context.resources.displayMetrics
    val paint = pillTextPaint(context)
    val content = pill.fitted(compact) { choice ->
        val width = slowInternetPillWidth(choice, label, compact) { text, sizeSp ->
            paint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sizeSp, metrics)
            paint.measureText(text) / metrics.density
        }
        width <= roomDp
    }
    val verticalPadding = if (compact) PILL_COMPACT_VERTICAL_PADDING_DP else PILL_VERTICAL_PADDING_DP
    Row(
        modifier = GlanceModifier
            .background(ImageProvider(R.drawable.widget_slow_internet_pill))
            .padding(
                start = PILL_START_PADDING_DP.dp,
                top = verticalPadding.dp,
                end = PILL_END_PADDING_DP.dp,
                bottom = verticalPadding.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(tunnelLevelIcon(pill.level)),
            contentDescription = null,
            modifier = GlanceModifier.size((if (compact) PILL_COMPACT_TUNNEL_DP else PILL_TUNNEL_DP).dp),
            colorFilter = ColorFilter.tint(WidgetTheme.slowInternet),
        )
        if (!compact) {
            Spacer(GlanceModifier.width(PILL_GAP_DP.dp))
            Text(
                label,
                style = TextStyle(color = WidgetTheme.slowInternet, fontSize = PILL_TEXT_SP.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
            )
        }
        for ((direction, number) in content.numbers()) {
            // the arrow smaller and muted; both amber for the worse direction.
            // Each direction is a row of its own: Glance drops a container's
            // children past ten, and the pill would have eleven
            val worse = content.worse == direction
            Spacer(GlanceModifier.width(PILL_GAP_DP.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    direction.arrow,
                    style = TextStyle(
                        color = if (worse) WidgetTheme.withAlpha(Amber, 0.75f) else WidgetTheme.withAlpha(Color.White, 0.55f),
                        fontSize = PILL_ARROW_SP.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 1,
                )
                Spacer(GlanceModifier.width(PILL_ARROW_GAP_DP.dp))
                Text(
                    number,
                    style = TextStyle(
                        color = if (worse) WidgetTheme.slowInternet else WidgetTheme.text,
                        fontSize = PILL_TEXT_SP.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * A paint in the face the launcher sets the copy in, Glance's Medium: the
 * device's default family at weight 500, bold before Android 10.
 */
private fun pillTextPaint(context: Context): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    typeface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val family = TextAppearanceSpan(context, android.R.style.TextAppearance_DeviceDefault).family
        Typeface.create(Typeface.create(family, Typeface.NORMAL), 500, false)
    } else {
        Typeface.DEFAULT_BOLD
    }
}

/**
 * One line of [text] at [sizeSp] as the launcher sets it, in dp: the font's
 * bounds, which Glance's text pads to, and from Android 10 a fallback
 * font's (CJK) where they reach further.
 *
 * The pill measures in dp, so the [metrics] are only for the user's font
 * size: TypedValue applies Android 14's non-linear font scaling from them,
 * and nothing public does it without them (FontScaleConverterFactory is
 * hidden). The display they describe divides back out, so it does not
 * matter which one it is, unlike the widgets' bitmaps (WidgetPixels.kt).
 */
private fun Paint.lineDp(text: String, sizeSp: Float, metrics: DisplayMetrics): Float {
    textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sizeSp, metrics)
    val font = fontMetricsInt
    var top = font.top
    var bottom = font.bottom
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val forText = Paint.FontMetricsInt()
        getFontMetricsInt(text, 0, text.length, 0, text.length, false, forText)
        top = minOf(top, forText.top)
        bottom = maxOf(bottom, forText.bottom)
    }
    return (bottom - top) / metrics.density
}
