package com.bringyour.network.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.bringyour.network.ui.theme.BlueMedium
import com.bringyour.network.ui.theme.URNetworkTheme

@Composable
fun URLinkText(
    text: String,
    url: String,
    fontSize: TextUnit = 16.sp
) {
    val annotatedString = remember(text, url, fontSize) {
        buildAnnotatedString {
            withLink(LinkAnnotation.Url(url)) {
                withStyle(
                    style = SpanStyle(
                        color = BlueMedium,
                        fontSize = fontSize
                    ),
                ) {
                    append(text)
                }
            }
        }
    }

    Text(
        text = annotatedString,
    )
}

@Preview
@Composable
fun URLinkTextPreview() {
    URNetworkTheme {
        URLinkText(text = "Hello world", url = "https://ur.io")
    }
}