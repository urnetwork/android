package com.bringyour.network.ui.account

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.bringyour.network.R
import com.bringyour.network.ui.components.ButtonStyle
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.components.URInlineErrorText
import com.bringyour.network.ui.components.URTextInput
import com.bringyour.network.ui.components.tabletReadableColumn
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.TextFaint
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.TopBarTitleTextStyle

/**
 * The bootstrap DNS-over-HTTPS servers block (ControlDohSettingsViewModel):
 * the description, which names what the chosen servers can see and so always
 * shows, the field with one url per line, the "Use China resolvers" preset,
 * the reset to the built-in servers, and Save. An empty field means the
 * built-in servers alone.
 *
 * Account > Extenders shows it below the extender settings, disabled with
 * them; the login screen's network settings open it in a dialog, whose top
 * bar carries the title instead.
 */
@Composable
fun ControlDohSettingsBlock(
    viewModel: ControlDohSettingsViewModel,
    enabled: Boolean,
    showsTitle: Boolean = true,
) {

    val context = LocalContext.current
    val text = viewModel.text
    val onSaved: () -> Unit = {
        Toast.makeText(
            context,
            R.string.control_doh_urls_saved,
            Toast.LENGTH_SHORT
        ).show()
    }

    // the field keeps the cursor while typing; text replaced from outside --
    // a load, the preset, the list a save read back -- puts it at the end
    var field by remember { mutableStateOf(TextFieldValue(text, TextRange(text.length))) }
    val shown = if (field.text == text) {
        field
    } else {
        TextFieldValue(text, TextRange(text.length))
    }

    val errorId = controlDohFormErrorId(
        validationErrorId = viewModel.validationErrorId,
        saveErrorId = viewModel.saveErrorId,
    )

    Column {

        if (showsTitle) {
            Text(
                stringResource(id = R.string.control_doh_urls),
                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
            )

            Spacer(modifier = Modifier.height(4.dp))
        }

        Text(
            stringResource(id = R.string.control_doh_urls_description),
            style = TextStyle(fontSize = 12.sp),
            color = TextFaint,
        )

        Spacer(modifier = Modifier.height(16.dp))

        URTextInput(
            value = shown,
            onValueChange = {
                field = it
                viewModel.update(it.text)
            },
            label = null,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Uri,
            ),
            isValid = errorId.isEmpty(),
            enabled = enabled,
            maxLines = 8,
        )

        Text(
            stringResource(id = R.string.control_doh_urls_hint),
            style = TextStyle(fontSize = 12.sp),
            color = TextMuted,
        )

        if (errorId.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            URInlineErrorText(stringResource(id = controlDohErrorResId(errorId)))
        }

        Spacer(modifier = Modifier.height(16.dp))

        URButton(
            onClick = { viewModel.useChinaPreset() },
            style = ButtonStyle.SECONDARY,
            enabled = enabled,
        ) { style ->
            Text(stringResource(id = R.string.control_doh_use_china), style = style)
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            stringResource(id = R.string.control_doh_use_china_hint),
            style = TextStyle(fontSize = 12.sp),
            color = TextFaint,
        )

        Spacer(modifier = Modifier.height(12.dp))

        URButton(
            onClick = { viewModel.reset(onSaved) },
            style = ButtonStyle.SECONDARY,
            enabled = enabled,
        ) { style ->
            Text(stringResource(id = R.string.control_doh_urls_reset), style = style)
        }

        Spacer(modifier = Modifier.height(12.dp))

        URButton(
            onClick = { viewModel.save(onSaved) },
            enabled = enabled,
            isProcessing = viewModel.saving,
        ) { style ->
            Text(stringResource(id = R.string.save), style = style)
        }
    }
}

/**
 * The same block over the login screen, opened from its network settings
 * ("Change Network API") for the active network space before sign-in: where
 * the default DoH servers are blocked, sign-in cannot reach the api without
 * it. Its view model outlives one opening, so each opening loads the space's
 * servers again.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlDohSettingsDialog(
    onDismiss: () -> Unit,
    viewModel: ControlDohSettingsViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) {
        viewModel.load()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            stringResource(id = R.string.control_doh_urls),
                            style = TopBarTitleTextStyle
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = stringResource(id = R.string.back)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Black
                    ),
                )
            },
            containerColor = Black
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .tabletReadableColumn()
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(PaddingValues(horizontal = 16.dp)),
            ) {
                ControlDohSettingsBlock(
                    viewModel = viewModel,
                    enabled = viewModel.editable,
                    showsTitle = false,
                )

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
