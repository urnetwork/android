package com.bringyour.network.ui.components

import androidx.compose.material3.adaptive.HingeInfo
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalWithComputedDefaultOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo

/**
 * The window the app lays out in, as the adaptive layouts (AdaptiveLayout.kt)
 * read it: its size in dp, Material's tabletop posture, and its fold. One
 * value for the whole screen, dialogs and sheets included, so every layout
 * decides from the same window.
 */
@Immutable
data class WindowLayout(
    val widthDp: Float,
    val heightDp: Float,
    /** Half open with the fold across the window (Material's isTabletop). */
    val tabletop: Boolean,
    /**
     * The window's fold, in window dp: the one that divides it when there is
     * one, else one lying flat (a fully opened book-style foldable's crease);
     * null without a fold. Side-by-side panes split at either (sideBySideSplit).
     */
    val hinge: WindowFold?,
) {
    /**
     * The fold that divides the window (half open, or a hinge between two
     * screens), in window dp; null when flat or without one. The posture,
     * the stacked split, the drawer, dialogs and sheets follow only this one.
     */
    val fold: WindowFold?
        get() = hinge?.takeIf { it.separating }

    /** Medium width and up: the panes and the list-detail layouts. */
    val isMediumWidthOrWider: Boolean
        get() = isMediumWidthOrWider(widthDp)
}

/**
 * The current [WindowLayout]. MainNavHost and LoginNavHost provide it from
 * the window and its fold; without a provider (previews) it is the window's
 * size with no fold.
 */
val LocalWindowLayout = compositionLocalWithComputedDefaultOf {
    val size = LocalWindowInfo.currentValue.containerSize
    val density = LocalDensity.currentValue.density
    WindowLayout(
        widthDp = size.width / density,
        heightDp = size.height / density,
        tabletop = false,
        hinge = null,
    )
}

/**
 * The window's [WindowLayout], following every resize, fold and unfold.
 * Material 3 Adaptive's window info with the large and extra-large width
 * classes, the 1.2 form of currentWindowAdaptiveInfoV2() (1.3 needs AGP 9.1
 * and compileSdk 37); its posture gives the fold, flat ones included.
 */
@Composable
fun rememberWindowLayout(): WindowLayout {
    val adaptiveInfo = currentWindowAdaptiveInfo(supportLargeAndXLargeWidth = true)
    val size = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current.density
    return remember(adaptiveInfo, size, density) {
        val hinges = adaptiveInfo.windowPosture.hingeList
        val hinge = hinges.firstOrNull { it.isSeparating } ?: hinges.firstOrNull()
        WindowLayout(
            widthDp = size.width / density,
            heightDp = size.height / density,
            tabletop = adaptiveInfo.windowPosture.isTabletop,
            hinge = hinge?.toWindowFold(density),
        )
    }
}

private fun HingeInfo.toWindowFold(density: Float): WindowFold =
    if (isVertical) {
        WindowFold(vertical = true, start = bounds.left / density, end = bounds.right / density, separating = isSeparating)
    } else {
        WindowFold(vertical = false, start = bounds.top / density, end = bounds.bottom / density, separating = isSeparating)
    }
