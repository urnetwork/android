package com.bringyour.network.utils

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp


fun Color.lighten(fraction: Float): Color {
    // Clamp fraction between 0f and 1f
    val safeFraction = fraction.coerceIn(0f, 1f)
    return lerp(this, Color.White, safeFraction)
}
