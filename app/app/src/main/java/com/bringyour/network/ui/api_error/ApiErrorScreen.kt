package com.bringyour.network.ui.api_error

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.bringyour.network.R
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.theme.Pink
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.Yellow

@Composable
fun ApiErrorScreen(
    refresh: () -> Unit,
    isLoading: Boolean
) {

    val supportUrl = "https://discord.com/invite/RUNZXMwPRK"
    val supportEmail = "support@ur.io"

    val uriHandler = LocalUriHandler.current

    val discordText = "Discord"
    val emailText = "support@ur.io"

    val supportFullText = stringResource(id = R.string.api_error_contact, emailText, discordText)

    val startDiscordIndex = supportFullText.indexOf(discordText)
    val endDiscordIndex = startDiscordIndex + discordText.length

    val startEmailIndex = supportFullText.indexOf(emailText)
    val endEmailIndex = startEmailIndex + emailText.length


    val supportAnnotatedString = buildAnnotatedString {
        withStyle(style = MaterialTheme.typography.bodyMedium.toSpanStyle().copy(color = TextMuted)) {
            append(supportFullText)
            // Style and annotate email
            addStyle(SpanStyle(color = Pink), startEmailIndex, endEmailIndex)
            addStringAnnotation(
                tag = "EMAIL",
                annotation = "mailto:$supportEmail",
                start = startEmailIndex,
                end = endEmailIndex
            )
            // Style and annotate Discord
            addStyle(SpanStyle(color = Pink), startDiscordIndex, endDiscordIndex)
            addStringAnnotation(
                tag = "URL",
                annotation = supportUrl,
                start = startDiscordIndex,
                end = endDiscordIndex
            )
        }
    }

    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }


    Scaffold() { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.Center
        ) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {

                Icon(
                    painter = painterResource(id = R.drawable.unstable),
                    contentDescription = "Unstable connection",
                    modifier = Modifier.size(64.dp)
                )

            }

            Spacer(modifier = Modifier.height(64.dp))

            Text(
                stringResource(id = R.string.error_loading_account_plan),
                style = MaterialTheme.typography.headlineSmall
            )

            Text(stringResource(id = R.string.please_try_again))

            Spacer(modifier = Modifier.height(16.dp))

            URButton(
                onClick = refresh,
                isProcessing = isLoading,
                enabled = !isLoading
            ) { btnStyle ->
                Text(
                    stringResource(id = R.string.retry),
                    style = btnStyle
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = supportAnnotatedString,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.pointerInput(Unit) {
                    detectTapGestures { offset ->
                        layoutResult?.let { layoutResult ->
                            val position = layoutResult.getOffsetForPosition(offset)
                            val annotation = supportAnnotatedString
                                .getStringAnnotations(position, position)
                                .firstOrNull()

                            when (annotation?.tag) {
                                "EMAIL" -> uriHandler.openUri(annotation.item)
                                "URL" -> uriHandler.openUri(annotation.item)
                            }
                        }
                    }
                },
                onTextLayout = { layoutResult = it }
            )

        }

    }

}