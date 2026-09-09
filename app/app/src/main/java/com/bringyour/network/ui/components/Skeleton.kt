package com.bringyour.network.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bringyour.network.R
import com.bringyour.network.ui.components.referral.rememberReducedMotion
import com.bringyour.network.ui.theme.MainBorderBase

/**
 * The shared placeholder style (mmm/DESIGNSTYLE.md "Placeholders, not pop-in"):
 * a low-contrast bar with a slow shimmer, no text, sized exactly like the
 * content it stands in for so the layout never moves when the data lands.
 * The shimmer stands still under reduced motion.
 *
 * Callers wrap the bars of one loading region in [SkeletonGroup], which
 * replaces the region's semantics with the loading description so a screen
 * reader announces "loading" once instead of an empty region.
 */
@Composable
fun SkeletonBar(
    width: Dp,
    height: Dp,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = height / 2,
) {
    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(cornerRadius))
            .background(MainBorderBase)
            .skeletonShimmer()
    )
}

/**
 * A full-width skeleton bar.
 */
@Composable
fun SkeletonLine(
    height: Dp,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = height / 2,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(cornerRadius))
            .background(MainBorderBase)
            .skeletonShimmer()
    )
}

/**
 * A skeleton bar the exact size of a line of text. The sample text is laid
 * out invisibly in the real style so the bar's box matches the loaded text
 * under every font scale; the bar is inset a hair so it reads as a text bar
 * rather than a block.
 */
@Composable
fun SkeletonText(
    sample: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        Text(
            sample,
            style = style,
            color = Color.Transparent,
            maxLines = 1,
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(vertical = 2.dp)
                .clip(RoundedCornerShape(50))
                .background(MainBorderBase)
                .skeletonShimmer()
        )
    }
}

/**
 * The container for one loading region: announces that the region is loading
 * and lays its bars out like the content they replace.
 */
@Composable
fun SkeletonGroup(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val loading = stringResource(id = R.string.loading)
    Column(
        modifier = modifier.clearAndSetSemantics {
            contentDescription = loading
        }
    ) {
        content()
    }
}

/**
 * A slow highlight sweeping the bar. One cycle every 1.6 s; a static bar when
 * the system animator scale is zero.
 */
@Composable
fun Modifier.skeletonShimmer(): Modifier {
    if (rememberReducedMotion()) {
        return this
    }
    val transition = rememberInfiniteTransition(label = "skeletonShimmer")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "skeletonShimmerPhase",
    )
    return this.drawWithContent {
        drawContent()
        val band = size.width * 0.6f
        val start = -band + (size.width + band * 2f) * phase
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = 0.06f),
                    Color.Transparent,
                ),
                start = Offset(start, 0f),
                end = Offset(start + band, size.height),
            )
        )
    }
}
