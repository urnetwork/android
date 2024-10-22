package com.bringyour.network.ui.components.overlays

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.tooling.preview.Preview
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

    OverlayBackground(
        onDismiss = onDismiss,
        bgImageResourceId = R.drawable.overlay_guest_onboarding_bg
    ) {

        AnimatedVisibility(
            visible = bodyVisible,
            enter = EnterTransition.None,
            exit = fadeOut()
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