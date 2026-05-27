package com.bringyour.network.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bringyour.network.ui.theme.BlueMedium
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.URNetworkTheme

@Composable
fun TermsCheckbox(
    checked: Boolean,
    onCheckChanged: (Boolean) -> Unit,
    focusRequester: FocusRequester? = null
) {

    var isFocused by remember { mutableStateOf(false) }

    val checkboxStr = remember { buildAnnotatedString {
        append("I agree to URnetwork's ")

        withLink(LinkAnnotation.Url("https://ur.io/terms")) {
            withStyle(
                style = SpanStyle(
                    color = BlueMedium
                )
            ) {
                append("Terms and Services")
            }
        }

        append(" and ")

        withLink(LinkAnnotation.Url("https://ur.io/privacy")) {
            withStyle(
                style = SpanStyle(
                    color = BlueMedium
                )
            ) {
                append("Privacy Policy")
            }
        }

    } }

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

        Text(
            text = checkboxStr,
            style = MaterialTheme.typography.bodyMedium.copy(color = TextMuted),
        )
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