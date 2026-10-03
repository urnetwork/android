package com.bringyour.network.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.bringyour.network.R
import com.bringyour.network.ui.theme.TextMuted

/** Shown in place of a section's spinner when its fetch failed. */
@Composable
fun SectionLoadError(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = TextMuted,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(id = R.string.load_failed),
            style = MaterialTheme.typography.bodySmall,
            color = color,
            modifier = Modifier.weight(1f, fill = false),
        )
        TextButton(onClick = onRetry) {
            Text(stringResource(id = R.string.try_again))
        }
    }
}
