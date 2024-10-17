package com.bringyour.network.ui.feedback

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bringyour.network.R
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.components.URLinkText
import com.bringyour.network.ui.components.URTextInput
import com.bringyour.network.ui.components.overlays.OverlayMode
import com.bringyour.network.ui.shared.viewmodels.OverlayViewModel
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.URNetworkTheme

@Composable
fun FeedbackScreen(
    feedbackViewModel: FeedbackViewModel = hiltViewModel(),
    overlayViewModel: OverlayViewModel,
) {

    FeedbackScreen(
        feedbackMsg = feedbackViewModel.feedbackMsg,
        setFeedbackMsg = feedbackViewModel.setFeedbackMsg,
        sendFeedback = feedbackViewModel.sendFeedback,
        launchOverlay = overlayViewModel.launch,
        isSendEnabled = feedbackViewModel.isSendEnabled
    )

}

@Composable
fun FeedbackScreen(
    feedbackMsg: TextFieldValue,
    setFeedbackMsg: (TextFieldValue) -> Unit,
    sendFeedback: () -> Unit,
    launchOverlay: (OverlayMode) -> Unit,
    isSendEnabled: Boolean,
) {

    val keyboardController = LocalSoftwareKeyboardController.current

    val submitFeedback = {
        if (feedbackMsg.text.isNotEmpty()) {

            sendFeedback()

            launchOverlay(OverlayMode.FeedbackSubmitted)

            setFeedbackMsg(TextFieldValue())

        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
            .padding(16.dp)
            .imePadding(),
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
                    if (feedbackMsg.text.isNotEmpty()) {
                        submitFeedback()
                    }
                    keyboardController?.hide()
                },
                keyboardController = keyboardController
            )
        }

        URButton(
            onClick = {
                submitFeedback()
                keyboardController?.hide()
            },
            enabled = isSendEnabled
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
                    tint = if (isSendEnabled) Color.White else Color.Gray
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
            feedbackMsg = TextFieldValue(),
            setFeedbackMsg = {},
            sendFeedback = {},
            launchOverlay = {},
            isSendEnabled = true
        )
    }

}