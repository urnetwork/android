package com.bringyour.network.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = BlueMedium,
    secondary = BlueLight,
    tertiary = Pink,
    background = Black,
    onBackground = OffWhite,
    onPrimary = OffWhite,
)

@Composable
fun URNetworkTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}