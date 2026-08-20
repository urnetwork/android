package com.bringyour.network.ui.stats

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bringyour.network.R
import com.bringyour.network.ui.theme.MainBorderBase
import com.bringyour.network.ui.theme.MainTintedBackgroundBase
import com.bringyour.network.ui.theme.TextFaint
import com.bringyour.network.ui.theme.TextMuted
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

// the app's general tween: segment resizing, legend, and the empty fade
private const val TRANSPORT_TWEEN_MILLIS = 1000
private val BAR_HEIGHT = 8.dp
// the horizontal spacing between legend / footer items
private val ITEM_SPACING = 12.dp

/**
 * The remote traffic of the stats window partitioned by the transport that
 * carried it, as one full-width stacked bar under the transfer chart.
 *
 * Each transport with traffic in the window is a segment proportional to its
 * share, in the sdk's stable transport order, so the bar reads as "this much
 * of the window's transfer went over each carrier". The segments always tile
 * exactly 100% of the width -- also mid-tween -- because the geometry is
 * derived from a single animated vector of the sdk's cumulative boundaries
 * rather than from independently animated widths. Enabled transports that
 * carried nothing in the window are listed in the unused footer instead of
 * drawing a zero-width segment. When the window has no remote traffic at all
 * the bar fades to an empty track and every enabled transport is unused.
 *
 * All the numbers (shares, boundaries, percents, used, enabled) come from the
 * sdk's `TransportDistribution`; this composable only draws and animates them.
 * Tapping anywhere on the component opens the transport settings editor; as
 * the inner clickable it wins over the surrounding card's click.
 */
@Composable
fun TransportDistributionBar(
    distribution: TransportDistributionUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasTraffic = distribution.active

    // the last non-empty boundaries, held while the window is empty so the bar
    // fades out in place (and back in from its last shape) instead of
    // collapsing to a corner
    var heldBoundaries by remember { mutableStateOf<List<Float>?>(null) }
    LaunchedEffect(distribution) {
        if (distribution.active) {
            heldBoundaries = distribution.boundaries
        }
    }
    // hold the last shape while empty; fall back to the live (all zero)
    // vector before any traffic has been seen
    val targetBoundaries = if (hasTraffic) {
        distribution.boundaries
    } else {
        heldBoundaries ?: distribution.boundaries
    }

    // one tween over the whole vector: every segment edge is read from the same
    // interpolation, so the segments tile 100% at every frame, and a retarget
    // mid-tween continues from the interpolated shape
    val boundaryTween = remember { BoundaryTween(targetBoundaries) }
    LaunchedEffect(targetBoundaries) {
        boundaryTween.animateTo(targetBoundaries)
    }

    // the segments fade out over the tween while the shape is held, leaving
    // the empty track
    val segmentAlpha by animateFloatAsState(
        targetValue = if (hasTraffic) 1f else 0f,
        animationSpec = tween(TRANSPORT_TWEEN_MILLIS, easing = FastOutSlowInEasing),
        label = "transportSegmentAlpha"
    )

    val colors = distribution.shares.map { it.transportType.color }
    val usedShares = distribution.used
    val unusedShares = distribution.unused

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {

        // title row, styled like the chart title, with a disclosure so the
        // nested tap target reads as its own control inside the card
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(id = R.string.transports),
                style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium),
                color = TextMuted
            )
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = TextFaint,
                modifier = Modifier.size(14.dp)
            )
        }

        // the bar: an empty track with the animated segments on top
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(BAR_HEIGHT)
                .clip(RoundedCornerShape(BAR_HEIGHT / 2))
                .background(MainBorderBase)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    // the layer alpha fades segments and separators together
                    .graphicsLayer { alpha = segmentAlpha }
            ) {
                // read the tween inside the draw scope so the animation frames
                // invalidate only the draw phase, not recompose
                drawTransportSegments(
                    boundaries = boundaryTween.current(),
                    colors = colors,
                    separatorColor = MainTintedBackgroundBase,
                )
            }
        }

        // legend: the transports with traffic and their share
        AnimatedVisibility(
            visible = usedShares.isNotEmpty(),
            enter = fadeIn(tween(TRANSPORT_TWEEN_MILLIS)),
            exit = fadeOut(tween(TRANSPORT_TWEEN_MILLIS)),
        ) {
            LegendRow(distribution.shares)
        }

        // unused footer: enabled transports that carried nothing in the window
        AnimatedVisibility(
            visible = unusedShares.isNotEmpty(),
            enter = fadeIn(tween(TRANSPORT_TWEEN_MILLIS)),
            exit = fadeOut(tween(TRANSPORT_TWEEN_MILLIS)),
        ) {
            UnusedRow(distribution.shares)
        }
    }
}

/**
 * The legend row: dot + name + the sdk percent for every used share, in stable
 * order. Items fade (and their slot expands or shrinks, so the neighbours
 * glide rather than jump) as transports enter and leave the window; the
 * percent labels roll to their new value with the general tween.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LegendRow(shares: List<TransportShareUi>) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        // items in a line share one bottom edge, so the names sit on one
        // common text baseline (every label is the same 11sp style)
        itemVerticalAlignment = Alignment.Bottom,
    ) {
        for (share in shares) {
            AnimatedVisibility(
                visible = share.used,
                enter = fadeIn(tween(TRANSPORT_TWEEN_MILLIS)) + expandHorizontally(tween(TRANSPORT_TWEEN_MILLIS)),
                exit = fadeOut(tween(TRANSPORT_TWEEN_MILLIS)) + shrinkHorizontally(tween(TRANSPORT_TWEEN_MILLIS)),
            ) {
                // the item spacing is inside the animated slot so it enters and
                // leaves with the item
                LegendItem(share, modifier = Modifier.padding(end = ITEM_SPACING))
            }
        }
    }
}

@Composable
private fun LegendItem(
    share: TransportShareUi,
    modifier: Modifier = Modifier,
) {
    // the last percent shown while used, held through the exit fade so a
    // leaving item does not roll to zero as it fades out
    val heldPercent = remember { IntArray(1) { share.percent } }
    if (share.used) {
        heldPercent[0] = share.percent
    }
    val animatedPercent by animateIntAsState(
        targetValue = heldPercent[0],
        animationSpec = tween(TRANSPORT_TWEEN_MILLIS, easing = FastOutSlowInEasing),
        label = "transportPercent"
    )
    val percentFormat = remember { NumberFormat.getPercentInstance(Locale.getDefault()) }
    // the sdk's whole percents sum to exactly 100, so a used sliver can round
    // to 0; label it "<1%" rather than a zero next to a visible segment
    val percentLabel = if (share.used && share.percent == 0) {
        "<" + percentFormat.format(0.01)
    } else {
        percentFormat.format(animatedPercent / 100.0)
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(share.transportType.color, CircleShape)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            share.transportType.label(),
            style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium),
            color = Color.White
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            percentLabel,
            // tabular digits so the roll does not shift the row
            style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum"),
            color = TextMuted
        )
    }
}

/**
 * The unused footer row: "unused" then a hollow dot + name for every enabled
 * transport without traffic in the window, in stable order, all faint.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UnusedRow(shares: List<TransportShareUi>) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        // the "unused" label and the names bottom-align on one common baseline
        itemVerticalAlignment = Alignment.Bottom,
    ) {
        Text(
            stringResource(id = R.string.transport_unused),
            style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium),
            color = TextFaint,
            modifier = Modifier.padding(end = ITEM_SPACING)
        )
        for (share in shares) {
            AnimatedVisibility(
                visible = share.enabled && !share.used,
                enter = fadeIn(tween(TRANSPORT_TWEEN_MILLIS)) + expandHorizontally(tween(TRANSPORT_TWEEN_MILLIS)),
                exit = fadeOut(tween(TRANSPORT_TWEEN_MILLIS)) + shrinkHorizontally(tween(TRANSPORT_TWEEN_MILLIS)),
            ) {
                Row(
                    modifier = Modifier.padding(end = ITEM_SPACING),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // a hollow dot in the transport's color: the color mapping
                    // stays legible even while the transport is idle
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .border(1.dp, share.transportType.color.copy(alpha = 0.6f), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        share.transportType.label(),
                        style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium),
                        color = TextFaint
                    )
                }
            }
        }
    }
}

/**
 * One tween over a whole vector of boundaries. `animateTo` retargets from the
 * currently interpolated shape, and every element is read from the same
 * eased progress, so a shape read mid-tween is a consistent interpolation of
 * the from and to shapes (its segments tile the bar exactly).
 *
 * The progress is a monotonic clock read relative to a base captured at the
 * retarget, rather than an animatable snapped back to zero, so the from/to
 * swap and the progress reset are one synchronous step: no draw can observe
 * the new shapes with a stale progress.
 */
private class BoundaryTween(initial: List<Float>) {
    private var from: List<Float> = initial
    private var to: List<Float> = initial
    private var base = 0f
    private val clock = Animatable(0f)

    /**
     * the interpolated boundaries at this frame
     */
    fun current(): List<Float> {
        val t = (clock.value - base).coerceIn(0f, 1f)
        return interpolateBoundaries(from, to, t)
    }

    suspend fun animateTo(target: List<Float>) {
        if (to == target) {
            return
        }
        from = current()
        to = target
        base = clock.value
        clock.animateTo(
            targetValue = base + 1f,
            animationSpec = tween(TRANSPORT_TWEEN_MILLIS, easing = FastOutSlowInEasing)
        )
        // settled: rebase the clock so it does not grow without bound. Every
        // intermediate read is consistent (t is clamped: 1 reads `to`, and
        // after the snap 0 reads `from`, which now equals `to`)
        from = to
        base = 0f
        clock.snapTo(0f)
    }
}

/**
 * Element-wise interpolation between two boundary vectors at progress `t`.
 * Vectors of different lengths combine element-wise, treating missing
 * elements as zero.
 */
internal fun interpolateBoundaries(from: List<Float>, to: List<Float>, t: Float): List<Float> {
    val n = max(from.size, to.size)
    return List(n) { i ->
        val a = from.getOrElse(i) { 0f }
        val b = to.getOrElse(i) { 0f }
        a + (b - a) * t
    }
}

/**
 * One drawn segment of the bar: [start, end) in pixels, and whether a
 * separator is drawn on its leading edge (against the previous visible
 * segment) and how wide.
 */
internal data class TransportSegment(
    val index: Int,
    val start: Float,
    val end: Float,
    val separatorWidth: Float,
)

/**
 * The visible segments for a boundary vector over `width` pixels: segment i
 * spans from the previous boundary to boundary i, so the segments tile the
 * width exactly whatever the interpolation. A hairline separator against the
 * previous visible segment eases in with the narrower of the two so a segment
 * sliding in from zero width does not pop a full separator.
 */
internal fun transportSegments(boundaries: List<Float>, width: Float, separatorMaxWidth: Float): List<TransportSegment> {
    if (width <= 0f || boundaries.isEmpty()) {
        return listOf()
    }
    val segments = mutableListOf<TransportSegment>()
    var start = 0f
    var lastVisibleEnd: Float? = null
    boundaries.forEachIndexed { i, value ->
        val end = width * value.coerceIn(0f, 1f)
        val segmentWidth = end - start
        if (0f < segmentWidth) {
            val separatorWidth = if (lastVisibleEnd != null) min(separatorMaxWidth, segmentWidth / 4f) else 0f
            segments.add(TransportSegment(i, start, end, separatorWidth))
            lastVisibleEnd = end
        }
        start = max(start, end)
    }
    return segments
}

/**
 * Draws the stacked segments from one vector of cumulative boundaries
 * (fractions of the width, stable transport order), with hairline separators
 * in the card color between adjacent visible segments.
 */
private fun DrawScope.drawTransportSegments(
    boundaries: List<Float>,
    colors: List<Color>,
    separatorColor: Color,
) {
    val segments = transportSegments(boundaries, size.width, 1.dp.toPx())
    for (segment in segments) {
        val color = colors.getOrNull(segment.index) ?: colors.lastOrNull() ?: Color.Gray
        drawRect(
            color = color,
            topLeft = Offset(segment.start, 0f),
            size = Size(segment.end - segment.start, size.height)
        )
        if (0f < segment.separatorWidth) {
            drawRect(
                color = separatorColor,
                topLeft = Offset(segment.start - segment.separatorWidth / 2f, 0f),
                size = Size(segment.separatorWidth, size.height)
            )
        }
    }
}
