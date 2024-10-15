package com.bringyour.network.ui.login

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.bringyour.network.R
import com.bringyour.network.ui.theme.URNetworkTheme
import kotlinx.coroutines.delay

@Composable
fun OnboardingCarousel() {

    val slides = listOf(
        // slide 1
        OnboardingSlide(
            painterResourceId = R.drawable.onboarding_carousel_1,
            contentDescription = stringResource(id = R.string.see_world_content_description)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    stringResource(id = R.string.see_world_content),
                    style = MaterialTheme.typography.headlineLarge.copy(textAlign = TextAlign.Center)
                )
                Text(stringResource(id = R.string.with_urnetwork), style = MaterialTheme.typography.headlineMedium)
            }
        },
        // slide 2
        OnboardingSlide(
            painterResourceId = R.drawable.onboarding_carousel_2,
            contentDescription = stringResource(id = R.string.stay_private_description)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    stringResource(id = R.string.stay_private),
                    style = MaterialTheme.typography.headlineLarge.copy(textAlign = TextAlign.Center)
                )
                Text(stringResource(id = R.string.with_urnetwork), style = MaterialTheme.typography.headlineMedium)
            }
        },
        // slide 3
        OnboardingSlide(
            painterResourceId = R.drawable.onboarding_carousel_3,
            contentDescription = stringResource(id = R.string.build_right_description)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    stringResource(R.string.build_right),
                    style = MaterialTheme.typography.headlineLarge.copy(textAlign = TextAlign.Center)
                )
                Text(stringResource(id = R.string.with_urnetwork), style = MaterialTheme.typography.headlineMedium)
            }
        }
    )

    var activeSlideIndex by remember { mutableIntStateOf(0) }
    var contentVisible by remember { mutableStateOf(false) }

    LaunchedEffect(activeSlideIndex) {
        delay(1000)
        contentVisible = true
        delay(5000)
        contentVisible = false
        delay(500)
        activeSlideIndex = (activeSlideIndex + 1) % slides.size
    }

    Box(
        modifier = Modifier
            .fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        slides.forEachIndexed { index, slide ->
            AnimatedVisibility(
                visible = index == activeSlideIndex,
                enter = fadeIn(animationSpec = tween(durationMillis = 1000)),
                exit = fadeOut(animationSpec = tween(durationMillis = 1000))
            ) {
                OnboardingCarouselSlide(
                    painterResourceId = slide.painterResourceId,
                    contentDescription = slide.contentDescription,
                    contentVisible = contentVisible,
                    content = slide.content
                )
            }
        }
    }
}

data class OnboardingSlide(
    val painterResourceId: Int,
    val contentDescription: String,
    val content: @Composable () -> Unit
)


@Preview(showBackground = true)
@Composable
private fun OnboardingCarouselPreview() {
    URNetworkTheme {
        Scaffold() { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                OnboardingCarousel()
            }
        }
    }
}