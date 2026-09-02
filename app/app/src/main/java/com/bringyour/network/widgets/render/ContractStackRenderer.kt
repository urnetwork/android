package com.bringyour.network.widgets.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import com.bringyour.network.widgets.WidgetContractSnapshot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * One contract stack drawn horizontally for a widget card: the direction
 * arrow, then one circle per contract, newest first. Circle area follows the
 * contract's total against the stack's largest, the inner disc is the used
 * fraction, active contracts draw a brighter ring, stream contracts a second
 * outer ring — the app's ContractStatsScreen circle at widget scale.
 */
object ContractStackRenderer {

    class Style(val density: Float, val compact: Boolean) {
        val slot: Float get() = (if (compact) 14f else 18f) * density
        val minimumDiameter: Float get() = (if (compact) 6f else 8f) * density
        val streamRingGap: Float get() = 1.5f * density
        val arrowWidth: Float get() = 7f * density
        val spacing: Float get() = 3f * density
    }

    fun width(contractCount: Int, style: Style): Int {
        val circles = max(1, contractCount)
        return (style.arrowWidth + style.spacing + circles * style.slot + (circles - 1) * style.spacing).toInt() + 1
    }

    fun height(style: Style): Int = style.slot.toInt() + 1

    /**
     * @param pointsRight the send stack's arrow points right (to the peer);
     *   the receive stack's points left.
     */
    fun render(contracts: List<WidgetContractSnapshot>, color: Int, pointsRight: Boolean, style: Style): Bitmap {
        val width = width(contracts.size, style)
        val height = height(style)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val centerY = height / 2f
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.style = Paint.Style.FILL }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.style = Paint.Style.STROKE }

        // arrow
        stroke.color = color
        stroke.strokeWidth = 1.5f * style.density
        stroke.strokeCap = Paint.Cap.ROUND
        stroke.strokeJoin = Paint.Join.ROUND
        val arrowHalf = style.arrowWidth / 2f
        val ax = arrowHalf
        val head = 2.5f * style.density
        val arrow = Path()
        if (pointsRight) {
            arrow.moveTo(ax - arrowHalf, centerY)
            arrow.lineTo(ax + arrowHalf, centerY)
            arrow.moveTo(ax + arrowHalf - head, centerY - head)
            arrow.lineTo(ax + arrowHalf, centerY)
            arrow.lineTo(ax + arrowHalf - head, centerY + head)
        } else {
            arrow.moveTo(ax + arrowHalf, centerY)
            arrow.lineTo(ax - arrowHalf, centerY)
            arrow.moveTo(ax - arrowHalf + head, centerY - head)
            arrow.lineTo(ax - arrowHalf, centerY)
            arrow.lineTo(ax - arrowHalf + head, centerY + head)
        }
        canvas.drawPath(arrow, stroke)

        var x = style.arrowWidth + style.spacing
        if (contracts.isEmpty()) {
            // a stack with nothing open: a dashed placeholder ring, so the two
            // directions always read as a pair of columns
            stroke.color = WidgetColors.withAlpha(color, 0.25f)
            stroke.strokeWidth = 1f * style.density
            stroke.pathEffect = android.graphics.DashPathEffect(floatArrayOf(2f * style.density, 2f * style.density), 0f)
            canvas.drawCircle(x + style.slot / 2f, centerY, style.minimumDiameter / 2f, stroke)
            stroke.pathEffect = null
            return bitmap
        }

        val stackMax = contracts.maxOf { it.totalByteCount }
        for (contract in contracts) {
            val diameter = if (0 < stackMax && 0 < contract.totalByteCount) {
                (style.slot * sqrt(contract.totalByteCount.toDouble() / stackMax.toDouble()).toFloat())
                    .coerceIn(style.minimumDiameter, style.slot)
            } else {
                style.minimumDiameter
            }
            val fraction = if (0 < contract.totalByteCount) {
                min(1.0, contract.usedByteCount.toDouble() / contract.totalByteCount.toDouble())
            } else {
                0.0
            }
            val innerSize = if (0 < fraction) max(2f * style.density, (diameter * sqrt(fraction)).toFloat()) else 0f
            val cx = x + style.slot / 2f
            val ringColor = WidgetColors.withAlpha(color, if (contract.isActive) 1f else 0.55f)
            val ringWidth = (if (contract.isActive) 1.25f else 0.75f) * style.density

            stroke.color = ringColor
            stroke.strokeWidth = ringWidth
            if (contract.hasStream) {
                canvas.drawCircle(cx, centerY, diameter / 2f + style.streamRingGap, stroke)
            }
            canvas.drawCircle(cx, centerY, diameter / 2f, stroke)
            if (0f < innerSize) {
                fill.color = WidgetColors.withAlpha(color, 0.3f)
                canvas.drawCircle(cx, centerY, innerSize / 2f, fill)
                stroke.color = WidgetColors.withAlpha(color, 0.6f)
                stroke.strokeWidth = 0.5f * style.density
                canvas.drawCircle(cx, centerY, innerSize / 2f, stroke)
            }
            x += style.slot + style.spacing
        }
        return bitmap
    }
}
