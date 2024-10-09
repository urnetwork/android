package com.bringyour.network.ui.components.overlays

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.bringyour.network.R
import com.bringyour.network.ui.components.ButtonStyle
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.theme.URNetworkTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.util.lerp
import com.bringyour.network.ui.theme.Black

@Composable
fun WelcomeAnimatedMainOverlay(
    animateIn: Boolean,
) {

    var isVisible by remember { mutableStateOf(animateIn) }
    var isBodyVisible by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()


    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

    val initialSize = screenWidthPx * 4
    val finalSize = with(density) { 256.dp.toPx() }
    val topOffsetFinal = with(density) { 122.dp.toPx() }

    var animationStarted by remember { mutableStateOf(false) }
    val animationProgress by animateFloatAsState(
        targetValue = if (animationStarted) 1f else 0f,
        animationSpec = tween(durationMillis = 1500, easing = LinearEasing), label = ""
    )
    var overlayClosed by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!overlayClosed) {
            delay(500)
            animationStarted = true

            delay(1500)
            isBodyVisible = true
        }
    }

    val animatedSize = lerp(initialSize, finalSize, animationProgress)
    val animatedTopPadding = lerp(0f, topOffsetFinal, animationProgress)

    val close: () -> Unit = {
        coroutineScope.launch {
            isBodyVisible = false

            delay(1000)

            isVisible = false

            delay(1000)

            overlayClosed = true
        }
    }

    if (!overlayClosed) {
        WelcomeAnimatedMainOverlay(
            isVisible,
            isBodyVisible,
            screenWidthPx,
            screenHeightPx,
            animatedSize,
            animatedTopPadding,
            close
        )
    }

}

@Composable
fun WelcomeAnimatedMainOverlay(
    isVisible: Boolean,
    isBodyVisible: Boolean,
    screenWidthPx: Float,
    screenHeightPx: Float,
    animatedSize: Float,
    animatedTopPadding: Float,
    close: () -> Unit
) {
    val context = LocalContext.current
    val backgroundBitmap: ImageBitmap = ImageBitmap.imageResource(context.resources, R.drawable.overlay_onboarding_bg)
    val vector = ImageVector.vectorResource(id = R.drawable.connect_mask)
    val painter = rememberVectorPainter(image = vector)

    AnimatedVisibility(
        visible = isVisible,
        enter = EnterTransition.None,
        exit = fadeOut(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    drawImage(
                        image = backgroundBitmap,
                        dstSize = IntSize(size.width.toInt(), size.height.toInt())
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val xOffset = (screenWidthPx - animatedSize) / 2

                // Draw the image
                translate(left = xOffset, top = animatedTopPadding) {
                    with(painter) {
                        draw(size = Size(animatedSize, animatedSize))
                    }
                }

                // top rectangle
                drawRect(
                    color = Black,
                    topLeft = Offset(0f, 0f),
                    size = Size(width = screenWidthPx + 2.dp.toPx(), height = animatedTopPadding + 1.dp.toPx())
                )

                // left rectangle
                drawRect(
                    color = Black,
                    topLeft = Offset(0f, animatedTopPadding),
                    size = Size(width = ((screenWidthPx - animatedSize) / 2) + 1.dp.toPx(), height = animatedSize)
                )

                // right rectangle
                drawRect(
                    color = Black,
                    topLeft = Offset(((screenWidthPx - animatedSize) / 2) + animatedSize - 1.dp.toPx(), animatedTopPadding),
                    size = Size(width = ((screenWidthPx - animatedSize) / 2) + 2.dp.toPx(), height = animatedSize)
                )

                // bottom rectangle
                drawRect(
                    color = Black,
                    topLeft = Offset(0f, animatedTopPadding + animatedSize - 1.dp.toPx()),
                    size = Size(width = screenWidthPx + 2.dp.toPx(), height = screenHeightPx)
                )
            }

            // "nicely done" content
            AnimatedVisibility(
                visible = isBodyVisible,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // top padding + mask size + 32.dp of room
                    Spacer(modifier = Modifier.height(122.dp + 256.dp + 32.dp))
                    Text(
                        stringResource(id = R.string.nicely_done),
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        stringResource(id = R.string.step_in),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.headlineLarge
                    )
                    Spacer(modifier = Modifier.height(48.dp))
                    URButton(
                        onClick = {
                            close()
                        },
                        style = ButtonStyle.OUTLINE
                    ) { buttonTextStyle ->
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 48.dp),
                        ) {
                            Text(
                                stringResource(id = R.string.enter),
                                style = buttonTextStyle
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun WelcomeAnimatedOverlayPreview() {

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

    val animatedSize = with(density) { 256.dp.toPx()}
    val topPadding = with(density) { 122.dp.toPx()}

    URNetworkTheme {
        WelcomeAnimatedMainOverlay(
            isVisible = true,
            isBodyVisible = true,
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx,
            animatedSize = animatedSize,
            animatedTopPadding = topPadding,
            close = {}
        )
    }
}

