package com.bringyour.network.ui.account

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer

/**
 * Reading an extender share code (EXTENDER.md K7, K8): the CameraX frame
 * analyzer and the photo decoder, both through zxing. Never ML Kit, which the
 * F-Droid build cannot carry.
 */

private fun qrReader(): MultiFormatReader = MultiFormatReader().apply {
    setHints(
        mapOf(
            DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
            DecodeHintType.TRY_HARDER to true,
        )
    )
}

/**
 * Decodes the camera's frames and reports the first code it reads. The
 * analyzer keeps reporting until the caller detaches it, so the caller is the
 * one that decides a scan is over.
 */
class ExtenderQrAnalyzer(
    private val onDecoded: (String) -> Unit,
) : ImageAnalysis.Analyzer {

    private val reader = qrReader()

    override fun analyze(image: ImageProxy) {
        try {
            val text = decode(image)
            if (text != null) {
                onDecoded(text)
            }
        } catch (e: Exception) {
            // a frame that does not decode is the normal case, and a frame
            // whose layout this build does not expect must not kill the scan
        } finally {
            image.close()
        }
    }

    private fun decode(image: ImageProxy): String? {
        val plane = image.planes.firstOrNull() ?: return null
        val buffer = plane.buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        // the luminance plane is row-padded on most devices, so the source is
        // built over the stride and cropped back to the frame
        val rowStride = plane.rowStride
        val dataWidth = if (image.width <= rowStride) rowStride else image.width
        val dataHeight = minOf(image.height, bytes.size / dataWidth.coerceAtLeast(1))
        if (dataHeight <= 0 || image.width <= 0) {
            return null
        }
        val source = PlanarYUVLuminanceSource(
            bytes,
            dataWidth,
            dataHeight,
            0,
            0,
            image.width,
            dataHeight,
            false,
        )
        return try {
            reader.decodeWithState(BinaryBitmap(HybridBinarizer(source)))?.text
        } catch (e: Exception) {
            null
        } finally {
            reader.reset()
        }
    }
}

/**
 * The code in a chosen photo, or null when there is none to read — which is
 * what the "no QR code was found" message says.
 */
fun decodeExtenderQrFromImage(context: Context, uri: Uri): String? {
    val bitmap = loadBitmap(context, uri) ?: return null
    return try {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val source = RGBLuminanceSource(width, height, pixels)
        qrReader().decode(BinaryBitmap(HybridBinarizer(source)))?.text
    } catch (e: Exception) {
        null
    } finally {
        bitmap.recycle()
    }
}

private fun loadBitmap(context: Context, uri: Uri): Bitmap? = try {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        ImageDecoder.decodeBitmap(
            ImageDecoder.createSource(context.contentResolver, uri)
        ) { decoder, _, _ ->
            // getPixels needs a readable, non-hardware bitmap
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = false
        }
    } else {
        @Suppress("DEPRECATION")
        MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
    }
} catch (e: Exception) {
    null
}
