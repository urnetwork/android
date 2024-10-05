package com.bringyour.network.ui.components.overlays

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bringyour.client.NetworkCreateArgs
import com.bringyour.network.LoginActivity
import com.bringyour.network.MainApplication
import com.bringyour.network.R
import com.bringyour.network.ui.components.ButtonStyle
import com.bringyour.network.ui.components.TermsCheckbox
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.TextFaint
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.URNetworkTheme
import com.bringyour.network.ui.theme.Yellow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

@Composable
fun OnboardingGuestModeOverlay(
    onDismiss: () -> Unit
) {

    val context = LocalContext.current
    val application = context.applicationContext as? MainApplication
    val loginActivity = context as? LoginActivity

    var termsAgreed by remember { mutableStateOf(false) }
    var inProgress by remember { mutableStateOf(false) }
    var createNetworkError by remember { mutableStateOf<String?>(null) }

    val createGuestNetwork = {

        Log.i("OnboardingGuestModeOverlay", "created guest network")
        Log.i("OnboardingGuestModeOverlay", "terms agreed? $termsAgreed")

        if (termsAgreed) {
            val args = NetworkCreateArgs()
            args.terms = termsAgreed
            // args.guestMode = true

            application?.api?.networkCreate(args) { result, err ->
                runBlocking(Dispatchers.Main.immediate) {

                    Log.i("OnboardingGuestModeOverlay", "inside of response")

                    inProgress = false

                    if (err != null) {
                        Log.i("OnboardingGuestModeOverlay", "error ${err.message}")
                        createNetworkError = err.message
                    } else if (result.error != null) {
                        Log.i("OnboardingGuestModeOverlay", "error ${result.error.message}")
                        createNetworkError = result.error.message
                    } else if (result.network != null && result.network.byJwt.isNotEmpty()) {
                        createNetworkError = null

                        Log.i("OnboardingGuestModeOverlay", "should be logging in")

                        application.login(result.network.byJwt)

                        inProgress = true

                        loginActivity?.authClientAndFinish { error ->
                            inProgress = false

                            Log.i("OnboardingGuestModeOverlay", "inside authClientAndFinish: error is: $error")

                            createNetworkError = error
                            if (error == null) {
                                onDismiss()
                            }
                        }
                    } else {
                        createNetworkError = context.getString(R.string.create_network_error)
                    }
                }
            }
        }
    }

    OverlayBackground(
        onDismiss = { onDismiss() },
        bgImageResourceId = R.drawable.overlay_onboarding_bg
    ) {
        Box(
            modifier = Modifier
                .background(
                    Yellow,
                    shape = RoundedCornerShape(12.dp)
                )
                .fillMaxWidth()
                .padding(24.dp)

        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
            ) {

                Icon(
                    painter = painterResource(id = R.drawable.globe_filled),
                    contentDescription = "URnetwork globe filled"
                )

                Spacer(modifier = Modifier.height(24.dp))
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
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                URButton(
                    onClick = {
                        createGuestNetwork()
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
            onDismiss = {}
        )
    }
}