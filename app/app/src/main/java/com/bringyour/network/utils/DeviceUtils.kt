package com.bringyour.network.utils

import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.window.layout.WindowMetricsCalculator
import android.app.Activity
import android.content.res.Configuration
import android.util.DisplayMetrics
import android.util.Log
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.sqrt

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun isTablet(): Boolean {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val densityDpi = configuration.densityDpi.toFloat()

    // Calculate the physical screen width and height in inches
    val widthInches = with(LocalDensity.current) { configuration.screenWidthDp.dp.toPx() / densityDpi }
    val heightInches = with(LocalDensity.current) { configuration.screenHeightDp.dp.toPx() / densityDpi }

    // Calculate the diagonal screen size in inches
    val screenSizeInches = kotlin.math.sqrt(widthInches * widthInches + heightInches * heightInches)

    // Check the current orientation
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Threshold for tablets is 9 inches or more
    val isLargeScreen = screenSizeInches >= 9.0

    // Optionally: Check the smallest width in dp for smaller devices
    val isSmallestWidthTablet = configuration.smallestScreenWidthDp >= 600

    // If the screen is smaller than 9 inches, check for landscape mode
    if (!isLargeScreen) {
        return isLandscape && isSmallestWidthTablet
    }

    // Optionally: Additional checks for window size for refinement
    val activity = context as? Activity
    val windowSizeClass = activity?.let { calculateWindowSizeClass(it) }

    val isWindowSizeClassTablet = windowSizeClass?.widthSizeClass?.let {
        it > WindowWidthSizeClass.Medium
    } ?: false

    // Final check: Must have a large screen (>= 9 inches) or be in landscape mode and have the smallest width check
    return isLargeScreen || (isWindowSizeClassTablet || (isSmallestWidthTablet && isLandscape))
}