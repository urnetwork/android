package com.bringyour.network.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.bringyour.network.R
import com.bringyour.network.ui.theme.BlueDark
import com.bringyour.network.ui.theme.BlueLight
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.URNetworkTheme

@Composable
fun InfoIconWithOverlay(
    content: @Composable () -> Unit
) {

    var showOverlay by remember { mutableStateOf(false) }

    Box() {
        IconButton(
            onClick = {
                showOverlay = true
            },
            modifier = Modifier.size(16.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.icon_info),
                contentDescription = "Info",
                tint = TextMuted,
            )

            if (showOverlay) {
                InfoPopup(
                    onDismiss = {
                        showOverlay = false
                    }
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
fun InfoPopup(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Popup(
            onDismissRequest = onDismiss,
            alignment = Alignment.TopStart,
            offset = IntOffset(x = 0, y = 96),
            properties = PopupProperties(
                focusable = true
            )
        ) {
            Box(
                modifier = Modifier
                    .background(BlueDark, shape = RoundedCornerShape(12.dp))
                    .padding(16.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = {
                            // Prevent closing when tapping inside the overlay
                        })
                    }
                    .width(192.dp)
            ) {
                content()
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun InfoIconWithOverlayPreview() {
    URNetworkTheme {
        InfoIconWithOverlay() {
            Text("This is some information.")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun InfoPopupPreview() {
    URNetworkTheme {
        InfoPopup(
            onDismiss = {}
        ) {
            Column() {
                Row {
                    Text(
                        "Unlock even faster speeds and first",
                        style = MaterialTheme.typography.bodySmall,
                        color = BlueLight
                    )
                }
                Row {
                    Text(
                        "dibs on new features.",
                        style = MaterialTheme.typography.bodySmall,
                        color = BlueLight
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Become a supporter",
                        style = TextStyle(
                            fontSize = 12.sp,
                            lineHeight = 20.sp,
                            fontWeight = FontWeight(700),
                            color = BlueLight,
                        )
                    )
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Right Arrow",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
