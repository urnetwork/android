package com.bringyour.network.ui.components.overlays

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                // Intercept all touch events
                // fixes a bug where account switcher was being toggled
            }
            // .systemBarsPadding()
            // .windowInsetsPadding(WindowInsets.systemBars),
    ) {

        Image(
            painter = painterResource(id = bgImageResourceId),
            contentDescription = "Overlay Background",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
            // contentScale = if (isLandscape) ContentScale.Crop else ContentScale.FillBounds
        )

        Box(modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
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
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                content()
            }
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