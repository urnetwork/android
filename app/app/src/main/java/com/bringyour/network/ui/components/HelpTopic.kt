package com.bringyour.network.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bringyour.network.R
import com.bringyour.network.ui.theme.TextMuted

/*
 * The '?' beside a term the app uses without saying what it is: providing,
 * where the provide mode is chosen; Pending, in the usage bar's keys; and
 * extenders, on their Account row. It opens a short answer from the string
 * catalogs, and does nothing else.
 */

/** A term with its question, which titles the answer and labels the '?', and the answer. */
enum class HelpTopic(
    @StringRes val titleId: Int,
    @StringRes val bodyId: Int,
) {
    Provide(R.string.help_provide_title, R.string.help_provide_body),
    Pending(R.string.help_pending_title, R.string.help_pending_body),
    Extender(R.string.help_extender_title, R.string.help_extender_body),
}

/**
 * The '?' for [topic]: a muted outlined help icon that screen readers name
 * with the topic's question, and that opens the answer with Got it. As the
 * info icon (InfoIconWithOverlay), the 16 dp glyph sits in a standard 48 dp
 * icon button (Touch_Targets), so no neighbor cuts into its touch area, and
 * its margin around the glyph is the space between it and its label.
 */
@Composable
fun HelpButton(topic: HelpTopic, modifier: Modifier = Modifier) {
    var presented by rememberSaveable { mutableStateOf(false) }
    val title = stringResource(id = topic.titleId)

    IconButton(
        onClick = { presented = true },
        modifier = modifier
    ) {
        Icon(
            Icons.AutoMirrored.Outlined.HelpOutline,
            contentDescription = title,
            tint = TextMuted,
            modifier = Modifier.size(16.dp)
        )
    }

    if (presented) {
        FoldSafeAlertDialog(
            onDismissRequest = { presented = false },
            title = { Text(title) },
            text = { Text(stringResource(id = topic.bodyId)) },
            confirmButton = {
                TextButton(onClick = { presented = false }) {
                    Text(stringResource(id = R.string.got_it))
                }
            },
        )
    }
}
