package com.bringyour.network.ui.introduction

import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.bringyour.network.ui.theme.Green
import com.bringyour.network.ui.theme.NeueBitLargeTextStyle
import com.bringyour.network.ui.theme.TopBarTitleTextStyle

@Composable
fun BulletPoint(
    text: String
) {

    // the dot sits on the first line, and a bullet that wraps stays left-aligned
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {

        Box(
            modifier = Modifier
                .padding(top = 10.dp)
                .size(8.dp)
                .clip(CircleShape)
                .background(Green)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text,
            style = TopBarTitleTextStyle.copy(
                textAlign = TextAlign.Start,
                lineHeight = 28.sp
            )
        )
    }

}