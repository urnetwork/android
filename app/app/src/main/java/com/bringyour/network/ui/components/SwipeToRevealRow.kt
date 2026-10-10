package com.bringyour.network.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bringyour.network.R
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.Red
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * A row that reveals an action button when swiped left, mimicking iOS list
 * behavior: the swipe only reveals the button, and the user must tap it to
 * act. Swiping back (or the button tap) closes the row. The action is Delete
 * unless the caller names another (Account -> Sessions reveals Sign out).
 *
 * Like iOS, the button is a normal-sized, vertically centered capsule (not
 * the full row height) with an icon and a small label. It grows and fades in
 * as the row slides out and shrinks/fades away as it slides back — the scale
 * and alpha track the open fraction so the resize is smooth and proportional
 * to the slide distance. The button also stays centered within the revealed
 * gap the whole way, so it reads as growing out of the trailing edge.
 *
 * Assistive tech cannot swipe, so the content also carries the action as an
 * accessibility custom action, labeled [accessibilityLabel]. A custom action
 * is offered only on a node TalkBack can focus: content with no focusable
 * node of its own sets [mergeDescendants], so the row reads as one node that
 * carries the action. A disabled row closes and offers neither the swipe nor
 * the action, for an action already running.
 */
@Composable
fun SwipeToRevealRow(
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    actionLabel: String = stringResource(id = R.string.delete),
    actionIcon: ImageVector = Icons.Filled.Delete,
    accessibilityLabel: String = stringResource(id = R.string.remove),
    enabled: Boolean = true,
    mergeDescendants: Boolean = false,
    content: @Composable () -> Unit,
) {
    // the row slides far enough to fully reveal the measured button plus the
    // same side margins as a bare-icon reveal; the label width varies by locale
    val gapMarginPx = with(LocalDensity.current) { 28.dp.toPx() }
    val buttonHeight = 44.dp
    var buttonWidthPx by remember { mutableFloatStateOf(0f) }
    val revealPx = buttonWidthPx + gapMarginPx

    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    val close: () -> Unit = {
        scope.launch { offsetX.animateTo(0f) }
    }

    // a disabled row never stays open on a button it cannot use
    LaunchedEffect(enabled) {
        if (!enabled) {
            offsetX.animateTo(0f)
        }
    }

    Box(
        modifier = modifier.fillMaxWidth()
    ) {

        // action button behind the content: a normal-sized, vertically centered
        // button that grows/fades proportional to the slide and stays centered
        // in the revealed gap. The graphicsLayer reads offsetX in the draw phase
        // (no recomposition), so the resize follows the drag frame-for-frame.
        Box(
            modifier = Modifier.matchParentSize(),
            contentAlignment = Alignment.CenterEnd
        ) {
            Row(
                modifier = Modifier
                    .height(buttonHeight)
                    .onSizeChanged { buttonWidthPx = it.width.toFloat() }
                    .graphicsLayer {
                        val openFraction = if (revealPx <= 0f) 0f else (-offsetX.value / revealPx).coerceIn(0f, 1f)
                        // scale + alpha track the slide: 0 when closed (hidden),
                        // full size when open. Grows from its center (default origin).
                        scaleX = openFraction
                        scaleY = openFraction
                        alpha = openFraction
                        // keep the button centered in the gap the content opens:
                        // gap center = rightEdge + offsetX/2, and a CenterEnd button
                        // sits half its width in from the edge, so shift by both.
                        translationX = offsetX.value / 2f + size.width / 2f
                    }
                    .clip(CircleShape)
                    .background(Red)
                    .clickable(enabled = enabled) {
                        onAction()
                        close()
                    }
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    // the label names the button; the icon is decorative
                    actionIcon,
                    contentDescription = null,
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    actionLabel,
                    style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
                    color = Color.White,
                    maxLines = 1
                )
            }
        }

        // foreground content, draggable horizontally to reveal the button
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .fillMaxWidth()
                .background(Black)
                .semantics(mergeDescendants = mergeDescendants) {
                    customActions = if (enabled) {
                        swipeToRevealAccessibilityActions(accessibilityLabel, onAction)
                    } else {
                        listOf()
                    }
                }
                .draggable(
                    enabled = enabled,
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        scope.launch {
                            offsetX.snapTo((offsetX.value + delta).coerceIn(-revealPx, 0f))
                        }
                    },
                    onDragStopped = {
                        scope.launch {
                            // settle open past the halfway point, else closed
                            val target = if (offsetX.value < -revealPx / 2f) -revealPx else 0f
                            offsetX.animateTo(target)
                        }
                    }
                )
        ) {
            content()
        }
    }
}
