package com.bringyour.network.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
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

@Composable
fun CircleImage(
    size: Dp,
    imageResourceId: Int? = null,
    backgroundColor: Color,
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