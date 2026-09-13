package com.bringyour.network.ui.account

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.appcompat.content.res.AppCompatResources
import com.bringyour.network.R
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * The extender share code (EXTENDER.md K7): the payload at error correction
 * level H with the black and white connector glyph centered and a 4 dp
 * outline of the connector's own shape around it.
 *
 * Level H tolerates about 30 percent damage, which is what lets the glyph sit
 * in the middle of the code; the glyph covers well under that.
 */

// the glyph's share of the code's side. Kept well inside level H's budget.
private const val GLYPH_FRACTION = 0.18f

/**
 * The bitmap of one share payload, or null when it cannot be encoded (an
 * empty payload, or a payload too long for the format).
 *
 * `outlinePx` is K7's 4 dp outline, in pixels; the glyph is drawn twice, once
 * in black at the outline's size and once in its own colors inside it, so the
 * outline follows the connector's shape rather than a box around it.
 */
fun extenderShareQrBitmap(
    context: Context,
    text: String,
    sizePx: Int,
    outlinePx: Float,
): Bitmap? {
    if (text.isEmpty() || sizePx <= 0) {
        return null
    }
    return runCatching {
        val matrix = QRCodeWriter().encode(
            text,
            BarcodeFormat.QR_CODE,
            sizePx,
            sizePx,
            mapOf(
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H,
                EncodeHintType.MARGIN to 1,
            ),
        )
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565)
        for (x in 0 until sizePx) {
            for (y in 0 until sizePx) {
                bitmap.setPixel(x, y, if (matrix.get(x, y)) Color.BLACK else Color.WHITE)
            }
        }
        drawConnectorGlyph(context, bitmap, outlinePx)
        bitmap
    }.getOrNull()
}

/** Centers the connector glyph and its outline on a rendered code. */
private fun drawConnectorGlyph(context: Context, bitmap: Bitmap, outlinePx: Float) {
    val glyph = AppCompatResources.getDrawable(context, R.drawable.connector_qr_glyph) ?: return
    val canvas = Canvas(bitmap)
    val center = bitmap.width / 2
    val glyphSize = (bitmap.width * GLYPH_FRACTION).toInt().coerceAtLeast(1)
    val outline = outlinePx.toInt().coerceAtLeast(0)

    // the outline: the same shape, solid black, one outline wider on each side
    if (0 < outline) {
        val outlined = glyph.constantState?.newDrawable()?.mutate() ?: glyph.mutate()
        outlined.setTint(Color.BLACK)
        val half = glyphSize / 2 + outline
        outlined.setBounds(center - half, center - half, center + half, center + half)
        outlined.draw(canvas)
    }

    val half = glyphSize / 2
    glyph.setBounds(center - half, center - half, center + half, center + half)
    glyph.draw(canvas)
}
