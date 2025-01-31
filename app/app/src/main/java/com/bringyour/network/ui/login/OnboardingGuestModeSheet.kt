package com.bringyour.network.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bringyour.network.ui.components.TermsCheckbox
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.URNetworkTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingGuestModeSheet(
    isPresenting: Boolean,
    setIsPresenting: (Boolean) -> Unit,
    onCreateGuestNetwork: () -> Unit
) {

    val scope = rememberCoroutineScope()
    var termsAgreed by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState()

    if (isPresenting) {

        ModalBottomSheet(
            onDismissRequest = {
                setIsPresenting(false)
            },
            sheetState = sheetState,
        ) {

            Column(
                modifier = Modifier
                    .padding(16.dp)
            ) {

                Text(
                    "Try guest mode.",
                    style = MaterialTheme.typography.headlineLarge,
                )

                Text(
                    "Step into the internet \nas it should be.",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextMuted
                )

                Spacer(modifier = Modifier.height(32.dp))

                TermsCheckbox(
                    checked = termsAgreed,
                    onCheckChanged = {
                        termsAgreed = it
                    },
                )

                Spacer(modifier = Modifier.height(48.dp))

                URButton(
                    onClick = {
                        onCreateGuestNetwork()
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            if (!sheetState.isVisible) {
                                setIsPresenting(false)
                            }
                        }

                    },
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

                Spacer(modifier = Modifier.height(16.dp))

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