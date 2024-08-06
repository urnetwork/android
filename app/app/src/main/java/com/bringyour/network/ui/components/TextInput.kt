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
import androidx.compose.ui.unit.dp

@Composable
fun TextInput(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    placeholder: String = "" // Optional placeholder parameter
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
                            style = TextStyle(color = Color.Gray)
                        )
                    }
                    innerTextField()
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        HorizontalDivider(
            color = Color.Gray,
            thickness = 1.dp, // Adjust thickness as needed
            modifier = Modifier
                .align(Alignment.BottomCenter) // Align to the bottom of the Box
                .fillMaxWidth()
        )
    }
}