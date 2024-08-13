package com.bringyour.network.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bringyour.network.ui.theme.Blue500
import com.bringyour.network.ui.theme.MainTintedBackgroundBase
import com.bringyour.network.ui.theme.TextFaint
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.URNetworkTheme

@Composable
fun URSearchInput(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    placeholder: String = "",
    keyboardController: SoftwareKeyboardController?
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(28.dp))
            .background(MainTintedBackgroundBase)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = "Search Icon",
                tint = TextMuted
            )

            Spacer(modifier = Modifier.width(12.dp))

            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                cursorBrush = SolidColor(Blue500),
                modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(color = Color.LightGray),
                decorationBox = { innerTextField ->
                if (value.text.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = TextStyle(color = TextFaint)
                    )
                }
                    innerTextField()
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Search
                ),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        keyboardController?.hide()
                    }
                )
            )
        }
    }
}

@Preview
@Composable
fun URSearchInputPreview() {
    URNetworkTheme {
        URSearchInput(
            value = TextFieldValue("Hello world"),
            onValueChange = {},
            keyboardController = LocalSoftwareKeyboardController.current
        )
    }
}

@Preview
@Composable
fun URSearchInputEmptyPreview() {
    URNetworkTheme {
        URSearchInput(
            value = TextFieldValue(""),
            onValueChange = {},
            placeholder = "Search for all locations",
            keyboardController = LocalSoftwareKeyboardController.current
        )
    }
}