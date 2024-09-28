package com.bringyour.network.utils

import android.app.Activity
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.runtime.Composable
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun isTablet(): Boolean {

    val context = LocalContext.current
    val activity = context as? Activity

    val windowSizeClass = activity?.let { calculateWindowSizeClass(it) }

    return windowSizeClass?.widthSizeClass?.let {
        it > WindowWidthSizeClass.Medium
    } ?: false
}