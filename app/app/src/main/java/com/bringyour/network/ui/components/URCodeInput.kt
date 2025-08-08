package com.bringyour.network.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
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
    var moveFocus by remember { mutableStateOf(false) }
    // prevent focus infinite loop
    var shouldHandleFocusChange by remember { mutableStateOf(true) }

    val findFirstEmptyFocus = {
        val firstEmptyIndex = value.indexOfFirst { it.isEmpty() }

        if (firstEmptyIndex != -1) {
            shouldHandleFocusChange = false
            focusRequesters[firstEmptyIndex].requestFocus()
            shouldHandleFocusChange = true
        }
    }

    val findFirstEmptyIndex: () -> Int = {
        value.indexOfFirst { it.isEmpty() }.takeIf { it != -1 } ?: 0
    }

    LaunchedEffect(value, moveFocus) {
        if (moveFocus) {
            findFirstEmptyFocus()
            moveFocus = false
        }
    }

    Row(
        modifier = Modifier.height(24.dp),
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

                            // move focus if a character is entered
                            if (newValue.isNotEmpty()) {
                                moveFocus = true
                            }
                        } else {
                            // new value is greater than 1
                            // handle paste event

                            val firstEmptyIndex = findFirstEmptyIndex()
                            val charsToPaste = newValue.take(codeLength - firstEmptyIndex)
                            val newCode = value.toMutableList()

                            for (j in charsToPaste.indices) {
                                newCode[firstEmptyIndex + j] = charsToPaste[j].toString()
                            }
                            onValueChange(newCode)

                            moveFocus = true
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

                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),

                    enabled = enabled,
                    modifier = Modifier
                        .fillMaxSize()
                        .focusRequester(focusRequesters[i])
                        .onFocusChanged { focusState ->
                            if (shouldHandleFocusChange && focusState.isFocused) {
                                moveFocus = true
                            }
                        }
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = {
                                    // focus the first empty field
                                    findFirstEmptyFocus()
                                }
                            )
                        }
                        .onKeyEvent { keyEvent ->
                            if (keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DEL) {
                                val newCode = value.toMutableList()

                                if (value[i].isEmpty() && i > 0) {
                                    // this handles delete presses
                                    // move focus to the previous field if the current one is empty
                                    focusRequesters[i - 1].requestFocus()
                                    newCode[i - 1] = "" // clear the previous field
                                } else {
                                    newCode[i] = ""
                                }

                                onValueChange(newCode)
                                true
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
