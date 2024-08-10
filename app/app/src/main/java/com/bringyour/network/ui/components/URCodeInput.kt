package com.bringyour.network.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bringyour.network.ui.theme.BlueMedium
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.URNetworkTheme
import com.bringyour.network.ui.theme.ppNeueBitBold

@Composable
fun URCodeInput(
    value: List<String>,
    onValueChange: (List<String>) -> Unit,
    codeLength: Int,
    enabled: Boolean = true
) {

    val focusRequesters = remember { List(codeLength) { FocusRequester() } }

    Row(
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        for (i in 0 until codeLength) {

            Box(
                modifier = Modifier
                    .width(24.dp)
                    .height(24.dp),
                contentAlignment = Alignment.Center
            ) {
                BasicTextField(
                    value = value[i],
                    onValueChange = { newValue ->
                        if (newValue.length <= 1) {
                            val newCode = value.toMutableList().apply {
                                this[i] = newValue
                            }
                            onValueChange(newCode)

                            if (newValue.isNotEmpty() && i < codeLength - 1) {
                                focusRequesters[i + 1].requestFocus()
                            }
                        }
                    },
                    visualTransformation = VisualTransformation.None,
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 24.sp,
                        color = if (enabled) Color.White else TextMuted,
                        textAlign = TextAlign.Center,
                        fontFamily = ppNeueBitBold
                    ),
                    enabled = enabled,
                    modifier = Modifier
                        .fillMaxSize()
                        .focusRequester(focusRequesters[i])
                        .onKeyEvent { keyEvent ->
                            if (keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DEL) {
                                val newCode = value.toMutableList()

                                if (value[i].isEmpty() && i > 0) {
                                    // Move focus to the previous field if the current one is empty
                                    focusRequesters[i - 1].requestFocus()
                                    newCode[i - 1] = "" // Clear the previous field
                                } else {
                                    newCode[i] = ""
                                }

                                onValueChange(newCode)
                                true // Return true to indicate the event is consumed
                            } else {
                                false
                            }
                        }
                )
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(
                    color = if (value[i].isEmpty()) Color.White else BlueMedium,
                    thickness = 1.dp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                )
            }
        }
    }
}

@Preview
@Composable
fun URCodeInputPreview() {

    URNetworkTheme {
        URCodeInput(
            value = listOf("W", "2", "a", "3", "z", "4", "", ""),
            onValueChange = {},
            codeLength = 8
        )
    }
}

@Preview
@Composable
fun URCodeInputDisabledPreview() {

    URNetworkTheme {
        URCodeInput(
            value = listOf("W", "2", "a", "3", "z", "4", "B", "z"),
            onValueChange = {},
            codeLength = 8,
            enabled = false
        )
    }
}
