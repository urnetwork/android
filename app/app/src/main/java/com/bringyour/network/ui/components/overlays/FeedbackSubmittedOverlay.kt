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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bringyour.network.R
import com.bringyour.network.ui.components.ButtonStyle
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.Pink
import com.bringyour.network.ui.theme.URNetworkTheme

@Composable
fun FeedbackSubmittedOverlay(
    onDismiss: () -> Unit
) {

    OverlayBackground(
        onDismiss = { onDismiss() },
        bgImageResourceId = R.drawable.overlay_feedback_bg
    ) {
        Box(
            modifier = Modifier
                .background(
                    Pink,
                    shape = RoundedCornerShape(12.dp)
                )
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Column() {

                Icon(
                    painter = painterResource(id = R.drawable.globe_filled),
                    contentDescription = "URnetwork globe filled"
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    "Feedback submitted.",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Black
                )

                Text(
                    "Thank you for sharing your feelings <3",
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

}

@Preview
@Composable
private fun FeedbackSubmittedOverlayPreview() {

    URNetworkTheme {
        FeedbackSubmittedOverlay(
            onDismiss = {}
        )
    }

}