package com.bringyour.network.widgets.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import kotlin.math.max
import kotlin.math.min

/**
 * A static version of the app's TransferChart (ui/stats/TransferChart.kt):
 * bytes (filled, green) and packets (a thin line, pink) for one side, egress
 * above the axis and ingress below, each pair on its own scale so both read
 * at once, Catmull-Rom smoothed with x-clamped control points. The app draws
 * one point per second over a 60 s window; the widget draws one bucket per
 * minute over the last hour.
 */
object ThroughputChartRenderer {

    class Point(
        val start: Long,
        val egress: Long,
        val ingress: Long,
        val egressPackets: Long = 0,
        val ingressPackets: Long = 0,
    )

    private const val WINDOW_BUCKETS = 60
    /** Floors for the y scales so an idle chart is flat rather than noisy: the app's 1 KiB/s and 8 pkt/s, per minute bucket. */
    private const val MINIMUM_BYTE_SCALE = 64L * 1024L
    private const val MINIMUM_PACKET_SCALE = 8L * 60L
    private const val AXIS_COLOR = 0x1FFFFFFF

    fun peak(points: List<Point>): Long = points.maxOfOrNull { max(it.egress, it.ingress) } ?: 0L

    fun peakPackets(points: List<Point>): Long = points.maxOfOrNull { max(it.egressPackets, it.ingressPackets) } ?: 0L

    fun render(
        widthPx: Int,
        heightPx: Int,
        points: List<Point>,
        bucketSeconds: Long,
        nowMillis: Long,
        byteColor: Int,
        packetColor: Int,
        density: Float,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(max(1, widthPx), max(1, heightPx), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val width = bitmap.width.toFloat()
        val height = bitmap.height.toFloat()
        val centerY = height / 2f
        val plotHalf = max(1f, centerY - 1f)
        val byteScale = max(peak(points), MINIMUM_BYTE_SCALE).toDouble()
        val packetScale = max(peakPackets(points), MINIMUM_PACKET_SCALE).toDouble()
        val window = (bucketSeconds * WINDOW_BUCKETS).toDouble()
        val nowSeconds = nowMillis / 1000.0
        val windowStart = nowSeconds - window

        // one sample per bucket across the whole window, zero where nothing was
        // recorded, so the spline is evenly spaced and reaches both edges
        val byStart = points.associateBy { it.start }
        val egressBytes = ArrayList<PointF>()
        val ingressBytes = ArrayList<PointF>()
        val egressPackets = ArrayList<PointF>()
        val ingressPackets = ArrayList<PointF>()
        fun x(time: Double): Float = (width * (1.0 - (nowSeconds - time) / window).coerceIn(0.0, 1.0)).toFloat()
        fun offset(value: Long, scale: Double): Float = (plotHalf * min(1.0, value.toDouble() / scale)).toFloat()
        var bucket = (windowStart.toLong() / bucketSeconds) * bucketSeconds
        while (bucket <= nowSeconds.toLong()) {
            val point = byStart[bucket]
            // plot at the bucket's end: its traffic is known once it has elapsed
            val time = (bucket + bucketSeconds).toDouble()
            val px = x(time)
            egressBytes.add(PointF(px, centerY - offset(point?.egress ?: 0L, byteScale)))
            ingressBytes.add(PointF(px, centerY + offset(point?.ingress ?: 0L, byteScale)))
            egressPackets.add(PointF(px, centerY - offset(point?.egressPackets ?: 0L, packetScale)))
            ingressPackets.add(PointF(px, centerY + offset(point?.ingressPackets ?: 0L, packetScale)))
            bucket += bucketSeconds
        }
        for (series in listOf(egressBytes, ingressBytes, egressPackets, ingressPackets)) {
            series.lastOrNull()?.let { if (it.x < width) series.add(PointF(width, it.y)) }
        }

        val topHalf = RectF(0f, 0f, width, centerY)
        val bottomHalf = RectF(0f, centerY, width, height)
        // bytes first, filled to the axis; the packet line rides on top, unfilled,
        // so both stay legible where they overlap (the app's TransferChart order)
        drawSeries(canvas, egressBytes, topHalf, byteColor, 1.5f * density, centerY, density)
        drawSeries(canvas, ingressBytes, bottomHalf, byteColor, 1.5f * density, centerY, density)
        drawSeries(canvas, egressPackets, topHalf, packetColor, 1f * density, null, density)
        drawSeries(canvas, ingressPackets, bottomHalf, packetColor, 1f * density, null, density)

        val axis = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = AXIS_COLOR
            strokeWidth = 1f * density
        }
        canvas.drawLine(0f, centerY, width, centerY, axis)
        return bitmap
    }

    private fun drawSeries(
        canvas: Canvas,
        points: List<PointF>,
        clip: RectF,
        color: Int,
        strokeWidth: Float,
        fillTo: Float?,
        density: Float,
    ) {
        if (points.size < 2) return
        canvas.save()
        canvas.clipRect(clip)
        val line = smoothPath(points)
        if (fillTo != null) {
            val area = Path(line).apply {
                lineTo(points.last().x, fillTo)
                lineTo(points.first().x, fillTo)
                close()
            }
            val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                this.color = WidgetColors.withAlpha(color, 0.12f)
            }
            canvas.drawPath(area, fill)
        }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            this.strokeWidth = max(strokeWidth, 0.5f * density)
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            this.color = WidgetColors.withAlpha(color, 0.9f)
        }
        canvas.drawPath(line, stroke)
        canvas.restore()
    }

    /**
     * Catmull-Rom smoothing with x-clamped control points, as in the app's
     * TransferChart: the curve can ease in y but never loops back in time.
     */
    private fun smoothPath(points: List<PointF>): Path {
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
            var c1x = p1.x + (p2.x - p0.x) / 6f
            val c1y = p1.y + (p2.y - p0.y) / 6f
            var c2x = p2.x - (p3.x - p1.x) / 6f
            val c2y = p2.y - (p3.y - p1.y) / 6f
            c1x = c1x.coerceIn(min(p1.x, p2.x), max(p1.x, p2.x))
            c2x = c2x.coerceIn(min(p1.x, p2.x), max(p1.x, p2.x))
            path.cubicTo(c1x, c1y, c2x, c2y, p2.x, p2.y)
        }
        return path
    }
}
