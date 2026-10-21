package com.bringyour.network.ui.upgrade

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * The QR code of a crypto invoice's payment uri, for a wallet on another
 * device, or null when it cannot be encoded. Only a uri that passed the sdk's
 * CheckCryptoPaymentUri gets here (CryptoPayment.view). Level M: a wallet's
 * camera reads it from a phone screen, and the payment uris (a 95 character
 * Monero address, a 140 character Zcash unified address, and their query) stay
 * a small code at that level. Called off the main thread: a code this size is
 * hundreds of thousands of pixels.
 */
fun paymentQrBitmap(uri: String, sizePx: Int): Bitmap? {
    if (uri.isEmpty() || sizePx <= 0) {
        return null
    }
    return runCatching {
        val matrix = QRCodeWriter().encode(
            uri,
            BarcodeFormat.QR_CODE,
            sizePx,
            sizePx,
            mapOf(
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN to 1,
            ),
        )
        val pixels = IntArray(sizePx * sizePx)
        for (y in 0 until sizePx) {
            val row = y * sizePx
            for (x in 0 until sizePx) {
                pixels[row + x] = if (matrix.get(x, y)) Color.BLACK else Color.WHITE
            }
        }
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565)
        bitmap.setPixels(pixels, 0, sizePx, 0, 0, sizePx, sizePx)
        bitmap
    }.getOrNull()
}
