package com.bringyour.network.widgets.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.bringyour.network.widgets.WidgetBalanceSnapshot
import kotlin.math.max

/**
 * The app's three-segment usage bar (used / pending / available out of the
 * daily start balance) as a bitmap. Glance rows only support equal weights,
 * so proportional segments have to be drawn.
 */
object BalanceBarRenderer {

    private const val MINIMUM_FRACTION = 0.015

    fun render(
        widthPx: Int,
        heightPx: Int,
        balance: WidgetBalanceSnapshot?,
        usedColor: Int,
        pendingColor: Int,
        availableColor: Int,
        density: Float,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(max(1, widthPx), max(1, heightPx), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val width = bitmap.width.toFloat()
        val height = bitmap.height.toFloat()
        val radius = height / 2f
        val gap = 2f * density
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

        val segments: List<Pair<Int, Double>> = if (balance == null || balance.startBalanceByteCount <= 0) {
            listOf(WidgetColors.withAlpha(availableColor, 0.5f) to 1.0)
        } else {
            val total = balance.startBalanceByteCount.toDouble()
            val raw = listOf(
                usedColor to balance.usedByteCount / total,
                pendingColor to balance.openTransferByteCount / total,
                availableColor to balance.balanceByteCount / total,
            ).filter { 0 < it.second }
            // clamp non-zero segments up to a visible minimum, then renormalize
            val clamped = raw.map { it.first to maxOf(it.second, MINIMUM_FRACTION) }
            val sum = clamped.sumOf { it.second }
            clamped.map { it.first to it.second / sum }
        }

        val usable = width - gap * (segments.size - 1)
        var x = 0f
        segments.forEachIndexed { index, (color, fraction) ->
            val w = (usable * fraction).toFloat()
            paint.color = color
            val rect = RectF(x, 0f, x + w, height)
            // round only the outer ends, as the app's bar does
            val path = android.graphics.Path()
            val left = if (index == 0) radius else 0f
            val right = if (index == segments.size - 1) radius else 0f
            path.addRoundRect(rect, floatArrayOf(left, left, right, right, right, right, left, left), android.graphics.Path.Direction.CW)
            canvas.drawPath(path, paint)
            x += w + gap
        }
        return bitmap
    }
}
