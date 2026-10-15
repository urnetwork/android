package com.bringyour.network.ui.wallet

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.PersistableBundle
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.bringyour.network.DeviceManager
import com.bringyour.network.R
import com.bringyour.network.ui.components.ButtonStyle
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.components.tabletReadableColumn
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.MainTintedBackgroundBase
import com.bringyour.network.ui.theme.Red
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.TopBarTitleTextStyle
import com.bringyour.network.ui.theme.URNetworkTheme
import com.bringyour.sdk.DeviceLocal
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** The sdk device behind the sign screen: the device's id, its public client key, its signer. */
private class SdkTop200SignDevice(private val device: DeviceLocal) : Top200SignDevice {
    override val clientId: String? get() = device.clientId?.idStr
    override val clientKey: String get() = device.snClientKey ?: ""
    override fun signFleetBinding(bindingJson: String): String = device.signSnFleetBinding(bindingJson) ?: ""
}

/**
 * Earnings › Top 200 › Sign fleet binding. Holds a [Top200SignFlow] on the
 * signed-in device. Nothing here goes to the network.
 */
@HiltViewModel
class Top200SignViewModel @Inject constructor(
    private val deviceManager: DeviceManager,
) : ViewModel() {

    private val flow = Top200SignFlow()
    val state: StateFlow<Top200SignState> = flow.state

    private val removeDeviceChangeListener: () -> Unit =
        deviceManager.addDeviceChangeListener { device ->
            flow.setDevice(device?.let { SdkTop200SignDevice(it) })
        }

    /** The device's key may have become available (the screen resumed). */
    fun refreshDevice() {
        flow.setDevice(deviceManager.device?.let { SdkTop200SignDevice(it) })
    }

    fun setText(text: String) = flow.setText(text)

    fun applyLink(link: Top200SignLink) = flow.applyLink(link)

    fun sign() {
        viewModelScope.launch { flow.sign() }
    }

    override fun onCleared() {
        super.onCleared()
        removeDeviceChangeListener()
    }
}

// copies a public value: the client id and key, the member entry or the payload, never a secret
private fun copyText(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    val clip = ClipData.newPlainText(label, text)
    clip.description.extras = PersistableBundle().apply {
        putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, false)
    }
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, context.getString(R.string.copied), Toast.LENGTH_SHORT).show()
}

private fun pasteText(context: Context): String? {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
    val clip = clipboard.primaryClip ?: return null
    if (clip.itemCount == 0) {
        return null
    }
    return clip.getItemAt(0).coerceToText(context)?.toString()
}

@Composable
fun Top200SignScreen(
    navController: NavHostController,
    top200SignViewModel: Top200SignViewModel = hiltViewModel(),
) {
    val state by top200SignViewModel.state.collectAsState()
    val pendingLink by Top200SignLinks.pending.collectAsState()

    // an app link only fills the field; it never signs
    LaunchedEffect(pendingLink) {
        if (pendingLink != null) {
            Top200SignLinks.take()?.let { top200SignViewModel.applyLink(it) }
        }
    }

    LifecycleResumeEffect(Unit) {
        top200SignViewModel.refreshDevice()
        onPauseOrDispose {}
    }

    Top200SignContent(
        state = state,
        onBack = { navController.popBackStack() },
        onTextChange = top200SignViewModel::setText,
        onSign = top200SignViewModel::sign,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Top200SignContent(
    state: Top200SignState,
    onBack: () -> Unit,
    onTextChange: (String) -> Unit,
    onSign: () -> Unit,
) {
    val context = LocalContext.current
    val monospace = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(id = R.string.top200_sign_binding),
                        style = TopBarTitleTextStyle
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(id = R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Black
                ),
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .tabletReadableColumn()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                stringResource(id = R.string.top200_sign_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )

            Spacer(modifier = Modifier.height(20.dp))

            // ---- this device

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MainTintedBackgroundBase, RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Text(
                    stringResource(id = R.string.top200_sign_this_device),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))
                val clientIdLabel = stringResource(id = R.string.client_id)
                ValueRow(
                    label = clientIdLabel,
                    value = state.clientId,
                    display = state.clientId,
                    monospace = monospace,
                    onCopy = { copyText(context, clientIdLabel, it) }
                )
                val clientKeyLabel = stringResource(id = R.string.top200_sign_client_key)
                ValueRow(
                    label = clientKeyLabel,
                    value = state.clientKey.ifEmpty { null },
                    display = state.clientKey.ifEmpty { null }?.let { Top200Bindings.shortHex(it) },
                    monospace = monospace,
                    onCopy = { copyText(context, clientKeyLabel, it) }
                )
                if (state.clientKey.isEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        stringResource(id = R.string.top200_sign_error_no_key),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Red
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    stringResource(id = R.string.top200_sign_member_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
                Spacer(modifier = Modifier.height(12.dp))
                val memberEntry = state.memberEntry
                val copyMemberLabel = stringResource(id = R.string.top200_sign_copy_member)
                URButton(
                    onClick = { memberEntry?.let { copyText(context, copyMemberLabel, it) } },
                    style = ButtonStyle.OUTLINE,
                    enabled = memberEntry != null,
                ) { buttonTextStyle ->
                    Text(copyMemberLabel, style = buttonTextStyle)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ---- the binding

            OutlinedTextField(
                value = state.text,
                onValueChange = onTextChange,
                label = { Text(stringResource(id = R.string.top200_sign_binding_label)) },
                textStyle = monospace,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Ascii,
                ),
                singleLine = false,
                minLines = 4,
                maxLines = 10,
                isError = state.check.errorMessageId() != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            URButton(
                onClick = { pasteText(context)?.let(onTextChange) },
                style = ButtonStyle.OUTLINE,
            ) { buttonTextStyle ->
                Text(stringResource(id = R.string.top200_sign_paste), style = buttonTextStyle)
            }

            state.check.errorMessageId()?.let { messageId ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    stringResource(id = messageId),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Red
                )
            }
            (state.result as? Top200SignResult.Failed)?.let { failed ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    stringResource(id = R.string.top200_sign_error_failed, failed.detail),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Red
                )
            }

            // ---- the review: fleet id, hotkey, chain and epochs before the sign button

            val check = state.check
            if (check is Top200BindingCheck.Valid) {
                val binding = check.binding
                Spacer(modifier = Modifier.height(20.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MainTintedBackgroundBase, RoundedCornerShape(12.dp))
                        .padding(16.dp)
                ) {
                    Text(
                        stringResource(id = R.string.top200_sign_review),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    ReviewRow(
                        label = stringResource(id = R.string.top200_sign_fleet_id),
                        value = Top200Bindings.shortHex(binding.fleetId),
                        monospace = monospace
                    )
                    // the hotkey in full, as the page's <pre> shows it
                    ReviewRow(
                        label = stringResource(id = R.string.top200_sign_hotkey),
                        value = binding.hotkey,
                        monospace = monospace
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        stringResource(id = R.string.top200_sign_chain, binding.chainId, binding.netuid),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                    Text(
                        stringResource(id = R.string.top200_sign_epochs, binding.validFromEpoch, binding.validToEpoch),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                URButton(
                    onClick = onSign,
                    enabled = state.canSign,
                    isProcessing = state.signing,
                ) { buttonTextStyle ->
                    Text(stringResource(id = R.string.top200_sign_action), style = buttonTextStyle)
                }
            }

            // ---- the signed payload

            (state.result as? Top200SignResult.Signed)?.let { signed ->
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    stringResource(id = R.string.top200_sign_done),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))
                SelectionContainer {
                    Text(
                        signed.payload,
                        style = monospace,
                        color = Color.White,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MainTintedBackgroundBase, RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                val copyPayloadLabel = stringResource(id = R.string.top200_sign_copy_payload)
                URButton(
                    onClick = { copyText(context, copyPayloadLabel, signed.payload) },
                ) { buttonTextStyle ->
                    Text(copyPayloadLabel, style = buttonTextStyle)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// the inline error under the field for a check
private fun Top200BindingCheck.errorMessageId(): Int? = when (this) {
    Top200BindingCheck.Invalid -> R.string.top200_sign_error_invalid
    Top200BindingCheck.OtherDevice -> R.string.top200_sign_error_other_device
    Top200BindingCheck.NoKey -> R.string.top200_sign_error_no_key
    Top200BindingCheck.Empty, is Top200BindingCheck.Valid -> null
}

@Composable
private fun ValueRow(
    label: String,
    value: String?,
    display: String?,
    monospace: androidx.compose.ui.text.TextStyle,
    onCopy: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = TextMuted)
            Text(display ?: "-", style = monospace, color = Color.White)
        }
        Spacer(modifier = Modifier.width(8.dp))
        TextButton(
            onClick = { value?.let(onCopy) },
            enabled = value != null
        ) {
            Text(stringResource(id = R.string.copy))
        }
    }
}

@Composable
private fun ReviewRow(
    label: String,
    value: String,
    monospace: androidx.compose.ui.text.TextStyle,
) {
    Column(modifier = Modifier.padding(bottom = 6.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = TextMuted)
        Text(value, style = monospace, color = Color.White)
    }
}

@Preview
@Composable
private fun Top200SignContentPreview() {
    URNetworkTheme {
        Top200SignContent(
            state = Top200SignState(
                clientId = "019b2e10-5a7c-4d3e-8f01-23456789abcd",
                clientKey = "0x79b5562e8fe654f94078b112e8a98ba7901f853ae695bed7e0e3910bad049664",
            ),
            onBack = {},
            onTextChange = {},
            onSign = {},
        )
    }
}
