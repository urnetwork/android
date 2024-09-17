package com.bringyour.network.ui.components.overlays

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.bringyour.network.R
import com.bringyour.network.ui.theme.Black
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun WelcomeAnimatedOverlay(
    animateIn: Boolean
) {

    var isVisible by remember { mutableStateOf(animateIn) }

    LaunchedEffect(Unit) {
        launch {
            delay(2000)
            isVisible = false
        }
    }

    val context = LocalContext.current
    val backgroundBitmap: ImageBitmap = ImageBitmap.imageResource(context.resources, R.drawable.overlay_onboarding)

    val exitTransition = fadeOut()


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
                    .height(122.dp)
                    .background(Black)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
            ) {

                Box(
                    modifier = Modifier
                        .height(256.dp)
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Black)
                )

                Image(
                    painter = painterResource(id = R.drawable.connect_mask),
                    contentDescription = "Connect Mask",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(256.dp)
                )

                Box(
                    modifier = Modifier
                        .height(256.dp)
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Black)
                )

            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .background(Black)
            )

        }

    }

}

@Preview
@Composable
private fun WelcomeAnimatedOverlayPreview() {
    WelcomeAnimatedOverlay(
        animateIn = true
    )
}