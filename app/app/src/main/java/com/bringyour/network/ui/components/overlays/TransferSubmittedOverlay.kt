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
import com.bringyour.network.ui.theme.URNetworkTheme
import com.bringyour.network.ui.theme.Yellow

@Composable
fun TransferSubmittedOverlay(
    onDismiss: () -> Unit
) {
    OverlayBackground(
        onDismiss = { onDismiss() },
        bgImageResourceId = R.drawable.overlay_transfer_submitted_bg
    ) {
        OverlayContent(
            backgroundColor = Yellow
        ) {
            Text(
                stringResource(id = R.string.transfer_submitted),
                style = MaterialTheme.typography.headlineLarge,
                color = Black
            )
    
            Spacer(modifier = Modifier.height(24.dp))
    
            Text(
                stringResource(id = R.string.transfer_submitted_details),
                style = MaterialTheme.typography.bodyLarge,
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
private fun TransferSubmittedOverlayPreview() {
    URNetworkTheme {
        TransferSubmittedOverlay(
            onDismiss = {}
        )
    }
}