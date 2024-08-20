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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bringyour.network.MainApplication
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.components.URLinkText
import com.bringyour.network.ui.components.URTextInput
import com.bringyour.network.ui.components.overlays.OverlayMode
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.URNetworkTheme

@Composable
fun FeedbackScreen() {

    val context = LocalContext.current
    val application = context.applicationContext as? MainApplication
    val overlayVc = application?.overlayVc

    var feedback by remember {
        mutableStateOf(TextFieldValue())
    }
    var isSending by remember {
        mutableStateOf(false)
    }

    val isBtnEnabled by remember {
        derivedStateOf {
            !isSending && feedback.text.isNotEmpty()
        }
    }

    val submitFeedback = {
        if (feedback.text.isNotEmpty()) {
            isSending = true

            // todo create view controller that sends feedback to /feedback/send-feedback

            overlayVc?.openOverlay(OverlayMode.FeedbackSubmitted.toString())

            feedback = TextFieldValue()

            isSending = false
        }
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
                value = feedback,
                onValueChange = { newValue ->
                    feedback = newValue
                },
                label = "Feedback box",
                placeholder = "Tell us how you really feel",
                maxLines = 5,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Send
                ),
                onSend = {
                    submitFeedback()
                }
            )
        }

        URButton(
            onClick = {
                submitFeedback()
            },
            enabled = isBtnEnabled
        ) { buttonTextStyle ->
            Row {
                Text("Send", style = buttonTextStyle)
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Right Arrow",
                    modifier = Modifier.size(16.dp),
                    tint = if (isBtnEnabled) Color.White else Color.Gray
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