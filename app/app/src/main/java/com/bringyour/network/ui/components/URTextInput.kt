package com.bringyour.network.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bringyour.network.ui.theme.Blue500
import com.bringyour.network.ui.theme.BlueMedium
import com.bringyour.network.ui.theme.TextDanger
import com.bringyour.network.ui.theme.TextFaint
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.URNetworkTheme

@Composable
fun URTextInput(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onSend: () -> Unit = {},
    onDone: () -> Unit = {},
    onGo: () -> Unit = {},
    placeholder: String = "",
    label: String?,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardController: SoftwareKeyboardController? = LocalSoftwareKeyboardController.current,
    isPassword: Boolean = false,
    isValidating: Boolean = false,
    isValid: Boolean = true,
    supportingText: String? = null,
    enabled: Boolean = true,
    maxLines: Int = 1
) {

    var isFocused by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val underlineColor = when {
        !enabled -> Color.Transparent
        isFocused -> BlueMedium
        else -> TextFaint
    }

    Column {

        if (label != null) {
            URTextInputLabel(text = label, inputIsValid = isValid)
        }

        Box(modifier = Modifier
            .fillMaxWidth()
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        cursorBrush = SolidColor(Blue500),
                        textStyle = TextStyle(color = Color.LightGray),
                        modifier = Modifier
                            // .weight(1f)
                            // .imePadding()
                            .fillMaxWidth()
                            .background(Color.Transparent)
//                            .focusable()
//                            .onFocusChanged {
////                                if (it.isFocused) {
////                                }
//                                coroutineScope.launch {
//                                    isFocused = it.isFocused
//                                    if (it.isFocused) {
//
//                                    }
//                                }
//                            }

//                            .then(
//                                if (enabled) {
//                                    Modifier
//                                        .focusable()
//                                        .onFocusChanged { state ->
//                                            Log.i("URTextInput", "focus changed: ${state.isFocused}")
//                                            isFocused = state.isFocused
//                                        }
//                                } else {
//                                    Modifier
//                                }
//                            )
                            // should not be focusable if disabled
                            ,
                        decorationBox = { innerTextField ->
                            if (value.text.isEmpty()) {
                                Text(
                                    text = if (isPassword) "************" else placeholder,
                                    style = TextStyle(color = TextFaint)
                                )
                            }
                            innerTextField()
                        },
                        keyboardOptions = keyboardOptions,
                        keyboardActions = KeyboardActions(
                            onSend = {
                                onSend()
                                keyboardController?.hide()
                            },
                            onDone = {
                                onDone()
                                keyboardController?.hide()
                            },
                            onGo = {
                                onGo()
                                keyboardController?.hide()
                            }
                        ),
                        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
                        enabled = enabled,
                        maxLines = maxLines
                    )

                    if (isValidating) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .width(16.dp)
                                .height(16.dp),
                            color = TextMuted,
                            trackColor = TextFaint,
                            strokeWidth = 2.dp
                        )
                    }

                    if (!isValidating && !isValid) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = "Warning",
                            modifier = Modifier.size(16.dp),
                            tint = TextDanger
                        )
                    }

                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            HorizontalDivider(
                color = underlineColor,
                thickness = 1.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            )
        }

        if (supportingText != null) {

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp),
                horizontalArrangement = Arrangement.Start
            ) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    supportingText.toString(),
                    style = TextStyle(
                        fontSize = 12.sp,
                        color = if (isValid) TextMuted else TextDanger
                    )
                )
            }

        }
        Spacer(modifier = Modifier.height(24.dp))

    }
}

@Preview()
@Composable()
private fun URTextInputEmptyPreview() {
    URNetworkTheme {
        URTextInput(
            value = TextFieldValue(""),
            onValueChange = {},
            placeholder = "Placeholder Text",
            label = "Email or Phone"
        )
    }
}

@Preview()
@Composable()
private fun URTextInputPreview() {
    URNetworkTheme {
        URTextInput(
            value = TextFieldValue("Hello world"),
            onValueChange = {},
            placeholder = "Placeholder Text",
            label = "Email or Phone"
        )
    }
}

@Preview()
@Composable()
private fun URTextInputPasswordPreview() {
    URNetworkTheme {
        URTextInput(
            value = TextFieldValue("Hello world"),
            onValueChange = {},
            placeholder = "***********",
            isPassword = true,
            label = "Password"
        )
    }
}

@Preview()
@Composable()
private fun URTextInputIsValidatingPreview() {
    URNetworkTheme {
        URTextInput(
            value = TextFieldValue("Hello world"),
            onValueChange = {},
            placeholder = "",
            isValidating = true,
            label = "Email or Phone"
        )
    }
}

@Preview()
@Composable()
private fun URTextInputErrorPreview() {
    URNetworkTheme {
        URTextInput(
            value = TextFieldValue("Hello world"),
            onValueChange = {},
            supportingText = "Error encountered",
            isValid = false,
            label = "Email or Phone"
        )
    }
}