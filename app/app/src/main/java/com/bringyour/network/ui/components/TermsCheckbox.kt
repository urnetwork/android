package com.bringyour.network.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
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
) {

    val context = LocalContext.current

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

    Row(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Checkbox(
            modifier = Modifier.size(16.dp),
            checked = checked,
            onCheckedChange = {
                onCheckChanged(it)
            }
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
}

@Preview(showBackground = true)
@Composable
private fun TermsCheckboxCheckedPreview() {
    URNetworkTheme {
        TermsCheckbox(
            checked = true,
            onCheckChanged = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun TermsCheckboxUncheckedPreview() {
    URNetworkTheme {
        TermsCheckbox(
            checked = false,
            onCheckChanged = {}
        )
    }
}