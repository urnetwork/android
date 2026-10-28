package com.bringyour.network.ui.connect

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LifecycleStartEffect
import com.bringyour.network.R
import com.bringyour.network.SLOW_INTERNET_RECOVERED_MILLIS
import com.bringyour.network.TransportDelayUi
import com.bringyour.network.ui.components.TUNNEL_FADE_IN_MILLIS
import com.bringyour.network.ui.components.TUNNEL_FADE_OUT_MILLIS
import com.bringyour.network.ui.components.TunnelIndicator
import com.bringyour.network.ui.components.tunnelClearDelayMillis
import com.bringyour.network.ui.components.tunnelDepth
import com.bringyour.network.ui.theme.Amber
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.Green
import com.bringyour.network.ui.theme.MainTextBase
import kotlinx.coroutines.delay

/** The pill's outline: the 35% amber the island draws around a Live Activity. */
private val SlowInternetPillOutline = Amber.copy(alpha = 0.35f)

// the design's sizes in the system font, the island's (the app's font ships
// no semibold); tabular digits so a changing number keeps its width. The
// recovered copy is set like the numbers
private val SlowInternetLabelStyle = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
private val SlowInternetNumberStyle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum")
private val SlowInternetArrowStyle = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold)

/** A change of content cross-fades the copy and the check, and eases the width, over this long. */
private const val SLOW_INTERNET_PILL_CHANGE_MILLIS = 350

/**
 * The connect screen's slow internet pill, top right below the status bar
 * (apple/DELAY.md §5.2, §7.1): a black rectangle with square corners, like
 * the tunnel's, in a 1 dp outline of 35% amber. The 18 dp tunnel, the label
 * in amber, then the upload and download numbers, the worse one amber
 * ([SlowInternetPillContent]); with too little room (a large font scale, a
 * long translation) it drops the better direction, then the numbers. It is
 * a readout, not a button: nothing opens from it. TalkBack reads one node,
 * the label and then the numbers.
 *
 * It follows the sdk's level directly while [available] (a connection is
 * requested), and appears with the tunnel's first square. On a return to
 * normal the squares fade out as a green check and "Internet back to normal"
 * fade in; that holds 3 s, then the pill fades. A slow level during the hold
 * brings the slow content back. Leaving any other way, no longer
 * [available] or [hidden] by the referral toast, is no recovery: the pill
 * waits until its last square has started to fade, then fades, at once for
 * the toast. The screen stopping forgets the pill, as the Apple store resets
 * while the app is suspended: it starts fresh when the screen returns, so
 * only a return to normal seen on screen is a recovery. While the slow
 * content shows on the visible screen, [setPillVisible] has its numbers read
 * again: they move without a report.
 */
@Composable
fun SlowInternetPill(
    transportDelay: TransportDelayUi,
    available: Boolean,
    hidden: Boolean,
    setPillVisible: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    // each stop of the screen starts the pill below over, from nothing; the
    // stopped screen does not redraw, so it does as the screen returns
    var screenStops by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        screenStops++
    }
    key(screenStops) {
        // what the pill shows, null while it is not up or is leaving. As it
        // starts it shows what is there, with no recovered state
        var shown by remember {
            mutableStateOf(SlowInternetPillContent.next(null, transportDelay, available, hidden))
        }
        LaunchedEffect(transportDelay, available, hidden) {
            shown = SlowInternetPillContent.next(shown, transportDelay, available, hidden)
        }
        val recovered = shown?.recovered == true
        LaunchedEffect(recovered) {
            if (recovered) {
                delay(SLOW_INTERNET_RECOVERED_MILLIS)
                // a slow level that arrived as the hold ran out keeps the pill
                if (shown?.recovered == true) {
                    shown = null
                }
            }
        }
        val slow = shown?.recovered == false
        LifecycleStartEffect(slow) {
            setPillVisible(slow)
            onStopOrDispose { setPillVisible(false) }
        }

        // what the pill draws: it keeps the last content while it fades out
        val lastContent = remember { arrayOf(shown ?: SlowInternetPillContent(TransportDelayUi.None)) }
        shown?.let { lastContent[0] = it }
        val content = lastContent[0]
        // the squares fade out from the inside on a return to normal, and on the way out
        val depth = if (slow) tunnelDepth(transportDelay.quality) else 0
        val lastDepth = remember { intArrayOf(depth) }
        if (0 < depth) {
            lastDepth[0] = depth
        }
        // after the hold the squares are gone, and the referral toast takes the top at once
        val exitDelayMillis = if (content.recovered || hidden) 0 else tunnelClearDelayMillis(lastDepth[0])

        Box(
            modifier = modifier
                .statusBarsPadding()
                .padding(top = 12.dp, end = 16.dp),
        ) {
            AnimatedVisibility(
                visible = shown != null,
                enter = fadeIn(tween(durationMillis = TUNNEL_FADE_IN_MILLIS)),
                exit = fadeOut(tween(durationMillis = TUNNEL_FADE_OUT_MILLIS, delayMillis = exitDelayMillis)),
            ) {
                val checkAlpha by animateFloatAsState(
                    targetValue = if (content.recovered) 1f else 0f,
                    animationSpec = tween(SLOW_INTERNET_PILL_CHANGE_MILLIS),
                    label = "slow internet recovered check",
                )
                // the label, then each direction's delay or rate as the Apple
                // pill reads it: "Upload delay 640 ms, Download delay 1.2 s"
                val description = listOfNotNull(
                    stringResource(if (content.recovered) R.string.slow_internet_recovered else content.label.textId),
                    content.up?.let { "${stringResource(R.string.slow_internet_upload_delay)} $it" },
                    content.down?.let { "${stringResource(R.string.slow_internet_download_delay)} $it" },
                ).joinToString(", ")

                Row(
                    modifier = Modifier
                        .clearAndSetSemantics { contentDescription = description }
                        .background(Color.Black)
                        .border(1.dp, SlowInternetPillOutline)
                        // the copy fading out as the width eases stays inside
                        .animateContentSize(tween(SLOW_INTERNET_PILL_CHANGE_MILLIS))
                        .heightIn(min = 30.dp)
                        .padding(start = 6.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        TunnelIndicator(depth = depth, size = 18.dp)
                        // square, like the tunnel
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .alpha(checkAlpha)
                                .background(Green),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = Black,
                                modifier = Modifier.size(12.dp),
                            )
                        }
                    }
                    AnimatedContent(
                        targetState = content,
                        transitionSpec = {
                            fadeIn(tween(SLOW_INTERNET_PILL_CHANGE_MILLIS)) togetherWith
                                fadeOut(tween(SLOW_INTERNET_PILL_CHANGE_MILLIS)) using null
                        },
                        // new numbers change in place; only a recovery cross-fades
                        contentKey = { it.recovered },
                        label = "slow internet copy",
                    ) { copy ->
                        if (copy.recovered) {
                            Text(
                                stringResource(id = R.string.slow_internet_recovered),
                                style = SlowInternetNumberStyle,
                                color = MainTextBase,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        } else {
                            // like the Apple pill's ViewThatFits: the first of
                            // the label with both numbers, with the worse one,
                            // and alone, that fits the room
                            Layout(
                                content = {
                                    SlowInternetCopy(copy)
                                    SlowInternetCopy(copy.worseOnly)
                                    SlowInternetCopy(copy.labelOnly)
                                },
                            ) { measurables, constraints ->
                                val unbounded = constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity)
                                val placeable = measurables.dropLast(1).asSequence()
                                    .map { it.measure(unbounded) }
                                    .firstOrNull { it.width <= constraints.maxWidth }
                                    // the label alone takes what room there is
                                    ?: measurables.last().measure(constraints.copy(minWidth = 0))
                                layout(placeable.width, placeable.height) {
                                    placeable.placeRelative(0, 0)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The label's copy: "Poor signal", or "Fallback" on the fallback connection. Also the dashboard widget's. */
internal val SlowInternetPillContent.Label.textId: Int
    get() = when (this) {
        SlowInternetPillContent.Label.POOR_SIGNAL -> R.string.slow_internet_poor_signal
        SlowInternetPillContent.Label.FALLBACK -> R.string.slow_internet_fallback_short
    }

/** The slow copy on one baseline: the label, then the numbers that show. */
@Composable
private fun SlowInternetCopy(content: SlowInternetPillContent) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(id = content.label.textId),
            modifier = Modifier.alignByBaseline(),
            style = SlowInternetLabelStyle,
            color = Amber,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        content.up?.let {
            SlowInternetNumber(
                arrow = "↑",
                text = it,
                worse = content.worse == SlowInternetPillContent.Direction.UP,
                modifier = Modifier.alignByBaseline(),
            )
        }
        content.down?.let {
            SlowInternetNumber(
                arrow = "↓",
                text = it,
                worse = content.worse == SlowInternetPillContent.Direction.DOWN,
                modifier = Modifier.alignByBaseline(),
            )
        }
    }
}

/** One direction: its muted arrow, then its number, both amber for the worse direction. */
@Composable
private fun SlowInternetNumber(
    arrow: String,
    text: String,
    worse: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            arrow,
            modifier = Modifier
                .alignByBaseline()
                // drawn a touch above the baseline, as the design sets it
                .graphicsLayer { translationY = -1.dp.toPx() },
            style = SlowInternetArrowStyle,
            color = if (worse) Amber.copy(alpha = 0.75f) else Color.White.copy(alpha = 0.55f),
        )
        Text(
            text,
            modifier = Modifier.alignByBaseline(),
            style = SlowInternetNumberStyle,
            color = if (worse) Amber else MainTextBase,
        )
    }
}
