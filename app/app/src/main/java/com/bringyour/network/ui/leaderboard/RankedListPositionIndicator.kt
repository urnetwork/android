package com.bringyour.network.ui.leaderboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.bringyour.network.R
import com.bringyour.network.ui.components.focusRing
import com.bringyour.network.ui.components.keyboardScrollFor
import com.bringyour.network.ui.theme.MainTintedBackgroundBase
import com.bringyour.network.ui.theme.TextFaint
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.sdk.Sdk
import java.text.NumberFormat
import kotlin.math.roundToLong

/** The drag label of a position indicator: the rank ("#1,240") and its tier ("Top 5%"), or no tier. */
data class RankedListLabel(
    val rankText: String,
    // empty when the rank has no tier
    val tierText: String,
)

/**
 * The draggable position indicator of a long ranked list (mmm/DESIGNSTYLE.md
 * "Long ranked lists"), shared by the Points and Protocol tabs: a faint track
 * spanning positions 1..`total` of the ranked population, an accent thumb at
 * least 44dp tall and 24dp wide whose top marks the position of the first
 * visible row and whose length is the loaded window over `total`. Dragging
 * the thumb shows the rank under it with its tier (`label`); releasing seeks
 * that rank, and the label fades. It is hidden while the list is shorter than
 * the screen. For accessibility the thumb is a slider over 1..`total` whose
 * set-progress action seeks.
 *
 * `positions` are the loaded rows' positions in list order; the rows start
 * at item `rowsOffset` of the lazy list, after the items above them.
 */
@Composable
internal fun RankedListPositionIndicator(
    listState: LazyListState,
    positions: List<Long>,
    rowsOffset: Int,
    total: Long,
    hasMoreBefore: Boolean,
    hasMoreAfter: Boolean,
    label: @Composable (Long) -> RankedListLabel,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val minThumbPx = with(density) { PointsLeaderboardIndicator.MIN_THUMB_DP.dp.toPx() }
    var trackPx by remember { mutableIntStateOf(0) }

    // the position at the top of the screen and how many rows fit, from the
    // lazy layout; the items above the rows count as the window's start
    var topPosition by remember { mutableLongStateOf(0L) }
    var visibleRows by remember { mutableIntStateOf(0) }
    LaunchedEffect(listState, positions) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo }
            .collect { visible ->
                val firstRow = visible.firstOrNull { it.index >= rowsOffset }
                val rowIndex = (firstRow?.index ?: rowsOffset) - rowsOffset
                topPosition = positions.getOrNull(rowIndex) ?: positions.firstOrNull() ?: 0L
                visibleRows = visible.count { it.index - rowsOffset in positions.indices }
            }
    }

    val visible = PointsLeaderboardIndicator.isVisible(
        total = total,
        loadedRows = positions.size,
        visibleRows = visibleRows,
        hasMoreBefore = hasMoreBefore,
        hasMoreAfter = hasMoreAfter,
    )

    var dragging by remember { mutableStateOf(false) }
    var dragTopPx by remember { mutableFloatStateOf(0f) }
    var dragRank by remember { mutableLongStateOf(0L) }
    // after a release the thumb stays where it was dropped until the seeked
    // page lands and the first visible row catches up with it
    var droppedRank by remember { mutableLongStateOf(0L) }
    LaunchedEffect(topPosition) {
        if (droppedRank > 0L && topPosition == droppedRank) {
            droppedRank = 0L
        }
    }

    val restingPosition = if (droppedRank > 0L) droppedRank else topPosition
    val thumb = PointsLeaderboardIndicator.thumb(
        position = restingPosition,
        windowRows = positions.size.toLong(),
        total = total,
        trackPx = trackPx.toFloat(),
        minThumbPx = minThumbPx,
    )
    if (!visible || thumb == null) {
        // measure the track anyway so the first show has its height
        Box(modifier = modifier.width(24.dp).onSizeChanged { trackPx = it.height })
        return
    }

    val thumbTopPx = if (dragging) dragTopPx else thumb.topPx
    val thumbHeightDp = with(density) { thumb.heightPx.toDp() }
    val labelRank = if (dragging || droppedRank > 0L) dragRank.takeIf { it > 0L } ?: droppedRank else restingPosition
    val shownLabel = label(labelRank)
    val indicatorDescription = stringResource(id = R.string.leaderboard_position_indicator)
    val accent = MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .width(24.dp)
            .onSizeChanged { trackPx = it.height }
    ) {
        // the track
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxHeight()
                .width(2.dp)
                .background(TextFaint, RoundedCornerShape(1.dp))
        )

        // the thumb: the full 24dp width is the hit target, the pill inside
        // is what is drawn
        Box(
            modifier = Modifier
                .offset { IntOffset(0, thumbTopPx.roundToLong().toInt()) }
                .width(24.dp)
                .height(thumbHeightDp)
                // the keyboard moves it as a drag does (Drawable_Focus): the
                // arrows and page keys move the thumb and its label, Home and
                // End go to the ends, and letting go of the key seeks, as
                // letting go of the thumb does
                .focusRing(shape = RoundedCornerShape(12.dp))
                .focusTarget()
                .onKeyEvent { event ->
                    val scroll = keyboardScrollFor(event.key, event.isShiftPressed) ?: return@onKeyEvent false
                    when (event.type) {
                        KeyEventType.KeyDown -> {
                            val rank = PointsLeaderboardIndicator.keyRank(
                                scroll = scroll,
                                rank = if (dragging) dragRank else restingPosition,
                                total = total,
                            )
                            dragging = true
                            dragRank = rank
                            dragTopPx = PointsLeaderboardIndicator.fractionOf(rank, total) *
                                (trackPx - thumb.heightPx).coerceAtLeast(0f)
                            true
                        }
                        KeyEventType.KeyUp -> {
                            if (dragging) {
                                dragging = false
                                droppedRank = dragRank
                                onSeek(dragRank)
                            }
                            true
                        }
                        else -> false
                    }
                }
                .progressSemantics(
                    value = labelRank.toFloat(),
                    valueRange = 1f..maxOf(1L, total).toFloat(),
                )
                .semantics {
                    contentDescription = indicatorDescription
                    setProgress { target ->
                        val rank = target.roundToLong().coerceIn(1L, maxOf(1L, total))
                        dragRank = rank
                        droppedRank = rank
                        onSeek(rank)
                        true
                    }
                }
                .pointerInput(trackPx, thumb.heightPx, total) {
                    detectDragGestures(
                        onDragStart = {
                            dragging = true
                            dragTopPx = thumbTopPx
                            dragRank = PointsLeaderboardIndicator.rankAt(
                                PointsLeaderboardIndicator.fractionAt(dragTopPx, trackPx.toFloat(), thumb.heightPx),
                                total,
                            )
                        },
                        onDragEnd = {
                            dragging = false
                            val rank = dragRank
                            if (rank > 0L) {
                                droppedRank = rank
                                onSeek(rank)
                            }
                        },
                        onDragCancel = {
                            dragging = false
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val travel = (trackPx.toFloat() - thumb.heightPx).coerceAtLeast(0f)
                            dragTopPx = (dragTopPx + dragAmount.y).coerceIn(0f, travel)
                            dragRank = PointsLeaderboardIndicator.rankAt(
                                PointsLeaderboardIndicator.fractionAt(dragTopPx, trackPx.toFloat(), thumb.heightPx),
                                total,
                            )
                        },
                    )
                }
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(accent, RoundedCornerShape(3.dp))
            )
        }

        // the label beside the thumb, only while dragging; fades on release
        AnimatedVisibility(
            visible = dragging,
            enter = fadeIn(tween(120)),
            exit = fadeOut(tween(400)),
            modifier = Modifier
                .offset { IntOffset(0, thumbTopPx.roundToLong().toInt()) }
                .offset(x = (-8).dp)
                .wrapContentSize(Alignment.TopEnd, unbounded = true),
        ) {
            Column(
                modifier = Modifier
                    .background(MainTintedBackgroundBase, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.End,
            ) {
                Text(
                    shownLabel.rankText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                )
                if (shownLabel.tierText.isNotEmpty()) {
                    Text(
                        shownLabel.tierText,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * The drag label for a rank among `total` ranked networks, from the sdk's
 * shared tier helper (PointsLeaderboardScrollLabel) so every app agrees.
 */
@Composable
internal fun rankedListLabel(label: PointsScrollLabel): RankedListLabel {
    val rankText = "#" + NumberFormat.getIntegerInstance().format(label.rank)
    val tierText = when (label.tier) {
        Sdk.PointsLeaderboardTierRest -> stringResource(id = R.string.leaderboard_tier_rest)
        Sdk.PointsLeaderboardTierUnknown -> ""
        else -> stringResource(id = R.string.leaderboard_tier_top, label.tierPercent.toString())
    }
    return RankedListLabel(rankText = rankText, tierText = tierText)
}
