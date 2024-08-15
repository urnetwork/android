package com.bringyour.network.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.BlueMedium
import com.bringyour.network.ui.theme.TextFaint
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.URNetworkTheme
import com.bringyour.network.ui.theme.ppNeueBitBold

enum class ButtonStyle {
    PRIMARY, SECONDARY, OUTLINE
}

val buttonTextStyle = TextStyle(
    fontFamily = ppNeueBitBold,
    fontSize = 24.sp
)

@Composable
fun URButton(
    onClick: () -> Unit,
    style: ButtonStyle = ButtonStyle.PRIMARY,
    enabled: Boolean = true,
    content: @Composable (TextStyle) -> Unit,
) {

    val buttonColors = when (style) {
        ButtonStyle.PRIMARY -> ButtonDefaults.buttonColors(
            containerColor = BlueMedium,
            contentColor = Color.White
        )
        ButtonStyle.SECONDARY -> ButtonDefaults.buttonColors(
            containerColor = Color.White,
            contentColor = Color.Black,
        )
        ButtonStyle.OUTLINE -> ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = TextMuted
        )
    }

    val modifier = when(style) {
        ButtonStyle.PRIMARY -> Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
        ButtonStyle.SECONDARY -> Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
        ButtonStyle.OUTLINE -> Modifier
            .border(1.dp, TextFaint, RoundedCornerShape(100))
            .defaultMinSize(minHeight = 48.dp)
            .padding(horizontal = 16.dp)
    }

    return Button(
        onClick = onClick,
        colors = buttonColors,
        modifier = modifier,
        enabled = enabled
    ) {

        content(buttonTextStyle)
    }
}

@Preview
@Composable
fun PrimaryButtonPreview() {
    URNetworkTheme {
        URButton(
            onClick = {},
        ) { buttonTextStyle ->
            Text("Get Started", style = buttonTextStyle)
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
        ) { buttonTextStyle ->
            Text("Get Started", style = buttonTextStyle)
        }
    }
}

@Preview
@Composable
fun OutlineButtonPreview() {
    URNetworkTheme {
        URButton(
            style = ButtonStyle.OUTLINE,
            onClick = {}
        ) { buttonTextStyle ->
            Text("Disconnect", style = buttonTextStyle)
        }
    }
}