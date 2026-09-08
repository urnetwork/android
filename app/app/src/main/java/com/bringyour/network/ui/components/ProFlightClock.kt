package com.bringyour.network.ui.components

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.bringyour.network.ui.components.referral.rememberReducedMotion

/**
 * The clock of one Pro celebration flight: 0..1 over [TOTAL_MILLIS], driven
 * by an Animatable so every reader (the sprite canvas, the pixelation layer)
 * follows the same frame without recomposing the tree. `sequence` 0 is idle.
 */
// The flight: confetti streams for the first 15 s; the pixelation fades in over
// the first 5 s, holds while the confetti flies, and fades out over the 5 s
// after the confetti has finished, so the screen is sharp again at 20 s.
const val PRO_FLIGHT_TOTAL_MILLIS = 20_000
const val PRO_FLIGHT_CONFETTI_SECONDS = 15f
const val PRO_FLIGHT_PIXELATE_IN_SECONDS = 5f
const val PRO_FLIGHT_PIXELATE_OUT_START_SECONDS = 15f
const val PRO_FLIGHT_PIXELATE_OUT_SECONDS = 5f
private val PIXELATE_MAX_CELL = 24.dp

@Stable
class ProFlightClock internal constructor(
    val sequence: Long,
    internal val animatable: Animatable<Float, *>,
) {
    /** True while a flight is in the air. Reads state: use inside draw or layer lambdas. */
    val active: Boolean
        get() = sequence != 0L && animatable.value > 0f && animatable.value < 1f

    /** Flight progress 0..1. Reads state: use inside draw or layer lambdas. */
    val progress: Float
        get() = animatable.value
}

/**
 * Runs the flight clock for `sequence`, restarting for every new sequence,
 * and reports the sequence it flew when done. Reduced motion (animator
 * duration scale 0) finishes at once without animating.
 */
@Composable
fun rememberProFlightClock(
    sequence: Long,
    onFinished: (Long) -> Unit,
): ProFlightClock {
    val reducedMotion = rememberReducedMotion()
    val animatable = remember(sequence) { Animatable(0f) }
    LaunchedEffect(sequence) {
        if (sequence == 0L) {
            return@LaunchedEffect
        }
        if (!reducedMotion) {
            animatable.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = PRO_FLIGHT_TOTAL_MILLIS, easing = LinearEasing)
            )
        }
        onFinished(sequence)
    }
    return remember(sequence, animatable) { ProFlightClock(sequence, animatable) }
}

/** The pixel cell size, in dp, at a point of the flight: ramp in, hold, ramp out. */
internal fun pixelateCellDp(progress: Float): Float {
    val seconds = progress * PRO_FLIGHT_TOTAL_MILLIS / 1000f
    val outEnd = PRO_FLIGHT_PIXELATE_OUT_START_SECONDS + PRO_FLIGHT_PIXELATE_OUT_SECONDS
    val ramp = when {
        seconds < PRO_FLIGHT_PIXELATE_IN_SECONDS ->
            FastOutSlowInEasing.transform(seconds / PRO_FLIGHT_PIXELATE_IN_SECONDS)
        seconds > PRO_FLIGHT_PIXELATE_OUT_START_SECONDS ->
            FastOutSlowInEasing.transform(
                ((outEnd - seconds) / PRO_FLIGHT_PIXELATE_OUT_SECONDS).coerceIn(0f, 1f)
            )
        else -> 1f
    }
    return ramp * PIXELATE_MAX_CELL.value
}

private const val PIXELATE_SHADER = """
    uniform shader content;
    uniform float pixel;
    uniform float2 size;
    half4 main(float2 p) {
        float2 q = floor(p / pixel) * pixel + pixel * 0.5;
        return content.eval(clamp(q, float2(0.0), size - 1.0));
    }
"""

/**
 * Pixelates the content this modifier wraps while the flight is in the air:
 * the cell size follows the flight clock, and the layer carries no render
 * effect at all when no flight is active. API 33+ uses an AGSL mosaic
 * shader; API 31-32 falls back to a blur of the same ramp; older devices
 * get the confetti only. The layer does not take touches.
 */
// one shader for the process; uniforms are set per frame
private var shaderCache: RuntimeShader? = null

fun Modifier.proFlightPixelation(clock: ProFlightClock): Modifier {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        return this
    }
    return this.graphicsLayer {
        // reads the clock inside the layer lambda: re-run per frame, no recomposition
        val cellDp = if (clock.active) pixelateCellDp(clock.progress) else 0f
        val cellPx = cellDp * density
        renderEffect = if (cellPx <= 1f) {
            null
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val shader = shaderCache ?: RuntimeShader(PIXELATE_SHADER).also { shaderCache = it }
            shader.setFloatUniform("pixel", cellPx)
            shader.setFloatUniform("size", size.width, size.height)
            RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
        } else {
            // API 31-32: the nearest approximation, a blur that follows the same ramp
            RenderEffect.createBlurEffect(cellPx / 2f, cellPx / 2f, Shader.TileMode.CLAMP)
                .asComposeRenderEffect()
        }
    }
}
