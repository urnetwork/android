package com.bringyour.network.ui.settings

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.bringyour.network.R
import com.bringyour.network.ui.components.ButtonStyle
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.components.URInlineErrorText
import com.bringyour.network.ui.components.URSwitch
import com.bringyour.network.ui.components.URTextInput
import com.bringyour.network.ui.components.URTextInputLabel
import com.bringyour.network.ui.components.tabletReadableColumn
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.MainTintedBackgroundBase
import com.bringyour.network.ui.theme.TextFaint
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.TopBarTitleTextStyle

/**
 * Account > Settings > VLESS: the VLESS server of this network space
 * (VlessSettingsViewModel).
 */
@Composable
fun VlessSettingsScreen(
    navController: NavController,
    viewModel: VlessSettingsViewModel = hiltViewModel(),
) {
    VlessSettingsScaffold(
        onBack = { navController.popBackStack() },
        viewModel = viewModel,
    )
}

/**
 * The same editor over the login screen, opened from its network settings
 * ("Change Network API") for the active network space before sign-in. Its
 * view model outlives one opening, so each opening loads the space's settings
 * again.
 */
@Composable
fun VlessSettingsDialog(
    onDismiss: () -> Unit,
    viewModel: VlessSettingsViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) {
        viewModel.load()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        VlessSettingsScaffold(
            onBack = onDismiss,
            viewModel = viewModel,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VlessSettingsScaffold(
    onBack: () -> Unit,
    viewModel: VlessSettingsViewModel,
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(id = R.string.vless),
                        style = TopBarTitleTextStyle
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
        VlessSettingsForm(
            viewModel = viewModel,
            modifier = Modifier.padding(innerPadding),
        )
    }
}

/**
 * The editor: the switch, the share link, the server, the transport and the
 * security, then only the fields that apply to them (VlessForm), and Save.
 * Every field stays editable while VLESS is off, since off settings save as
 * typed.
 */
@Composable
private fun VlessSettingsForm(
    viewModel: VlessSettingsViewModel,
    modifier: Modifier = Modifier,
) {

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val form = viewModel.form
    val editable = viewModel.editable

    var link by remember { mutableStateOf(TextFieldValue()) }

    Column(
        modifier = modifier
            .tabletReadableColumn()
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(PaddingValues(horizontal = 16.dp)),
    ) {

        Text(
            stringResource(id = R.string.vless_settings_description),
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted,
        )

        Spacer(modifier = Modifier.height(24.dp))

        VlessSwitchRow(
            text = stringResource(id = R.string.vless_enabled),
            checked = form.enabled,
            enabled = editable,
            toggle = { viewModel.update { it.copy(enabled = !it.enabled) } },
        )

        Spacer(modifier = Modifier.height(24.dp))

        /**
         * The share link: a pasted link fills the whole form, and the form
         * copies out as one
         */
        URTextInput(
            value = link,
            onValueChange = {
                link = it
                viewModel.clearLinkError()
            },
            label = stringResource(id = R.string.vless_link),
            placeholder = stringResource(id = R.string.vless_link_hint),
            keyboardOptions = vlessKeyboardOptions(KeyboardType.Uri, ImeAction.Done),
            onDone = { viewModel.applyLink(link.text) },
            enabled = editable,
        )

        URButton(
            onClick = {
                // the clipboard goes into the box; with nothing on it, the
                // box's own text is read
                val clip = clipboardManager.getText()?.text.orEmpty().trim()
                if (clip.isNotEmpty()) {
                    link = TextFieldValue(clip, TextRange(clip.length))
                }
                viewModel.applyLink(clip.ifEmpty { link.text })
            },
            style = ButtonStyle.SECONDARY,
            enabled = editable,
        ) { style ->
            Text(stringResource(id = R.string.vless_paste_link), style = style)
        }

        Spacer(modifier = Modifier.height(12.dp))

        URButton(
            onClick = {
                val shareLink = viewModel.shareLink()
                if (shareLink.isNotEmpty()) {
                    clipboardManager.setText(AnnotatedString(shareLink))
                    Toast.makeText(
                        context,
                        R.string.vless_link_copied,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            style = ButtonStyle.SECONDARY,
            // only settings that validate have a link
            enabled = editable && viewModel.validationErrorId.isEmpty(),
        ) { style ->
            Text(stringResource(id = R.string.vless_copy_link), style = style)
        }

        if (viewModel.linkErrorId.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            URInlineErrorText(stringResource(id = vlessErrorResId(viewModel.linkErrorId)))
        }

        Spacer(modifier = Modifier.height(32.dp))

        VlessTextField(
            value = form.name,
            onValueChange = { value -> viewModel.update { it.copy(name = value) } },
            label = stringResource(id = R.string.name_label),
            enabled = editable,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )

        VlessTextField(
            value = form.address,
            onValueChange = { value -> viewModel.update { it.copy(address = value) } },
            label = stringResource(id = R.string.vless_server_address),
            enabled = editable,
            keyboardOptions = vlessKeyboardOptions(KeyboardType.Uri),
        )

        VlessTextField(
            value = form.port,
            onValueChange = { value -> viewModel.update { it.copy(port = vlessPortText(value)) } },
            label = stringResource(id = R.string.vless_port),
            enabled = editable,
            keyboardOptions = vlessKeyboardOptions(KeyboardType.Number),
        )

        VlessTextField(
            value = form.id,
            onValueChange = { value -> viewModel.update { it.copy(id = value) } },
            label = stringResource(id = R.string.vless_user_id),
            enabled = editable,
        )

        VlessPicker(
            label = stringResource(id = R.string.transport),
            options = viewModel.networks,
            selected = form.network,
            optionLabelResId = ::vlessNetworkLabelResId,
            onSelect = { value -> viewModel.update { it.copy(network = value) } },
            enabled = editable,
        )

        VlessPicker(
            label = stringResource(id = R.string.vless_security),
            options = viewModel.securities,
            selected = form.security,
            optionLabelResId = ::vlessSecurityLabelResId,
            onSelect = { value -> viewModel.update { it.copy(security = value) } },
            enabled = editable,
        )

        if (form.showsFlow) {
            VlessPicker(
                label = stringResource(id = R.string.vless_flow),
                options = viewModel.flows,
                selected = form.flow,
                optionLabelResId = ::vlessFlowLabelResId,
                onSelect = { value -> viewModel.update { it.copy(flow = value) } },
                enabled = editable,
            )
        }

        if (form.showsServerName) {
            VlessTextField(
                value = form.serverName,
                onValueChange = { value -> viewModel.update { it.copy(serverName = value) } },
                label = stringResource(id = R.string.vless_server_name),
                enabled = editable,
                keyboardOptions = vlessKeyboardOptions(KeyboardType.Uri),
            )

            VlessPicker(
                label = stringResource(id = R.string.vless_fingerprint),
                options = viewModel.fingerprints,
                selected = form.fingerprint,
                optionLabelResId = ::vlessFingerprintLabelResId,
                onSelect = { value -> viewModel.update { it.copy(fingerprint = value) } },
                enabled = editable,
            )
        }

        if (form.showsTlsOptions) {
            VlessTextField(
                value = form.alpn,
                onValueChange = { value -> viewModel.update { it.copy(alpn = value) } },
                label = stringResource(id = R.string.vless_alpn),
                enabled = editable,
            )

            VlessSwitchRow(
                text = stringResource(id = R.string.vless_allow_insecure),
                checked = form.allowInsecure,
                enabled = editable,
                toggle = { viewModel.update { it.copy(allowInsecure = !it.allowInsecure) } },
            )

            Spacer(modifier = Modifier.height(24.dp))
        }

        if (form.showsRealityKeys) {
            VlessTextField(
                value = form.publicKey,
                onValueChange = { value -> viewModel.update { it.copy(publicKey = value) } },
                label = stringResource(id = R.string.vless_public_key),
                enabled = editable,
            )

            VlessTextField(
                value = form.shortId,
                onValueChange = { value -> viewModel.update { it.copy(shortId = value) } },
                label = stringResource(id = R.string.vless_short_id),
                enabled = editable,
            )
        }

        if (form.showsHttpOptions) {
            VlessTextField(
                value = form.path,
                onValueChange = { value -> viewModel.update { it.copy(path = value) } },
                label = stringResource(id = R.string.vless_path),
                enabled = editable,
                keyboardOptions = vlessKeyboardOptions(KeyboardType.Uri),
            )

            VlessTextField(
                value = form.host,
                onValueChange = { value -> viewModel.update { it.copy(host = value) } },
                label = stringResource(id = R.string.vless_host_header),
                enabled = editable,
                keyboardOptions = vlessKeyboardOptions(KeyboardType.Uri),
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        URButton(
            onClick = {
                viewModel.save {
                    Toast.makeText(
                        context,
                        R.string.vless_settings_saved,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            enabled = editable,
            isProcessing = viewModel.saving,
        ) { style ->
            Text(stringResource(id = R.string.save), style = style)
        }

        val errorId = vlessFormErrorId(
            enabled = form.enabled,
            validationErrorId = viewModel.validationErrorId,
            saveErrorId = viewModel.saveErrorId,
        )
        if (errorId.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            URInlineErrorText(stringResource(id = vlessErrorResId(errorId)))
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * The keyboard of a server field: an address, an id or a key, which a
 * correction or a capital would break.
 */
private fun vlessKeyboardOptions(
    keyboardType: KeyboardType = KeyboardType.Ascii,
    imeAction: ImeAction = ImeAction.Next,
) = KeyboardOptions(
    capitalization = KeyboardCapitalization.None,
    autoCorrectEnabled = false,
    keyboardType = keyboardType,
    imeAction = imeAction,
)

/**
 * One text field of the form. The form holds strings, so this keeps the
 * cursor while typing, and a value changed from outside -- a load, a pasted
 * link, a filtered port -- replaces the text with the cursor at its end.
 */
@Composable
private fun VlessTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean,
    keyboardOptions: KeyboardOptions = vlessKeyboardOptions(),
) {
    var field by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    val shown = if (field.text == value) {
        field
    } else {
        TextFieldValue(value, TextRange(value.length))
    }

    URTextInput(
        value = shown,
        onValueChange = {
            field = it
            onValueChange(it.text)
        },
        label = label,
        keyboardOptions = keyboardOptions,
        enabled = enabled,
    )
}

/**
 * A picker of the sdk's option values. An option shows its label, or the value
 * itself where it has none (the tls fingerprints).
 */
@Composable
private fun VlessPicker(
    label: String,
    options: List<String>,
    selected: String,
    optionLabelResId: (String) -> Int?,
    onSelect: (String) -> Unit,
    enabled: Boolean,
) {
    var expanded by remember { mutableStateOf(false) }

    Column {

        URTextInputLabel(text = label)

        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        enabled = enabled,
                        onClickLabel = label,
                        role = Role.DropdownList,
                    ) {
                        expanded = true
                    }
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    vlessOptionText(selected, optionLabelResId),
                    style = TextStyle(color = if (enabled) Color.LightGray else TextFaint),
                )
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = TextMuted,
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                containerColor = MainTintedBackgroundBase,
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                vlessOptionText(option, optionLabelResId),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        },
                        onClick = {
                            expanded = false
                            onSelect(option)
                        },
                    )
                }
            }
        }

        HorizontalDivider(color = TextFaint, thickness = 1.dp)

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun vlessOptionText(option: String, optionLabelResId: (String) -> Int?): String =
    optionLabelResId(option)?.let { stringResource(id = it) } ?: option

@Composable
private fun VlessSwitchRow(
    text: String,
    checked: Boolean,
    enabled: Boolean,
    toggle: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(16.dp))
        URSwitch(
            checked = checked,
            enabled = enabled,
            toggle = toggle,
        )
    }
}
