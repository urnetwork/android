package com.bringyour.network.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.BlueMedium
import com.bringyour.network.ui.theme.Red
import com.bringyour.network.ui.theme.TextFaint
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.URNetworkTheme
import com.bringyour.network.ui.theme.ppNeueBitBold

enum class ButtonStyle {
    PRIMARY, SECONDARY, OUTLINE, WARNING
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
    borderColor: Color? = null,
    isProcessing: Boolean = false,
    content: @Composable (TextStyle) -> Unit,
) {

    var isFocused by remember { mutableStateOf(false) }

    val buttonColors = when (style) {
        ButtonStyle.PRIMARY -> ButtonDefaults.buttonColors(
            containerColor = if (isFocused) BlueMedium.copy(alpha = 0.5f) else BlueMedium,
            contentColor = Color.White
        )
        ButtonStyle.SECONDARY -> ButtonDefaults.buttonColors(
            containerColor = if (isFocused)
                BlueMedium.copy(alpha = 0.5f)
            else Color.White,
            contentColor = if (isFocused) Color.White else Color.Black,
        )
        ButtonStyle.OUTLINE -> ButtonDefaults.buttonColors(
            containerColor = if (isFocused) BlueMedium.copy(alpha = 0.5f) else Color.Transparent,
            contentColor = TextMuted
        )
        ButtonStyle.WARNING -> ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = Red
        )
    }

    val baseModifier = when(style) {
        ButtonStyle.PRIMARY -> Modifier
            .fillMaxWidth()
        ButtonStyle.SECONDARY -> Modifier
            .fillMaxWidth()
        ButtonStyle.WARNING -> Modifier
            .fillMaxWidth()
            .border(1.dp, Red, RoundedCornerShape(100))
        ButtonStyle.OUTLINE -> when(enabled) {
            true -> Modifier
                .border(1.dp, borderColor ?: TextFaint, RoundedCornerShape(100))
            false -> Modifier
        }
    }

    return Button(
        onClick = onClick,
        colors = buttonColors,
        modifier = baseModifier.then(
            Modifier.defaultMinSize(minHeight = 48.dp)
                .onFocusChanged {
                    isFocused = it.isFocused
                }
                .focusable()
        ),
        enabled = enabled,

    ) {
        if (!isProcessing) {
            content(buttonTextStyle)
        } else {
            Column {
                CircularProgressIndicator(
                    modifier = Modifier
                        .width(24.dp)
                        .height(24.dp),
                    color = MaterialTheme.colorScheme.secondary,
                    trackColor = TextMuted,
                )
            }
        }
    }
}

@Preview
@Composable
private fun PrimaryButtonPreview() {
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
private fun PrimaryButtonIsProcessingPreview() {
    URNetworkTheme {
        URButton(
            onClick = {},
            isProcessing = true
        ) { buttonTextStyle ->
            Text("Get Started", style = buttonTextStyle)
        }
    }
}

@Preview
@Composable
private fun SecondaryButtonPreview() {
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
private fun OutlineButtonPreview() {
    URNetworkTheme {
        URButton(
            style = ButtonStyle.OUTLINE,
            onClick = {}
        ) { buttonTextStyle ->
            Text("Disconnect", style = buttonTextStyle)
        }
    }
}

@Preview
@Composable
private fun WarningButtonPreview() {
    URNetworkTheme {
        URButton(
            style = ButtonStyle.WARNING,
            onClick = {}
        ) { buttonTextStyle ->
            Text("Disconnect", style = buttonTextStyle)
        }
    }
}