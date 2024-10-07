package com.bringyour.network.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

@Composable
fun isTablet(): Boolean {
    val configuration = LocalConfiguration.current
    val densityDpi = configuration.densityDpi.toFloat()

    // check screen size in inches
    val widthInches = with(LocalDensity.current) { configuration.screenWidthDp.dp.toPx() / densityDpi }
    val heightInches = with(LocalDensity.current) { configuration.screenHeightDp.dp.toPx() / densityDpi }

    // diagonal screen size in inches
    val screenSizeInches = kotlin.math.sqrt(widthInches * widthInches + heightInches * heightInches)

    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val isLargeScreen = screenSizeInches >= 9.0

    // smallest width in dp for smaller devices
    val isSmallestWidthTablet = configuration.smallestScreenWidthDp >= 600

    // If the screen is smaller than 9 inches, check for landscape mode
    return if (!isLargeScreen) {
        isLandscape && isSmallestWidthTablet
    } else {
        true
    }
}