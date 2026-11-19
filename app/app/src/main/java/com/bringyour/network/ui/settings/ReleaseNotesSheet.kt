package com.bringyour.network.ui.settings

import android.text.BidiFormatter
import android.text.TextUtils
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bringyour.network.R
import com.bringyour.network.ui.components.ButtonStyle
import com.bringyour.network.ui.components.FoldSafeModalBottomSheet
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.components.rememberFoldSafeSheetState
import com.bringyour.network.ui.theme.SheetBlack
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.URNetworkTheme
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale

/**
 * The bundled release note (ReleaseNotes), opened from the version row in
 * Account -> Settings: the note's title, the running build's version with the
 * note's day, its intro, then its sections, each an optional heading over
 * bullets. A sheet like the data info sheet, no wider than the readable
 * column, kept off a fold; it scrolls when the note is taller than the window.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReleaseNotesSheet(
    // the file, for the note's day
    notes: ReleaseNotes,
    // the note in the reader's language (ReleaseNotes.noteFor)
    note: ReleaseNote,
    // the running build's version, exactly as the version row shows it
    version: String,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberFoldSafeSheetState()
    val scope = rememberCoroutineScope()

    FoldSafeModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SheetBlack,
    ) {
        ReleaseNotesContent(
            notes = notes,
            note = note,
            version = version,
            onClose = {
                // slides away as a swipe down does, then closes
                scope.launch { sheetState.hide() }.invokeOnCompletion {
                    if (!sheetState.isVisible) {
                        onDismiss()
                    }
                }
            },
        )
    }
}

@Composable
private fun ReleaseNotesContent(
    notes: ReleaseNotes,
    note: ReleaseNote,
    version: String,
    onClose: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    // the intro and the bullets share one line height, so a wrapped bullet
    // and the gap to the next keep the same rhythm
    val bodyStyle = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp)
    // the note runs in its own language's direction: an English note, the
    // fallback, reads left to right in an Arabic window, its bullets at the left
    val noteDirection = remember(note.locale) {
        val direction = TextUtils.getLayoutDirectionFromLocale(Locale.forLanguageTag(note.locale))
        if (direction == View.LAYOUT_DIRECTION_RTL) LayoutDirection.Rtl else LayoutDirection.Ltr
    }
    // the running build's version with the note's day, the day in the
    // reader's form, so maybe the other direction: each part keeps its own
    // order within the note's direction
    val versionLine = remember(notes, version, locale, noteDirection) {
        val bidi = BidiFormatter.getInstance(noteDirection == LayoutDirection.Rtl)
        notes.versionLine(version, locale) { bidi.unicodeWrap(it) }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 32.dp)
    ) {
        Text(
            stringResource(id = R.string.release_notes),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )

        Spacer(modifier = Modifier.height(16.dp))

        CompositionLocalProvider(LocalLayoutDirection provides noteDirection) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    note.title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                    modifier = Modifier.semantics { heading() },
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    versionLine,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted,
                )

                note.intro?.let { intro ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(intro, style = bodyStyle, color = Color.White)
                }

                for (section in note.sections) {
                    Spacer(modifier = Modifier.height(20.dp))

                    section.heading?.let { text ->
                        Text(
                            text,
                            style = bodyStyle.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.White,
                            modifier = Modifier.semantics { heading() },
                        )
                        if (section.items.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }

                    if (section.items.isNotEmpty()) {
                        // TalkBack reads the bullets as a list ("list, 3 items")
                        Column(
                            modifier = Modifier.semantics {
                                collectionInfo = CollectionInfo(rowCount = section.items.size, columnCount = 1)
                            },
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            section.items.forEachIndexed { index, item ->
                                ReleaseNoteBullet(
                                    text = item,
                                    style = bodyStyle,
                                    modifier = Modifier.semantics {
                                        collectionItemInfo = CollectionItemInfo(
                                            rowIndex = index,
                                            rowSpan = 1,
                                            columnIndex = 0,
                                            columnSpan = 1,
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        URButton(
            onClick = onClose,
            style = ButtonStyle.OUTLINE,
        ) { buttonTextStyle ->
            Text(
                stringResource(id = R.string.close),
                style = buttonTextStyle,
            )
        }
    }
}

/**
 * One bullet. The dot is drawn, so TalkBack reads only the text; its middle
 * sits on the first line's lowercase letters at any font scale, and a
 * wrapped line hangs under the text, not the dot.
 */
@Composable
private fun ReleaseNoteBullet(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    // from the dot's middle down to the first line's baseline: about half
    // the x-height
    val lift = with(LocalDensity.current) { (style.fontSize * 0.3f).roundToPx() }

    Row(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .alignBy { it.measuredHeight / 2 + lift }
                .size(5.dp)
                .background(TextMuted, CircleShape)
        )

        Spacer(modifier = Modifier.width(10.dp))

        Text(
            text,
            style = style,
            color = Color.White,
            modifier = Modifier
                .weight(1f)
                .alignByBaseline(),
        )
    }
}

// the previews' note is placeholder text: a real note's words come from
// release-notes/latest.yaml in the localizations store

@Preview
@Composable
private fun ReleaseNotesContentPreview() {
    val note = ReleaseNote(
        locale = "en",
        title = "Note title",
        intro = "An intro paragraph under the title.",
        sections = listOf(
            ReleaseNoteSection(
                heading = "Section heading",
                items = listOf(
                    "A first item that is long enough to wrap onto a second line.",
                    "A second item.",
                ),
            ),
            ReleaseNoteSection(
                heading = null,
                items = listOf("An item in a section without a heading."),
            ),
        ),
    )
    URNetworkTheme {
        Column(modifier = Modifier.background(SheetBlack)) {
            ReleaseNotesContent(
                notes = ReleaseNotes(version = "2026.11", date = LocalDate.of(2026, 11, 19), notes = mapOf("en" to note)),
                note = note,
                version = "1.2.3",
                onClose = {},
            )
        }
    }
}

@Preview
@Composable
private fun ReleaseNotesContentIntroOnlyPreview() {
    val note = ReleaseNote(
        locale = "en",
        title = "Note title",
        intro = "An intro paragraph, and no sections.",
        sections = listOf(),
    )
    URNetworkTheme {
        Column(modifier = Modifier.background(SheetBlack)) {
            ReleaseNotesContent(
                notes = ReleaseNotes(version = "2026.11", date = LocalDate.of(2026, 11, 19), notes = mapOf("en" to note)),
                note = note,
                version = "1.2.3",
                onClose = {},
            )
        }
    }
}
