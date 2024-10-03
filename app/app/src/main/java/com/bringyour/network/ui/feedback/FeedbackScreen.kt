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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bringyour.network.MainApplication
import com.bringyour.network.R
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.components.URLinkText
import com.bringyour.network.ui.components.URTextInput
import com.bringyour.network.ui.components.overlays.OverlayMode
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.URNetworkTheme

@Composable
fun FeedbackScreen(
    feedbackViewModel: FeedbackViewModel = hiltViewModel(),
) {

    FeedbackScreen(
        isSending = feedbackViewModel.isSendingFeedback,
        feedbackMsg = feedbackViewModel.feedbackMsg,
        setFeedbackMsg = feedbackViewModel.setFeedbackMsg,
        sendFeedback = feedbackViewModel.sendFeedback
    )

}

@Composable
fun FeedbackScreen(
    isSending: Boolean,
    feedbackMsg: TextFieldValue,
    setFeedbackMsg: (TextFieldValue) -> Unit,
    sendFeedback: () -> Unit
) {

    val context = LocalContext.current
    val application = context.applicationContext as? MainApplication
    val overlayVc = application?.overlayVc

    val isBtnEnabled by remember {
        derivedStateOf {
            !isSending && feedbackMsg.text.isNotEmpty()
        }
    }

    val submitFeedback = {
        if (feedbackMsg.text.isNotEmpty()) {

            sendFeedback()

            overlayVc?.openOverlay(OverlayMode.FeedbackSubmitted.toString())

            setFeedbackMsg(TextFieldValue())

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
            Text(
                stringResource(id = R.string.feedback_header),
                style = MaterialTheme.typography.headlineSmall
            )

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
                value = feedbackMsg,
                onValueChange = { newValue ->
                    setFeedbackMsg(newValue)
                },
                label = stringResource(id = R.string.feedback_label),
                placeholder = stringResource(id = R.string.feedback_placeholder),
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
                Text(
                    stringResource(id = R.string.send),
                    style = buttonTextStyle
                )
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
private fun FeedbackScreenPreview() {

    URNetworkTheme {
        FeedbackScreen(
            isSending = false,
            feedbackMsg = TextFieldValue(),
            setFeedbackMsg = {},
            sendFeedback = {}
        )
    }

}