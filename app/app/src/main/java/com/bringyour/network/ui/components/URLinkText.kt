package com.bringyour.network.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.text.ClickableText
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import com.bringyour.network.ui.theme.BlueMedium
import com.bringyour.network.ui.theme.URNetworkTheme

@Composable
fun URLinkText(text: String, url: String) {
    val context = LocalContext.current

    val annotatedString = buildAnnotatedString {
        withStyle(
            style = SpanStyle(
                color = BlueMedium,
                fontSize = 16.sp
            ),
        ) {
            append(text)
            addStringAnnotation(
                tag = "URL",
                annotation = url,start = 0,
                end = text.length
            )
        }
    }

    ClickableText(
        text = annotatedString,
        onClick = { offset ->
            annotatedString.getStringAnnotations("URL", start = offset, end = offset)
                .firstOrNull()?.let { annotation ->
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(annotation.item))
                    context.startActivity(intent)
                }
        }
    )
}

@Preview
@Composable
fun URLinkTextPreview() {
    URNetworkTheme {
        URLinkText(text = "Hello world", url = "https://ur.io")
    }
}