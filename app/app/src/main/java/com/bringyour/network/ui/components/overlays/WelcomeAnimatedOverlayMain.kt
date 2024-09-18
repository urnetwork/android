package com.bringyour.network.ui.components.overlays

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.bringyour.network.R
import com.bringyour.network.ui.components.ButtonStyle
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.URNetworkTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun WelcomeAnimatedOverlay(
    animateIn: Boolean
) {

    var maskExpanded by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        launch {
            delay(2000)

            maskExpanded = false

        }
    }

    val maskSize by animateDpAsState(targetValue = if (maskExpanded) 2000.dp else 248.dp,
        label = "mask size"
    )
    val topPaddingSize by animateDpAsState(targetValue = if (maskExpanded) 0.dp else 122.dp,
        label = "top padding size"
    )


    WelcomeAnimatedOverlay(
        // isVisible = isVisible,
        animateIn = animateIn,
        maskExpanded = maskExpanded,
        maskSize = maskSize,
        topPaddingSize = topPaddingSize
    )
}

@Composable
fun WelcomeAnimatedOverlay(
    // isVisible: Boolean,
    animateIn: Boolean,
    maskExpanded: Boolean,
    maskSize: Dp,
    topPaddingSize: Dp,
) {

    var isVisible by remember { mutableStateOf(animateIn) }
    var isClosing by remember { mutableStateOf(false) }
    var isBodyVisible by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val backgroundBitmap: ImageBitmap = ImageBitmap.imageResource(context.resources, R.drawable.overlay_onboarding)

    val exitTransition = fadeOut()

    LaunchedEffect(maskExpanded) {
        if (!maskExpanded) {
            delay(1000)
            isBodyVisible = true
        }
    }

    LaunchedEffect(isClosing) {

        launch {

            if (isClosing) {
                isBodyVisible = false

                delay(1000)

                isVisible = false

                delay(1000)

                isClosing = false
            }

        }


    }


    AnimatedVisibility(
        visible = isVisible,
        enter = EnterTransition.None,
        exit = exitTransition,
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

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    // .height(122.dp)
                    .height(topPaddingSize)
                    .background(Black)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
            ) {

                Box(
                    modifier = Modifier
                        .height(maskSize)
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Black)
                )

                Image(
                    painter = painterResource(id = R.drawable.connect_mask),
                    contentDescription = "Connect Mask",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        // .size(256.dp)
                        .size(maskSize)
                )

                Box(
                    modifier = Modifier
                        .height(maskSize)
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Black)
                )

            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .background(Black)
            ) {

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
                                // isVisible = false
                                isClosing = true
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
}

@Preview
@Composable
private fun WelcomeAnimatedOverlayPreview() {
    URNetworkTheme {
        WelcomeAnimatedOverlay(
            animateIn = true,
            // isVisible = true,
            maskExpanded = false,
            maskSize = 248.dp,
            topPaddingSize = 122.dp
        )
    }
}