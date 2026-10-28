package com.bringyour.network.ui.connect

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bringyour.network.ui.components.CONNECT_GRAPHIC_FULL_DP
import com.bringyour.network.ui.components.PaneSplit
import com.bringyour.network.ui.components.connectGraphicSize
import com.bringyour.network.ui.theme.SheetBlack
import kotlin.math.roundToInt

/**
 * The two panes of the connect screen's pane arrangements (AdaptiveLayout.kt):
 * the statistics pane at the start and the connect pane at the end side by
 * side, or the connect pane above and the statistics pane below when stacked.
 * [splitFor] places the edge for the container's size in pixels, at the fold
 * when one divides the window; it runs in the layout pass, so a fold that
 * moves re-lays the panes without recomposing them.
 */
@Composable
internal fun ConnectPanes(
    sideBySide: Boolean,
    splitFor: Density.(width: Int, height: Int) -> PaneSplit,
    statsPane: @Composable () -> Unit,
    connectPane: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Layout(
        contents = listOf(statsPane, connectPane),
        modifier = modifier,
    ) { (statsMeasurables, connectMeasurables), constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val split = splitFor(width, height)

        if (sideBySide) {
            // the split is left to right in window terms; the statistics take
            // the start side, the left one in a left-to-right layout
            val leftEnd = split.firstEnd.dp.roundToPx().coerceIn(0, width)
            val rightStart = split.secondStart.dp.roundToPx().coerceIn(leftEnd, width)
            val statsOnLeft = layoutDirection == LayoutDirection.Ltr
            val statsX = if (statsOnLeft) 0 else rightStart
            val statsWidth = if (statsOnLeft) leftEnd else width - rightStart
            val connectX = if (statsOnLeft) rightStart else 0
            val connectWidth = if (statsOnLeft) width - rightStart else leftEnd
            val stats = statsMeasurables.map { it.measure(Constraints.fixed(statsWidth, height)) }
            val connect = connectMeasurables.map { it.measure(Constraints.fixed(connectWidth, height)) }
            layout(width, height) {
                stats.forEach { it.place(statsX, 0) }
                connect.forEach { it.place(connectX, 0) }
            }
        } else {
            val topEnd = split.firstEnd.dp.roundToPx().coerceIn(0, height)
            val bottomStart = split.secondStart.dp.roundToPx().coerceIn(topEnd, height)
            val connect = connectMeasurables.map { it.measure(Constraints.fixed(width, topEnd)) }
            val stats = statsMeasurables.map { it.measure(Constraints.fixed(width, height - bottomStart)) }
            layout(width, height) {
                connect.forEach { it.place(0, 0) }
                stats.forEach { it.place(0, bottomStart) }
            }
        }
    }
}

/**
 * The connect graphic and what sits under it (the status, and in the stacked
 * arrangement the peers line), centered in the room it has. The graphic is
 * the 256dp connect button drawn as one picture at the size the room leaves
 * it, between [minSize] and [maxSize]: a short window shrinks it and a pane
 * grows it, and its grid, dots and taps scale with it. [topSpacing] and [gap]
 * are the spacing above and below it at full size and scale with it too.
 *
 * A layout rather than a BoxWithConstraints: the graphic is movable content
 * that keeps its state as it moves between the arrangements, which a
 * subcomposition in between would lose.
 */
@Composable
internal fun ConnectGraphicLayout(
    minSize: Dp,
    maxSize: Dp,
    topSpacing: Dp,
    gap: Dp,
    graphic: @Composable () -> Unit,
    below: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Layout(
        contents = listOf(graphic, below),
        modifier = modifier,
    ) { (graphicMeasurables, belowMeasurables), constraints ->
        val width = constraints.maxWidth
        val belowPlaceables = belowMeasurables.map { it.measure(Constraints(maxWidth = width)) }
        val belowHeight = belowPlaceables.sumOf { it.height }
        val fullPx = CONNECT_GRAPHIC_FULL_DP.dp.toPx()
        // the spacing grows and shrinks with the graphic, so the room it
        // leaves the graphic is shared in that proportion
        val spacingShare = (topSpacing + gap).toPx() / fullPx
        val roomHeight = if (constraints.hasBoundedHeight) {
            (constraints.maxHeight - belowHeight) / (1f + spacingShare)
        } else {
            maxSize.toPx()
        }
        val side = connectGraphicSize(
            availableWidth = width.toFloat(),
            availableHeight = roomHeight,
            min = minSize.toPx(),
            max = maxSize.toPx(),
        )
        val scale = side / fullPx
        val top = topSpacing.toPx() * scale
        val gapPx = gap.toPx() * scale
        val graphicPlaceables = graphicMeasurables.map { it.measure(Constraints()) }
        val contentHeight = (top + side + gapPx).roundToInt() + belowHeight
        val height = if (constraints.hasBoundedHeight) constraints.maxHeight else contentHeight
        val groupTop = ((height - contentHeight) / 2).coerceAtLeast(0)

        layout(width, height) {
            val slotTop = groupTop + top
            graphicPlaceables.forEach { placeable ->
                // scaled about its center, in the middle of its slot
                placeable.placeWithLayer(
                    x = (width - placeable.width) / 2,
                    y = (slotTop + (side - placeable.height) / 2f).roundToInt(),
                ) {
                    scaleX = scale
                    scaleY = scale
                }
            }
            var y = (slotTop + side + gapPx).roundToInt()
            belowPlaceables.forEach { placeable ->
                placeable.place((width - placeable.width) / 2, y)
                y += placeable.height
            }
        }
    }
}

/**
 * The location picker as a dialog, in the pane arrangements (apple/DUO.md):
 * it opens over the pane that holds the location row, under the row, and
 * never crosses the fold or the other pane. [regionOnScreen] is that region
 * in screen pixels; the dialog window spans the app window, maps the region
 * into its own coordinates, and shortens the card above the keyboard. A tap
 * beside the card closes it.
 */
@Composable
internal fun LocationPickerDialog(
    regionOnScreen: Rect,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val density = LocalDensity.current
        var originOnScreen by remember { mutableStateOf(Offset.Zero) }
        val imeBottom = WindowInsets.ime.getBottom(density)

        Layout(
            content = {
                Surface(
                    modifier = Modifier
                        .padding(8.dp)
                        // a tap on the card stays in the card
                        .pointerInput(Unit) { detectTapGestures { } },
                    shape = RoundedCornerShape(16.dp),
                    color = SheetBlack,
                ) {
                    content()
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { originOnScreen = it.positionOnScreen() }
                .pointerInput(onDismiss) { detectTapGestures { onDismiss() } },
        ) { measurables, constraints ->
            val region = regionOnScreen.translate(-originOnScreen)
            val left = region.left.roundToInt().coerceIn(0, constraints.maxWidth)
            val right = region.right.roundToInt().coerceIn(left, constraints.maxWidth)
            val top = region.top.roundToInt().coerceIn(0, constraints.maxHeight)
            val bottom = minOf(region.bottom.roundToInt(), constraints.maxHeight - imeBottom)
                .coerceIn(top, constraints.maxHeight)
            val card = measurables.map { it.measure(Constraints.fixed(right - left, bottom - top)) }
            layout(constraints.maxWidth, constraints.maxHeight) {
                card.forEach { it.place(left, top) }
            }
        }
    }
}
