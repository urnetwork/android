package com.bringyour.network.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Tablet layout convention (mmm/DESIGNSTYLE.md "Tablet layouts"): in a window
 * of medium width and up a full-screen view lays its content in one readable
 * centered column the width of one of the app's sheets, and an auth screen in a
 * narrower form column. Both clamps are no-ops in compact windows, so one view
 * serves phones, foldables, tablets and resizable windows in every posture. The
 * connect screen puts its drawer in a pane instead (AdaptiveLayout.kt).
 */
object TabletLayout {

    /**
     * The readable content column. It equals the width of the app's modal
     * bottom sheets on a tablet (Material 3 `BottomSheetDefaults.SheetMaxWidth`, 640dp): the
     * referral network editor, add sign-in method, redeem code and emoji tag
     * sheets all render at this width, so a full-screen view reads like a sheet.
     */
    val contentWidth: Dp = 640.dp

    /** The auth form column (sign in, create network, instant account, seed phrase). */
    val formWidth: Dp = 400.dp
}

/**
 * True when the current window is of medium width or wider: the window size
 * class, not the device, so an unfolded phone, a tablet and a wide split-screen
 * window all count, and a folded phone or a narrow window never does.
 */
@Composable
@ReadOnlyComposable
fun isTabletWidth(): Boolean =
    LocalWindowLayout.current.isMediumWidthOrWider

/**
 * Centers the content in a column of [TabletLayout.contentWidth] on tablet
 * widths; a no-op on phones. Apply outside the view's own padding, before its
 * `fillMaxWidth`/`fillMaxSize`.
 */
fun Modifier.tabletReadableColumn(): Modifier = tabletColumn(TabletLayout.contentWidth)

/**
 * Centers an auth form in a column of [TabletLayout.formWidth] on tablet widths;
 * a no-op on phones.
 */
fun Modifier.tabletForm(): Modifier = tabletColumn(TabletLayout.formWidth)

private fun Modifier.tabletColumn(maxWidth: Dp): Modifier = composed {
    if (isTabletWidth()) {
        this
            .fillMaxWidth()
            .wrapContentWidth(Alignment.CenterHorizontally)
            .widthIn(max = maxWidth)
    } else {
        this
    }
}
