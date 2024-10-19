package com.bringyour.network.ui.components.overlays

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bringyour.network.R
import com.bringyour.network.ui.components.ButtonStyle
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.Green100
import com.bringyour.network.ui.theme.URNetworkTheme

@Composable
fun PlanUpgradedOverlay(
    onDismiss: () -> Unit
) {
    OverlayBackground(
        onDismiss = onDismiss,
        bgImageResourceId = R.drawable.overlay_plan_upgraded_bg
    ) {
        OverlayContent(
            backgroundColor = Green100,
            onDismiss = onDismiss
        ) {
            Text(
                "You're premium.",
                style = MaterialTheme.typography.headlineMedium,
                color = Black
            )
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "Thanks for building the new internet with us.",
                style = MaterialTheme.typography.headlineLarge,
                color = Black
            )

            Spacer(modifier = Modifier.height(128.dp))

            URButton(
                onClick = {
                    onDismiss()
                },
                style = ButtonStyle.OUTLINE,
                borderColor = Black
            ) { buttonTextStyle ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        "Close",
                        style = buttonTextStyle,
                        color = Black
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun PlanUpgradedOverlayPreview() {
    URNetworkTheme {
        PlanUpgradedOverlay(
            onDismiss = {}
        )
    }
}

