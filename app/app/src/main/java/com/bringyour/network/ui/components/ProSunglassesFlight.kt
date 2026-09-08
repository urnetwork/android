package com.bringyour.network.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bringyour.network.R
import com.bringyour.network.ui.components.referral.rememberReducedMotion
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The Pro celebration: the pixel sunglasses, the square eye cover and the
 * circular face cover race one after another from off the left edge to off
 * the right edge, each bobbing like Flappy Bird and pitching with its
 * vertical velocity, with a fading trail behind it. The overlay draws above
 * everything else, takes no touches, dismisses itself when the last sprite
 * has left, and does not play at all when the system animator duration
 * scale is 0.
 *
 * `sequence` is the OverlayViewModel flight sequence: 0 is idle, and each
 * launch bumps it, which starts a fresh flight even mid-air. `onFinished`
 * reports the sequence it flew so the host clears only that launch.
 */
private const val FLIGHT_MILLIS = 1600
private const val STAGGER_MILLIS = 250L
private const val BOB_CYCLES = 3.5f
private const val PITCH_DEGREES = 20f
private const val TRAIL_COUNT = 3

// the three sprites, in flight order: drawable, rendered size, bob phase offset
private data class FlightSprite(
    val drawable: Int,
    val width: Dp,
    val height: Dp,
    val phaseOffset: Float,
)

private val sprites = listOf(
    // privacy_glasses.xml geometry in the pink accent (pro_flight_sunglasses.xml)
    FlightSprite(R.drawable.pro_flight_sunglasses, 96.dp, 22.dp, 0f),
    // the pixel censor bar
    FlightSprite(R.drawable.pixel_eye_cover, 96.dp, 35.dp, 1.9f),
    // the stepped pixel disc
    FlightSprite(R.drawable.pixel_face_cover, 64.dp, 64.dp, 3.7f),
)

@Composable
fun ProSunglassesFlight(
    sequence: Long,
    onFinished: (Long) -> Unit,
) {
    if (sequence == 0L) {
        return
    }
    val reducedMotion = rememberReducedMotion()
    if (reducedMotion) {
        LaunchedEffect(sequence) { onFinished(sequence) }
        return
    }

    // one flight progress per sprite, restarted for every sequence; the
    // sprites take off a quarter second apart
    val progress = remember(sequence) { sprites.map { Animatable(0f) } }
    LaunchedEffect(sequence) {
        progress.forEachIndexed { index, animatable ->
            if (index > 0) {
                delay(STAGGER_MILLIS)
            }
            // the last take-off waits for its own landing, the others fly on
            if (index < progress.lastIndex) {
                launch {
                    animatable.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = FLIGHT_MILLIS, easing = FastOutSlowInEasing)
                    )
                }
            } else {
                animatable.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = FLIGHT_MILLIS, easing = FastOutSlowInEasing)
                )
            }
        }
        onFinished(sequence)
    }

    val density = LocalDensity.current
    val bobAmplitudePx = with(density) { 40.dp.toPx() }
    val trailStepPx = with(density) { 22.dp.toPx() }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            // decoration only: never announced, never a touch target
            .clearAndSetSemantics {}
    ) {
        val widthPx = with(density) { maxWidth.toPx() }
        sprites.forEachIndexed { index, sprite ->
            val t = progress[index].value
            if (t <= 0f || t >= 1f) {
                return@forEachIndexed
            }
            val spriteWidthPx = with(density) { sprite.width.toPx() }
            val travelPx = widthPx + 2 * spriteWidthPx
            // just off the left edge to just off the right edge
            val x = -spriteWidthPx + t * travelPx

            // the trail: fading copies a step behind, slightly smaller
            for (i in TRAIL_COUNT downTo 1) {
                val trailT = (t - i * trailStepPx / travelPx).coerceAtLeast(0f)
                Sprite(
                    sprite = sprite,
                    translationX = x - i * trailStepPx,
                    progress = trailT,
                    bobAmplitudePx = bobAmplitudePx,
                    alpha = 0.32f - 0.09f * i,
                    scale = 1f - 0.08f * i,
                )
            }
            Sprite(
                sprite = sprite,
                translationX = x,
                progress = t,
                bobAmplitudePx = bobAmplitudePx,
                alpha = 1f,
                scale = 1f,
            )
        }
    }
}

@Composable
private fun BoxWithConstraintsScope.Sprite(
    sprite: FlightSprite,
    translationX: Float,
    progress: Float,
    bobAmplitudePx: Float,
    alpha: Float,
    scale: Float,
) {
    val phase = 2.0 * PI * BOB_CYCLES * progress + sprite.phaseOffset
    // the vertical velocity sets the pitch: nose up while rising (y
    // decreasing on screen), nose down while falling
    val translationY = bobAmplitudePx * sin(phase).toFloat()
    val pitch = -PITCH_DEGREES * cos(phase).toFloat()
    Image(
        painter = painterResource(id = sprite.drawable),
        contentDescription = null,
        modifier = Modifier
            .align(Alignment.CenterStart)
            .size(sprite.width, sprite.height)
            .graphicsLayer {
                this.translationX = translationX
                this.translationY = translationY
                this.rotationZ = pitch
                this.alpha = alpha
                this.scaleX = scale
                this.scaleY = scale
                // a soft drop shadow so it reads as fast against any ground
                this.shadowElevation = 6.dp.toPx()
            }
    )
}
