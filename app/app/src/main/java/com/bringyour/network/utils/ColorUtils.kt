package com.bringyour.network.utils

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp


fun Color.lighten(fraction: Float): Color {
    // Clamp fraction between 0f and 1f
    val safeFraction = fraction.coerceIn(0f, 1f)
    return lerp(this, Color.White, safeFraction)
}

//fun Color.lighten(amount: Float): Color {
//    val safeAmount = amount.coerceIn(0f, 1f)
//    val r = red + (1f - red) * safeAmount
//    val g = green + (1f - green) * safeAmount
//    val b = blue + (1f - blue) * safeAmount
//    return Color(r, g, b, alpha)
//}