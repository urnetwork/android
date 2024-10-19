package com.bringyour.network.ui.components.overlays

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.bringyour.network.R

@Composable
fun OverlayContent(
    backgroundColor: Color,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyUp) {
                    when (keyEvent.key) {
                        Key.Back -> {
                            onDismiss()
                            true
                        }
                        Key.Escape -> {
                            onDismiss()
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(512.dp)
                .background(
                    backgroundColor,
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(24.dp)
        ) {

            Icon(
                painter = painterResource(id = R.drawable.globe_filled),
                contentDescription = "URnetwork globe filled"
            )

            Spacer(modifier = Modifier.height(24.dp))

            content()
        }
    }
}