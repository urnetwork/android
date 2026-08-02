package com.bringyour.network.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.bringyour.network.ui.theme.Green
import com.bringyour.network.ui.theme.MainBorderBase
import com.bringyour.network.ui.theme.MainTextBase
import com.bringyour.network.ui.theme.Pink
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.utils.formatByteRate
import com.bringyour.network.utils.formatPacketRate
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.max
import kotlin.math.min

/**
 * Live transfer chart for one throughput route.
 *
 * The chart is mirrored around a center horizontal axis: egress traffic is
 * drawn above the line and ingress traffic below it. Byte counts and packet
 * counts are drawn on parallel axes, each normalized to its own window
 * maximum. The egress window maximums are labeled on the top right, with the
 * ingress maximums under them. The latest data is on the right and shifts
 * left as time progresses.
 */
@Composable
fun TransferChart(
    points: List<ThroughputPointUi>,
    route: ThroughputRoute,
    title: String? = null,
    windowSeconds: Long = 60,
    byteColor: Color = Green,
    packetColor: Color = Pink,
    height: Dp = 128.dp,
) {

    val samples = remember(points, route) {
        points.map { it.timeMillis to route.sample(it) }
    }

    // scale to the window peak so the peak curve reaches the plot edge
    val peakEgressBytes = remember(samples) { samples.maxOfOrNull { it.second.egressBytes } ?: 0L }
    val peakIngressBytes = remember(samples) { samples.maxOfOrNull { it.second.ingressBytes } ?: 0L }
    val peakEgressPackets = remember(samples) { samples.maxOfOrNull { it.second.egressPackets } ?: 0L }
    val peakIngressPackets = remember(samples) { samples.maxOfOrNull { it.second.ingressPackets } ?: 0L }
    val targetScaleBytes = max(max(peakEgressBytes, peakIngressBytes), 1024L)
    val targetScalePackets = max(max(peakEgressPackets, peakIngressPackets), 8L)

    // top-right stats are the rolling average over the last N buckets
    val recent = remember(samples) { samples.takeLast(AVERAGE_BUCKET_COUNT) }
    val avgEgressBytes = if (recent.isEmpty()) 0L else recent.sumOf { it.second.egressBytes } / recent.size
    val avgIngressBytes = if (recent.isEmpty()) 0L else recent.sumOf { it.second.ingressBytes } / recent.size
    val avgEgressPackets = if (recent.isEmpty()) 0L else recent.sumOf { it.second.egressPackets } / recent.size
    val avgIngressPackets = if (recent.isEmpty()) 0L else recent.sumOf { it.second.ingressPackets } / recent.size

    // the peak byte bucket per direction, whose label slides to track it
    val peakEgress = remember(samples) { samples.maxByOrNull { it.second.egressBytes } }
    val peakIngress = remember(samples) { samples.maxByOrNull { it.second.ingressBytes } }

    // paint for the sliding peak byte-rate labels, in the muted text color
    val density = LocalDensity.current
    val peakLabelPaint = remember(density) {
        android.graphics.Paint().apply {
            color = TextMuted.toArgb()
            textSize = with(density) { 9.sp.toPx() }
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        }
    }

    // clock that drives the leftward shift; advanced by the ticker below
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // the axis scale eases toward a new maximum rather than jumping, starting
    // from wherever the previous transition left off
    var byteScaleTransition by remember {
        mutableStateOf(ScaleTransition(targetScaleBytes.toFloat(), targetScaleBytes.toFloat(), 0L))
    }
    var packetScaleTransition by remember {
        mutableStateOf(ScaleTransition(targetScalePackets.toFloat(), targetScalePackets.toFloat(), 0L))
    }
    LaunchedEffect(targetScaleBytes) {
        val now = System.currentTimeMillis()
        byteScaleTransition = ScaleTransition(byteScaleTransition.valueAt(now), targetScaleBytes.toFloat(), now)
    }
    LaunchedEffect(targetScalePackets) {
        val now = System.currentTimeMillis()
        packetScaleTransition = ScaleTransition(packetScaleTransition.valueAt(now), targetScalePackets.toFloat(), now)
    }

    // Run the ~20fps ticker ONLY while something is actually moving: recent
    // traffic still scrolling through the window, or an axis rescale in flight.
    // Otherwise the chart is a static flat line and the ticker pauses (no
    // per-frame work), resuming when new data arrives. The samples series still
    // updates ~1/s, so this re-evaluates without a separate waker.
    val clock = System.currentTimeMillis()
    val lastActivityMillis = remember(samples) {
        samples.lastOrNull {
            0L < it.second.egressBytes || 0L < it.second.ingressBytes ||
                0L < it.second.egressPackets || 0L < it.second.ingressPackets
        }?.first
    }
    val hasRecentActivity = lastActivityMillis != null &&
        clock - lastActivityMillis < windowSeconds * 1000
    val scaleSettling = clock - byteScaleTransition.startMillis < TRANSITION_MILLIS ||
        clock - packetScaleTransition.startMillis < TRANSITION_MILLIS
    val rootView = LocalView.current
    var chartVisible by remember { mutableStateOf(false) }
    val animationActive = chartVisible && (hasRecentActivity || scaleSettling)
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(animationActive, lifecycleOwner) {
        if (animationActive) {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    nowMillis = System.currentTimeMillis()
                    delay(50)
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .onGloballyPositioned { coordinates ->
                chartVisible = transferChartIntersectsViewport(
                    coordinates.boundsInWindow(),
                    rootView.width,
                    rootView.height,
                )
            }
            .graphicsLayer()
    ) {

        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            // read the ticker clock + scale transitions inside the draw scope so
            // the ~20fps updates invalidate only the draw phase, not recompose
            val now = nowMillis
            drawTransferChart(
                samples = samples,
                nowMillis = now,
                windowMillis = windowSeconds * 1000,
                scaleBytes = byteScaleTransition.valueAt(now),
                scalePackets = packetScaleTransition.valueAt(now),
                byteColor = byteColor,
                packetColor = packetColor,
                peakEgressValue = peakEgress?.second?.egressBytes ?: 0L,
                peakEgressTimeMillis = peakEgress?.first ?: 0L,
                peakIngressValue = peakIngress?.second?.ingressBytes ?: 0L,
                peakIngressTimeMillis = peakIngress?.first ?: 0L,
                peakLabelPaint = peakLabelPaint,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {

            if (title != null) {
                Text(
                    title,
                    style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium),
                    color = TextMuted
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Column(
                horizontalAlignment = Alignment.End
            ) {
                DirectionLabel(
                    pointsUp = true,
                    byteValue = avgEgressBytes,
                    packetValue = avgEgressPackets,
                    byteColor = byteColor,
                    packetColor = packetColor,
                )
                Spacer(modifier = Modifier.height(2.dp))
                DirectionLabel(
                    pointsUp = false,
                    byteValue = avgIngressBytes,
                    packetValue = avgIngressPackets,
                    byteColor = byteColor,
                    packetColor = packetColor,
                )
            }

        }

    }
}

internal fun transferChartIntersectsViewport(
    bounds: Rect,
    viewportWidth: Int,
    viewportHeight: Int,
): Boolean {
    if (viewportWidth <= 0 || viewportHeight <= 0) {
        return false
    }
    return bounds.right > 0f &&
        bounds.left < viewportWidth.toFloat() &&
        bounds.bottom > 0f &&
        bounds.top < viewportHeight.toFloat()
}

// the top-right stats average over the last N buckets (≈ N seconds)
private const val AVERAGE_BUCKET_COUNT = 5

// shared easing duration for the rightmost value glide and the axis rescale
private const val TRANSITION_MILLIS = 500L

/**
 * A time-based ease of the axis scale from an old value to a new one, so the
 * plot rescales smoothly instead of jumping when the window peak changes.
 */
private data class ScaleTransition(val from: Float, val to: Float, val startMillis: Long) {
    fun valueAt(nowMillis: Long): Float {
        val progress = ((nowMillis - startMillis).toFloat() / TRANSITION_MILLIS).coerceIn(0f, 1f)
        // easeOutCubic
        val eased = 1f - (1f - progress) * (1f - progress) * (1f - progress)
        return from + (to - from) * eased
    }
}

/**
 * Linear interpolation between two throughput samples, per component.
 */
private fun lerpSample(a: ThroughputSampleUi, b: ThroughputSampleUi, t: Float): ThroughputSampleUi {
    fun lerp(x: Long, y: Long): Long = x + ((y - x) * t).toLong()
    return ThroughputSampleUi(
        egressBytes = lerp(a.egressBytes, b.egressBytes),
        ingressBytes = lerp(a.ingressBytes, b.ingressBytes),
        egressPackets = lerp(a.egressPackets, b.egressPackets),
        ingressPackets = lerp(a.ingressPackets, b.ingressPackets),
    )
}

@Composable
private fun DirectionLabel(
    pointsUp: Boolean,
    byteValue: Long,
    packetValue: Long,
    byteColor: Color,
    packetColor: Color,
) {
    val labelStyle = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Medium)
    // the triangle is last so it aligns at the trailing edge across the
    // egress and ingress rows
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            formatByteRate(byteValue),
            style = labelStyle,
            color = byteColor.copy(alpha = if (0 < byteValue) 1f else 0.4f)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            formatPacketRate(packetValue),
            style = labelStyle,
            color = packetColor.copy(alpha = if (0 < packetValue) 1f else 0.4f)
        )
        Spacer(modifier = Modifier.width(5.dp))
        // the arrow lights up in brand white when this direction has
        // activity, like a link light
        DirectionTriangle(
            pointsUp = pointsUp,
            color = if (0 < byteValue || 0 < packetValue) MainTextBase else TextMuted
        )
    }
}

/**
 * An equilateral triangle centered in `rect`, pointing up or down.
 */
private fun equilateralTrianglePath(rect: Rect, pointsUp: Boolean): Path {
    val path = Path()
    val cx = rect.center.x
    // equilateral: height is base * sqrt(3)/2
    val height = rect.width * 0.866f
    val top = rect.center.y - height / 2f
    val bottom = rect.center.y + height / 2f
    if (pointsUp) {
        path.moveTo(cx, top)
        path.lineTo(rect.right, bottom)
        path.lineTo(rect.left, bottom)
    } else {
        path.moveTo(cx, bottom)
        path.lineTo(rect.right, top)
        path.lineTo(rect.left, top)
    }
    path.close()
    return path
}

/**
 * A small equilateral direction triangle with slightly rounded tips
 * (filled, then stroked with a round join).
 */
@Composable
private fun DirectionTriangle(pointsUp: Boolean, color: Color = TextMuted) {
    Canvas(modifier = Modifier.size(7.dp)) {
        // inset so the round-join stroke stays inside the frame
        val rect = Rect(1f, 1f, size.width - 1f, size.height - 1f)
        val path = equilateralTrianglePath(rect, pointsUp)
        drawPath(path, color = color, style = Fill)
        drawPath(
            path,
            color = color,
            style = Stroke(width = 1.5.dp.toPx(), join = StrokeJoin.Round)
        )
    }
}

private fun DrawScope.drawTransferChart(
    samples: List<Pair<Long, ThroughputSampleUi>>,
    nowMillis: Long,
    windowMillis: Long,
    scaleBytes: Float,
    scalePackets: Float,
    byteColor: Color,
    packetColor: Color,
    peakEgressValue: Long,
    peakEgressTimeMillis: Long,
    peakIngressValue: Long,
    peakIngressTimeMillis: Long,
    peakLabelPaint: android.graphics.Paint,
) {
    // reserve a band at the top for the average stats and the sliding peak
    // label, and a band at the bottom for the sliding peak label, so the peak
    // labels never overlap the stats or leave the component
    val statsBand = 30.dp.toPx()
    val peakBand = 13.dp.toPx()
    val plotTop = statsBand + peakBand
    val plotBottom = size.height - peakBand
    val centerY = (plotTop + plotBottom) / 2f
    val plotHalf = max((plotBottom - plotTop) / 2f, 8.dp.toPx())

    // the center zero axis is drawn last (below) so it always spans the
    // full width and reads consistently as data shifts in
    val drawAxis = {
        drawLine(
            color = MainBorderBase,
            start = Offset(0f, centerY),
            end = Offset(size.width, centerY),
            strokeWidth = 1.dp.toPx()
        )
    }

    if (samples.isEmpty() || size.width <= 0f) {
        drawAxis()
        return
    }

    // ease the newest bucket from the previous value to its new value so the
    // right edge of the curve glides in rather than hopping on each update
    val displaySamples = run {
        val lastIdx = samples.size - 1
        val lastTime = samples[lastIdx].first
        val prevSample = if (2 <= samples.size) samples[lastIdx - 1].second else ThroughputSampleUi.Zero
        val progress = ((nowMillis - lastTime).toFloat() / TRANSITION_MILLIS).coerceIn(0f, 1f)
        val eased = 1f - (1f - progress) * (1f - progress) * (1f - progress)
        samples.toMutableList().also {
            it[lastIdx] = lastTime to lerpSample(prevSample, samples[lastIdx].second, eased)
        }
    }

    // pad the series so the baseline spans the full width: a flat run of zeros
    // back to the window start on the left (the not-yet-filled region) and a
    // hold of the latest value out to the right edge. the left zeros are laid
    // at the sample cadence rather than as a single far-left point, so the
    // points feeding the spline stay evenly spaced -- a lone real sample
    // sitting after one giant gap back to the window start is what makes the
    // curve loop on itself.
    val padded = mutableListOf<Pair<Long, ThroughputSampleUi>>()
    val windowStart = nowMillis - windowMillis
    val first = displaySamples.first()
    val last = displaySamples.last()
    if (windowStart < first.first) {
        val step = if (2 <= displaySamples.size) max(200L, displaySamples[1].first - displaySamples[0].first) else 1000L
        // anchor the baseline at the window start so it reaches the left edge,
        // then walk back from just before the first real sample one bucket at a
        // time so the ramp-in from zero is uniform
        padded.add(windowStart to ThroughputSampleUi.Zero)
        val ramp = mutableListOf<Long>()
        var t = first.first - step
        while (windowStart < t) {
            ramp.add(t)
            t -= step
        }
        ramp.reversed().forEach { padded.add(it to ThroughputSampleUi.Zero) }
    }
    padded.addAll(displaySamples)
    if (last.first < nowMillis) {
        padded.add(nowMillis to last.second)
    }

    fun x(timeMillis: Long): Float {
        return size.width * (1f - (nowMillis - timeMillis).toFloat() / windowMillis.toFloat())
    }

    fun offsetFor(value: Long, scale: Float): Float {
        return plotHalf * min(1f, value.toFloat() / max(scale, 1f))
    }

    val egressBytes = padded.map { Offset(x(it.first), centerY - offsetFor(it.second.egressBytes, scaleBytes)) }
    val ingressBytes = padded.map { Offset(x(it.first), centerY + offsetFor(it.second.ingressBytes, scaleBytes)) }
    val egressPackets = padded.map { Offset(x(it.first), centerY - offsetFor(it.second.egressPackets, scalePackets)) }
    val ingressPackets = padded.map { Offset(x(it.first), centerY + offsetFor(it.second.ingressPackets, scalePackets)) }

    // clip each direction to its half so curve smoothing never
    // crosses the center axis
    val topHalf = Rect(0f, 0f, size.width, centerY)
    val bottomHalf = Rect(0f, centerY, size.width, size.height)

    drawSeries(egressBytes, topHalf, byteColor, 1.5.dp.toPx(), fillTo = centerY)
    drawSeries(ingressBytes, bottomHalf, byteColor, 1.5.dp.toPx(), fillTo = centerY)
    drawSeries(egressPackets, topHalf, packetColor, 1.dp.toPx(), fillTo = null)
    drawSeries(ingressPackets, bottomHalf, packetColor, 1.dp.toPx(), fillTo = null)

    drawAxis()

    // sliding peak byte-rate labels, above the peak on top and below on bottom.
    // each carries its direction triangle so egress vs ingress reads at a glance
    drawPeakLabel(peakEgressValue, peakEgressTimeMillis, statsBand + peakBand / 2f, nowMillis, windowMillis, peakLabelPaint, pointsUp = true)
    drawPeakLabel(peakIngressValue, peakIngressTimeMillis, size.height - peakBand / 2f, nowMillis, windowMillis, peakLabelPaint, pointsUp = false)
}

private fun DrawScope.drawPeakLabel(
    value: Long,
    timeMillis: Long,
    y: Float,
    nowMillis: Long,
    windowMillis: Long,
    paint: android.graphics.Paint,
    pointsUp: Boolean,
) {
    if (value <= 0L) {
        return
    }
    val text = formatByteRate(value)
    val textWidth = paint.measureText(text)
    val triangleSize = 6.dp.toPx()
    val gap = 3.dp.toPx()
    val total = textWidth + gap + triangleSize

    val rawX = size.width * (1f - (nowMillis - timeMillis).toFloat() / windowMillis.toFloat())
    // keep the whole label (value + direction triangle) inside the component
    val halfWidth = total / 2f + 2f
    val centerX = rawX.coerceIn(halfWidth, size.width - halfWidth)
    val leftX = centerX - total / 2f

    // the paint is center-aligned, so draw the text at its center
    val baselineY = y - (paint.descent() + paint.ascent()) / 2f
    drawContext.canvas.nativeCanvas.drawText(text, leftX + textWidth / 2f, baselineY, paint)

    // the direction triangle sits to the right of the value
    val triangleColor = Color(paint.color)
    val triangleRect = Rect(
        leftX + textWidth + gap,
        y - triangleSize / 2f,
        leftX + textWidth + gap + triangleSize,
        y + triangleSize / 2f
    )
    val triangle = equilateralTrianglePath(triangleRect, pointsUp)
    drawPath(triangle, color = triangleColor, style = Fill)
    drawPath(triangle, color = triangleColor, style = Stroke(width = 1.dp.toPx(), join = StrokeJoin.Round))
}

private fun DrawScope.drawSeries(
    points: List<Offset>,
    clip: Rect,
    color: Color,
    strokeWidth: Float,
    fillTo: Float?,
) {
    if (points.size < 2) {
        return
    }

    clipRect(clip.left, clip.top, clip.right, clip.bottom) {

        val path = smoothPath(points)

        if (fillTo != null) {
            val fill = Path()
            fill.addPath(path)
            fill.lineTo(points.last().x, fillTo)
            fill.lineTo(points.first().x, fillTo)
            fill.close()
            drawPath(
                path = fill,
                color = color.copy(alpha = 0.07f)
            )
        }

        drawPath(
            path = path,
            color = color.copy(alpha = 0.9f),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

/**
 * Catmull-Rom smoothing through the sample points
 */
private fun smoothPath(points: List<Offset>): Path {
    val path = Path()
    path.moveTo(points[0].x, points[0].y)
    if (points.size == 2) {
        path.lineTo(points[1].x, points[1].y)
        return path
    }
    for (i in 1 until points.size) {
        val p0 = points[max(i - 2, 0)]
        val p1 = points[i - 1]
        val p2 = points[i]
        val p3 = points[min(i + 1, points.size - 1)]
        // the x axis is time and strictly increasing, so keep both control
        // points within the segment's x span. clamping x keeps the cubic
        // monotonic in x -- it can never bow back on itself into a loop when a
        // neighbour is far away (an outlier, or the zero baseline across a gap).
        // y is left free so the curve still eases naturally.
        val loX = min(p1.x, p2.x)
        val hiX = max(p1.x, p2.x)
        val c1 = Offset((p1.x + (p2.x - p0.x) / 6f).coerceIn(loX, hiX), p1.y + (p2.y - p0.y) / 6f)
        val c2 = Offset((p2.x - (p3.x - p1.x) / 6f).coerceIn(loX, hiX), p2.y - (p3.y - p1.y) / 6f)
        path.cubicTo(c1.x, c1.y, c2.x, c2.y, p2.x, p2.y)
    }
    return path
}
