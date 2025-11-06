package com.bringyour.network.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
            .border(1.dp, Red, RoundedCornerShape(12.dp))
        ButtonStyle.OUTLINE -> when(enabled) {
            true -> Modifier
                .border(1.dp, borderColor ?: TextFaint, RoundedCornerShape(12.dp))
            false -> Modifier
        }
    }

    return Button(
        onClick = onClick,
        colors = buttonColors,
        modifier = baseModifier.then(
            Modifier.defaultMinSize(minHeight = 48.dp)
        ),
        enabled = enabled,
        shape = RoundedCornerShape(12.dp)

    ) {
        if (!isProcessing) {
            content(buttonTextStyle)
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
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