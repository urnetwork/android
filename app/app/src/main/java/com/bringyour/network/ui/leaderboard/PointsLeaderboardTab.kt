package com.bringyour.network.ui.leaderboard

import com.bringyour.network.ui.components.tabletReadableColumn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bringyour.network.R
import com.bringyour.network.ui.components.URSwitch
import com.bringyour.network.ui.components.keyboardScrollable
import com.bringyour.network.ui.indexedLazyListKey
import com.bringyour.network.ui.theme.Green500
import com.bringyour.network.ui.theme.HeadingLargeCondensed
import com.bringyour.network.ui.theme.MainBorderBase
import com.bringyour.network.ui.theme.MainTintedBackgroundBase
import com.bringyour.network.ui.theme.TextFaint
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.sdk.Sdk
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * The Points tab of the leaderboard (android/POINTSLEADERBOARD.md): the
 * network's own stats and ranks, the opt-in switch and the emoji tag editor
 * in a header card, sort chips, and the infinitely scrolling ranked list.
 * Rows, ranks and pages all come from the sdk view controller through the
 * view model; nothing here sorts, ranks or pages.
 *
 * The list carries the draggable position indicator of mmm/DESIGNSTYLE.md
 * ("Long ranked lists", RankedListPositionIndicator): a track spanning
 * positions 1..N of the whole ranked population whose thumb marks the
 * position at the top of the screen; a release seeks the controller to the
 * rank under the thumb, after which the list pages backward as well as
 * forward from that window.
 *
 * `resetToTop` counts the taps on the Points tab (including re-taps): each
 * change scrolls the list back to the top, reloading the first page when the
 * loaded window no longer starts at position 1.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PointsLeaderboardTab(
    snackbarHostState: SnackbarHostState,
    resetToTop: Int = 0,
    // false where the own-stats card sits beside the list (the leaderboard's
    // list-detail layout); its list item stays, empty, so the rows keep their place
    showHeader: Boolean = true,
    // the emoji tag editor, kept by the leaderboard so the card beside the
    // list can open it too
    showEmojiSheet: Boolean,
    setShowEmojiSheet: (Boolean) -> Unit,
    viewModel: PointsLeaderboardViewModel = hiltViewModel(),
) {
    val rows by viewModel.rows.collectAsState()
    val listState = rememberLazyListState()
    val keyboardScope = rememberCoroutineScope()

    // a tab tap scrolls up; after a seek the window is reloaded from the top
    // first so the top of the list is rank 1 again
    LaunchedEffect(resetToTop) {
        if (resetToTop <= 0) {
            return@LaunchedEffect
        }
        when (LeaderboardTabReset.onTabTap(isPointsTab = true, firstLoadedPosition = viewModel.firstLoadedPosition)) {
            LeaderboardTabReset.Action.RELOAD_FROM_TOP -> {
                viewModel.reloadFromTop()
                listState.scrollToItem(0)
            }
            LeaderboardTabReset.Action.SCROLL_TO_TOP -> {
                listState.animateScrollToItem(0)
            }
        }
    }
    val refreshState = rememberPullToRefreshState()
    var refreshing by remember { mutableStateOf(false) }
    var emojiSaveError by remember { mutableStateOf<String?>(null) }
    // each opening of the editor starts without the last save's error
    LaunchedEffect(showEmojiSheet) {
        if (showEmojiSheet) {
            emojiSaveError = null
        }
    }

    val ownNetworkId = viewModel.me?.row?.networkId
    // the own-stats card always shows the caller's own name: the me row's, or
    // the jwt's until me lands; the list row is what everyone else sees
    val jwtNetworkName by viewModel.ownNetworkName.collectAsState()
    val ownName = viewModel.me?.row?.displayName?.takeIf { it.isNotEmpty() } ?: jwtNetworkName

    // the pull indicator follows the controller's loading flag only for a
    // refresh the user asked for; page loads show in the footer instead
    LaunchedEffect(viewModel.isLoading) {
        if (!viewModel.isLoading) {
            refreshing = false
        }
    }

    // ask for the next page when the last visible row is within reach of the
    // end. item 0 is the header, 1 the chips; rows start at item 2
    val hasError = viewModel.errorMessage.isNotEmpty()
    LaunchedEffect(listState, rows.size, viewModel.isLoading, viewModel.isEndReached, hasError) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .distinctUntilChanged()
            .collect { lastVisibleItem ->
                val lastVisibleRow = lastVisibleItem - POINTS_LIST_ROWS_OFFSET
                if (
                    PointsLeaderboardPaging.shouldLoadMore(
                        lastVisibleRowIndex = lastVisibleRow,
                        rowCount = rows.size,
                        isLoading = viewModel.isLoading,
                        isEndReached = viewModel.isEndReached,
                        hasError = hasError,
                    )
                ) {
                    viewModel.loadMore()
                }
            }
    }

    // after a seek, ask for the page before the window when the first rows
    // come into reach. Compose keeps the first visible row anchored by its
    // position key when rows are prepended; only when the header above the
    // rows is on screen would the prepended rows push the old first row down,
    // so that case re-anchors the row explicitly at its last known offset.
    val hasMoreBefore = viewModel.hasMoreBefore
    var anchorRowIndex by remember { mutableIntStateOf(-1) }
    var anchorRowOffset by remember { mutableIntStateOf(0) }
    var headerVisible by remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo }
            .collect { visible ->
                headerVisible = (visible.firstOrNull()?.index ?: 0) < POINTS_LIST_ROWS_OFFSET
                val firstRow = visible.firstOrNull { it.index >= POINTS_LIST_ROWS_OFFSET }
                anchorRowIndex = firstRow?.index ?: -1
                anchorRowOffset = firstRow?.offset ?: 0
            }
    }
    LaunchedEffect(listState, rows.size, viewModel.isLoading, hasMoreBefore, hasError) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: -1 }
            .distinctUntilChanged()
            .collect { firstVisibleItem ->
                if (firstVisibleItem < 0) {
                    return@collect
                }
                if (
                    PointsLeaderboardPaging.shouldLoadMoreBefore(
                        firstVisibleRowIndex = firstVisibleItem - POINTS_LIST_ROWS_OFFSET,
                        rowCount = rows.size,
                        isLoading = viewModel.isLoading,
                        hasMoreBefore = hasMoreBefore,
                        hasError = hasError,
                    )
                ) {
                    viewModel.loadMoreBefore()
                }
            }
    }
    val firstPosition = rows.firstOrNull()?.position ?: 0L
    var previousFirstPosition by remember { mutableLongStateOf(0L) }
    // the rank a released thumb asked for; cleared once its page has landed
    // and the list sits at that row
    var pendingSeekRank by remember { mutableLongStateOf(0L) }
    LaunchedEffect(firstPosition, rows.size) {
        val previous = previousFirstPosition
        previousFirstPosition = firstPosition
        val seekRank = pendingSeekRank
        if (seekRank > 0L && rows.isNotEmpty()) {
            val index = rows.indexOfFirst { it.position >= seekRank }
            if (index >= 0 && rows.first().position <= seekRank) {
                pendingSeekRank = 0L
                listState.scrollToItem(POINTS_LIST_ROWS_OFFSET + index)
                return@LaunchedEffect
            }
        }
        if (previous > 0L && firstPosition in 1L until previous && headerVisible && anchorRowIndex >= 0) {
            val prepended = (previous - firstPosition).toInt().coerceAtMost(rows.size)
            listState.scrollToItem(anchorRowIndex + prepended, -anchorRowOffset)
        }
    }

    LaunchedEffect(viewModel.actionError) {
        val message = viewModel.actionError ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(
            message = message,
            withDismissAction = true,
        )
        viewModel.clearActionError()
    }

    if (showEmojiSheet) {
        EmojiTagSheet(
            currentTag = viewModel.emojiTag,
            isSaving = viewModel.isSavingEmojiTag,
            saveError = emojiSaveError,
            onSave = { tag ->
                emojiSaveError = null
                viewModel.saveEmojiTag(tag) { error ->
                    if (error == null) {
                        setShowEmojiSheet(false)
                    } else {
                        emojiSaveError = error
                    }
                }
            },
            onClear = {
                emojiSaveError = null
                viewModel.saveEmojiTag("") { error ->
                    if (error == null) {
                        setShowEmojiSheet(false)
                    } else {
                        emojiSaveError = error
                    }
                }
            },
            onDismiss = {
                emojiSaveError = null
                setShowEmojiSheet(false)
            },
        )
    }

    PullToRefreshBox(
        isRefreshing = refreshing,
        state = refreshState,
        onRefresh = {
            refreshing = true
            viewModel.refresh()
        },
    ) {
        Box(
            modifier = Modifier.tabletReadableColumn().fillMaxSize()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    // its rows take no focus: the keyboard scrolls the list
                    // itself, as the indicator beside it does by drag
                    .keyboardScrollable(
                        state = listState,
                        scope = keyboardScope,
                        linePx = with(LocalDensity.current) { LeaderboardKeyboardLine.toPx() },
                    )
            ) {
                item(key = "points-header") {
                    if (showHeader) {
                        PointsHeader(
                            viewModel = viewModel,
                            ownName = ownName,
                            onEditEmoji = {
                                emojiSaveError = null
                                setShowEmojiSheet(true)
                            },
                        )
                    }
                }

                item(key = "points-sort") {
                    PointsSortChips(
                        sort = viewModel.sort,
                        setSort = viewModel::selectSort,
                    )
                }

                // keyed by the row's position in the total order (no ties),
                // which is what keeps the first visible row anchored when a
                // page is prepended. A server without positions (every row
                // 0) falls back to the index key; it has no seek either
                itemsIndexed(
                    rows,
                    key = { index, row ->
                        if (row.position > 0L) row.position else indexedLazyListKey("points", index, row.networkId)
                    },
                ) { _, row ->
                    Column {
                        HorizontalDivider()
                        PointsRow(
                            row = row,
                            sort = viewModel.sort,
                            isNetworkRow = ownNetworkId != null && ownNetworkId == row.networkId,
                        )
                    }
                }

                item(key = "points-footer") {
                    PointsFooter(
                        rowCount = rows.size,
                        isLoading = viewModel.isLoading,
                        hasLoaded = viewModel.hasLoaded,
                        errorMessage = viewModel.errorMessage,
                        retry = viewModel::retry,
                    )
                }
            }

            val positions = remember(rows) { rows.map { it.position } }
            RankedListPositionIndicator(
                listState = listState,
                positions = positions,
                rowsOffset = POINTS_LIST_ROWS_OFFSET,
                total = viewModel.totalRanked,
                hasMoreBefore = hasMoreBefore,
                hasMoreAfter = viewModel.hasMoreAfter,
                label = { rank -> rankedListLabel(viewModel.scrollLabel(rank)) },
                onSeek = { rank ->
                    pendingSeekRank = rank
                    viewModel.seekToRank(rank)
                },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .padding(vertical = 8.dp),
            )
        }
    }
}

/** header + sort chips precede the rows in the lazy list */
private const val POINTS_LIST_ROWS_OFFSET = 2

@Composable
internal fun PointsHeader(
    viewModel: PointsLeaderboardViewModel,
    ownName: String,
    onEditEmoji: () -> Unit,
) {
    val me = viewModel.me
    val ownRow = me?.row
    val emojiTag = viewModel.emojiTag

    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
    ) {
        Box(
            modifier = Modifier
                .background(
                    color = MainTintedBackgroundBase,
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {

                // identity: the network's own name and the pencil that opens
                // the editor on the first line, the emoji tag on its own line
                // below the name, then the ranked count on the line after that
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        ownName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    IconButton(onClick = onEditEmoji) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = stringResource(
                                id = if (emojiTag.isEmpty()) R.string.add_emoji else R.string.edit_emoji
                            ),
                            tint = TextMuted
                        )
                    }
                }
                if (emojiTag.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        emojiTag,
                        fontSize = 28.sp,
                        maxLines = 1,
                    )
                }
                if (viewModel.totalRanked > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        stringResource(
                            id = R.string.ranked_networks_count,
                            Sdk.formatPoints(viewModel.totalRanked.toDouble())
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // the three dimensions, each with its own rank
                Row(modifier = Modifier.fillMaxWidth()) {
                    PointsStatTile(
                        label = stringResource(id = R.string.points),
                        value = ownRow?.totalPointsText ?: "-",
                        rank = ownRow?.rankPointsText ?: "-",
                        emphasized = viewModel.sort == Sdk.PointsLeaderboardSortPoints,
                        modifier = Modifier.weight(1f)
                    )
                    PointsStatTile(
                        label = stringResource(id = R.string.blocks),
                        value = ownRow?.blocksWithPointsText ?: "-",
                        rank = ownRow?.rankBlocksText ?: "-",
                        emphasized = viewModel.sort == Sdk.PointsLeaderboardSortBlocks,
                        modifier = Modifier.weight(1f)
                    )
                    PointsStatTile(
                        label = stringResource(id = R.string.streak),
                        value = ownRow?.streakText ?: "-",
                        rank = ownRow?.rankStreakText ?: "-",
                        emphasized = viewModel.sort == Sdk.PointsLeaderboardSortStreak,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (ownRow != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "${stringResource(id = R.string.longest_streak)}: ${ownRow.longestStreakText}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                HorizontalDivider(color = MainBorderBase)

                // the switch's 48 dp touch target fills the rest
                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(id = R.string.show_on_points_leaderboard),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )

                    // the switch's touch target adds 6 dp of its own
                    Spacer(modifier = Modifier.width(6.dp))

                    URSwitch(
                        checked = viewModel.isPointsPublic,
                        enabled = !viewModel.isSettingPublic,
                        toggle = viewModel::togglePointsPublic,
                    )
                }

                if (!viewModel.isPointsPublic) {
                    Text(
                        stringResource(id = R.string.points_leaderboard_private_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            stringResource(id = R.string.points_leaderboard_description),
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun PointsStatTile(
    label: String,
    value: String,
    rank: String,
    emphasized: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted
        )
        Text(
            value,
            style = HeadingLargeCondensed,
            color = Color.White,
            maxLines = 1,
        )
        Box(
            modifier = Modifier
                .background(
                    color = if (emphasized) Green500.copy(alpha = 0.18f) else MainBorderBase,
                    shape = RoundedCornerShape(6.dp)
                )
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(
                rank,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = if (emphasized) Green500 else TextMuted
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PointsSortChips(
    sort: String,
    setSort: (String) -> Unit,
) {
    val options = listOf(
        Sdk.PointsLeaderboardSortPoints to stringResource(id = R.string.points),
        Sdk.PointsLeaderboardSortBlocks to stringResource(id = R.string.blocks),
        Sdk.PointsLeaderboardSortStreak to stringResource(id = R.string.streak),
    )

    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp)
    ) {
        options.forEachIndexed { index, (id, label) ->
            SegmentedButton(
                shape = SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = options.size
                ),
                onClick = { setSort(id) },
                selected = sort == id,
                label = {
                    Text(label, maxLines = 1)
                }
            )
        }
    }
}

@Composable
private fun PointsRow(
    row: PointsLeaderboardRowUi,
    sort: String,
    isNetworkRow: Boolean,
) {
    val rank = when (sort) {
        Sdk.PointsLeaderboardSortBlocks -> row.rankBlocksText
        Sdk.PointsLeaderboardSortStreak -> row.rankStreakText
        else -> row.rankPointsText
    }
    // an anonymous row reads "Anonymous" to everyone, its owner included: the
    // caller sees the list as everyone sees it, and only the highlight (keyed
    // on the network id, never the name) marks the own row
    val name = if (row.anonymous || row.displayName.isEmpty()) {
        stringResource(id = R.string.anonymous)
    } else {
        row.displayName
    }

    val nameColor = when {
        isNetworkRow -> Green500
        row.anonymous -> TextMuted
        else -> Color.White
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            rank,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isNetworkRow) FontWeight.ExtraBold else FontWeight.Normal,
            color = if (isNetworkRow) Green500 else TextMuted,
            modifier = Modifier.width(56.dp),
            textAlign = TextAlign.Start,
            maxLines = 1,
        )

        // identity cell: the name on the first line and the emoji tag on its
        // own line below it, like the own-stats header, so a long tag never
        // squeezes the name on narrow screens; rows without a tag show just
        // the name, centered
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isNetworkRow) FontWeight.ExtraBold else FontWeight.Normal,
                color = nameColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (row.emojiTag.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    row.emojiTag,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // the three values, the sorted one emphasized
        PointsRowValue(
            value = row.totalPointsText,
            emphasized = sort == Sdk.PointsLeaderboardSortPoints,
            isNetworkRow = isNetworkRow,
            width = 72.dp,
        )
        PointsRowValue(
            value = row.blocksWithPointsText,
            emphasized = sort == Sdk.PointsLeaderboardSortBlocks,
            isNetworkRow = isNetworkRow,
            width = 40.dp,
        )
        PointsRowValue(
            value = row.streakText,
            emphasized = sort == Sdk.PointsLeaderboardSortStreak,
            isNetworkRow = isNetworkRow,
            width = 40.dp,
        )
    }
}

@Composable
private fun PointsRowValue(
    value: String,
    emphasized: Boolean,
    isNetworkRow: Boolean,
    width: androidx.compose.ui.unit.Dp,
) {
    Text(
        value,
        style = if (emphasized) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodySmall,
        fontWeight = if (isNetworkRow || emphasized) FontWeight.ExtraBold else FontWeight.Normal,
        color = when {
            isNetworkRow -> Green500
            emphasized -> Color.White
            else -> TextFaint
        },
        modifier = Modifier.width(width),
        textAlign = TextAlign.End,
        maxLines = 1,
    )
}

@Composable
private fun PointsFooter(
    rowCount: Int,
    isLoading: Boolean,
    hasLoaded: Boolean,
    errorMessage: String,
    retry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when {
            isLoading -> {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
            errorMessage.isNotEmpty() -> {
                Text(
                    errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted,
                    textAlign = TextAlign.Center
                )
                TextButton(onClick = retry) {
                    Text(stringResource(id = R.string.try_again))
                }
            }
            rowCount == 0 && hasLoaded -> {
                Text(
                    stringResource(id = R.string.points_leaderboard_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted,
                    textAlign = TextAlign.Center
                )
            }
            else -> {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}
