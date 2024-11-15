package com.bringyour.network.ui.components.overlays

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.bringyour.network.R
import com.bringyour.network.ui.components.ButtonStyle
import com.bringyour.network.ui.components.TermsCheckbox
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.URNetworkTheme
import com.bringyour.network.ui.theme.Yellow

@Composable
fun OnboardingGuestModeOverlay(
    bodyVisible: Boolean,
    onDismiss: () -> Unit,
    onCreateGuestNetwork: () -> Unit
) {
    var termsAgreed by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val context = LocalContext.current
    val backgroundBitmap: ImageBitmap = ImageBitmap.imageResource(context.resources, R.drawable.overlay_guest_onboarding_bg)

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

        AnimatedVisibility(
            visible = bodyVisible,
            enter = EnterTransition.None,
            exit = fadeOut()
        ) {

            Box(modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close Overlay",
                        tint = Color.White,
                        modifier = Modifier
                            .focusRequester(focusRequester)
                            .clickable {
                                onDismiss()
                            }
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    OverlayContent(
                        backgroundColor = Yellow,
                    ) {
                        Text(
                            "Nicely done.",
                            style = MaterialTheme.typography.headlineMedium,
                            color = Black
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            "Step into the internet as it should be.",
                            style = MaterialTheme.typography.headlineLarge,
                            color = Black
                        )

                        Spacer(modifier = Modifier.height(128.dp))

                        TermsCheckbox(
                            checked = termsAgreed,
                            onCheckChanged = { it ->
                                termsAgreed = it
                            },
                            focusRequester = focusRequester
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        URButton(
                            onClick = {
                                onCreateGuestNetwork()
                            },
                            style = ButtonStyle.OUTLINE,
                            borderColor = if (termsAgreed) Black else TextMuted,
                            enabled = termsAgreed
                        ) { buttonTextStyle ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    "Enter",
                                    style = buttonTextStyle,
                                    color = if (termsAgreed) Black else TextMuted
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
private fun OnboardingGuestModePreview() {
    URNetworkTheme {
        OnboardingGuestModeOverlay(
            onDismiss = {},
            onCreateGuestNetwork = {},
            bodyVisible = true
        )
    }
}

@Preview(
    name = "Landscape Preview",
    device = "spec:width=1920dp,height=1080dp,dpi=480"
)
@Composable
private fun OnboardingGuestModeLandscapePreview() {
    URNetworkTheme {
        OnboardingGuestModeOverlay(
            onDismiss = {},
            onCreateGuestNetwork = {},
            bodyVisible = true
        )
    }
}