package com.bringyour.network.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bringyour.network.ui.theme.BlueMedium
import com.bringyour.network.ui.theme.URNetworkTheme
import com.bringyour.network.ui.theme.ppNeueBitBold

enum class ButtonStyle {
    PRIMARY, SECONDARY
}

val buttonTextStyle = TextStyle(
    fontFamily = ppNeueBitBold,
    fontSize = 24.sp
)

@Composable
fun URButton(
    // text: String,
    onClick: () -> Unit,
    style: ButtonStyle = ButtonStyle.PRIMARY,
    content: @Composable (TextStyle) -> Unit
) {

    val buttonColors = when (style) {
        ButtonStyle.PRIMARY -> ButtonDefaults.buttonColors(
            containerColor = BlueMedium,
            contentColor = Color.White
        )
        ButtonStyle.SECONDARY -> ButtonDefaults.buttonColors(
            containerColor = Color.White,
            contentColor = Color.Black
        )
    }

    return Button(
        onClick = onClick,
        colors = buttonColors,
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)

    ) {

        content(buttonTextStyle)
    }
}

@Preview
@Composable
fun PrimaryButtonPreview() {
    URNetworkTheme {
        URButton(
            onClick = {}
        ) {
            Text("Get Started")
        }
    }
}

@Preview
@Composable
fun SecondaryButtonPreview() {
    URNetworkTheme {
        URButton(
            style = ButtonStyle.SECONDARY,
            onClick = {}
        ) {
            Text("Get Started")
        }
    }
}