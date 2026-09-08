package com.bringyour.network.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
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
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * The Pro celebration: a 15 s confetti stream of pixel sunglasses, eye covers
 * and face discs racing from off the left edge to off the right edge, every
 * one on its own lane, at its own speed, size and bob, pitching with its
 * vertical velocity and towing a light trail. Some fly behind (smaller,
 * dimmer), some in front. Everything is drawn in one Canvas from three
 * cached vector painters, so a flight of two dozen sprites costs one draw
 * pass per frame. The overlay takes no touches, dismisses itself when the
 * last sprite has left, and does not play at all when the system animator
 * duration scale is 0.
 *
 * The flight follows a [ProFlightClock] (the same clock the pixelation layer
 * reads); its sequence seeds the mix, so a replay is a new burst.
 */
// a steady stream: a new sprite about every 150 ms (with a little jitter)
// until the last one can still leave the screen before the confetti ends
private const val SPAWN_INTERVAL_SECONDS = 0.15f
private const val SPAWN_JITTER_SECONDS = 0.03f
// time to cross the screen, fast to slow
private const val MIN_CROSSING_SECONDS = 1.2f
private const val MAX_CROSSING_SECONDS = 1.9f
// the most sprites in the air at once; the schedule skips a take-off that
// would exceed it (the interval and crossing times keep it near a dozen)
private const val MAX_LIVE_SPRITES = 30
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

// The whole take-off schedule of one flight, computed once at launch (no
// allocation per spawn): sprites take off at a steady rate for the first
// part of the confetti window so the last one has left the screen by
// PRO_FLIGHT_CONFETTI_SECONDS, and nothing spawns after that.
private fun burst(seed: Long): List<Sprite> {
    val random = Random(seed)
    val sprites = ArrayList<Sprite>()
    var takeOff = 0f
    val lastTakeOff = PRO_FLIGHT_CONFETTI_SECONDS - MAX_CROSSING_SECONDS
    while (takeOff <= lastTakeOff) {
        val crossing = MIN_CROSSING_SECONDS +
            random.nextFloat() * (MAX_CROSSING_SECONDS - MIN_CROSSING_SECONDS)
        val scale = 0.6f + random.nextFloat() * 0.6f
        val live = sprites.count { it.delaySeconds + it.crossingSeconds > takeOff }
        if (live < MAX_LIVE_SPRITES) sprites.add(Sprite(
            kind = when (random.nextInt(5)) {
                0, 1 -> SpriteKind.Sunglasses
                2, 3 -> SpriteKind.EyeCover
                else -> SpriteKind.FaceCover
            },
            delaySeconds = takeOff,
            crossingSeconds = crossing,
            lane = 0.08f + random.nextFloat() * 0.84f,
            bobAmplitude = (12 + random.nextInt(29)).dp,
            bobCycles = 2f + random.nextFloat() * 2f,
            phaseOffset = random.nextFloat() * 2f * PI.toFloat(),
            scale = scale,
            // the small ones fly behind
            behind = scale < 0.85f,
        ))
        takeOff += SPAWN_INTERVAL_SECONDS + (random.nextFloat() * 2f - 1f) * SPAWN_JITTER_SECONDS
    }
    // behind first so the front layer draws over it
    return sprites.sortedBy { if (it.behind) 0 else 1 }
}

@Composable
fun ProSunglassesFlight(
    clock: ProFlightClock,
) {
    val sequence = clock.sequence
    if (sequence == 0L) {
        return
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
        val seconds = clock.progress * PRO_FLIGHT_TOTAL_MILLIS / 1000f
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
