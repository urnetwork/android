package com.bringyour.network.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bringyour.network.ui.theme.BlueMedium
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.URNetworkTheme
import com.bringyour.network.utils.isTv

@Composable
fun TermsCheckbox(
    checked: Boolean,
    onCheckChanged: (Boolean) -> Unit,
    focusRequester: FocusRequester? = null
) {

    val context = LocalContext.current

    var isFocused by remember { mutableStateOf(false) }

    val checkboxStr = buildAnnotatedString {
        append("I agree to URnetwork's ")

        pushStringAnnotation(
            tag = "URL",
            annotation = "https://ur.io/terms"
        )
        withStyle(
            style = SpanStyle(
                color = BlueMedium
            )
        ) {
            append("Terms and Services")
        }
        pop()

        append(" and ")

        pushStringAnnotation(
            tag = "URL",
            annotation = "https://ur.io/privacy"
        )
        withStyle(
            style = SpanStyle(
                color = BlueMedium
            )
        ) {
            append("Privacy Policy")
        }
        pop()

    }

    val checkboxModifier = if (focusRequester != null)
        Modifier
        .size(16.dp)
        .onFocusChanged {
            isFocused = it.isFocused
        }
        .focusRequester(focusRequester)
        .focusable()
    else Modifier
        .size(16.dp)

    Row(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Checkbox(
            modifier = checkboxModifier.then(
                if (isFocused) {
                    Modifier.background(TextMuted.copy(alpha = 0.3f), CircleShape) // Focused border color
                } else {
                    Modifier
                }
            ),
            checked = checked,
            onCheckedChange = {
                onCheckChanged(it)
            },

        )
        Spacer(modifier = Modifier.width(12.dp))

        ClickableText(
            text = checkboxStr,
            onClick = { offset ->
                checkboxStr.getStringAnnotations(
                    tag = "URL", start = offset, end = offset
                ).firstOrNull()?.let { annotation ->
                    // open the link in browser
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(annotation.item))
                    context.startActivity(intent)
                }
            },
            style = MaterialTheme.typography.bodyMedium.copy(color = TextMuted),
        )
    }

    if (focusRequester != null && isTv()) {
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }
    }

}

@Preview(showBackground = true)
@Composable
private fun TermsCheckboxCheckedPreview() {

    val focusRequester = remember { FocusRequester() }

    URNetworkTheme {
        TermsCheckbox(
            checked = true,
            onCheckChanged = {},
            focusRequester
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun TermsCheckboxUncheckedPreview() {

    val focusRequester = remember { FocusRequester() }

    URNetworkTheme {
        TermsCheckbox(
            checked = false,
            onCheckChanged = {},
            focusRequester
        )
    }
}