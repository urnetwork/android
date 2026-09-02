package com.bringyour.network.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bringyour.network.R
import com.bringyour.network.ui.components.referral.rememberReducedMotion
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.ProGold
import com.bringyour.network.ui.theme.ProGoldLight
import com.bringyour.network.ui.theme.TopBarTitleTextStyle

/**
 * The Pro-gold dress of the recommended sign-up box, shared by every flavor's
 * plan card: a breathing halo of fading rings that follows the box, an opaque
 * ground with a gold wash brighter at the top left, and a white light that
 * runs around the gold border. Both motions stop under reduced motion,
 * leaving the static gold dress.
 */
class GoldDress(
    /** where the light is along the border, 0..1 */
    val sweep: Float,
    /** the halo's breath, 0..1 */
    val breath: Float,
)

@Composable
fun rememberGoldDress(): GoldDress {
    val reducedMotion = rememberReducedMotion()
    if (reducedMotion) {
        return GoldDress(sweep = 0.25f, breath = 0.5f)
    }
    val transition = rememberInfiniteTransition(label = "gold-dress")
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "gold-dress-sweep"
    )
    val breath by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "gold-dress-breath"
    )
    return GoldDress(sweep = sweep, breath = breath)
}

/** Draws the dress behind the content; clip and pad after it. */
fun Modifier.goldPlanDress(
    dress: GoldDress,
    cornerRadius: Dp = 12.dp,
    strokeWidth: Dp = 2.dp,
    selected: Boolean = true,
): Modifier = drawBehind {
    drawGoldDress(
        cornerRadius = cornerRadius.toPx(),
        strokeWidth = strokeWidth.toPx(),
        sweep = dress.sweep,
        breath = dress.breath,
        selected = selected
    )
}

/** The gold "Best value" pill that sits on the box's top-right corner. */
@Composable
fun BestValuePill(
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .shadow(
                elevation = 10.dp,
                shape = shape,
                ambientColor = ProGold,
                spotColor = ProGold
            )
            .background(
                Brush.verticalGradient(
                    colors = listOf(ProGoldLight, ProGold)
                ),
                shape = shape
            )
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.45f),
                shape = shape
            )
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            stringResource(id = R.string.best_value),
            color = Black,
            style = TopBarTitleTextStyle
        )
    }
}

private fun DrawScope.drawGoldDress(
    cornerRadius: Float,
    strokeWidth: Float,
    sweep: Float,
    breath: Float,
    selected: Boolean,
) {
    val corner = CornerRadius(cornerRadius)

    // the breathing halo: rings that follow the box's shape and fade out
    // over the spill, brightest against the border
    val spill = 28.dp.toPx()
    val rings = 44
    val ringWidth = spill / rings
    val peak = 0.20f + 0.14f * breath
    for (ring in 0 until rings) {
        val t = ring / (rings - 1f)
        val distance = ringWidth * (ring + 0.5f)
        val fade = (1f - t) * (1f - t)
        drawRoundRect(
            color = ProGold.copy(alpha = peak * fade),
            topLeft = Offset(-distance, -distance),
            size = Size(size.width + 2 * distance, size.height + 2 * distance),
            cornerRadius = CornerRadius(cornerRadius + distance),
            style = Stroke(width = ringWidth + 1f)
        )
    }

    // opaque ground, then the gold wash with a brighter top-left
    drawRoundRect(color = Black, cornerRadius = corner)
    drawRoundRect(color = ProGold.copy(alpha = 0.08f), cornerRadius = corner)
    drawRoundRect(
        brush = Brush.radialGradient(
            colors = listOf(
                ProGold.copy(alpha = 0.18f),
                ProGold.copy(alpha = 0f)
            ),
            center = Offset(size.width * 0.1f, 0f),
            radius = size.width * 0.9f
        ),
        cornerRadius = corner
    )

    // the border: solid gold with a bright light travelling around it
    val base = if (selected) ProGold else ProGold.copy(alpha = 0.7f)
    val stops = ArrayList<Pair<Float, Color>>()
    stops += 0f to base
    stops += 1f to base
    val width = 0.14f
    for (centre in listOf(sweep - 1f, sweep, sweep + 1f)) {
        for ((delta, color) in listOf(
            -width to base,
            -width / 2f to ProGoldLight,
            0f to Color.White,
            width / 2f to ProGoldLight,
            width to base
        )) {
            val position = centre + delta
            if (position in 0f..1f) {
                stops += position to color
            }
        }
    }
    val inset = strokeWidth / 2f
    drawRoundRect(
        brush = Brush.sweepGradient(
            colorStops = stops.sortedBy { it.first }.toTypedArray(),
            center = center
        ),
        topLeft = Offset(inset, inset),
        size = Size(size.width - strokeWidth, size.height - strokeWidth),
        cornerRadius = corner,
        style = Stroke(width = strokeWidth)
    )
}
