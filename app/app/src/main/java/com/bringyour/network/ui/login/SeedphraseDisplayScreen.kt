package com.bringyour.network.ui.login

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.PersistableBundle
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.components.ButtonStyle
import com.bringyour.network.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.size
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.MainTintedBackgroundBase
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.Yellow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeedphraseDisplayScreen(
    seedphrase: String,
    onConfirmed: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var showBackConfirmDialog by remember { mutableStateOf(false) }

    BackHandler {
        showBackConfirmDialog = true
    }

    if (showBackConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showBackConfirmDialog = false },
            title = { Text(stringResource(id = R.string.site_save_your_seedphrase)) },
            text = { Text(stringResource(id = R.string.seedphrase_leave_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showBackConfirmDialog = false
                    onBack()
                }) {
                    Text(stringResource(id = R.string.go_back))
                }
            },
            dismissButton = {
                TextButton(onClick = { showBackConfirmDialog = false }) {
                    Text(stringResource(id = R.string.stay))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(id = R.string.secure_your_account),
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        showBackConfirmDialog = true
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = stringResource(id = R.string.back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Black
                ),
                actions = {}
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(16.dp)
                .imePadding(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier.widthIn(max = 512.dp)
            ) {
                Text(
                    stringResource(id = R.string.your_seedphrase),
                    style = MaterialTheme.typography.headlineLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    "⚠️ This is the ONLY time you'll see this.",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center,
                    color = Yellow,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    stringResource(id = R.string.write_it_down_and_store_it_somewhere),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = TextMuted,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(32.dp))

                SelectionContainer {
                    val words = seedphrase.split(" ").filter { it.isNotEmpty() }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 1.dp,
                                color = TextMuted.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        words.withIndex().toList().chunked(2).forEach { rowWords ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowWords.forEach { (wordIndex, word) ->
                                    SeedWordChip(
                                        number = wordIndex + 1,
                                        word = word,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("acceptance.instant.word.$wordIndex")
                                    )
                                }
                                // odd word count on the last row: keep the grid's 2 columns aligned
                                if (rowWords.size < 2) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                URButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Seedphrase", seedphrase)
                        // sensitive flag: Android 13+ hides the phrase from the
                        // clipboard preview overlay and marks it for clipboard sync
                        clip.description.extras = PersistableBundle().apply {
                            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                        }
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, context.getString(R.string.seedphrase_copied_to_clipboard), Toast.LENGTH_SHORT).show()
                    },
                    // secondary action: the one primary button on this screen is the
                    // confirmation below
                    style = ButtonStyle.OUTLINE,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("acceptance.instant.copy")
                ) { buttonTextStyle ->
                    Icon(
                        painter = painterResource(id = R.drawable.content_copy),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(id = R.string.copy), style = buttonTextStyle)
                }

                Spacer(modifier = Modifier.height(24.dp))

                URButton(
                    onClick = onConfirmed,
                    modifier = Modifier.testTag("acceptance.instant.continue")
                ) { buttonTextStyle ->
                    Text(stringResource(id = R.string.i_ve_saved_my_seedphrase), style = buttonTextStyle)
                }
            }
        }
    }
}

@Composable
private fun SeedWordChip(
    number: Int,
    word: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(
                color = MainTintedBackgroundBase,
                shape = RoundedCornerShape(6.dp)
            )
            .padding(vertical = 6.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "$number.",
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = TextMuted,
            textAlign = TextAlign.End,
            modifier = Modifier.width(24.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            word,
            style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            color = Color.White,
            maxLines = 1
        )
    }
}
