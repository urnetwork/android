package com.bringyour.network.ui.feedback

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.components.URLinkText
import com.bringyour.network.ui.components.URTextInput
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.URNetworkTheme

@Composable
fun FeedbackScreen() {

    var feedback by remember {
        mutableStateOf("")
    }
    var isSending by remember {
        mutableStateOf(false)
    }

    val submitFeedback = {
        isSending = true



        isSending = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text("Get in touch", style = MaterialTheme.typography.headlineSmall)

            Spacer(modifier = Modifier.height(64.dp))

            Text("Send us your feedback directly or take a look at", style = MaterialTheme.typography.bodyLarge, color = Color.White)

            Row(
                verticalAlignment = Alignment.Bottom
            ) {
                Text("our", style = MaterialTheme.typography.bodyLarge, color = Color.White)
                Spacer(modifier = Modifier.width(1.dp))
                URLinkText(text = "support resources", url = "https://support.bringyour.com/en/")
            }

            Spacer(modifier = Modifier.height(32.dp))

            URTextInput(
                value = TextFieldValue(feedback),
                onValueChange = { newValue ->
                    feedback = newValue.text
                },
                label = "Feedback box",
                placeholder = "Tell us how you really feel",
                maxLines = 5
            )
        }

        URButton(
            onClick = { /*TODO*/ },
            enabled = !isSending
        ) { buttonTextStyle ->
            Row {
                Text("Send", style = buttonTextStyle)
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Right Arrow",
                    modifier = Modifier.size(16.dp),
                    tint = if (!isSending) Color.White else Color.Gray
                )
            }
        }

    }

}

@Preview
@Composable
private fun ConnectPreview() {

    URNetworkTheme {
        FeedbackScreen()
    }

}