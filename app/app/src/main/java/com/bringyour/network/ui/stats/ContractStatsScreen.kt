package com.bringyour.network.ui.stats

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.bringyour.network.R
import com.bringyour.network.ui.indexedLazyListKey
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.BlueMedium
import com.bringyour.network.ui.theme.Green
import com.bringyour.network.ui.theme.Pink
import com.bringyour.network.ui.theme.TextFaint
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.TopBarTitleTextStyle
import com.bringyour.network.utils.formatByteCountCompact
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

// tetris timings, mirroring the shared design: a leaver slides off over the
// slide window while holding its slot open, then one settle transaction drops the
// leavers, admits arrivals at the top, and the stack falls into the gap
private const val SLIDE_MILLIS = 400
private const val SETTLE_MILLIS = 500
// the inner disc grows/drains smoothly, matching the transfer-chart transition
private const val DISC_MILLIS = 500

// the fixed block each contract occupies; circles center in it, so the stack
// falls in uniform increments
private const val CIRCLE_SLOT_DP = 56f
private const val MIN_DIAMETER_DP = 16f
// a stream contract gets a second concentric ring this much bigger in diameter than
// the main outer ring (a ~2dp gap on each side), so streams read as a double ring
// vs a single ring for direct contracts
private const val STREAM_RING_GAP_DP = 4f

/**
 * Live contract details: a scrollable list, one row per peer client. Each row
 * shows every contract separately -- no pairing or aggregation -- as two
 * independent stacks: send contracts (green) and receive contracts (pink),
 * newest on top, laid out as four columns mirrored around the row center
 * (send stats | send circles | receive circles | receive stats). Each circle is
 * one contract, sized relative to the largest contract in its stack, with its
 * inner disc growing as the contract is used. Removed contracts slide off to the
 * side and the stack falls down into the space; new contracts drop in at the top.
 *
 * The ordering is owned by the shared SDK view controller: at the top it floats
 * rows with recent activity above idle ones and merges newly arrived rows;
 * scrolled away it freezes membership+order so rows under the reader don't shift
 * and new rows collect behind a "N new" chip. The screen just reports its scroll
 * position (setAtTop) and renders the ordered rows the view model publishes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContractStatsScreen(
    navController: NavController,
    provider: Boolean,
    contractStatsViewModel: ContractStatsViewModel = hiltViewModel(),
) {

    LaunchedEffect(Unit) {
        contractStatsViewModel.start(provider)
    }

    // the view controller owns the ordering (the at-top active-above-idle sort and
    // the scrolled-away freeze) and the "N new" pending count; the screen renders
    // the already-ordered rows it hands back and just reports its scroll position.
    val rows = contractStatsViewModel.rows
    val pendingCount = contractStatsViewModel.pendingCount
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // whether the list is at the very top. At the top the view controller floats
    // active rows above idle ones and merges new rows; scrolled away it freezes
    // membership+order and collects new rows behind the "N new" chip.
    //
    // This tracks the user's INTENT, not the raw first-visible index: when rows
    // merge in at the front, Compose anchors to the first visible item's key and
    // bumps firstVisibleItemIndex to 1, so a raw index==0 check would read false
    // exactly when we need to stay pinned. We therefore only update it from a
    // user-driven scroll (isScrollInProgress) and re-pin to 0 (below) when the
    // front changes -- a prepend never flips it off the top.
    var atTop by remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        snapshotFlow {
            Triple(
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset,
                listState.isScrollInProgress
            )
        }.collect { (index, offset, scrolling) ->
            if (scrolling) {
                atTop = index == 0 && offset == 0
            }
        }
    }

    // report the scroll position to the view controller (fires on mount and on
    // every at-top change as the list scrolls)
    LaunchedEffect(atTop) {
        contractStatsViewModel.setAtTop(atTop)
    }

    // keep the viewport pinned to the very top as rows merge in / re-sort at the
    // front while at the top. Keyed on the top row's identity so it re-anchors
    // exactly when the front changes; a no-op when scrolled away (atTop is false,
    // and the frozen order keeps the front stable anyway). scrollToItem is an
    // instant jump (no isScrollInProgress), so it can't feed back into `atTop`.
    val firstRowId = rows.firstOrNull()?.clientId
    LaunchedEffect(firstRowId) {
        if (atTop && firstRowId != null) {
            listState.scrollToItem(0)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(
                            id = if (provider) R.string.provider_contracts else R.string.client_contracts
                        ),
                        style = TopBarTitleTextStyle
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Black
                ),
            )
        },
        containerColor = Black
    ) { innerPadding ->

        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {

            if (rows.isEmpty()) {

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        stringResource(id = R.string.no_open_contracts),
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        stringResource(
                            id = if (provider) R.string.contracts_appear_providing else R.string.contracts_appear_connected
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextFaint
                    )
                }

            } else {

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(
                        rows,
                        key = { index, row ->
                            indexedLazyListKey("contract-peer", index, row.clientId)
                        }
                    ) { _, row ->
                        // a new/closed row fades and the rest reflow; the per-stack
                        // choreography handles contracts coming and going within a row
                        Column(
                            modifier = Modifier.animateItem(
                                fadeInSpec = tween(durationMillis = 500),
                                placementSpec = tween(durationMillis = 500),
                                fadeOutSpec = tween(durationMillis = 500)
                            )
                        ) {
                            ContractPeerRowView(row = row)
                            HorizontalDivider()
                        }
                    }
                }

            }

            /**
             * new contracts chip: tell the view controller we're back at the top
             * (it merges + re-sorts) and scroll up
             */
            if (!atTop && 0 < pendingCount) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp)
                        .background(BlueMedium, shape = RoundedCornerShape(16.dp))
                        .clickable {
                            atTop = true
                            contractStatsViewModel.setAtTop(true)
                            scope.launch {
                                listState.animateScrollToItem(0)
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        pluralStringResource(
                            id = R.plurals.new_items_count,
                            count = pendingCount,
                            pendingCount,
                        ),
                        style = TextStyle(fontSize = 12.sp),
                        color = Color.White
                    )
                }
            }

        }
    }
}

@Composable
private fun ContractPeerRowView(
    row: ContractPeerRowUi,
) {

    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {

        // the full client id, tappable to copy
        Text(
            row.clientId,
            style = TextStyle(fontSize = 13.sp, fontFamily = FontFamily.Monospace),
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    clipboardManager.setText(AnnotatedString(row.clientId))
                    Toast.makeText(context, R.string.client_id_copied, Toast.LENGTH_SHORT).show()
                }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // the two stacks are top-anchored: their headers align at the top of the
        // row and the piles grow downward. four columns, mirrored around the
        // center: send stats | send circles | receive circles | receive stats
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            ContractStackView(
                entries = row.send,
                byteCount = row.sendByteCount,
                title = stringResource(id = R.string.send),
                color = Green,
                pointsRight = true,
                removalToLeft = true,
                mirrored = true,
                modifier = Modifier.weight(1f),
            )

            Spacer(modifier = Modifier.width(20.dp))

            ContractStackView(
                entries = row.receive,
                byteCount = row.receiveByteCount,
                title = stringResource(id = R.string.receive),
                color = Pink,
                pointsRight = false,
                removalToLeft = false,
                mirrored = false,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

// one contract as presented in a stack: the live entry plus whether it is in its
// slide-off phase (still holding its slot, animating out to the side)
private data class DisplayedContract(
    val entry: ContractEntryUi,
    val leaving: Boolean = false,
) {
    val id: String get() = entry.contractId
}

/**
 * One direction's stack of contracts, newest on top, with the direction header
 * (title, arrow, summed bit rate) above and the scale anchor ("max N") below.
 *
 * Membership changes are choreographed in two phases, tetris style:
 *   1. a removed contract slides off toward [removalToLeft], still holding its
 *      slot open;
 *   2. once clear, one settle transaction drops the leavers, admits new
 *      contracts at the top, and the stack falls down into the open space.
 * Value updates (used bytes, bit rate) apply live in any phase. The truth is
 * mirrored into state so the deferred phase completions read the live intent
 * rather than a stale capture.
 */
@Composable
private fun ContractStackView(
    entries: List<ContractEntryUi>,
    byteCount: Long,
    title: String,
    color: Color,
    pointsRight: Boolean,
    removalToLeft: Boolean,
    // a mirrored stack (the send side) puts its stats column on the outside and
    // its circle column against the row center
    mirrored: Boolean,
    modifier: Modifier = Modifier,
) {

    val scope = rememberCoroutineScope()

    // the live truth, mirrored into state for the deferred phase completions
    var truth by remember { mutableStateOf(entries) }
    // what is on screen: truth plus leavers still sliding off
    val displayed = remember {
        mutableStateListOf<DisplayedContract>().apply {
            addAll(entries.map { DisplayedContract(it) })
        }
    }
    // a slide-off phase is in flight; membership changes queue behind it
    var settling by remember { mutableStateOf(false) }

    // scale reference: the largest contract on screen, leavers included so the
    // survivors rescale in the settle transaction rather than mid-slide
    val stackMax = displayed.maxOfOrNull { it.entry.totalByteCount } ?: 0L

    // reconcile the screen with the truth: values always track live; membership
    // changes run the two-phase choreography, one phase at a time
    fun sync() {
        val truthById = truth.associateBy { it.contractId }

        // 1. blocks still in the truth track its values (the inner disc grows
        //    smoothly); a leaver keeps its final snapshot
        for (i in displayed.indices) {
            val live = truthById[displayed[i].entry.contractId]
            if (live != null && live != displayed[i].entry) {
                displayed[i] = displayed[i].copy(entry = live)
            }
        }

        // 2. membership, one choreographed phase at a time
        if (settling) {
            return
        }

        val shownIds = displayed.mapTo(HashSet()) { it.id }
        val hasDepartures = displayed.any { truthById[it.entry.contractId] == null }
        val hasArrivals = truth.any { it.contractId !in shownIds }

        if (hasDepartures) {
            settling = true
            // phase 1: leavers slide off sideways, holding their slot open
            for (i in displayed.indices) {
                if (truthById[displayed[i].entry.contractId] == null) {
                    displayed[i] = displayed[i].copy(leaving = true)
                }
            }
            scope.launch {
                delay(SLIDE_MILLIS.toLong())
                // phase 2: one settle transaction -- drop the leavers, admit
                // arrivals at the top, the stack falls down and rescales
                displayed.clear()
                displayed.addAll(truth.map { DisplayedContract(it) })
                delay(SETTLE_MILLIS.toLong())
                settling = false
                // fold in whatever landed mid-phase
                sync()
            }
        } else if (hasArrivals) {
            // arrivals only: drop in at the top as the stack shifts down
            displayed.clear()
            displayed.addAll(truth.map { DisplayedContract(it) })
        }
    }

    LaunchedEffect(entries) {
        truth = entries
        sync()
    }

    // header and max label align to the circle column (the row-center edge of a
    // mirrored stack); blocks span the full width
    Column(
        modifier = modifier,
        horizontalAlignment = if (mirrored) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        // direction header: the live summed bit rate sits against the row center,
        // above the circle column it measures. The mirrored (send) side reads
        // title-arrow-total; the receive side reads total-arrow-title.
        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (mirrored) {
                HeaderTitle(title)
                HeaderArrow(pointsRight, color)
                HeaderTotal(byteCount, color)
            } else {
                HeaderTotal(byteCount, color)
                HeaderArrow(pointsRight, color)
                HeaderTitle(title)
            }
        }

        // the pile, newest first
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            displayed.forEach { d ->
                key(d.id) {
                    // a new contract drops in at the top as the stack shifts down;
                    // a leaver already slid offscreen in phase 1, so its removal
                    // from the list is instant (it just vanishes and the stack falls)
                    val enterState = remember { MutableTransitionState(false).apply { targetState = true } }
                    AnimatedVisibility(
                        visibleState = enterState,
                        enter = fadeIn(tween(SETTLE_MILLIS)) +
                            slideInVertically(tween(SETTLE_MILLIS)) { -it },
                        exit = ExitTransition.None,
                        modifier = Modifier.animatePlacement(),
                    ) {
                        ContractBlock(
                            entry = d.entry,
                            stackMax = stackMax,
                            color = color,
                            leaving = d.leaving,
                            removalToLeft = removalToLeft,
                            mirrored = mirrored,
                        )
                    }
                }
            }
        }

        // the scale anchor: all circles are sized relative to this
        Text(
            stringResource(id = R.string.contract_stack_max, formatByteCountCompact(stackMax)),
            style = TextStyle(fontSize = 10.sp),
            color = TextFaint,
            modifier = Modifier.alpha(if (displayed.isEmpty()) 0f else 1f)
        )
    }
}

@Composable
private fun HeaderTitle(title: String) {
    Text(
        title,
        style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium),
        color = TextMuted
    )
}

@Composable
private fun HeaderArrow(pointsRight: Boolean, color: Color) {
    Icon(
        if (pointsRight) Icons.AutoMirrored.Filled.ArrowForward else Icons.AutoMirrored.Filled.ArrowBack,
        contentDescription = null,
        tint = color,
        modifier = Modifier.size(10.dp)
    )
}

@Composable
private fun HeaderTotal(byteCount: Long, color: Color) {
    val active = 0 < byteCount
    Text(
        if (active) formatByteCountCompact(byteCount) else " ",
        style = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Medium),
        color = if (active) color else Color.Transparent
    )
}

/**
 * One contract in a stack: a fixed-size block with the contract circle centered
 * in it, and the used/total counts beside. The outer ring is sized by the
 * contract total relative to the stack max (area-proportional); the inner disc is
 * the used fraction of this contract. A contract actively moving bytes brightens
 * its ring.
 *
 * A mirrored block (the send side) lays out stats-then-circle so the circle
 * column sits against the row center; an unmirrored block is circle-then-stats.
 * Together the two stacks read as four columns. While [leaving], the block slides
 * clear of the row toward [removalToLeft] and fades, still holding its slot.
 */
@Composable
private fun ContractBlock(
    entry: ContractEntryUi,
    stackMax: Long,
    color: Color,
    leaving: Boolean,
    removalToLeft: Boolean,
    mirrored: Boolean,
) {

    // area-proportional to the stack max, clamped to the slot; animated so a
    // survivor rescales smoothly when a bigger contract leaves the stack
    val targetDiameter: Dp = remember(entry.totalByteCount, stackMax) {
        if (stackMax <= 0L || entry.totalByteCount <= 0L) {
            MIN_DIAMETER_DP.dp
        } else {
            val d = CIRCLE_SLOT_DP * sqrt(entry.totalByteCount.toDouble() / stackMax.toDouble())
            d.coerceIn(MIN_DIAMETER_DP.toDouble(), CIRCLE_SLOT_DP.toDouble()).toFloat().dp
        }
    }
    val diameter by animateDpAsState(
        targetValue = targetDiameter,
        animationSpec = tween(durationMillis = SETTLE_MILLIS, easing = EaseOut),
        label = "contractDiameter"
    )

    // area-proportional inner disc; grows/drains smoothly and persists across a
    // stack-max rescale
    val fraction = if (0L < entry.totalByteCount) {
        min(1.0, entry.usedByteCount.toDouble() / entry.totalByteCount.toDouble())
    } else {
        0.0
    }
    val animatedFraction by animateFloatAsState(
        targetValue = fraction.toFloat(),
        animationSpec = tween(durationMillis = DISC_MILLIS, easing = EaseOut),
        label = "contractInnerDisc"
    )

    val active = entry.isActive

    // slide fully clear of the row toward the removal edge
    val slideTarget = if (leaving) CIRCLE_SLOT_DP * 4f * (if (removalToLeft) -1f else 1f) else 0f
    val slide by animateFloatAsState(
        targetValue = slideTarget,
        animationSpec = tween(durationMillis = SLIDE_MILLIS),
        label = "contractSlide"
    )
    val blockAlpha by animateFloatAsState(
        targetValue = if (leaving) 0f else 1f,
        animationSpec = tween(durationMillis = SLIDE_MILLIS),
        label = "contractLeaveAlpha"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(CIRCLE_SLOT_DP.dp)
            .graphicsLayer {
                translationX = slide.dp.toPx()
                alpha = blockAlpha
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (mirrored) {
            Spacer(modifier = Modifier.weight(1f))
            ContractStats(entry = entry, alignEnd = true)
            Spacer(modifier = Modifier.width(10.dp))
            ContractCircle(diameter = diameter, fraction = animatedFraction, active = active, color = color, hasStream = entry.hasStream)
        } else {
            ContractCircle(diameter = diameter, fraction = animatedFraction, active = active, color = color, hasStream = entry.hasStream)
            Spacer(modifier = Modifier.width(10.dp))
            ContractStats(entry = entry, alignEnd = false)
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun ContractCircle(
    diameter: Dp,
    fraction: Float,
    active: Boolean,
    color: Color,
    hasStream: Boolean,
) {
    // a stream contract adds a second ring STREAM_RING_GAP_DP (radially) outside the main
    // one; grow the canvas to fit it so it is never clipped. A radial gap is twice the
    // diameter delta, hence 2x. The main ring and inner disc are sized off `diameter` (not
    // the canvas), so they are unchanged for direct contracts.
    val canvasSize = if (hasStream) diameter + (2 * STREAM_RING_GAP_DP).dp else diameter
    Box(
        modifier = Modifier.size(CIRCLE_SLOT_DP.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(canvasSize)) {
            val radius = diameter.toPx() / 2f
            // the outer ring is the contract total; active contracts brighten it
            val strokeWidth = (if (active) 1.5f else 1f).dp.toPx()
            val ringColor = color.copy(alpha = if (active) 1f else 0.55f)
            // stream contracts: a second concentric ring just outside the main one --
            // same color/width/brightening -- kept outside the used disc so it stays
            // visible even when the contract is full
            if (hasStream) {
                drawCircle(
                    color = ringColor,
                    radius = radius - strokeWidth / 2f + STREAM_RING_GAP_DP.dp.toPx(),
                    style = Stroke(width = strokeWidth)
                )
            }
            drawCircle(
                color = ringColor,
                radius = radius - strokeWidth / 2f,
                style = Stroke(width = strokeWidth)
            )
            // the inner disc is the used fraction, area-proportional
            if (0f < fraction) {
                val innerRadius = max(2.dp.toPx(), radius * sqrt(fraction))
                drawCircle(
                    color = color.copy(alpha = 0.3f),
                    radius = innerRadius,
                )
                drawCircle(
                    color = color.copy(alpha = 0.6f),
                    radius = innerRadius,
                    style = Stroke(width = 0.5.dp.toPx())
                )
            }
        }
    }
}

@Composable
private fun ContractStats(
    entry: ContractEntryUi,
    alignEnd: Boolean,
) {
    Column(
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            formatByteCountCompact(entry.usedByteCount),
            style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium),
            color = Color.White
        )
        Text(
            stringResource(id = R.string.of_total, formatByteCountCompact(entry.totalByteCount)),
            style = TextStyle(fontSize = 10.sp),
            color = TextMuted
        )
    }
}

/**
 * Animate a child's layout position when it changes because siblings were added
 * or removed -- the standard Compose recipe. Used so a stack's blocks fall down
 * smoothly when a leaver is dropped, and shift down when a new contract drops in
 * at the top, without a LazyColumn.
 */
private fun Modifier.animatePlacement(): Modifier = composed {
    val scope = rememberCoroutineScope()
    var targetOffset by remember { mutableStateOf(IntOffset.Zero) }
    var animatable by remember {
        mutableStateOf<Animatable<IntOffset, AnimationVector2D>?>(null)
    }
    this
        .onPlaced { coordinates ->
            targetOffset = coordinates.positionInParent().round()
        }
        .offset {
            val anim = animatable
                ?: Animatable(targetOffset, IntOffset.VectorConverter).also { animatable = it }
            if (anim.targetValue != targetOffset) {
                scope.launch {
                    anim.animateTo(targetOffset, spring(stiffness = Spring.StiffnessMediumLow))
                }
            }
            animatable?.let { it.value - targetOffset } ?: IntOffset.Zero
        }
}
