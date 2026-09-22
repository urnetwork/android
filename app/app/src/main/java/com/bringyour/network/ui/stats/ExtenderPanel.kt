package com.bringyour.network.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bringyour.network.R
import com.bringyour.network.ui.connect.EXTENDER_RING_STROKE_DP
import com.bringyour.network.ui.connect.extenderColorArgb
import com.bringyour.network.ui.theme.Green
import com.bringyour.network.ui.theme.Red
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.Yellow

/**
 * The extender panel of the connect drawer (EXTENDER.md K4), under the IP
 * family status row: one hollow ring per extender carrying a live connection
 * right now, the active-of-reserve count, and the gossip network's state with
 * the rate of records and revocations it applied in the trailing minute.
 * Tapping does nothing yet.
 */

// the drawn diameter of one panel ring. The widget's dots follow the grid
// cell; these are a fixed reading size, since the panel is a list of
// extenders rather than a grid of providers.
private val RING_SIZE = 12.dp
private val RING_SPACING = 4.dp
private val STATUS_DOT_SIZE = 8.dp
// the fixed width of the row labels, so both rows share one left edge
private val LABEL_WIDTH = 108.dp

/** One known extender as the panel reads it, from the sdk's `ExtenderInfo`. */
data class ExtenderUi(
    val ip: String,
    val colorHex: String,
    // live dials over this address right now
    val inUse: Int,
)

/**
 * The gossip network's state as the panel draws it (K4). The sdk derives one
 * value for both roles; these are only the three it produces, plus the
 * fallback for a value this build does not know.
 */
enum class ExtenderGossipUi(val sdkState: String) {
    // the values mirror Sdk.ExtenderGossipState* as literals so the mapping
    // stays a plain JVM unit (the Sdk class loads the native library)
    CONNECTED("connected"),
    CONNECTING("connecting"),
    DISCONNECTED("disconnected");

    companion object {
        /**
         * The state for an sdk value. Anything unknown — an older device
         * process that reports nothing, or a value added later — reads as
         * disconnected, which is the state that claims the least.
         */
        fun fromSdk(state: String?): ExtenderGossipUi = when (state) {
            CONNECTED.sdkState -> CONNECTED
            CONNECTING.sdkState -> CONNECTING
            else -> DISCONNECTED
        }
    }
}

/** The localized label of a gossip state. */
fun ExtenderGossipUi.labelResId(): Int = when (this) {
    ExtenderGossipUi.CONNECTED -> R.string.connected
    // the generated catalog carries the connecting label as `gossip_connecting`
    ExtenderGossipUi.CONNECTING -> R.string.gossip_connecting
    ExtenderGossipUi.DISCONNECTED -> R.string.disconnected
}

/** The status dot color of a gossip state: green, yellow, red (K4). */
fun ExtenderGossipUi.dotColor(): Color = when (this) {
    ExtenderGossipUi.CONNECTED -> Green
    ExtenderGossipUi.CONNECTING -> Yellow
    ExtenderGossipUi.DISCONNECTED -> Red
}

/**
 * The whole panel as one plain snapshot of the sdk's `ExtenderStatus`, so
 * every display rule below is testable without the gomobile bindings. An
 * empty one is what a device that reports no extender status shows.
 */
data class ExtenderPanelUi(
    val extenders: List<ExtenderUi> = listOf(),
    // K4's N: the addresses carrying a live connection right now
    val activeCount: Int = 0,
    // K4's M: every usable directory entry. Normally the larger of the two,
    // but an address that just went on hold with a live connection counts in
    // activeCount and not here, so neither is derived from the other.
    val reserveCount: Int = 0,
    val gossipState: String = "",
    val eventCountLastMinute: Int = 0,
) {
    /**
     * The rings, left to right: one per extender carrying at least one live
     * connection, in the sdk's order and its color.
     */
    val ringColorHexes: List<String>
        get() = extenders.filter { 0 < it.inUse }.map { it.colorHex }

    val gossip: ExtenderGossipUi
        get() = ExtenderGossipUi.fromSdk(gossipState)

    /** Whether there is anything at all to show, i.e. any extender network. */
    val present: Boolean
        get() = extenders.isNotEmpty() || 0 < reserveCount || 0 < activeCount
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExtenderPanel(
    panel: ExtenderPanelUi,
    modifier: Modifier = Modifier,
) {
    val labelStyle = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // title, styled like the transport bar's
        Text(
            stringResource(id = R.string.extenders),
            style = labelStyle,
            color = TextMuted
        )

        /**
         * The extenders carrying a connection right now, one ring each, and
         * K4's "N of M": the active count and every usable entry behind it.
         */
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(id = R.string.active_extenders),
                style = labelStyle,
                color = TextMuted,
                modifier = Modifier.width(LABEL_WIDTH),
            )

            FlowRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(RING_SPACING),
                verticalArrangement = Arrangement.spacedBy(RING_SPACING),
            ) {
                panel.ringColorHexes.forEachIndexed { index, colorHex ->
                    key("$index:$colorHex") {
                        ExtenderRing(colorHex = colorHex)
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                stringResource(
                    id = R.string.extenders_active_of_reserve,
                    panel.activeCount,
                    panel.reserveCount,
                ),
                style = labelStyle,
                color = TextMuted,
            )
        }

        /**
         * The gossip network: its state as a dot, and the records and
         * revocations it applied in the trailing minute.
         */
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(id = R.string.gossip_network),
                style = labelStyle,
                color = TextMuted,
                modifier = Modifier.width(LABEL_WIDTH),
            )

            Canvas(modifier = Modifier.size(STATUS_DOT_SIZE)) {
                drawCircle(color = panel.gossip.dotColor(), radius = size.minDimension / 2f)
            }

            Spacer(modifier = Modifier.width(6.dp))

            Text(
                stringResource(id = panel.gossip.labelResId()),
                style = labelStyle,
                color = TextMuted,
                modifier = Modifier.weight(1f),
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                pluralStringResource(
                    id = R.plurals.gossip_events_per_minute,
                    count = panel.eventCountLastMinute,
                    panel.eventCountLastMinute,
                ),
                style = labelStyle,
                color = TextMuted,
            )
        }
    }
}

/** One hollow ring in an extender's own color (K3). */
@Composable
private fun ExtenderRing(colorHex: String) {
    val argb = extenderColorArgb(colorHex)
    val color = if (argb != null) Color(argb) else TextMuted
    Canvas(modifier = Modifier.size(RING_SIZE)) {
        val stroke = EXTENDER_RING_STROKE_DP.dp.toPx()
        drawCircle(
            color = color,
            radius = (size.minDimension - stroke) / 2f,
            style = Stroke(width = stroke),
        )
    }
}
