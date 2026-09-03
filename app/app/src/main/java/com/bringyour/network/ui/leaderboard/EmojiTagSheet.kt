package com.bringyour.network.ui.leaderboard

import android.content.Context
import android.content.res.Configuration
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.emoji2.emojipicker.EmojiPickerView
import com.bringyour.network.R
import com.bringyour.network.ui.theme.MainBorderBase
import com.bringyour.network.ui.theme.MainTintedBackgroundBase
import com.bringyour.network.ui.theme.TextDanger
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.ppNeueBitBold
import com.bringyour.sdk.Sdk

/**
 * The emoji tag editor. The tag is typed on an emoji-only keyboard
 * (androidx.emoji2 EmojiPickerView), never the system text keyboard: the
 * field is a read-only display of the draft with a backspace key that
 * removes one emoji at a time. A network with no tag starts from a random
 * 1-3 emoji suggestion from the sdk (`Sdk.suggestEmojiTag`), and the shuffle
 * key re-rolls it; a suggestion is only a draft until Save. Every change is
 * validated by the sdk exactly the way the server validates it, and the
 * counter reads "n / max". `onSave` gets the sdk's normalized tag; `onClear`
 * sends an empty tag.
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
    val maxCount = Sdk.EmojiTagMaxCount.toInt()

    var draft by rememberSaveable {
        mutableStateOf(if (currentTag.isEmpty()) suggestEmojiTag() else currentTag)
    }

    // the sdk validates exactly the way the server does; run it on every
    // change so the counter and Save always reflect what the server would say
    val validation = remember(draft) { Sdk.validateEmojiTag(draft) }
    val ok = validation?.ok == true
    val count = validation?.count?.toInt() ?: 0
    val normalized = validation?.normalized ?: ""
    val error = EmojiTagEditor.errorFor(ok, validation?.reason)
    val showsError = EmojiTagEditor.showsError(draft, error)
    val canSave = EmojiTagEditor.canSave(ok, normalized, currentTag, isSaving)
    val full = ok && count >= maxCount

    val supportingText = when {
        saveError != null -> saveError
        showsError -> when (error) {
            EmojiTagError.EMPTY -> stringResource(id = R.string.emoji_tag_error_empty)
            EmojiTagError.TOO_MANY -> stringResource(id = R.string.emoji_tag_error_too_many, maxCount)
            EmojiTagError.NOT_EMOJI, null -> stringResource(id = R.string.emoji_tag_error_not_emoji)
        }
        else -> stringResource(id = R.string.emoji_tag_counter, count, maxCount)
    }

    // the picker's listener is installed once in the view factory; it must
    // always see the current draft, not the one from the first composition
    val appendEmoji by rememberUpdatedState { emoji: String ->
        if (!full) {
            draft += emoji
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
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
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )

            Spacer(modifier = Modifier.height(16.dp))

            // the draft: read-only, edited only through the keys below
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 56.dp)
                        .background(
                            color = MainTintedBackgroundBase,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        draft,
                        fontSize = 28.sp,
                        maxLines = 1,
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                IconButton(
                    onClick = { draft = EmojiTagEditor.dropLastEmoji(draft) },
                    enabled = draft.isNotEmpty() && !isSaving
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Backspace,
                        contentDescription = stringResource(id = R.string.emoji_tag_delete_last),
                    )
                }

                IconButton(
                    onClick = { draft = suggestEmojiTag() },
                    enabled = !isSaving
                ) {
                    Icon(
                        imageVector = Icons.Filled.Shuffle,
                        contentDescription = stringResource(id = R.string.emoji_tag_shuffle),
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                supportingText,
                style = MaterialTheme.typography.bodySmall,
                color = if (showsError || saveError != null) TextDanger else TextMuted
            )

            Spacer(modifier = Modifier.height(12.dp))

            // the keyboard: emoji only. The picker takes its colors from the
            // night resources, so it is built on a night configuration to
            // match the sheet whatever the system theme is.
            //
            // The picker scrolls its own grid (a RecyclerView). Inside the
            // bottom sheet a vertical drag on it used to move the whole sheet:
            // Compose's view interop lets the sheet's drag gesture take the
            // pointer stream from the view once it passes touch slop. The
            // guard around the picker asks Compose not to intercept while a
            // finger is on it, so the grid scrolls itself; its nested scroll
            // still reaches the sheet, which therefore only moves once the
            // grid cannot scroll any further
            AndroidView(
                factory = { context ->
                    PickerTouchGuard(context).apply {
                        addView(
                            EmojiPickerView(nightContext(context)).apply {
                                setOnEmojiPickedListener { item ->
                                    appendEmoji(item.emoji)
                                }
                            },
                            FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                FrameLayout.LayoutParams.MATCH_PARENT
                            )
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .background(
                        color = MainBorderBase,
                        shape = RoundedCornerShape(12.dp)
                    )
            )

            Spacer(modifier = Modifier.height(12.dp))

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
                    onClick = { if (canSave) onSave(normalized) },
                    enabled = canSave
                ) {
                    Text(stringResource(id = R.string.save))
                }
            }
        }
    }
}

/** a random 1-3 emoji draft from the sdk; empty if the sdk has none */
private fun suggestEmojiTag(): String {
    return Sdk.suggestEmojiTag(0L) ?: ""
}

/**
 * Wraps the emoji picker so that, while a finger is down on it, its parents
 * (Compose's view holder, and through it the sheet's drag gesture) do not
 * intercept the touch stream. Every event still reaches the picker, so its
 * grid scrolls and taps pick; the picker's nested scroll is unaffected, so
 * the sheet is still told when the grid runs out of scroll.
 */
private class PickerTouchGuard(context: Context) : FrameLayout(context) {
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE ->
                parent?.requestDisallowInterceptTouchEvent(true)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                parent?.requestDisallowInterceptTouchEvent(false)
        }
        return super.dispatchTouchEvent(ev)
    }
}

/** the context with night mode forced on, for views that theme by uiMode */
private fun nightContext(context: Context): Context {
    val configuration = Configuration(context.resources.configuration)
    configuration.uiMode =
        (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
    return context.createConfigurationContext(configuration)
}
