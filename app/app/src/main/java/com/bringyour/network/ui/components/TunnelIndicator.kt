package com.bringyour.network.ui.components

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bringyour.network.ui.components.referral.rememberReducedMotion
import com.bringyour.network.ui.theme.Amber
import kotlin.math.pow

/** The tunnel's fades ease out: fast at first, settling into place (the canvas's 1 − (1 − t)³). */
private val TunnelEaseOut = Easing { fraction -> 1f - (1f - fraction).pow(3) }

/**
 * The slow internet tunnel ([tunnelSquares]) showing [depth] of its six
 * squares, 0 for none. All six squares are always drawn, each with its own
 * opacity: a change of depth only fades squares in or out in place, staggered
 * per [tunnelSquareMotion], and a square fading in settles from
 * [TUNNEL_HIDDEN_SCALE] about the vanishing point. When it first shows it
 * builds in from [startDepth]. Under reduced motion the fades stay and the
 * scale goes. Decorative: whatever shows it says what it means.
 */
@Composable
fun TunnelIndicator(
    depth: Int,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
    color: Color = Amber,
    startDepth: Int = 0,
) {
    val reducedMotion = rememberReducedMotion()
    val targetDepth = depth.coerceIn(0, TUNNEL_SQUARE_COUNT)
    // the depth the squares show: the start depth for the first frame, then
    // each depth given, so the tunnel builds in as it first appears
    var shownDepth by remember { mutableIntStateOf(startDepth.coerceIn(0, TUNNEL_SQUARE_COUNT)) }
    LaunchedEffect(targetDepth) {
        shownDepth = targetDepth
    }
    // the depth the squares last moved from, read once per change, so every
    // recomposition until the next change keeps the same stagger
    val lastDepth = remember { intArrayOf(shownDepth) }
    val fromDepth = remember(shownDepth) {
        val previous = lastDepth[0]
        lastDepth[0] = shownDepth
        previous
    }
    val visibilities = (0 until TUNNEL_SQUARE_COUNT).map { index ->
        val motion = tunnelSquareMotion(index, fromDepth, shownDepth)
        animateFloatAsState(
            targetValue = if (motion.visible) 1f else 0f,
            animationSpec = tween(
                durationMillis = motion.durationMillis,
                delayMillis = motion.delayMillis,
                easing = TunnelEaseOut,
            ),
            label = "tunnel square $index",
        )
    }

    Canvas(modifier = modifier.size(size)) {
        val box = this.size.minDimension
        val vanishingPoint = Offset(tunnelVanishingPoint(box), tunnelVanishingPoint(box))
        tunnelSquares(box).forEachIndexed { index, square ->
            val visibility = visibilities[index].value
            if (visibility <= 0f) {
                return@forEachIndexed
            }
            val squareScale = if (reducedMotion) 1f else 1f + (TUNNEL_HIDDEN_SCALE - 1f) * (1f - visibility)
            scale(scale = squareScale, pivot = vanishingPoint) {
                drawRect(
                    color = color,
                    topLeft = Offset(square.start, square.start),
                    size = Size(square.side, square.side),
                    alpha = visibility * square.opacity,
                    style = Stroke(width = square.strokeWidth, join = StrokeJoin.Miter),
                )
            }
        }
    }
}
