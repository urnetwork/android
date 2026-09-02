package com.bringyour.network.ui.introduction

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import com.bringyour.network.R
import com.bringyour.network.ui.components.referral.rememberReducedMotion
import com.bringyour.network.ui.theme.Black
import kotlin.math.roundToInt

/**
 * The connector mark of the onboarding flow lives in one place and moves:
 * large in page 1's route line, small next to the step bubbles on every
 * later page. The two slots report their bounds here; the host draws the
 * mark and flies it between them, so the pages after the first give the
 * icon's vertical room back to their content.
 */
class IntroConnectorState {
    /** page 1's route slot, in window coordinates */
    var heroBounds by mutableStateOf<Rect?>(null)
    /** the top bar's slot on later pages, in window coordinates */
    var headerBounds by mutableStateOf<Rect?>(null)
    /** true once the flow has left page 1 */
    var inHeader by mutableStateOf(false)
    /** true while the host draws the mark; page 1's route draws its own otherwise, under the traveller */
    var floating by mutableStateOf(false)
}

val LocalIntroConnector = compositionLocalOf<IntroConnectorState?> { null }

/** Page 1's route slot: reports its bounds to the host. */
fun Modifier.introConnectorHero(state: IntroConnectorState?): Modifier =
    if (state == null) this else onGloballyPositioned { state.heroBounds = it.boundsInWindow() }

/** The top bar's slot next to the step bubbles: reports its bounds to the host. */
fun Modifier.introConnectorHeaderSlot(state: IntroConnectorState?): Modifier =
    if (state == null) this else onGloballyPositioned { state.headerBounds = it.boundsInWindow() }

/** The connector mark on a black ground, which also masks the route line behind it. */
@Composable
fun IntroConnectorMark(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(id = R.drawable.ic_launcher_foreground),
        contentDescription = null,
        modifier = modifier.background(Black)
    )
}

/**
 * The flying mark, drawn over the intro pages by the host. Parked (and
 * hidden) on the route slot while page 1 shows; on leaving page 1 it appears
 * there and flies into the header; on coming back it flies down and hands
 * the mark back to the route.
 */
@Composable
fun FloatingIntroConnector(state: IntroConnectorState) {
    val reducedMotion = rememberReducedMotion()
    var originInWindow by remember { mutableStateOf(Offset.Zero) }
    val rect = remember { Animatable(Rect.Zero, Rect.VectorConverter) }

    // stay parked on the route slot while page 1 draws its own mark
    LaunchedEffect(state.heroBounds, state.floating) {
        val hero = state.heroBounds
        if (!state.floating && hero != null) {
            rect.snapTo(hero)
        }
    }

    LaunchedEffect(state.inHeader, state.headerBounds, state.heroBounds) {
        if (state.inHeader) {
            val target = state.headerBounds ?: return@LaunchedEffect
            state.floating = true
            if (reducedMotion) rect.snapTo(target) else rect.animateTo(target, tween(520, easing = FastOutSlowInEasing))
        } else if (state.floating) {
            val target = state.heroBounds ?: return@LaunchedEffect
            if (reducedMotion) rect.snapTo(target) else rect.animateTo(target, tween(520, easing = FastOutSlowInEasing))
            state.floating = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { originInWindow = it.positionInWindow() }
    ) {
        if (state.floating) {
            val r = rect.value
            val density = LocalDensity.current
            IntroConnectorMark(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (r.left - originInWindow.x).roundToInt(),
                            (r.top - originInWindow.y).roundToInt()
                        )
                    }
                    .size(
                        with(density) { r.width.toDp() },
                        with(density) { r.height.toDp() }
                    )
            )
        }
    }
}
