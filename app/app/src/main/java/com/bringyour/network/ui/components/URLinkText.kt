package com.bringyour.network.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowOutward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
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
import com.bringyour.network.ui.theme.Pink
import androidx.compose.ui.text.TextLinkStyles
import com.bringyour.network.ui.theme.TextMuted
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

/**
 * A plain body sentence followed by a small "Learn more" link in the pink
 * accent with an outward arrow, which opens `url`. The sentence itself is not
 * tappable; only the link is. The link rides inline after the sentence, so it
 * sits on the same line when it fits and wraps to the next otherwise.
 */
@Composable
fun URLearnMoreText(
    text: String,
    linkText: String,
    url: String,
    color: Color = TextMuted,
    fontSize: TextUnit = 14.sp
) {
    val iconId = "external-link"
    val linkStyle = SpanStyle(color = Pink, textDecoration = TextDecoration.None)
    val annotatedString = remember(text, linkText, url) {
        buildAnnotatedString {
            append(text)
            append("\u00A0")
            withLink(
                LinkAnnotation.Url(
                    url,
                    styles = TextLinkStyles(style = linkStyle, pressedStyle = linkStyle)
                )
            ) {
                append(linkText)
                // a non-breaking space keeps the arrow on the link's line
                append("\u00A0")
                appendInlineContent(iconId, "\u2197")
            }
        }
    }
    val inlineContent = mapOf(
        iconId to InlineTextContent(
            Placeholder(
                width = fontSize,
                height = fontSize,
                placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter
            )
        ) {
            Icon(
                Icons.Filled.ArrowOutward,
                contentDescription = null,
                tint = Pink,
                modifier = Modifier.fillMaxSize()
            )
        }
    )

    Text(
        text = annotatedString,
        inlineContent = inlineContent,
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = fontSize),
        color = color
    )
}

@Preview
@Composable
fun URLinkTextPreview() {
    URNetworkTheme {
        URLinkText(text = "Hello world", url = "https://ur.io")
    }
}