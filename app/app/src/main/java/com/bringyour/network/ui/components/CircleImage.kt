package com.bringyour.network.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
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

@Composable
fun CircleImage(
    size: Dp,
    imageResourceId: Int? = null,
    backgroundColor: Color = MaterialTheme.colorScheme.primary,
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
            imageResourceId = R.drawable.ic_account_active_512,
            size = 40.dp
        )
    }
}

@Preview
@Composable
fun CircleImageEmptyPreview() {
    URNetworkTheme {
        CircleImage(
            size = 40.dp
        )
    }
}