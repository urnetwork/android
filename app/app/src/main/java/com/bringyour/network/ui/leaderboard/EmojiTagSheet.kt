package com.bringyour.network.ui.leaderboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bringyour.network.R
import com.bringyour.network.ui.components.URTextInput
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.ppNeueBitBold
import com.bringyour.sdk.Sdk

/**
 * The emoji tag editor: one field that accepts only emoji, validated on
 * every change by the sdk (the same rule the server applies), a counter
 * "n / max", Save for a valid changed tag and Clear for an existing one.
 * `onSave` gets the sdk's normalized tag; `onClear` sends an empty tag.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmojiTagSheet(
    currentTag: String,
    isSaving: Boolean,
    saveError: String?,
    onSave: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var value by rememberSaveable(stateSaver = androidx.compose.ui.text.input.TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(currentTag, selection = androidx.compose.ui.text.TextRange(currentTag.length)))
    }
    val maxCount = Sdk.EmojiTagMaxCount.toInt()

    // the sdk validates exactly the way the server does; run it on every
    // change so a stray letter is rejected before any request
    val validation = remember(value.text) { Sdk.validateEmojiTag(value.text) }
    val ok = validation?.ok == true
    val count = validation?.count?.toInt() ?: 0
    val normalized = validation?.normalized ?: ""
    val error = EmojiTagEditor.errorFor(ok, validation?.reason)
    val showsError = EmojiTagEditor.showsError(value.text, error)
    val canSave = EmojiTagEditor.canSave(ok, normalized, currentTag, isSaving)

    val supportingText = when {
        saveError != null -> saveError
        showsError -> when (error) {
            EmojiTagError.EMPTY -> stringResource(id = R.string.emoji_tag_error_empty)
            EmojiTagError.TOO_MANY -> stringResource(id = R.string.emoji_tag_error_too_many, maxCount)
            EmojiTagError.NOT_EMOJI, null -> stringResource(id = R.string.emoji_tag_error_not_emoji)
        }
        else -> stringResource(id = R.string.emoji_tag_counter, count, maxCount)
    }

    val save = {
        if (canSave) {
            onSave(normalized)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                stringResource(id = R.string.emoji_tag),
                style = TextStyle(
                    fontFamily = ppNeueBitBold,
                    fontSize = 24.sp
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                stringResource(id = R.string.emoji_tag_hint, maxCount),
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )

            Spacer(modifier = Modifier.height(16.dp))

            URTextInput(
                value = value,
                onValueChange = { value = it },
                label = null,
                placeholder = "🐬🔥",
                supportingText = supportingText,
                isValid = !showsError && saveError == null,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done
                ),
                onDone = { save() },
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentTag.isNotEmpty()) {
                    TextButton(
                        onClick = onClear,
                        enabled = !isSaving
                    ) {
                        Text(stringResource(id = R.string.clear))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                TextButton(
                    onClick = onDismiss,
                    enabled = !isSaving
                ) {
                    Text(stringResource(id = R.string.cancel))
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = { save() },
                    enabled = canSave
                ) {
                    Text(stringResource(id = R.string.save))
                }
            }
        }
    }
}
