package com.bringyour.network.ui.feedback

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bringyour.network.R
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.components.URTextInput
import com.bringyour.network.ui.components.URTextInputLabel
import com.bringyour.network.ui.components.overlays.OverlayMode
import com.bringyour.network.ui.shared.managers.rememberReviewManager
import com.bringyour.network.ui.shared.viewmodels.OverlayViewModel
import com.bringyour.network.ui.theme.Pink
import com.bringyour.network.ui.theme.URNetworkTheme
import com.bringyour.network.utils.isTablet
import com.bringyour.sdk.DeviceLocal
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
        isSendEnabled = feedbackViewModel.isSendEnabled,
        starCount = feedbackViewModel.starCount,
        setStarCount = feedbackViewModel.setStarCount,
        device = feedbackViewModel.device
    )

}

@Composable
fun FeedbackScreen(
    feedbackMsg: TextFieldValue,
    setFeedbackMsg: (TextFieldValue) -> Unit,
    sendFeedback: () -> Unit,
    launchOverlay: (OverlayMode) -> Unit,
    isSendEnabled: Boolean,
    starCount: Int,
    setStarCount: (Int) -> Unit,
    device: DeviceLocal?
) {

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val reviewManagerRequest = rememberReviewManager(device)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()


    val submitFeedback = {
        if (feedbackMsg.text.isNotEmpty()) {

            sendFeedback()

            launchOverlay(OverlayMode.FeedbackSubmitted)

            setFeedbackMsg(TextFieldValue())

            if (starCount == 5) {
                scope.launch {
                    delay(5000)
                    val activity = context as? android.app.Activity
                    activity?.let {
                        reviewManagerRequest.launchReviewFlow(it)
                    }
                }
            }

            setStarCount(0)
        }
    }
    Scaffold() { innerPadding ->

        if (isTablet() && !isLandscape) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(innerPadding)
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
                        isSendEnabled = isSendEnabled,
                        starCount = starCount,
                        setStarCount = setStarCount
                    )
                }

            }
        } else if (isTablet() && isLandscape) {
            Column(
                modifier = Modifier
                    .width(512.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(innerPadding)
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
                        isSendEnabled = isSendEnabled,
                        starCount = starCount,
                        setStarCount = setStarCount
                    )
                }

            }
        } else {
            Column(
                modifier = Modifier
                    .width(512.dp)
                    .fillMaxHeight()
                    .padding(horizontal = 16.dp)
                    .padding(innerPadding)
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
                    isSendEnabled = isSendEnabled,
                    starCount = starCount,
                    setStarCount = setStarCount
                )
            }
        }

    }

}

@Composable
private fun FeedbackForm(
    feedbackMsg: TextFieldValue,
    setFeedbackMsg: (TextFieldValue) -> Unit,
    sendFeedback: () -> Unit,
    isSendEnabled: Boolean,
    starCount: Int,
    setStarCount: (Int) -> Unit,
) {

    val supportUrl = "https://discord.com/invite/RUNZXMwPRK"

    val uriHandler = LocalUriHandler.current

    val feedbackAnnotatedString = buildAnnotatedString {
        withStyle(
            style = MaterialTheme.typography.bodyLarge.toSpanStyle()
                .copy(color = Color.White)
        ) {
            append("Send us your feedback directly or ")

            pushStringAnnotation(
                tag = "URL",
                annotation = supportUrl
            )
            withStyle(
                style = SpanStyle(
                    color = Pink
                )
            ) {
                append("join our Discord ")
            }

            append("for direct support.")

            pop()
        }
    }

    val keyboardController = LocalSoftwareKeyboardController.current

    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    Column {
        Text(
            stringResource(id = R.string.feedback_header),
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(modifier = Modifier.height(64.dp))

        Text(
            text = feedbackAnnotatedString,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.pointerInput(Unit) {
                detectTapGestures { offset ->
                    layoutResult?.let { layoutResult ->
                        val position = layoutResult.getOffsetForPosition(offset)
                        val annotation = feedbackAnnotatedString
                            .getStringAnnotations(position, position)
                            .firstOrNull()

                        if (annotation?.tag == "URL") {
                            uriHandler.openUri(annotation.item)
                        }
                    }
                }
            },
            onTextLayout = { layoutResult = it }
        )

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

        Spacer(modifier = Modifier.height(16.dp))

        URTextInputLabel("How are we doing?")

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            for (index in 1..5) {
                Spacer(modifier = Modifier.width(8.dp))

                val starIcon: Painter = if (index <= starCount) {
                    rememberVectorPainter(image = Icons.Filled.Star)
                } else {
                    rememberVectorPainter(image = ImageVector.vectorResource(id = R.drawable.baseline_star_outline_24))
                }

                Icon(
                    painter = starIcon,
                    contentDescription = if (index <= starCount) "Filled star" else "Empty star",
                    tint = Pink,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable {
                            setStarCount(index)
                            // starCount.value = index
                        }
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
        }

    }

    Column {

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

        Spacer(modifier = Modifier.height(16.dp))
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
                    isSendEnabled = true,
                    starCount = 3,
                    setStarCount = {},
                    device = null
                )
            }
        }
    }
}