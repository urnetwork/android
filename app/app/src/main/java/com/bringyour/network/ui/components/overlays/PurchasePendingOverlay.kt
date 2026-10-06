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
import com.bringyour.network.ui.theme.Pink
import com.bringyour.network.ui.theme.URNetworkTheme

/**
 * Shown when Google Play ACCEPTED a purchase but has not completed it -- it is waiting
 * on an approval (a child needing a parent's OK) or an out-of-band payment.
 *
 * This state used to be invisible. `acknowledgePurchases` filters to PURCHASED, found
 * nothing for a pending purchase, quietly stopped the spinner and returned -- no
 * success, no error, no message. The user is left on the plan screen with nothing to
 * go on, concludes it failed, and tries to buy again.
 *
 * So the one job of this overlay is to say: it worked, it is not done yet, and you do
 * NOT need to pay again.
 */
@Composable
fun PurchasePendingOverlay(
    onDismiss: () -> Unit
) {

    OverlayBackground(
        onDismiss = onDismiss,
        bgImageResourceId = R.drawable.overlay_plan_upgraded_bg
    ) {

        OverlayContent(
            backgroundColor = Pink,
        ) {
            Text(
                stringResource(id = R.string.purchase_pending_title),
                style = MaterialTheme.typography.headlineMedium,
                color = Black
            )

            Text(
                stringResource(id = R.string.purchase_pending_body),
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
                        stringResource(id = R.string.got_it),
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
private fun PurchasePendingOverlayPreview() {
    URNetworkTheme {
        PurchasePendingOverlay(
            onDismiss = {}
        )
    }
}
