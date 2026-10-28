package com.bringyour.network.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalWithComputedDefaultOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * The window the app lays out in, as the adaptive layouts (AdaptiveLayout.kt)
 * read it: its size in dp, Material's tabletop posture, and the fold that
 * divides it. One value for the whole screen, dialogs and sheets included,
 * so every layout decides from the same window.
 */
@Immutable
data class WindowLayout(
    val widthDp: Float,
    val heightDp: Float,
    /** Half open with the fold across the window (Material's isTabletop). */
    val tabletop: Boolean,
    /** The fold that divides the window, in window dp; null when flat or without one. */
    val fold: SeparatingFold?,
) {
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
        fold = null,
    )
}

/**
 * The window's [WindowLayout], following every resize, fold and unfold.
 * Material 3 Adaptive's window info with the large and extra-large width
 * classes, the 1.2 form of currentWindowAdaptiveInfoV2() (1.3 needs AGP 9.1
 * and compileSdk 37); its posture gives the fold.
 */
@Composable
fun rememberWindowLayout(): WindowLayout {
    val adaptiveInfo = currentWindowAdaptiveInfo(supportLargeAndXLargeWidth = true)
    val size = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current.density
    return remember(adaptiveInfo, size, density) {
        val hinge = adaptiveInfo.windowPosture.hingeList.firstOrNull { it.isSeparating }
        WindowLayout(
            widthDp = size.width / density,
            heightDp = size.height / density,
            tabletop = adaptiveInfo.windowPosture.isTabletop,
            fold = hinge?.let {
                if (it.isVertical) {
                    SeparatingFold(vertical = true, start = it.bounds.left / density, end = it.bounds.right / density)
                } else {
                    SeparatingFold(vertical = false, start = it.bounds.top / density, end = it.bounds.bottom / density)
                }
            },
        )
    }
}

/**
 * A modal dialog kept off a fold that divides the window (apple/DUO.md item
 * 11): with no such fold it is the platform's centered dialog; with one, the
 * dialog window spans the app window and the card opens centered in one side
 * of it (foldSafeDialogRegion), and a tap beside the card dismisses it like a
 * tap outside a centered dialog.
 */
@Composable
fun FoldSafeDialog(
    onDismissRequest: () -> Unit,
    content: @Composable () -> Unit,
) {
    val windowLayout = LocalWindowLayout.current
    val fold = windowLayout.fold
    if (fold == null) {
        Dialog(onDismissRequest = onDismissRequest) {
            content()
        }
        return
    }

    val region = foldSafeDialogRegion(
        width = windowLayout.widthDp,
        height = windowLayout.heightDp,
        fold = fold,
        startOnLeft = LocalLayoutDirection.current == LayoutDirection.Ltr,
    )
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(onDismissRequest) { detectTapGestures { onDismissRequest() } }
        ) {
            Box(
                modifier = Modifier
                    .offset(x = region.left.dp, y = region.top.dp)
                    .size(width = region.width.dp, height = region.height.dp)
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                // the card takes its taps, so only a tap beside it dismisses
                Box(
                    modifier = Modifier
                        .widthIn(max = DialogMaxWidth)
                        .pointerInput(Unit) { detectTapGestures { } }
                ) {
                    content()
                }
            }
        }
    }
}

/** The widest a dialog card gets, as the platform's own dialogs. */
private val DialogMaxWidth = 560.dp

/**
 * A modal bottom sheet's state that keeps a tall sheet off the fold in
 * tabletop (apple/DUO.md item 11): with a horizontal fold dividing the window
 * a sheet taller than half of it opens to its partially expanded position,
 * which is the half below the fold, and a drag shows the rest. Otherwise it
 * opens fully, as the app's sheets always have; a short sheet does either way.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberFoldSafeSheetState(
    confirmValueChange: (SheetValue) -> Boolean = { true },
): SheetState {
    val tabletopFold = LocalWindowLayout.current.fold?.vertical == false
    return rememberModalBottomSheetState(
        skipPartiallyExpanded = !tabletopFold,
        confirmValueChange = confirmValueChange,
    )
}
