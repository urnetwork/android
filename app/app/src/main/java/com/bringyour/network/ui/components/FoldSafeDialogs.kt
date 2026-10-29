package com.bringyour.network.ui.components

import android.view.View
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetDefaults
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToInt

/*
 * Dialogs and sheets kept off a fold that divides the window (apple/DUO.md
 * item 11). Without such a fold each one is exactly the Material or platform
 * component it wraps. With one, it opens on one side of the fold, the side
 * foldSafeDialogRegion (AdaptiveLayout.kt) picks: in tabletop the half below
 * the fold, where Google puts a half-open device's controls; beside a book
 * fold the half the user touched to open it.
 */

/**
 * Where the last touch went down in the activity's window, in px, recorded
 * by [recordDialogAnchor] over the screens: the touch a dialog or a sheet is
 * opened from, so beside a fold it opens on that touch's side. Flutter's
 * hinge-aware dialogs call it the anchor point.
 */
@Stable
class DialogAnchor {
    internal var rootInWindow: Offset = Offset.Zero
    internal var lastDownInWindow: Offset? = null
}

/** The screens' [DialogAnchor]. Without a provider nothing is recorded, and dialogs take the start side. */
val LocalDialogAnchor = staticCompositionLocalOf { DialogAnchor() }

/**
 * Records in [anchor] where each touch goes down in this layout, before
 * anything under it sees the touch, and without taking it from them.
 */
fun Modifier.recordDialogAnchor(anchor: DialogAnchor): Modifier = this
    .onGloballyPositioned { anchor.rootInWindow = it.positionInWindow() }
    .pointerInput(anchor) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.firstOrNull { it.changedToDownIgnoreConsumed() }?.let { down ->
                    anchor.lastDownInWindow = anchor.rootInWindow + down.position
                }
            }
        }
    }

/** The window, the fold and the touch a dialog opens beside. */
@Stable
private class FoldPlacement(
    val windowLayout: WindowLayout,
    /** The activity's view, to map the window's coordinates onto the dialog's. */
    val hostView: View,
    /** The touch the dialog was opened from, in window dp; null when no touch is known. */
    val anchorX: Float?,
) {
    /** True when a fold divides the window, so the dialog keeps to one side of it. */
    val besideFold: Boolean get() = windowLayout.fold != null
}

@Composable
private fun rememberFoldPlacement(): FoldPlacement {
    val anchor = LocalDialogAnchor.current
    val density = LocalDensity.current.density
    // as the dialog opens: it keeps its side while it is open
    val anchorX = remember { anchor.lastDownInWindow?.let { it.x / density } }
    return FoldPlacement(LocalWindowLayout.current, LocalView.current, anchorX)
}

/** A dialog's margin from the fold and the window's edges in its region. */
private val DialogRegionMargin = 24.dp

/** The widest a dialog card gets, as the platform's own dialogs. */
private val DialogMaxWidth = 560.dp

/**
 * [this] properties for a dialog window that spans the app's window, so the
 * dialog's own layout places the card: the platform's centering, and its
 * fitting to the system bars, would put it on the fold.
 */
private fun DialogProperties.spanningWindow(): DialogProperties = DialogProperties(
    dismissOnBackPress = dismissOnBackPress,
    dismissOnClickOutside = dismissOnClickOutside,
    securePolicy = securePolicy,
    usePlatformDefaultWidth = false,
    decorFitsSystemWindows = false,
    windowTitle = windowTitle,
    windowType = windowType,
    windowToken = windowToken,
)

/**
 * Lays a dialog's card in the region beside the fold
 * ([foldSafeDialogRegion]), within the window's safe area and above the
 * keyboard. Applied to the dialog's root, which spans the dialog's window;
 * that window spans the screen as the app's does, and their offset, if any,
 * is measured. [fillRegion] makes the card the region's size (a full-screen
 * dialog), else it is centered in it at its own size. A tap beside the card
 * calls [onTapOutside], as a tap outside a centered dialog would; without
 * it the tap does nothing.
 */
private fun Modifier.foldPlacement(
    placement: FoldPlacement,
    fillRegion: Boolean,
    onTapOutside: (() -> Unit)?,
): Modifier = composed {
    val safeDrawing = WindowInsets.safeDrawing
    var originOnScreen by remember { mutableStateOf<Offset?>(null) }
    // the card's bounds as placed, in this layout's px: left, top, right, bottom
    val card = remember { IntArray(4) }
    val tapOutside by rememberUpdatedState(onTapOutside)
    this
        .onGloballyPositioned { originOnScreen = it.positionOnScreen() }
        .pointerInput(Unit) {
            detectTapGestures { position ->
                val onCard = card[0] <= position.x && position.x < card[2] &&
                    card[1] <= position.y && position.y < card[3]
                if (!onCard) {
                    tapOutside?.invoke()
                }
            }
        }
        // the card keeps clear of the bars and the keyboard by its place, so
        // what is in it does not pad for them again
        .consumeWindowInsets(safeDrawing)
        .layout { measurable, constraints ->
            val width = constraints.maxWidth
            val height = constraints.maxHeight
            // this layout's origin in the app window's px: zero when both
            // windows span the screen
            val host = IntArray(2).also { placement.hostView.rootView.getLocationOnScreen(it) }
            val origin = originOnScreen ?: Offset(host[0].toFloat(), host[1].toFloat())
            val shiftX = origin.x - host[0]
            val shiftY = origin.y - host[1]
            val usable = WindowRegion(
                left = (shiftX + safeDrawing.getLeft(this, layoutDirection)) / density,
                top = (shiftY + safeDrawing.getTop(this)) / density,
                right = (shiftX + width - safeDrawing.getRight(this, layoutDirection)) / density,
                bottom = (shiftY + height - safeDrawing.getBottom(this)) / density,
            )
            val window = placement.windowLayout
            val region = foldSafeDialogRegion(
                width = window.widthDp,
                height = window.heightDp,
                fold = window.fold,
                startOnLeft = layoutDirection == LayoutDirection.Ltr,
                anchorX = placement.anchorX,
                usable = usable,
            )
            val margin = if (fillRegion) 0 else DialogRegionMargin.roundToPx()
            val left = (region.left * density - shiftX).roundToInt() + margin
            val top = (region.top * density - shiftY).roundToInt() + margin
            val regionWidth = ((region.width * density).roundToInt() - 2 * margin).coerceAtLeast(0)
            val regionHeight = ((region.height * density).roundToInt() - 2 * margin).coerceAtLeast(0)
            val placeable = measurable.measure(
                if (fillRegion) {
                    Constraints.fixed(regionWidth, regionHeight)
                } else {
                    Constraints(maxWidth = regionWidth, maxHeight = regionHeight)
                }
            )
            val x = left + (regionWidth - placeable.width) / 2
            val y = top + (regionHeight - placeable.height) / 2
            card[0] = x
            card[1] = y
            card[2] = x + placeable.width
            card[3] = y + placeable.height
            layout(width, height) {
                placeable.place(x, y)
            }
        }
}

/**
 * A modal dialog kept off a fold that divides the window. With no such fold
 * it is the platform's centered dialog. With one, the dialog window spans the
 * app window and the card opens in the region beside the fold
 * ([foldSafeDialogRegion]); a tap beside it dismisses it as a tap outside a
 * centered dialog does. A fold or an unfold while it is open moves it there,
 * opening it again, so what it shows keeps its state above it.
 *
 * A [fullScreen] dialog (a screen shown as a dialog) fills its region, and
 * a tap beside it does nothing, as there is no outside to a full-screen
 * dialog. It keeps the window it opened with, so a fold or an unfold never
 * opens it again and a page it loads (the web checkout) keeps its place.
 * Opened beside a fold it fills the region beside it, and the window's safe
 * area once the fold is gone; opened without one it stays the platform's
 * full-screen dialog.
 */
@Composable
fun FoldSafeDialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    fullScreen: Boolean = false,
    content: @Composable () -> Unit,
) {
    val placement = rememberFoldPlacement()
    // a full-screen dialog keeps the window it opened with
    val openedBesideFold = remember { placement.besideFold }
    val spanning = if (fullScreen) openedBesideFold else placement.besideFold
    if (!spanning) {
        Dialog(onDismissRequest = onDismissRequest, properties = properties, content = content)
    } else {
        Dialog(onDismissRequest = onDismissRequest, properties = properties.spanningWindow()) {
            Box(
                modifier = Modifier
                    .foldPlacement(
                        placement,
                        fillRegion = fullScreen,
                        onTapOutside = onDismissRequest.takeIf { properties.dismissOnClickOutside && !fullScreen },
                    )
                    .then(if (fullScreen) Modifier else Modifier.widthIn(max = DialogMaxWidth))
            ) {
                content()
            }
        }
    }
}

/**
 * Material's [AlertDialog], kept off a fold that divides the window: without
 * one it is that dialog, unchanged; with one it opens in the region beside
 * the fold ([FoldSafeDialog]).
 */
@Composable
fun FoldSafeAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    shape: Shape = AlertDialogDefaults.shape,
    containerColor: Color = AlertDialogDefaults.containerColor,
    iconContentColor: Color = AlertDialogDefaults.iconContentColor,
    titleContentColor: Color = AlertDialogDefaults.titleContentColor,
    textContentColor: Color = AlertDialogDefaults.textContentColor,
    tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
    properties: DialogProperties = DialogProperties(),
) {
    val placement = rememberFoldPlacement()
    if (!placement.besideFold) {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            confirmButton = confirmButton,
            modifier = modifier,
            dismissButton = dismissButton,
            icon = icon,
            title = title,
            text = text,
            shape = shape,
            containerColor = containerColor,
            iconContentColor = iconContentColor,
            titleContentColor = titleContentColor,
            textContentColor = textContentColor,
            tonalElevation = tonalElevation,
            properties = properties,
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            confirmButton = confirmButton,
            modifier = Modifier
                .foldPlacement(
                    placement,
                    fillRegion = false,
                    onTapOutside = onDismissRequest.takeIf { properties.dismissOnClickOutside },
                )
                .then(modifier),
            dismissButton = dismissButton,
            icon = icon,
            title = title,
            text = text,
            shape = shape,
            containerColor = containerColor,
            iconContentColor = iconContentColor,
            titleContentColor = titleContentColor,
            textContentColor = textContentColor,
            tonalElevation = tonalElevation,
            properties = properties.spanningWindow(),
        )
    }
}

/**
 * Material's [BasicAlertDialog], kept off a fold that divides the window:
 * without one it is that dialog, unchanged; with one it opens in the region
 * beside the fold ([FoldSafeDialog]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoldSafeBasicAlertDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit,
) {
    val placement = rememberFoldPlacement()
    if (!placement.besideFold) {
        BasicAlertDialog(onDismissRequest = onDismissRequest, modifier = modifier, properties = properties, content = content)
    } else {
        BasicAlertDialog(
            onDismissRequest = onDismissRequest,
            modifier = Modifier
                .foldPlacement(
                    placement,
                    fillRegion = false,
                    onTapOutside = onDismissRequest.takeIf { properties.dismissOnClickOutside },
                )
                .then(modifier),
            properties = properties.spanningWindow(),
            content = content,
        )
    }
}

/**
 * Material's [ModalBottomSheet], kept off a fold that divides the window.
 * Beside a book fold it opens in the region a dialog would
 * ([foldSafeSheetRegion]): as wide as that half at most, and centered in it.
 * In tabletop it keeps its width and opens to the half below the fold
 * ([rememberFoldSafeSheetState]). Without a fold it is that sheet, unchanged.
 * A fold or an unfold while it is open moves it without closing it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoldSafeModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberFoldSafeSheetState(),
    sheetMaxWidth: Dp = BottomSheetDefaults.SheetMaxWidth,
    containerColor: Color = BottomSheetDefaults.ContainerColor,
    contentColor: Color = contentColorFor(containerColor),
    properties: ModalBottomSheetProperties = ModalBottomSheetDefaults.properties,
    content: @Composable ColumnScope.() -> Unit,
) {
    val placement = rememberFoldPlacement()
    val window = placement.windowLayout
    val region = foldSafeSheetRegion(
        width = window.widthDp,
        height = window.heightDp,
        fold = window.fold,
        startOnLeft = LocalLayoutDirection.current == LayoutDirection.Ltr,
        anchorX = placement.anchorX,
    )
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        // the sheet is centered in its window, which spans the app's: from
        // there to the middle of its half
        modifier = if (region == null) modifier else Modifier
            .absoluteOffset(x = (region.left + region.width / 2f - window.widthDp / 2f).dp)
            .then(modifier),
        sheetState = sheetState,
        sheetMaxWidth = if (region == null) sheetMaxWidth else minOf(sheetMaxWidth, region.width.dp),
        containerColor = containerColor,
        contentColor = contentColor,
        properties = properties,
        content = content,
    )
}

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
