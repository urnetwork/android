package com.bringyour.network.ui.components.overlays

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.bringyour.network.R
import com.bringyour.network.ui.theme.URNetworkTheme

@Composable
fun OverlayBackground(
    onDismiss: () -> Unit,
    bgImageResourceId: Int,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val imageBitmap: ImageBitmap = ImageBitmap.imageResource(context.resources, bgImageResourceId)

    Box(
        modifier = Modifier
            .fillMaxSize()

            .drawBehind {

                drawImage(
                    image = imageBitmap,
                    dstSize = IntSize(size.width.toInt(), size.height.toInt())
                )
            }
            .padding(16.dp)
            .pointerInput(Unit) {
                // Intercept all touch events
                // fixes a bug where account switcher was being toggled
            }
            .systemBarsPadding()
            // .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Close Overlay",
                tint = Color.White,
                modifier = Modifier.clickable {
                    onDismiss()
                }
            )
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            content()
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun FullScreenOverlayPreview() {
    URNetworkTheme {
        OverlayBackground(
            onDismiss = {},
            bgImageResourceId = R.drawable.overlay_guest_mode_bg
        ) {
            Text("Hello world")
        }
    }
}