package com.bringyour.network.ui.feedback

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
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
import com.bringyour.network.ui.theme.URNetworkTheme
import com.bringyour.network.utils.isTablet
import com.bringyour.network.utils.isTv

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

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val submitFeedback = {
        if (feedbackMsg.text.isNotEmpty()) {

            sendFeedback()

            launchOverlay(OverlayMode.FeedbackSubmitted)

            setFeedbackMsg(TextFieldValue())

        }
    }

    if (isTv()) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(32.dp),
        ) {

            Column {
                FeedbackForm(
                    feedbackMsg = feedbackMsg,
                    setFeedbackMsg = setFeedbackMsg,
                    sendFeedback = {
                        if (feedbackMsg.text.isNotEmpty()) {
                            submitFeedback()
                        }
                    },
                    isSendEnabled = isSendEnabled
                )
            }

        }

    } else if (isTablet() && !isLandscape) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {

            Column(
                modifier = Modifier
            ) {
                FeedbackForm(
                    feedbackMsg = feedbackMsg,
                    setFeedbackMsg = setFeedbackMsg,
                    sendFeedback = {
                        if (feedbackMsg.text.isNotEmpty()) {
                            submitFeedback()
                        }
                    },
                    isSendEnabled = isSendEnabled
                )
            }

        }
    } else if (isTablet() && isLandscape) {
        Column(
            modifier = Modifier
                .width(512.dp)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {

            Column {
                FeedbackForm(
                    feedbackMsg = feedbackMsg,
                    setFeedbackMsg = setFeedbackMsg,
                    sendFeedback = {
                        if (feedbackMsg.text.isNotEmpty()) {
                            submitFeedback()
                        }
                    },
                    isSendEnabled = isSendEnabled
                )
            }

        }
    } else {
        Column(
            modifier = Modifier
                .width(512.dp)
                .fillMaxHeight()
                .padding(16.dp)
                .imePadding(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {

            FeedbackForm(
                feedbackMsg = feedbackMsg,
                setFeedbackMsg = setFeedbackMsg,
                sendFeedback = {
                    if (feedbackMsg.text.isNotEmpty()) {
                        submitFeedback()
                    }},
                isSendEnabled = isSendEnabled
            )
        }
    }
}

@Composable
private fun FeedbackForm(
    feedbackMsg: TextFieldValue,
    setFeedbackMsg: (TextFieldValue) -> Unit,
    sendFeedback: () -> Unit,
    isSendEnabled: Boolean,
) {

    val keyboardController = LocalSoftwareKeyboardController.current

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
                sendFeedback()
                keyboardController?.hide()
            },
            keyboardController = keyboardController
        )
    }

    URButton(
        onClick = {
            sendFeedback()
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

@Preview
@Composable
private fun FeedbackScreenPreview() {

    URNetworkTheme {
        Scaffold(
            modifier = Modifier.fillMaxSize()
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .padding(paddingValues)
            ) {
                FeedbackScreen(
                    feedbackMsg = TextFieldValue(),
                    setFeedbackMsg = {},
                    sendFeedback = {},
                    launchOverlay = {},
                    isSendEnabled = true
                )
            }
        }
    }
}