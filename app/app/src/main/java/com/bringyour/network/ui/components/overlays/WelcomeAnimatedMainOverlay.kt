package com.bringyour.network.ui.components.overlays

import android.widget.Space
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.vectorResource
import com.bringyour.network.ui.theme.Black

// import androidx.compose.ui.graphics.painterResource

@Composable
fun WelcomeAnimatedMainOverlay(
    animateIn: Boolean,
) {

    var isVisible by remember { mutableStateOf(animateIn) }
    var isBodyVisible by remember { mutableStateOf(false) }
    var isMaskExpanded by remember { mutableStateOf(true) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect (Unit) {
        launch {
            delay(2000)

            isMaskExpanded = false

        }
    }

    val close: () -> Unit = {
        coroutineScope.launch {
            isBodyVisible = false

            delay(1000)

            isVisible = false

            delay(1000)

            // isClosing = false
        }
    }

    WelcomeAnimatedMainOverlay(
        isVisible,
        isBodyVisible,
        isMaskExpanded,
        close
    )

}

@Composable
fun WelcomeAnimatedMainOverlay(
    isVisible: Boolean,
    isBodyVisible: Boolean,
    isMaskExpanded: Boolean,
    close: () -> Unit
) {

    // var isVisible by remember { mutableStateOf(animateIn) }
    // var maskExpanded by remember { mutableStateOf(true) }
    // var isBodyVisible by remember { mutableStateOf(false) }
    // val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val backgroundBitmap: ImageBitmap = ImageBitmap.imageResource(context.resources, R.drawable.overlay_onboarding)
    // val painter = painterResource(id = R.drawable.connector_globe)

    val vector = ImageVector.vectorResource(id = R.drawable.connect_mask)
    val painter = rememberVectorPainter(image = vector)
//    val initialSize = 1024.dp  // Start large (1024.dp or any value you want)
//    val finalSize = 512.dp     // Target size

    val configuration = LocalConfiguration.current
    val screenWidthPx = with(LocalDensity.current) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(LocalDensity.current) { configuration.screenHeightDp.dp.toPx() }

    // States for animating the image and rectangles
    var targetSize by remember { mutableStateOf(screenWidthPx * 2) } // Initial size is 2x the screen width
    var rectSize by remember { mutableStateOf(0f) }

    // Animate the image size and rectangle dimensions together
    val animatedSize by animateFloatAsState(
        targetValue = targetSize,
        animationSpec = tween(durationMillis = 2000)  // Duration of the animation (2 seconds)
    )

    val animatedRectSize by animateFloatAsState(
        targetValue = rectSize,
        animationSpec = tween(durationMillis = 2000)  // Same animation duration for the rectangles
    )

    val density = LocalDensity.current

    // Start the animation after some delay
    LaunchedEffect(Unit) {
        delay(500)  // Optional: Delay the start of the animation
        targetSize = with(density) { 512.dp.toPx() }  // Final size is 512.dp
        rectSize = (screenWidthPx - targetSize) / 2  // Calculate rectangle width based on screen size
    }

//    // State for animating image size
//    var targetSize by remember { mutableStateOf(initialSize) }
//    var rectSize by remember { mutableFloatStateOf(0f) }
//
//    val animatedSize by animateDpAsState(
//        targetValue = targetSize,
//        animationSpec = tween(durationMillis = 2000),
//        label = ""
//    )
//
//    val animatedRectSize by animateFloatAsState(
//        targetValue = rectSize,
//        animationSpec = tween(durationMillis = 2000),
//        label = ""  // Same animation duration for the rectangles
//    )
//
//    LaunchedEffect(Unit) {
//        delay(500)  // Optional: Delay the start of the animation
//        targetSize = finalSize  // Trigger animation
//        rectSize = (size.width - finalSize) / 2
//    }

    // val animatedSizePx = with(LocalDensity.current) { animatedSize.toPx() }

    AnimatedVisibility(
        visible = isVisible,
        enter = EnterTransition.None,
        exit = fadeOut(),
    ) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {

                    drawImage(
                        image = backgroundBitmap,
                        dstSize = IntSize(size.width.toInt(), size.height.toInt())
                    )
                }
        ) {

            val topOffsetInDp = 122.dp
//            val topOffsetInPx = with(LocalDensity.current) { topOffsetInDp.toPx() }

            Canvas(modifier = Modifier.fillMaxSize()) {
                val topOffsetInPx = with(density) { topOffsetInDp.toPx() }

                // Calculate the X and Y offsets to center the image as it shrinks
                val xOffset = (screenWidthPx - animatedSize) / 2
                val yOffset = (screenHeightPx - animatedSize) / 2 + topOffsetInPx

                // Draw the image with the animated size and centered offset
                translate(left = xOffset, top = yOffset) {
                    with(painter) {
                        draw(
                            size = Size(
                                animatedSize,
                                animatedSize
                            )
                        )  // Use animated size for both width and height
                    }
                }

                // Draw the top rectangle, animating the height
                drawRect(
                    color = Black,
                    topLeft = Offset(0f, 0f),  // Start at the top of the canvas
                    size = Size(width = screenWidthPx, height = animatedRectSize)  // Animate height
                )

                // Draw the bottom rectangle, animating the height
                drawRect(
                    color = Color.Blue,
                    topLeft = Offset(0f, screenHeightPx - animatedRectSize),  // Start from the bottom
                    size = Size(width = screenWidthPx, height = animatedRectSize)
                )

                // Draw the left rectangle, animating the width
                drawRect(
                    color = Color.Blue,
                    topLeft = Offset(0f, 0f),  // Start on the left of the canvas
                    size = Size(width = animatedRectSize, height = screenHeightPx)  // Animate width
                )

                // Draw the right rectangle, animating the width
                drawRect(
                    color = Color.Blue,
                    topLeft = Offset(screenWidthPx - animatedRectSize, 0f),  // Start on the right side
                    size = Size(width = animatedRectSize, height = screenHeightPx)
                )
            }


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
                    Spacer(modifier = Modifier.height(32.dp))
                    Text(
                        "Nicely done",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        "Step into the internet as it should be.",
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
                                "Enter",
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
    URNetworkTheme {
        WelcomeAnimatedMainOverlay(
            isVisible = true,
            isBodyVisible = false,
            isMaskExpanded = false,
            close = {}
        )
    }
}
