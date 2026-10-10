package com.bringyour.network.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bringyour.network.ui.theme.URNetworkTheme
import com.bringyour.network.R
import com.bringyour.network.ui.theme.Red400

/**
 * A filled circle of [backgroundColor], optionally with an image that fills
 * it, and [content] centered on it (Account -> Sessions centers a device
 * logo on the session's country color).
 */
@Composable
fun CircleImage(
    size: Dp,
    imageResourceId: Int? = null,
    backgroundColor: Color,
    content: @Composable BoxScope.() -> Unit = {},
) {

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(backgroundColor)
    ) {
        imageResourceId?.let {
            Image(
                painter = painterResource(id = it),
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
            )
        }
        Box(
            modifier = Modifier.matchParentSize(),
            contentAlignment = Alignment.Center,
            content = content,
        )
    }
}

@Preview
@Composable
fun CircleImagePreview() {
    URNetworkTheme {
        CircleImage(
            imageResourceId = R.mipmap.ic_launcher,
            size = 40.dp,
            backgroundColor = Red400
        )
    }
}

@Preview
@Composable
fun CircleImageEmptyPreview() {
    URNetworkTheme {
        CircleImage(
            size = 40.dp,
            backgroundColor = Red400
        )
    }
}