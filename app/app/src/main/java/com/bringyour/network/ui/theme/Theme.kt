package com.bringyour.network.ui.theme

import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private val DarkColorScheme = darkColorScheme(
    primary = BlueMedium,
    secondary = BlueLight,
    tertiary = Pink,
    background = Black,
    onBackground = OffWhite,
    onPrimary = OffWhite,
)

/**
 * The state layers every clickable draws in its content color. Hover and
 * keyboard focus are stronger than Material's 8% and 10%, which barely show
 * on the app's near-black surfaces, so a pointer over a control and the
 * keyboard's focus are always apparent (adaptive app quality, Hover_States
 * and Drawable_Focus). Neither shows for touch; a press keeps Material's.
 */
private val URRippleConfiguration = RippleConfiguration(
    rippleAlpha = RippleAlpha(
        pressedAlpha = 0.10f,
        focusedAlpha = 0.20f,
        draggedAlpha = 0.16f,
        hoveredAlpha = 0.12f,
    ),
)

@Composable
fun URNetworkTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
    ) {
        CompositionLocalProvider(
            LocalRippleConfiguration provides URRippleConfiguration,
            content = content,
        )
    }
}
