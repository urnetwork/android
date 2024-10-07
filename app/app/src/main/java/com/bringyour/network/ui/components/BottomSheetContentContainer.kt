package com.bringyour.network.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.bringyour.network.ui.theme.MainBorderBase

@Composable
fun BottomSheetContentContainer(
    content: @Composable () -> Unit,
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp

    Box(
        modifier = Modifier
            .then(
                if (screenWidth > 640.dp) {
                    Modifier.fillMaxWidth()
                } else {
                    Modifier
                        .requiredWidth(LocalConfiguration.current.screenWidthDp.dp + 4.dp)
                        .fillMaxHeight()
                }
            )
            .offset(y = 1.dp)
            .border(
                1.dp,
                MainBorderBase,
                shape = RoundedCornerShape(
                    topStart = 12.dp,
                    topEnd = 12.dp,
                    bottomEnd = 0.dp,
                    bottomStart = 0.dp
                )
            )
    ) {

        content()

    }
}