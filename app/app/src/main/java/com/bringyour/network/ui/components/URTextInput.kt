package com.bringyour.network.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bringyour.network.ui.theme.TextFaint
import com.bringyour.network.ui.theme.URNetworkTheme

@Composable
fun URTextInput(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    placeholder: String = ""
) {
    Box(modifier = Modifier
        .fillMaxWidth()
    ) {
        Column {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = TextStyle(color = Color.LightGray),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Transparent),
                decorationBox = { innerTextField ->
                    if (value.text.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = TextStyle(color = TextFaint)
                        )
                    }
                    innerTextField()
                }
            )
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