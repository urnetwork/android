package com.bringyour.network.ui.components.overlays

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.EaseOutExpo
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bringyour.network.R
import com.bringyour.network.ui.components.ButtonStyle
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.theme.URNetworkTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.Yellow

@Composable
fun WelcomeAnimatedMainOverlay(
    animateIn: Boolean,
) {

    var isVisible by remember { mutableStateOf(animateIn) }
    val coroutineScope = rememberCoroutineScope()

    var overlayClosed by rememberSaveable { mutableStateOf(false) }

    val close: () -> Unit = {

        coroutineScope.launch {
            isVisible = false

            delay(1000)

            overlayClosed = true
        }

    }

    if (!overlayClosed) {
        WelcomeAnimatedMainOverlay(
            isVisible,
            close
        )
    }

}

@Composable
fun WelcomeAnimatedMainOverlay(
    isVisible: Boolean,
    close: () -> Unit
) {
    val context = LocalContext.current
    val backgroundBitmap: ImageBitmap = ImageBitmap.imageResource(context.resources, R.drawable.overlay_guest_onboarding_bg)

    var isPresentedWelcomeCard by remember { mutableStateOf(false) }

    val density = LocalDensity.current

    val welcomeCardOffsetY by animateFloatAsState(
        targetValue = if (isPresentedWelcomeCard) 0f else with(density) { 400.dp.toPx() },
        animationSpec = tween(
            durationMillis = 1000,
            easing = EaseOutExpo
        )
    )

    LaunchedEffect(Unit) {
        delay(500)
        isPresentedWelcomeCard = true
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = EnterTransition.None,
        exit = fadeOut(),
    ) {

        Box(
            modifier = Modifier
                .fillMaxSize(),
            contentAlignment = Alignment.BottomCenter

        ) {
            Image(
                bitmap = backgroundBitmap,
                contentDescription = "Entrance Background",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds
            )

            Box(
                modifier = Modifier
                    .padding(16.dp)
                    .graphicsLayer {
                        translationY = welcomeCardOffsetY
                    }
            ) {

                /** Welcome card */
                OverlayContent(
                    backgroundColor = Yellow
                ) {
                    Column(
                        modifier = Modifier,
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            stringResource(id = R.string.nicely_done),
                            style = MaterialTheme.typography.headlineMedium,
                            color = Black
                        )
                        Text(
                            stringResource(id = R.string.step_in),
                            style = MaterialTheme.typography.headlineLarge,
                            color = Black
                        )
                        Spacer(modifier = Modifier.height(48.dp))
                        URButton(
                            onClick = {
                                close()
                            },
                            style = ButtonStyle.OUTLINE,
                            borderColor = Black
                        ) { buttonTextStyle ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    stringResource(id = R.string.enter),
                                    style = buttonTextStyle,
                                    color = Black
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
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

    URNetworkTheme {
        WelcomeAnimatedMainOverlay(
            isVisible = true,
            close = {}
        )
    }
}

