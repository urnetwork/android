package com.bringyour.network.ui.components.overlays

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bringyour.network.MainApplication
import com.bringyour.network.R
import com.bringyour.network.ui.components.ButtonStyle
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.Blue200
import com.bringyour.network.ui.theme.URNetworkTheme

@Composable
fun GuestModeOverlay(
    onDismiss: () -> Unit
) {

    val context = LocalContext.current
    val application = context.applicationContext as? MainApplication

    OverlayBackground(
        onDismiss = { onDismiss() },
        bgHorizontalOffset = 255f,
        bgImageResourceId = R.drawable.overlay_guest_mode_bg
    ) {
        Box(modifier = Modifier
            .background(
                Blue200,
                shape = RoundedCornerShape(12.dp)
            )
            .fillMaxWidth()
            .padding(24.dp)
        ) {
            Column() {
                Text(
                    "You're in guest mode.",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Black
                )
                Text(
                    "To start",
                    style = MaterialTheme.typography.headlineLarge,
                    color = Black
                )
                Text(
                    "earning, join",
                    style = MaterialTheme.typography.headlineLarge,
                    color = Black
                )
                Text(
                    "the network.",
                    style = MaterialTheme.typography.headlineLarge,
                    color = Black
                )

                Spacer(modifier = Modifier.height(128.dp))

                URButton(
                    onClick = {
                        application?.logout()
                    },
                    style = ButtonStyle.OUTLINE
                ) { buttonTextStyle ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text("Create an account", style = buttonTextStyle, color = Black)
                    }
                }
            }
        }
    }
}

@Preview
@Composable
fun GuestModeOverlayPreview() {
    URNetworkTheme {
        GuestModeOverlay(
            onDismiss = {}
        )
    }
}