package com.bringyour.network.ui.components.overlays

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bringyour.network.R
import com.bringyour.network.ui.theme.URNetworkTheme
import com.bringyour.network.utils.isTv

@Composable
fun OverlayBackground(
    onDismiss: () -> Unit,
    bgImageResourceId: Int,
    content: @Composable () -> Unit,
) {

    val focusRequester = remember { FocusRequester() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                // Intercept all touch events
                // fixes a bug where account switcher was being toggled
            }
            .focusGroup()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyUp) {
                    when (keyEvent.key) {
                        Key.Back -> {
                            Log.i("OverlayContent", "Back key pressed")
                            onDismiss()
                            true
                        }
                        Key.Escape -> {
                            Log.i("OverlayContent", "Escape key pressed")
                            onDismiss()
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            },
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
                    modifier = Modifier
                        .focusRequester(focusRequester)
                        .clickable {
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

    if (isTv()) {
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
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