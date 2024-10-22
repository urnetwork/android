package com.bringyour.network.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
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
    keyboardController: SoftwareKeyboardController?,
    onSearch: () -> Unit = {},
    onClear: () -> Unit,
) {

    var isFocused by remember { mutableStateOf(false) }
    val isFocusedBgColor = MainTintedBackgroundBase.copy(alpha = 0.6f)
    val bgColor = animateColorAsState(
        targetValue = if (isFocused) {
            isFocusedBgColor
        } else {
            MainTintedBackgroundBase
        }, label = ""
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(bgColor.value)
            .onFocusChanged {
                isFocused = it.isFocused
            }
            .focusable()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
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
                modifier = Modifier
                    .weight(1f)
                    .onKeyEvent {

                        if (it.key == Key.Enter) {
                            onSearch()
                            keyboardController?.hide()
                        }

                        false
                    },
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
                        onSearch()
                        keyboardController?.hide()
                    }
                ),
                maxLines = 1
            )
            
            if (value.text.isNotEmpty()) {
                Icon(
                    imageVector = Icons.Default.Clear,
                    contentDescription = "Clear text",
                    modifier = Modifier
                        .clickable {
                            onClear()
                            keyboardController?.hide()
                                   },
                    tint = TextMuted
                )
            }
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
            keyboardController = LocalSoftwareKeyboardController.current,
            onClear = {}
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
            keyboardController = LocalSoftwareKeyboardController.current,
            onClear = {}
        )
    }
}