package com.bringyour.network.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.URNetworkTheme

@Composable
fun URTextInputLabel(
    text: String
) {
     Column {
         Row(
             modifier = Modifier.fillMaxWidth(),
             horizontalArrangement = Arrangement.Start
         ) {
             Text(
                 text,
                 style = TextStyle(
                     fontSize = 12.sp,
                     color = TextMuted
                 )
             )
         }
         Spacer(modifier = Modifier.height(8.dp))
     }
}

@Preview
@Composable
fun TextInputLabelPreview() {
    URNetworkTheme {
        URTextInputLabel(text = "Enter your email or phone")
    }
}
