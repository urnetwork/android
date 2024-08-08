package com.bringyour.network.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bringyour.network.ui.theme.Blue500
import com.bringyour.network.ui.theme.BlueMedium
import com.bringyour.network.ui.theme.TextFaint
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.URNetworkTheme

@Composable
fun URTextInput(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    placeholder: String = "",
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    isPassword: Boolean = false,
    isValidating: Boolean = false,
    error: String? = null,
) {
    Column() {
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
                            // .fillMaxWidth()
                            .weight(1f)
                            .background(Color.Transparent),
                        decorationBox = { innerTextField ->
                            if (value.text.isEmpty()) {
                                Text(
                                    text = if (isPassword) "***********" else placeholder,
                                    style = TextStyle(color = TextFaint)
                                )
                            }
                            innerTextField()
                        },
                        keyboardOptions = keyboardOptions,
                        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
                    )

                    if (isValidating) {
                        CircularProgressIndicator(
                            modifier = Modifier.width(16.dp).height(16.dp),
                            color = TextMuted,
                            trackColor = TextFaint,
                            strokeWidth = 2.dp
                        )
                    }

                    if (!isValidating && error != null) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = "Right Arrow",
                            modifier = Modifier.size(16.dp),
                            tint = Color.Gray
                        )
                    }

                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            HorizontalDivider(
                color = TextFaint,
                thickness = 1.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            )
        }

        if (error != null) {

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp),
                horizontalArrangement = Arrangement.Start
            ) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    error.toString(),
                    style = TextStyle(
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

        } else {
            Spacer(modifier = Modifier.height(16.dp))
        }

    }
}

@Preview()
@Composable()
fun URTextInputEmptyPreview() {
    URNetworkTheme {
        URTextInput(
            value = TextFieldValue(""),
            onValueChange = {},
            placeholder = "Placeholder Text"
        )
    }
}

@Preview()
@Composable()
fun URTextInputPreview() {
    URNetworkTheme {
        URTextInput(
            value = TextFieldValue("Hello world"),
            onValueChange = {},
            placeholder = "Placeholder Text"
        )
    }
}

@Preview()
@Composable()
fun URTextInputPasswordPreview() {
    URNetworkTheme {
        URTextInput(
            value = TextFieldValue("Hello world"),
            onValueChange = {},
            placeholder = "***********",
            isPassword = true
        )
    }
}

@Preview()
@Composable()
fun URTextInputIsValidatingPreview() {
    URNetworkTheme {
        URTextInput(
            value = TextFieldValue("Hello world"),
            onValueChange = {},
            placeholder = "***********",
            isValidating = true
        )
    }
}

@Preview()
@Composable()
fun URTextInputErrorPreview() {
    URNetworkTheme {
        URTextInput(
            value = TextFieldValue("Hello world"),
            onValueChange = {},
            error = "Error encountered"
        )
    }
}