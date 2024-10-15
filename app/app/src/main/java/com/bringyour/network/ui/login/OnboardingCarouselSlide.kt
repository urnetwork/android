package com.bringyour.network.ui.login

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.bringyour.network.R
import com.bringyour.network.ui.theme.URNetworkTheme

@Composable
fun OnboardingCarouselSlide(
    painterResourceId: Int,
    contentDescription: String,
    contentVisible: Boolean,
    content: @Composable () -> Unit
) {
    Box(
        // modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(256.dp)
                .clipToBounds(),
            contentAlignment = Alignment.Center,
        ) {

            Box(
                modifier = Modifier
                    .zIndex(0f)
                    .fillMaxSize()
            ) {
                Image(
                    painter = painterResource(id = painterResourceId),
                    contentDescription = contentDescription,
                    modifier = Modifier.size(256.dp)
                )
            }

            Image(
                painter = painterResource(id = R.drawable.connect_mask),
                contentDescription = "Clickable Image",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(512.dp)
                    .align(Alignment.Center)
                    .zIndex(1f)
            )

        }

        AnimatedVisibility(
            visible = contentVisible,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it / 2 })
        ) {
            content()
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun OnboardingCarouselSlidePreview() {
    URNetworkTheme {
        Scaffold() { innerPadding ->
            Column(modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                ,
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                OnboardingCarouselSlide(
                    painterResourceId = R.drawable.onboarding_carousel_1,
                    contentVisible = true,
                    contentDescription = "See all the world's content with URnetwork"
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("See all the", style = MaterialTheme.typography.headlineLarge)
                        Text("world's content", style = MaterialTheme.typography.headlineLarge)
                        Text("with URnetwork", style = MaterialTheme.typography.headlineMedium)
                    }
                }
            }
        }
    }
}