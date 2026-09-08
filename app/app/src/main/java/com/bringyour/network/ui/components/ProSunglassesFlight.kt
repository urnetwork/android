package com.bringyour.network.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
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
import kotlin.random.Random

/**
 * The Pro celebration: a confetti burst of pixel sunglasses, eye covers and
 * face discs racing from off the left edge to off the right edge, every one
 * on its own lane, at its own speed, size and bob, pitching with its
 * vertical velocity and towing a light trail. Some fly behind (smaller,
 * dimmer), some in front. Everything is drawn in one Canvas from three
 * cached vector painters, so a flight of two dozen sprites costs one draw
 * pass per frame. The overlay takes no touches, dismisses itself when the
 * last sprite has left, and does not play at all when the system animator
 * duration scale is 0.
 *
 * `sequence` is the OverlayViewModel flight sequence: 0 is idle, and each
 * launch bumps it, which starts a fresh flight even mid-air. It also seeds
 * the mix, so a replay is a new burst. `onFinished` reports the sequence it
 * flew so the host clears only that launch.
 */
private const val TOTAL_MILLIS = 2500
private const val SPRITE_COUNT = 24
// take-offs spread over the first part of the flight
private const val MAX_DELAY_SECONDS = 0.6f
// time to cross the screen, fast to slow
private const val MIN_CROSSING_SECONDS = 1.2f
private const val MAX_CROSSING_SECONDS = 1.9f
private const val PITCH_DEGREES = 20f
private const val TRAIL_COUNT = 2

private enum class SpriteKind { Sunglasses, EyeCover, FaceCover }

// one sprite of the burst, fixed for the whole flight
private class Sprite(
    val kind: SpriteKind,
    val delaySeconds: Float,
    val crossingSeconds: Float,
    // vertical lane, as a fraction of the height
    val lane: Float,
    val bobAmplitude: Dp,
    val bobCycles: Float,
    val phaseOffset: Float,
    val scale: Float,
    val behind: Boolean,
)

private fun burst(seed: Long): List<Sprite> {
    val random = Random(seed)
    val sprites = List(SPRITE_COUNT) {
        val scale = 0.6f + random.nextFloat() * 0.6f
        Sprite(
            kind = when (random.nextInt(5)) {
                0, 1 -> SpriteKind.Sunglasses
                2, 3 -> SpriteKind.EyeCover
                else -> SpriteKind.FaceCover
            },
            delaySeconds = random.nextFloat() * MAX_DELAY_SECONDS,
            crossingSeconds = MIN_CROSSING_SECONDS +
                random.nextFloat() * (MAX_CROSSING_SECONDS - MIN_CROSSING_SECONDS),
            lane = 0.08f + random.nextFloat() * 0.84f,
            bobAmplitude = (12 + random.nextInt(29)).dp,
            bobCycles = 2f + random.nextFloat() * 2f,
            phaseOffset = random.nextFloat() * 2f * PI.toFloat(),
            scale = scale,
            // the small ones fly behind
            behind = scale < 0.85f,
        )
    }
    // behind first so the front layer draws over it
    return sprites.sortedBy { if (it.behind) 0 else 1 }
}

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

    // the flight clock, 0..1 over the whole burst, restarted for every sequence
    val clock = remember(sequence) { Animatable(0f) }
    LaunchedEffect(sequence) {
        clock.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = TOTAL_MILLIS, easing = LinearEasing)
        )
        onFinished(sequence)
    }
    val sprites = remember(sequence) { burst(sequence) }

    val sunglasses = painterResource(id = R.drawable.pro_flight_sunglasses)
    val eyeCover = painterResource(id = R.drawable.pixel_eye_cover)
    val faceCover = painterResource(id = R.drawable.pixel_face_cover)
    val density = LocalDensity.current
    val sunglassesSize = with(density) { Size(96.dp.toPx(), 22.dp.toPx()) }
    val eyeCoverSize = with(density) { Size(96.dp.toPx(), 35.dp.toPx()) }
    val faceCoverSize = with(density) { Size(64.dp.toPx(), 64.dp.toPx()) }
    val trailStepPx = with(density) { 18.dp.toPx() }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            // decoration only: never announced, never a touch target
            .clearAndSetSemantics {}
    ) {
        val seconds = clock.value * TOTAL_MILLIS / 1000f
        for (sprite in sprites) {
            val local = (seconds - sprite.delaySeconds) / sprite.crossingSeconds
            if (local <= 0f || local >= 1f) {
                continue
            }
            val t = FastOutSlowInEasing.transform(local)
            val painter: Painter
            val base: Size
            when (sprite.kind) {
                SpriteKind.Sunglasses -> { painter = sunglasses; base = sunglassesSize }
                SpriteKind.EyeCover -> { painter = eyeCover; base = eyeCoverSize }
                SpriteKind.FaceCover -> { painter = faceCover; base = faceCoverSize }
            }
            val spriteWidth = base.width * sprite.scale
            val travel = size.width + 2 * spriteWidth
            val laneY = size.height * sprite.lane
            val amplitude = with(density) { sprite.bobAmplitude.toPx() }
            val layerAlpha = if (sprite.behind) 0.55f else 1f

            // the trail: fading copies a step behind
            for (i in TRAIL_COUNT downTo 1) {
                val trailT = (t - i * trailStepPx / travel).coerceAtLeast(0f)
                drawSprite(
                    painter = painter,
                    base = base,
                    x = -spriteWidth + trailT * travel - i * trailStepPx,
                    laneY = laneY,
                    amplitude = amplitude,
                    sprite = sprite,
                    progress = trailT,
                    alpha = layerAlpha * (0.28f - 0.1f * i),
                    scale = sprite.scale * (1f - 0.08f * i),
                )
            }
            drawSprite(
                painter = painter,
                base = base,
                x = -spriteWidth + t * travel,
                laneY = laneY,
                amplitude = amplitude,
                sprite = sprite,
                progress = t,
                alpha = layerAlpha,
                scale = sprite.scale,
            )
        }
    }
}

private fun DrawScope.drawSprite(
    painter: Painter,
    base: Size,
    x: Float,
    laneY: Float,
    amplitude: Float,
    sprite: Sprite,
    progress: Float,
    alpha: Float,
    scale: Float,
) {
    val phase = 2.0 * PI * sprite.bobCycles * progress + sprite.phaseOffset
    // the vertical velocity sets the pitch: nose up while rising (y
    // decreasing on screen), nose down while falling
    val y = laneY + amplitude * sin(phase).toFloat() - base.height * scale / 2
    val pitch = -PITCH_DEGREES * cos(phase).toFloat()
    translate(left = x, top = y) {
        scale(scale = scale, pivot = androidx.compose.ui.geometry.Offset.Zero) {
            rotate(degrees = pitch, pivot = androidx.compose.ui.geometry.Offset(base.width / 2, base.height / 2)) {
                with(painter) {
                    draw(size = base, alpha = alpha)
                }
            }
        }
    }
}
