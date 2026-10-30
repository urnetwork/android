package com.bringyour.network.ui.leaderboard

import android.content.Context
import android.text.format.DateUtils
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import com.bringyour.network.R
import com.bringyour.network.ui.components.ButtonStyle
import com.bringyour.network.ui.components.SectionLoadError
import com.bringyour.network.ui.components.SkeletonBar
import com.bringyour.network.ui.components.SkeletonGroup
import com.bringyour.network.ui.components.SkeletonLine
import com.bringyour.network.ui.components.SkeletonText
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.components.URSwitch
import com.bringyour.network.ui.components.keyboardScrollable
import com.bringyour.network.ui.components.tabletReadableColumn
import com.bringyour.network.ui.theme.Amber
import com.bringyour.network.ui.theme.BlueLight
import com.bringyour.network.ui.theme.BlueMedium
import com.bringyour.network.ui.theme.Green300
import com.bringyour.network.ui.theme.Green500
import com.bringyour.network.ui.theme.HeadingLargeCondensed
import com.bringyour.network.ui.theme.MainBorderBase
import com.bringyour.network.ui.theme.MainTintedBackgroundBase
import com.bringyour.network.ui.theme.TextFaint
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.sdk.Sdk
import kotlinx.coroutines.flow.distinctUntilChanged
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The Protocol tab of the leaderboard (apple/LEADERTOP.md), the first tab and
 * the default: whether claiming a Top 200 miner UID on SN25 would pay, for a
 * network that has not claimed yet.
 *
 * At compact width one list holds, top to bottom, the banners, the caller's
 * own-status card, the explainer and the ranked top 400 around the Top 200
 * line. From medium width the list stands alone and LeaderboardScreen shows
 * the banners, card and explainer beside it ([ProtocolHeader]), as the Points
 * tab does with its own card. The rows keep one item each, so the position
 * indicator (RankedListPositionIndicator) maps items to positions; the line
 * is drawn under the last head seat's row.
 *
 * Every loading region holds a placeholder of its loaded size; a failed load
 * puts the standard retry row in the same place, and a failed refresh keeps
 * the rows under a notice. Tapping a claimed row opens its detail sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProtocolLeaderboardTab(
    snackbarHostState: SnackbarHostState,
    onOpenEarnings: () -> Unit,
    // each change scrolls the list back to its top (the tab reset)
    resetToTop: Int = 0,
    // false where the card sits beside the list (the list-detail layout); its
    // list item stays, empty, so the rows keep their place
    showHeader: Boolean = true,
    viewModel: ProtocolLeaderboardViewModel = hiltViewModel(),
) {
    val ui = viewModel.ui
    val text = rememberProtocolText()
    val listState = rememberLazyListState()
    val keyboardScope = rememberCoroutineScope()
    val refreshState = rememberPullToRefreshState()
    val ownName by viewModel.ownNetworkName.collectAsState()

    LifecycleStartEffect(viewModel) {
        viewModel.setVisible(true)
        onStopOrDispose {
            viewModel.setVisible(false)
        }
    }

    LaunchedEffect(resetToTop) {
        if (0 < resetToTop) {
            listState.animateScrollToItem(0)
        }
    }

    val genericError = stringResource(id = R.string.something_went_wrong)
    LaunchedEffect(viewModel.actionFailed) {
        if (viewModel.actionFailed) {
            snackbarHostState.showSnackbar(message = genericError, withDismissAction = true)
            viewModel.clearActionFailed()
        }
    }

    val rows = ui.rows
    // the model asks for the next page when the last visible row nears the
    // end; a landed page or a failure re-reads where the list stands
    LaunchedEffect(listState, rows.size, ui.footer) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .distinctUntilChanged()
            .collect { lastVisibleItem ->
                viewModel.onLastVisibleRow(lastVisibleItem - PROTOCOL_LIST_ROWS_OFFSET)
            }
    }

    // a released position indicator scrolls to its position once that row is loaded
    var pendingSeek by remember { mutableLongStateOf(0L) }
    LaunchedEffect(pendingSeek, rows.size, ui.hasMoreAfter, ui.footer) {
        val target = pendingSeek
        if (target <= 0L) {
            return@LaunchedEffect
        }
        val index = rows.indexOfFirst { target <= it.row.position }
        if (0 <= index) {
            pendingSeek = 0L
            listState.scrollToItem(PROTOCOL_LIST_ROWS_OFFSET + index)
        } else if (!ui.hasMoreAfter || ui.footer == ProtocolListFooter.Failed) {
            pendingSeek = 0L
        }
    }

    var detailPosition by rememberSaveable { mutableLongStateOf(0L) }
    val detailRow = rows.firstOrNull { it.row.position == detailPosition }?.row
    if (detailRow != null && detailRow.fleets.isNotEmpty()) {
        ProtocolDetailSheet(
            row = detailRow,
            text = text,
            onDismiss = { detailPosition = 0L },
        )
    }

    PullToRefreshBox(
        isRefreshing = ui.refreshing,
        state = refreshState,
        onRefresh = viewModel::refresh,
    ) {
        Box(
            modifier = Modifier.tabletReadableColumn().fillMaxSize()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(PROTOCOL_LIST_TEST_TAG)
                    // its rows take no keyboard focus: the keyboard scrolls the
                    // list, as the indicator beside it does by drag
                    .keyboardScrollable(
                        state = listState,
                        scope = keyboardScope,
                        linePx = with(LocalDensity.current) { LeaderboardKeyboardLine.toPx() },
                    )
            ) {
                item(key = "protocol-header") {
                    if (showHeader) {
                        ProtocolHeader(
                            ui = ui,
                            text = text,
                            ownName = ownName,
                            onNamePublic = viewModel::setNamePublic,
                            settingNamePublic = viewModel.settingNamePublic,
                            onOpenEarnings = onOpenEarnings,
                            onRetry = viewModel::retry,
                        )
                    }
                }

                // a failed refresh keeps the rows, under this notice
                item(key = "protocol-notice") {
                    if (ui.refreshFailed) {
                        SectionLoadError(
                            onRetry = viewModel::retry,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }

                when (ui.body) {
                    ProtocolListBody.Rows -> itemsIndexed(
                        rows,
                        key = { _, item -> item.key },
                    ) { _, item ->
                        ProtocolRowItem(
                            item = item,
                            lineScore = ui.lineScore,
                            text = text,
                            onOpenDetail = { detailPosition = item.row.position },
                        )
                    }
                    ProtocolListBody.Progress -> items(PROTOCOL_PLACEHOLDER_ROWS, key = { "protocol-placeholder-$it" }) {
                        ProtocolRowPlaceholder()
                    }
                    ProtocolListBody.LoadFailed -> item(key = "protocol-failed") {
                        SectionLoadError(
                            onRetry = viewModel::retry,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    ProtocolListBody.Empty -> item(key = "protocol-empty") {
                        Text(
                            stringResource(id = R.string.leaderboard_protocol_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                        )
                    }
                    ProtocolListBody.None -> {}
                }

                item(key = "protocol-footer") {
                    ProtocolFooter(
                        footer = ui.footer,
                        onRetry = viewModel::retry,
                    )
                }
            }

            val positions = remember(rows) { rows.map { it.row.position } }
            RankedListPositionIndicator(
                listState = listState,
                positions = positions,
                rowsOffset = PROTOCOL_LIST_ROWS_OFFSET,
                total = ui.indicatorTotal,
                hasMoreBefore = false,
                hasMoreAfter = ui.hasMoreAfter,
                label = { rank -> protocolScrollLabel(rank, ui.totalRanked) },
                onSeek = { rank ->
                    pendingSeek = rank
                    viewModel.seekTo(rank)
                },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .padding(vertical = 8.dp),
            )
        }
    }
}

/** how the tests find the ranked list; never shown or read out */
internal const val PROTOCOL_LIST_TEST_TAG = "leaderboard.protocol.list"

/** the header and the refresh notice precede the rows in the lazy list */
internal const val PROTOCOL_LIST_ROWS_OFFSET = 2

/** placeholder rows while the first page loads: about a screen of them */
private const val PROTOCOL_PLACEHOLDER_ROWS = 8

/** the height every row and placeholder row takes */
private val ProtocolRowHeight = 56.dp

/**
 * The drag label of the position indicator: the rank, and its tier among
 * every ranked network from the sdk's shared helper. Without the sdk's
 * answer (the inert sdk of the screenshot tests) the label has no tier.
 */
@Composable
private fun protocolScrollLabel(rank: Long, totalRanked: Long): RankedListLabel {
    val parts = remember(rank, totalRanked) {
        runCatching { Sdk.pointsLeaderboardScrollLabel(rank, totalRanked) }.getOrNull()
    }
    if (parts == null) {
        return RankedListLabel(rankText = "#" + NumberFormat.getIntegerInstance().format(rank), tierText = "")
    }
    return rankedListLabel(PointsScrollLabel(parts.rank, parts.total, parts.tier, parts.tierPercent))
}

/**
 * The banners, the own-status card and the explainer beside the list, from
 * medium width. The tab beside it is what shows the board, so the tab alone
 * tells the view model it is visible.
 */
@Composable
fun ProtocolHeader(
    viewModel: ProtocolLeaderboardViewModel,
    onOpenEarnings: () -> Unit,
) {
    val ownName by viewModel.ownNetworkName.collectAsState()
    ProtocolHeader(
        ui = viewModel.ui,
        text = rememberProtocolText(),
        ownName = ownName,
        onNamePublic = viewModel::setNamePublic,
        settingNamePublic = viewModel.settingNamePublic,
        onOpenEarnings = onOpenEarnings,
        onRetry = viewModel::retry,
    )
}

@Composable
private fun ProtocolHeader(
    ui: ProtocolLeaderboardUi,
    text: ProtocolText,
    ownName: String,
    onNamePublic: (Boolean) -> Unit,
    settingNamePublic: Boolean,
    onOpenEarnings: () -> Unit,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ui.banners.forEach { banner ->
            ProtocolBannerView(banner = banner, text = text)
        }

        when (val card = ui.card) {
            ProtocolCard.SignedOut -> {}
            ProtocolCard.Loading -> ProtocolCardPlaceholder()
            ProtocolCard.LoadFailed -> ProtocolCardBox {
                SectionLoadError(onRetry = onRetry)
            }
            ProtocolCard.NotRanked -> ProtocolCardBox {
                Text(
                    stringResource(id = R.string.leaderboard_protocol_not_measurable_providing),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                )
            }
            is ProtocolCard.Standing -> ProtocolStandingCard(
                standing = card.standing,
                text = text,
                ownName = ownName,
                onNamePublic = onNamePublic,
                settingNamePublic = settingNamePublic,
                onOpenEarnings = onOpenEarnings,
            )
        }

        Text(
            stringResource(id = R.string.leaderboard_protocol_explainer),
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted,
        )

        Spacer(modifier = Modifier.height(4.dp))
    }
}

/** A banner in the neutral style (LEADERTOP §4): information, not an error. */
@Composable
private fun ProtocolBannerView(
    banner: ProtocolBanner,
    text: ProtocolText,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BlueMedium.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
            .border(1.dp, BlueMedium.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = if (banner is ProtocolBanner.Stale) Icons.Outlined.Schedule else Icons.Outlined.Info,
            contentDescription = null,
            tint = BlueMedium,
            modifier = Modifier.size(18.dp),
        )
        Text(
            protocolBannerText(banner, text),
            style = MaterialTheme.typography.bodyMedium,
            color = BlueLight,
        )
    }
}

/** The card's box: the Points tab's own-stats card shape. */
@Composable
private fun ProtocolCardBox(
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MainTintedBackgroundBase, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        content()
    }
}

/** The own-status card (LEADERTOP §5). */
@Composable
private fun ProtocolStandingCard(
    standing: ProtocolStanding,
    text: ProtocolText,
    ownName: String,
    onNamePublic: (Boolean) -> Unit,
    settingNamePublic: Boolean,
    onOpenEarnings: () -> Unit,
) {
    ProtocolCardBox {
        // identity: the tag and the name as others see it, then the shared switch
        val name = when {
            standing.name.isNotEmpty() -> standing.name
            standing.namePublic == true && ownName.isNotEmpty() -> ownName
            else -> stringResource(id = R.string.anonymous)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (standing.tag.isNotEmpty()) {
                Text(standing.tag, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            }
            Text(
                name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // position: "#173", "projected", "of 41,230 ranked networks"
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.semantics(mergeDescendants = true) {},
        ) {
            Text(
                protocolPositionText(standing.position, text),
                style = HeadingLargeCondensed,
                color = Color.White,
            )
            Column {
                Text(
                    stringResource(id = R.string.leaderboard_protocol_projected),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted,
                )
                if (0L < standing.totalRanked) {
                    Text(
                        protocolOfRankedText(standing.totalRanked, text),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted,
                    )
                }
            }
        }

        // the label chip, the margin to the line, and the trend
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            standing.label?.let { ProtocolLabelChip(label = it) }
            Text(
                protocolMarginText(standing.margin, text),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
            )
            protocolTrendText(standing.trend, text)?.let { trend ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProtocolTrendArrow(trend = standing.trend)
                    Text(
                        trend,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (0L < standing.trend) BlueMedium else Amber,
                    )
                }
            }
        }

        val points = remember(standing.history) { protocolChartPoints(standing.history) }
        if (points.isNotEmpty()) {
            ProtocolProgressChart(
                points = points,
                headSeats = standing.headSeats,
                position = standing.position,
                text = text,
            )
        }

        // why this score: counts only, never IPs (Q4)
        Text(
            protocolWhyText(standing, text) + " " + protocolSoloText(standing, text),
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted,
        )

        standing.notMeasurable?.let { reason ->
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.semantics(mergeDescendants = true) {},
            ) {
                Icon(
                    imageVector = Icons.Outlined.VisibilityOff,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    stringResource(id = reason.textRes ?: R.string.leaderboard_protocol_label_not_measurable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                )
            }
        }

        standing.prompt?.let { prompt ->
            ProtocolClaimPromptView(prompt = prompt, onOpenEarnings = onOpenEarnings)
        }

        HorizontalDivider(color = MainBorderBase)

        // the one "show my name on leaderboards" switch, shared with Points (Q12)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(id = R.string.show_on_points_leaderboard),
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(6.dp))
            URSwitch(
                checked = standing.namePublic == true,
                enabled = standing.namePublic != null && !settingNamePublic,
                toggle = { onNamePublic(standing.namePublic != true) },
            )
        }
    }
}

/**
 * The claim prompt (Q5, Q13, Q16). With binding open it holds the screen's
 * one primary button; with binding closed a note says claiming opens soon and
 * nothing invites registering early. Every prompt carries the warning.
 */
@Composable
private fun ProtocolClaimPromptView(
    prompt: ProtocolClaimPrompt,
    onOpenEarnings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
            .border(1.dp, MainBorderBase, RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val inside = stringResource(id = R.string.leaderboard_protocol_claim_inside)
        if (prompt.bindingOpen) {
            Text(
                inside,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            URButton(
                onClick = onOpenEarnings,
                style = ButtonStyle.PRIMARY,
            ) { buttonTextStyle ->
                Text(stringResource(id = R.string.leaderboard_protocol_claim_button), style = buttonTextStyle)
            }
        } else {
            Text(
                inside + " " + stringResource(id = R.string.leaderboard_protocol_claim_soon),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
        if (prompt.borderline) {
            Text(
                stringResource(id = R.string.leaderboard_protocol_claim_borderline),
                style = MaterialTheme.typography.bodySmall,
                color = Amber,
            )
        }
        Text(
            stringResource(id = R.string.leaderboard_protocol_claim_warning),
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
        )
    }
}

/** The label chip (Q13, Q17); it reads as its text. */
@Composable
internal fun ProtocolLabelChip(
    label: ProtocolLabel,
    modifier: Modifier = Modifier,
) {
    val (fill, edge, ink) = when (label) {
        ProtocolLabel.ESTIMATE -> Triple(Color.Transparent, TextMuted, Color.White)
        ProtocolLabel.LIKELY -> Triple(Green500.copy(alpha = 0.16f), Green500.copy(alpha = 0.5f), Green300)
        ProtocolLabel.BORDERLINE -> Triple(Amber.copy(alpha = 0.16f), Amber.copy(alpha = 0.5f), Amber)
        ProtocolLabel.UNLIKELY -> Triple(MainBorderBase, TextFaint, TextMuted)
        ProtocolLabel.NOT_MEASURABLE -> Triple(Color.Transparent, TextFaint, TextMuted)
    }
    Box(
        modifier = modifier
            .background(fill, RoundedCornerShape(50))
            .border(1.dp, edge, RoundedCornerShape(50))
            .padding(horizontal = 9.dp, vertical = 2.dp)
    ) {
        Text(
            stringResource(id = label.textRes),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = ink,
            maxLines = 1,
        )
    }
}

/**
 * The trend's direction as a small triangle, up in the accent and down in
 * amber; nothing when the position held. Decoration: the row and the card
 * say the trend in words.
 */
@Composable
private fun ProtocolTrendArrow(
    trend: Long,
    modifier: Modifier = Modifier,
) {
    if (trend == 0L) {
        return
    }
    val up = 0L < trend
    val color = if (up) BlueMedium else Amber
    Canvas(modifier = modifier.size(16.dp)) {
        val halfWidth = size.width * 0.25f
        val halfHeight = size.height * 0.18f
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val tip = if (up) centerY - halfHeight else centerY + halfHeight
        val base = if (up) centerY + halfHeight else centerY - halfHeight
        val triangle = Path().apply {
            moveTo(centerX - halfWidth, base)
            lineTo(centerX + halfWidth, base)
            lineTo(centerX, tip)
            close()
        }
        drawPath(path = triangle, color = color)
    }
}

/**
 * The 90-day progress chart (Q11): the network's daily projected rank as a
 * line, rank 1 at the top so up means better, with the Top 200 line drawn
 * across. A tap or a drag shows that day's rank. TalkBack reads the summary.
 */
@Composable
private fun ProtocolProgressChart(
    points: List<ProtocolHistoryPoint>,
    headSeats: Long,
    position: Long,
    text: ProtocolText,
) {
    val scale = remember(points, headSeats) { protocolChartScale(points, headSeats) }
    var selected by remember(points) { mutableIntStateOf(-1) }
    val summary = protocolChartSpoken(position, text)
    val lineLabel = stringResource(id = R.string.leaderboard_protocol_chart_line)
    val accent = BlueMedium

    Column(
        modifier = Modifier.clearAndSetSemantics { contentDescription = summary },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(id = R.string.leaderboard_protocol_chart_title),
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                modifier = Modifier.weight(1f),
            )
            val day = points.getOrNull(selected)
            if (day != null) {
                Text(
                    protocolPositionText(day.rank, text) + " · " + protocolDayText(day.day),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            } else {
                Text(
                    lineLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = accent,
                )
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
                .pointerInput(points) {
                    val pick = { x: Float ->
                        selected = protocolChartIndexAt(x, size.width.toFloat(), points.size)
                    }
                    detectTapGestures(onPress = { offset -> pick(offset.x) })
                }
                // sideways only: a vertical drag that starts here still scrolls the list
                .pointerInput(points) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            selected = protocolChartIndexAt(offset.x, size.width.toFloat(), points.size)
                        },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            selected = protocolChartIndexAt(change.position.x, size.width.toFloat(), points.size)
                        },
                    )
                }
        ) {
            val inset = 6.dp.toPx()
            val height = size.height - inset * 2
            fun yOf(rank: Double) = inset + scale.fraction(rank) * height
            fun xOf(index: Int) = if (points.size <= 1) size.width else index * size.width / (points.size - 1)

            if (0L < headSeats) {
                val y = yOf(headSeats.toDouble())
                drawLine(
                    color = accent.copy(alpha = 0.7f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
                )
            }

            val path = Path()
            points.forEachIndexed { index, point ->
                val x = xOf(index)
                val y = yOf(point.rank.toDouble())
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                color = Color.White,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
            val last = points.lastIndex
            drawCircle(color = accent, radius = 3.5.dp.toPx(), center = Offset(xOf(last), yOf(points[last].rank.toDouble())))

            val chosen = selected
            if (chosen in points.indices) {
                val x = xOf(chosen)
                drawLine(color = TextMuted, start = Offset(x, 0f), end = Offset(x, size.height), strokeWidth = 1.dp.toPx())
                drawCircle(color = Color.White, radius = 4.dp.toPx(), center = Offset(x, yOf(points[chosen].rank.toDouble())))
            }
        }
    }
}

/** The day of a history point the touch is over: the nearest along the chart's width. */
internal fun protocolChartIndexAt(x: Float, width: Float, count: Int): Int {
    if (count <= 0) {
        return -1
    }
    if (count == 1 || width <= 0f) {
        return count - 1
    }
    return Math.round(x.coerceIn(0f, width) / width * (count - 1)).coerceIn(0, count - 1)
}

/** A history day ("2026-10-28") in the locale's medium date; the raw day if it does not parse. */
private fun protocolDayText(day: String): String = runCatching {
    LocalDate.parse(day).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
}.getOrDefault(day)

/**
 * One ranked network: its position, tag and name (or "Anonymous", or the UID
 * of an anonymous claimed network), the claimed badge, the projected score
 * with its trend arrow, and the label chip on the caller's own row only.
 * Rows inside the head seats carry a faint accent tint, the caller's row is
 * highlighted, an unmeasurable row is dimmed with an icon, and the Top 200
 * line is drawn under the last head seat. A claimed row opens its detail.
 */
@Composable
private fun ProtocolRowItem(
    item: ProtocolListRow,
    lineScore: Double,
    text: ProtocolText,
    onOpenDetail: () -> Unit,
) {
    val row = item.row
    val claimed = row.fleets.isNotEmpty()
    val spoken = protocolRowSpoken(row, text)
    val nameText = protocolRowNameText(protocolRowName(row), text)
    val ink = if (row.isMe) Green500 else Color.White
    Column {
        HorizontalDivider(color = MainBorderBase)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    when {
                        row.isMe -> BlueMedium.copy(alpha = 0.18f)
                        item.aboveLine -> BlueMedium.copy(alpha = 0.06f)
                        else -> Color.Transparent
                    }
                )
                .then(if (claimed) Modifier.clickable(onClick = onOpenDetail) else Modifier)
                .heightIn(min = ProtocolRowHeight)
                .padding(start = 16.dp, end = 32.dp)
                .clearAndSetSemantics { contentDescription = spoken }
                .alpha(if (row.measurable) 1f else 0.5f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                protocolPositionText(row.position, text),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (row.isMe) FontWeight.ExtraBold else FontWeight.Normal,
                color = if (row.isMe) Green500 else TextMuted,
                modifier = Modifier.width(48.dp),
                maxLines = 1,
            )
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (row.tag.isNotEmpty()) {
                    Text(row.tag, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                }
                Text(
                    nameText,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (row.isMe) FontWeight.ExtraBold else FontWeight.Normal,
                    color = if (row.name.isEmpty() && !row.isMe) TextMuted else ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (row.isMe) {
                    row.label?.let { ProtocolLabelChip(label = it) }
                }
            }
            protocolBadgeUid(row)?.let { uid ->
                Box(
                    modifier = Modifier
                        .background(BlueMedium.copy(alpha = 0.22f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        protocolUidText(uid, text),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = BlueLight,
                        maxLines = 1,
                    )
                }
            }
            if (!row.measurable) {
                Icon(
                    imageVector = Icons.Outlined.VisibilityOff,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(16.dp),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProtocolTrendArrow(trend = row.trend, modifier = Modifier.size(16.dp))
                Text(
                    text.score(row.projectedScore),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (row.isMe) FontWeight.ExtraBold else FontWeight.Normal,
                    color = ink,
                    textAlign = TextAlign.End,
                    modifier = Modifier.widthIn56(),
                    maxLines = 1,
                )
            }
        }
        if (item.lineAfter) {
            ProtocolLine(score = lineScore, text = text)
        }
    }
}

private fun Modifier.widthIn56(): Modifier = this.then(Modifier.width(56.dp))

/** The Top 200 line between the last head seat and the next (LEADERTOP §6); TalkBack reads it as a heading. */
@Composable
private fun ProtocolLine(
    score: Double,
    text: ProtocolText,
) {
    val spoken = protocolLineSpoken(score, text)
    val accent = BlueMedium
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .padding(start = 16.dp, end = 32.dp)
            .clearAndSetSemantics {
                contentDescription = spoken
                heading()
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Canvas(modifier = Modifier.weight(1f).height(2.dp)) {
            drawLine(
                color = accent,
                start = Offset(0f, size.height / 2),
                end = Offset(size.width, size.height / 2),
                strokeWidth = size.height,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
            )
        }
        Text(
            protocolLineText(score, text),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = accent,
            maxLines = 1,
        )
        Canvas(modifier = Modifier.width(12.dp).height(2.dp)) {
            drawLine(
                color = accent,
                start = Offset(0f, size.height / 2),
                end = Offset(size.width, size.height / 2),
                strokeWidth = size.height,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
            )
        }
    }
}

@Composable
private fun ProtocolFooter(
    footer: ProtocolListFooter,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (footer) {
            ProtocolListFooter.Loading -> CircularProgressIndicator(modifier = Modifier.size(24.dp))
            ProtocolListFooter.Failed -> SectionLoadError(onRetry = onRetry)
            ProtocolListFooter.None -> Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

/** A row's placeholder, the size of a loaded row. */
@Composable
private fun ProtocolRowPlaceholder() {
    SkeletonGroup {
        HorizontalDivider(color = MainBorderBase)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(ProtocolRowHeight)
                .padding(start = 16.dp, end = 32.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SkeletonBar(width = 36.dp, height = 14.dp)
            Spacer(modifier = Modifier.width(12.dp))
            SkeletonBar(width = 140.dp, height = 14.dp)
            Spacer(modifier = Modifier.weight(1f))
            SkeletonBar(width = 44.dp, height = 14.dp)
        }
    }
}

/** The card's placeholder, laid out like a loaded card with its chart. */
@Composable
private fun ProtocolCardPlaceholder() {
    SkeletonGroup {
        ProtocolCardBox {
            SkeletonText(sample = "kayak-hollow", style = MaterialTheme.typography.bodyLarge)
            SkeletonText(sample = "#000", style = HeadingLargeCondensed)
            SkeletonText(sample = "00% above the Top 200 line", style = MaterialTheme.typography.bodyMedium)
            SkeletonLine(height = 112.dp, cornerRadius = 8.dp)
            SkeletonText(sample = "00 distinct IP prefixes, 0 shared with other networks.", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/**
 * The detail sheet of a claimed row (LEADERTOP §6): for each of its fleets
 * the UID, whether the validator selected it this tempo, and the facts of
 * [protocolDetailFacts], then the note that on-chain incentive lags.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProtocolDetailSheet(
    row: ProtocolRow,
    text: ProtocolText,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MainTintedBackgroundBase,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val name = protocolRowName(row)
            val subtitle = when (name) {
                is ProtocolRowName.Named -> name.name
                else -> stringResource(id = R.string.anonymous)
            } + " · " + stringResource(id = R.string.leaderboard_protocol_detail_claimed)
            row.fleets.sortedBy { it.uid }.forEach { fleet ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            protocolUidText(fleet.uid, text),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.semantics { heading() },
                        )
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted,
                        )
                    }
                    fleet.selected?.let { selected ->
                        ProtocolSelectedChip(selected = selected)
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MainBorderBase, RoundedCornerShape(12.dp))
                ) {
                    protocolDetailFacts(row, fleet, text).forEachIndexed { index, fact ->
                        if (0 < index) {
                            HorizontalDivider(color = MainBorderBase)
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                                .semantics(mergeDescendants = true) {},
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                fact.label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextMuted,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                fact.value,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                textAlign = TextAlign.End,
                            )
                        }
                    }
                }
            }
            if (row.fleets.any { it.incentive != null }) {
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.semantics(mergeDescendants = true) {},
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        tint = Amber,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        stringResource(id = R.string.leaderboard_protocol_detail_incentive_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                    )
                }
            }
            URButton(
                onClick = onDismiss,
                style = ButtonStyle.OUTLINE,
                modifier = Modifier.fillMaxWidth(),
            ) { buttonTextStyle ->
                Text(stringResource(id = R.string.close), style = buttonTextStyle)
            }
        }
    }
}

/** "Selected" or "Not selected" this tempo, from the validator's head summary (Q9). */
@Composable
internal fun ProtocolSelectedChip(
    selected: Boolean,
) {
    Box(
        modifier = Modifier
            .background(
                if (selected) Green500.copy(alpha = 0.16f) else MainBorderBase,
                RoundedCornerShape(50),
            )
            .border(1.dp, if (selected) Green500.copy(alpha = 0.5f) else TextFaint, RoundedCornerShape(50))
            .padding(horizontal = 9.dp, vertical = 2.dp)
    ) {
        Text(
            stringResource(
                id = if (selected) R.string.leaderboard_protocol_detail_selected else R.string.leaderboard_protocol_detail_not_selected
            ),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (selected) Green300 else TextMuted,
            maxLines = 1,
        )
    }
}

/** ProtocolText on the platform: Context strings, NumberFormat and DateUtils. */
@Composable
internal fun rememberProtocolText(): ProtocolText {
    val context = LocalContext.current
    return remember(context) { ContextProtocolText(context) }
}

private class ContextProtocolText(
    private val context: Context,
) : ProtocolText {
    // the numbers in the locale the catalog strings come from
    private val numbers = ProtocolNumberFormat(context.resources.configuration.locales[0])

    override fun string(@StringRes id: Int, vararg args: Any): String = context.getString(id, *args)

    override fun count(value: Long): String = numbers.count(value)

    override fun score(value: Double): String = numbers.score(value)

    override fun percent(fraction: Double): String = numbers.percent(fraction)

    override fun signedPercent(fraction: Double): String = numbers.signedPercent(fraction)

    override fun share(fraction: Double): String = numbers.share(fraction)

    override fun relativeTime(timeMillis: Long): String = DateUtils.getRelativeTimeSpanString(
        timeMillis,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
    ).toString()
}
