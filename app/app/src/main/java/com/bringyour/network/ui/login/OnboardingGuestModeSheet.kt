package com.bringyour.network.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
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
import com.bringyour.network.ui.components.overlays.OverlayContent
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.MainTintedBackgroundBase
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.URNetworkTheme
import com.bringyour.network.ui.theme.Yellow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingGuestModeSheet(
    isPresenting: Boolean,
    setIsPresenting: (Boolean) -> Unit,
    onCreateGuestNetwork: () -> Unit
) {

    var termsAgreed by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )
    val context = LocalContext.current
    val backgroundBitmap: ImageBitmap = ImageBitmap.imageResource(context.resources, R.drawable.overlay_guest_onboarding_bg)


    if (isPresenting) {

        ModalBottomSheet(
            onDismissRequest = {
                setIsPresenting(false)
            },
            sheetState = sheetState,
            dragHandle = null,
            modifier = Modifier
                .padding(
                    top = WindowInsets.systemBars
                        .asPaddingValues()
                        .calculateTopPadding()
                ),
            contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
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
                    .padding(bottom = 16.dp)
            ) {

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Your drag handle content here
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(4.dp)
                            .background(MainTintedBackgroundBase, shape = RoundedCornerShape(2.dp))
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
                            onCheckChanged = {
                                termsAgreed = it
                            },
                            // focusRequester = focusRequester
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
private fun OnboardingGuestModeSheetPreview() {
    URNetworkTheme {

        Scaffold { innerPadding ->

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {

                OnboardingGuestModeSheet(
                    isPresenting = true,
                    setIsPresenting = {},
                    onCreateGuestNetwork = {}
                )

            }

        }

    }
}