package com.bringyour.network.ui.components.overlays

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import com.bringyour.network.R
import com.bringyour.network.ui.theme.URNetworkTheme

@Composable
fun WelcomeAnimatedOverlayLogin(
    isVisible: Boolean
) {
    val enterTransition = fadeIn()

    AnimatedVisibility(
        visible = isVisible,
        enter = enterTransition,
        exit = ExitTransition.None,
    ) {

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    // Intercept all touch events
                    // fixes a bug where account switcher was being toggled
                }
            // .systemBarsPadding()
            // .windowInsetsPadding(WindowInsets.systemBars),
        ) {

            Image(
                painter = painterResource(id = R.drawable.overlay_guest_onboarding_bg),
                contentDescription = "Overlay Background",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}

@Preview
@Composable
private fun WelcomeAnimatedOverlayLoginPreview() {
    URNetworkTheme {
        WelcomeAnimatedOverlayLogin(
            isVisible = true
        )
    }
}