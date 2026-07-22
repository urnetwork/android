package com.bringyour.network.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import com.bringyour.sdk.Sdk

/**
 * The one component that renders an identity key identicon.
 *
 * The raster is the canonical square png from the SDK
 * (`Sdk.renderIdenticonPng`), pre-resampled at 2x the dp size for crispness,
 * so no view-side interpolation adjustment is wanted. The composable just
 * sizes it and clips the corners with the standard slight rounding
 * (proportional to size, consistent everywhere identicons appear).
 *
 * Callers hold the bitmap in their view model, cached per (key hash, size)
 * via `renderIdenticon` -- never render per composition.
 */
@Composable
fun Identicon(
    image: ImageBitmap?,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size / 6))
    ) {
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds,
            )
        } else {
            // key not available yet: a quiet placeholder with the same
            // footprint, so the layout does not jump when the key loads
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.15f))
            )
        }
    }
}

/**
 * Render the canonical identicon for `key` at 2x the display dp size (the
 * raster convention shared with every platform).
 */
fun renderIdenticon(key: ByteArray, sizeDp: Int): ImageBitmap? {
    return try {
        val png = Sdk.renderIdenticonPng(key, (sizeDp * 2).toLong()) ?: return null
        BitmapFactory.decodeByteArray(png, 0, png.size)?.asImageBitmap()
    } catch (e: Exception) {
        null
    }
}
