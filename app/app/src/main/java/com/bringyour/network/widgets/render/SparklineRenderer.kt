package com.bringyour.network.widgets.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import kotlin.math.max

/**
 * The overview widget's traffic today as a bar sparkline: one bar per hour
 * of the day, its height the hour's share of the busiest hour's, the hours
 * still ahead left empty. The bars are square, so the image stretches to
 * its tile's height without changing them.
 */
object SparklineRenderer {

    /** A bar for a little traffic still shows: this share of the plot at least. */
    const val MINIMUM_FRACTION = 0.08f

    /** Each slot's bar height, 0 to 1 of the plot: its value against the largest, at least [MINIMUM_FRACTION] above 0. */
    fun fractions(values: List<Long>, slots: Int): List<Float> {
        val largest = values.maxOrNull() ?: 0L
        return (0 until slots).map { slot ->
            val value = values.getOrElse(slot) { 0L }
            if (value <= 0L || largest <= 0L) 0f else max(MINIMUM_FRACTION, value.toFloat() / largest)
        }
    }

    fun render(widthPx: Int, heightPx: Int, values: List<Long>, slots: Int, color: Int, density: Float): Bitmap {
        val bitmap = Bitmap.createBitmap(max(1, widthPx), max(1, heightPx), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val width = bitmap.width.toFloat()
        val height = bitmap.height.toFloat()
        val gap = 1.5f * density
        val slot = width / max(1, slots)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            this.color = color
        }
        fractions(values, slots).forEachIndexed { index, fraction ->
            if (fraction <= 0f) return@forEachIndexed
            val left = index * slot
            canvas.drawRect(left, height * (1f - fraction), left + max(1f, slot - gap), height, paint)
        }
        return bitmap
    }
}
