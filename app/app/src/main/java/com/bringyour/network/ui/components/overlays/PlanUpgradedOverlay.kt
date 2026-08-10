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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bringyour.network.R
import com.bringyour.network.ui.components.ButtonStyle
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.Green100
import com.bringyour.network.ui.theme.URNetworkTheme

/**
 * Post-purchase overlay.
 *
 * This used to assert "You're premium." the instant the store or the payment sheet
 * reported success -- before acknowledgement, before any server confirmation. A lost
 * webhook meant the user was congratulated for a plan they never received.
 *
 * Now the overlay only claims what is actually known:
 *  - `confirmed = false` (server has not reported `currentSubscription` yet): the
 *    payment was received and confirmation is in progress. The confirmation poll runs
 *    behind this; when the server confirms, recomposition flips the copy to premium.
 *  - `confirmed = true`: the server says the subscription is active -- celebrate.
 */
@Composable
fun PlanUpgradedOverlay(
    confirmed: Boolean,
    onDismiss: () -> Unit
) {
    OverlayBackground(
        onDismiss = onDismiss,
        bgImageResourceId = R.drawable.overlay_plan_upgraded_bg
    ) {
        OverlayContent(
            backgroundColor = Green100,
        ) {
            if (confirmed) {
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
            } else {
                Text(
                    stringResource(id = R.string.payment_received_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = Black
                )
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    stringResource(id = R.string.payment_confirmation_processing),
                    style = MaterialTheme.typography.headlineLarge,
                    color = Black
                )
            }

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
            confirmed = true,
            onDismiss = {}
        )
    }
}

@Preview
@Composable
private fun PlanUpgradedOverlayUnconfirmedPreview() {
    URNetworkTheme {
        PlanUpgradedOverlay(
            confirmed = false,
            onDismiss = {}
        )
    }
}
