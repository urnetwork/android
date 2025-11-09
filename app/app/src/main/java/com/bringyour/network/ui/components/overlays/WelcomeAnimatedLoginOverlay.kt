package com.bringyour.network.ui.components.overlays

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bringyour.network.R
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.MainTintedBackgroundBase
import com.bringyour.network.ui.theme.URNetworkTheme
import kotlinx.coroutines.delay

@Composable
fun WelcomeAnimatedOverlayLogin() {
    val enterTransition = fadeIn()

    val context = LocalContext.current
    val backgroundBitmap: ImageBitmap = ImageBitmap.imageResource(context.resources, R.drawable.overlay_guest_onboarding_bg)

    val maskVector = ImageVector.vectorResource(id = R.drawable.connect_mask)
    val maskPainter = rememberVectorPainter(image = maskVector)

    var isVisible by remember { mutableStateOf(false) }

    // animate background visibility
    var isBackgroundVisible by remember { mutableStateOf(false) }

    var isAnimatingMaskSize by remember { mutableStateOf(false) }

    // animate the mask size
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp
    val maxScreenDimension = maxOf(screenWidth, screenHeight).value

    val initialSize = 256.dp.value
    val extraScaleFactor = 1.35f
    val targetScale = (maxScreenDimension / initialSize) * extraScaleFactor

    LaunchedEffect(Unit) {
        isVisible = true
        delay(500)
        isBackgroundVisible = true
        delay(500)
        isAnimatingMaskSize = true
    }

    val scale by animateFloatAsState(
        targetValue = if (isAnimatingMaskSize) targetScale else 1f,
        animationSpec = tween(durationMillis = 2000) // Adjust duration as needed
    )

    AnimatedVisibility(
        visible = isVisible,
        enter = enterTransition,
        exit = ExitTransition.None,
    ) {

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MainTintedBackgroundBase)
                .pointerInput(Unit) {
                    // Intercept all touch events
                    // fixes a bug where account switcher was being toggled
                },
            contentAlignment = Alignment.Center
        ) {

            AnimatedVisibility(
                visible = isBackgroundVisible,
                enter = fadeIn(animationSpec = tween(durationMillis = 1000)), // Adjust duration as needed
                exit = ExitTransition.None,
            ) {

                Image(
                    bitmap = backgroundBitmap,
                    contentDescription = "Entrance Background",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            BoxWithConstraints(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                val baseImageSize = 256.dp
                val scaledSize = baseImageSize * scale
                val horizontalBarSize = (maxWidth - scaledSize) / 2
                val verticalBarSize = (maxHeight - scaledSize) / 2

                // Top bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(verticalBarSize + 2.dp) // add pixels to avoid bar spacing
                        .align(Alignment.TopCenter)
                        .background(Black)
                )

                // Bottom bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(verticalBarSize + 2.dp)
                        .align(Alignment.BottomCenter)
                        .background(Black)
                )

                // Left bar
                Box(
                    modifier = Modifier
                        .width(horizontalBarSize + 2.dp)
                        .height(scaledSize)
                        .align(Alignment.CenterStart)
                        .background(Black)
                )

                // Right bar
                Box(
                    modifier = Modifier
                        .width(horizontalBarSize + 2.dp)
                        .height(scaledSize)
                        .align(Alignment.CenterEnd)
                        .background(Black)
                )

                // Centered mask
                Image(
                    painter = maskPainter,
                    contentDescription = "Globe mask",
                    modifier = Modifier
                        .size(baseImageSize)
                        .scale(scale)
                        .align(Alignment.Center)
                )

            }
        }
    }
}

@Preview
@Composable
private fun WelcomeAnimatedOverlayLoginPreview() {
    URNetworkTheme {
        WelcomeAnimatedOverlayLogin()
    }
}