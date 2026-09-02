package com.bringyour.network.ui.introduction

import kotlin.math.roundToInt
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.ImageBitmap
import com.bringyour.network.R
import com.bringyour.network.ui.components.referral.rememberReducedMotion
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.BlueLight
import com.bringyour.network.ui.theme.Pink
import kotlin.math.PI
import kotlin.math.sin

/**
 * The ur.io docs route line for the first onboarding page: a dashed line from
 * "You" through the URnetwork connector to "Internet", with one of the
 * pixel-art ur-people (the docs' travellers, on their connector-shaped tiles)
 * walking along it, trip after trip. A new person makes each trip, drawn from
 * a shuffled deck so all of them appear before any repeats, and the traveller
 * fades in at the start and out at the end like the docs
 * (react/src/pages/Docs.jsx RouteLine, styles/docs-explorer.css dxlPacket).
 * Under reduced motion person 0 rests near the internet end.
 */
private const val TRIP_MILLIS = 6500L
private const val TRAVELLER_SIZE_DP = 40
private const val CONNECTOR_SIZE_DP = 72
private const val ROUTE_HEIGHT_DP = 76

private val UR_PEOPLE = listOf(
    R.drawable.ur_person_1,
    R.drawable.ur_person_2,
    R.drawable.ur_person_3,
)

@Composable
fun IntroTraveller(
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()

    // one frame clock drives both the position and the trip count, so the
    // person changes exactly at the wrap, while the traveller is faded out
    var trip by remember { mutableFloatStateOf(if (reducedMotion) 0.9f else 0f) }
    var tripCount by remember { mutableIntStateOf(0) }

    if (!reducedMotion) {
        LaunchedEffect(Unit) {
            val start = withFrameNanos { it }
            while (true) {
                withFrameNanos { now ->
                    val elapsedMillis = (now - start) / 1_000_000L
                    trip = (elapsedMillis % TRIP_MILLIS).toFloat() / TRIP_MILLIS
                    tripCount = (elapsedMillis / TRIP_MILLIS).toInt()
                }
            }
        }
    }

    // the deck: every person once, in a random order, before any repeats
    val deck = remember { ArrayDeque<Int>() }
    var person by remember { mutableIntStateOf(0) }
    LaunchedEffect(tripCount) {
        if (tripCount == 0) {
            return@LaunchedEffect
        }
        if (deck.isEmpty()) {
            deck.addAll(UR_PEOPLE.indices.filter { it != person }.shuffled())
        }
        person = deck.removeFirst()
    }

    // the docs keyframes: 1% -> 94% of the line, visible from 4% to 88%
    val position = if (reducedMotion) 0.85f else 0.01f + 0.93f * trip
    val alpha = when {
        reducedMotion -> 1f
        trip < 0.04f -> trip / 0.04f
        trip > 0.94f -> 0f
        trip > 0.88f -> 1f - (trip - 0.88f) / 0.06f
        else -> 1f
    }
    // a little bounce in the step, so the trip reads as a walk, not a slide
    val bob = if (reducedMotion) 0f else sin(trip * 2f * PI.toFloat() * 9f)

    val personBitmap = ImageBitmap.imageResource(id = UR_PEOPLE[person])

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(ROUTE_HEIGHT_DP.dp)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.CenterStart
    ) {

        // the dashed route
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .padding(horizontal = 6.dp)
                .drawBehind {
                    drawLine(
                        color = Pink.copy(alpha = 0.4f),
                        start = Offset(0f, 0f),
                        end = Offset(size.width, 0f),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))
                    )
                }
        )

        // the stops, painted over the line: you, the connector, the internet
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            RouteStop(stringResource(id = R.string.route_you), color = BlueLight, padStart = false)
            // the connector: the host flies it into the header on leaving this page
            val connector = LocalIntroConnector.current
            Box(
                modifier = Modifier
                    .size(CONNECTOR_SIZE_DP.dp)
                    .introConnectorHero(connector)
            ) {
                if (connector == null || !connector.floating) {
                    IntroConnectorMark(modifier = Modifier.size(CONNECTOR_SIZE_DP.dp))
                }
            }
            RouteStop(stringResource(id = R.string.route_internet), color = Color.White, padEnd = false)
        }

        // the traveller, above the stops: crisp pixels, a hint of white to
        // lift the tile off the line
        Image(
            bitmap = personBitmap,
            contentDescription = null,
            filterQuality = FilterQuality.None,
            modifier = Modifier
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    layout(constraints.maxWidth, placeable.height) {
                        val travel = constraints.maxWidth - placeable.width
                        placeable.placeRelative((travel * position).toInt(), 0)
                    }
                }
                .size(TRAVELLER_SIZE_DP.dp)
                .graphicsLayer {
                    this.alpha = alpha
                    translationY = -2.dp.toPx() * (bob + 1f) / 2f
                    rotationZ = 4f * bob
                }
                .drawBehind {
                    // the docs' drop-shadow(0 0 4px rgba(255,255,255,.3)), traced on
                    // the tile's own connector outline: its silhouette, tinted
                    // white, drawn a little larger a few times behind it
                    val layers = listOf(0.10f, 0.07f, 0.05f, 0.04f, 0.03f, 0.02f)
                    layers.forEachIndexed { index, layerAlpha ->
                        val scale = 1f + 0.035f * (index + 1)
                        val w = size.width * scale
                        val h = size.height * scale
                        drawImage(
                            image = personBitmap,
                            dstOffset = IntOffset(((size.width - w) / 2f).roundToInt(), ((size.height - h) / 2f).roundToInt()),
                            dstSize = IntSize(w.roundToInt(), h.roundToInt()),
                            alpha = layerAlpha,
                            colorFilter = ColorFilter.tint(Color.White),
                            filterQuality = FilterQuality.Low
                        )
                    }
                }
        )
    }
}

@Composable
private fun RouteStop(
    label: String,
    color: Color,
    padStart: Boolean = true,
    padEnd: Boolean = true,
) {
    Text(
        label,
        style = MaterialTheme.typography.bodySmall.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp
        ),
        color = color.copy(alpha = 0.9f),
        modifier = Modifier
            .background(Black)
            .padding(start = if (padStart) 8.dp else 0.dp, end = if (padEnd) 8.dp else 0.dp, top = 2.dp, bottom = 2.dp)
    )
}
