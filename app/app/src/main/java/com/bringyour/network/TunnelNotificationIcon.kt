package com.bringyour.network

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.ui.graphics.toArgb
import com.bringyour.network.ui.components.tunnelSquares
import com.bringyour.network.ui.theme.Amber
import kotlin.math.roundToInt

/** The large icon's size: the notification templates' right icon. */
internal const val TUNNEL_LARGE_ICON_DP = 48

/** "Back to normal" keeps a fading trace of the tunnel: the outer square at a third (the canvas's recovered board). */
internal const val TUNNEL_RECOVERED_LARGE_ICON_DEPTH = 0.35f

/** The large icon's tile, so the amber tunnel reads on light and dark shades alike (the canvas's Android board). */
private const val TUNNEL_LARGE_ICON_TILE_COLOR = 0xFF161616.toInt()

/** The tile's corner radius and the tunnel's size, relative to the tile: 12 and 32 of 44 on the canvas. */
private const val TUNNEL_LARGE_ICON_CORNER = 12f / 44f
private const val TUNNEL_LARGE_ICON_TUNNEL = 32f / 44f

/**
 * The VPN notification's large icon while the connection is slow
 * (apple/DELAY.md §7.2): the amber tunnel on a dark rounded tile, [sizePx]
 * square, showing [depth] squares. A fractional depth draws its last square
 * partly faded, as the "back to normal" icon does. The geometry is
 * [tunnelSquares], the same as the connect screen's indicator.
 */
internal fun tunnelLargeIconBitmap(sizePx: Int, depth: Float): Bitmap {
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val size = sizePx.toFloat()
    val corner = size * TUNNEL_LARGE_ICON_CORNER
    canvas.drawRoundRect(
        0f,
        0f,
        size,
        size,
        corner,
        corner,
        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = TUNNEL_LARGE_ICON_TILE_COLOR },
    )

    val tunnelSize = size * TUNNEL_LARGE_ICON_TUNNEL
    val inset = (size - tunnelSize) / 2f
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        // square corners: rounded ones soften the tunnel
        strokeJoin = Paint.Join.MITER
        color = Amber.toArgb()
    }
    tunnelSquares(tunnelSize).forEachIndexed { index, square ->
        val visibility = (depth - index).coerceIn(0f, 1f)
        if (visibility <= 0f) {
            return@forEachIndexed
        }
        paint.strokeWidth = square.strokeWidth
        paint.alpha = (255f * visibility * square.opacity).roundToInt()
        canvas.drawRect(
            inset + square.start,
            inset + square.start,
            inset + square.end,
            inset + square.end,
            paint,
        )
    }
    return bitmap
}
