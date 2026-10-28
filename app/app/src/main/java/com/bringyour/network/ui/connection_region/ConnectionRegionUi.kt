package com.bringyour.network.ui.connection_region

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.bringyour.network.NetworkSpaceManagerProvider
import com.bringyour.network.R
import com.bringyour.network.ui.theme.MainTintedBackgroundBase
import com.bringyour.network.ui.theme.TextFaint
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.utils.sdkStringListToList
import com.bringyour.sdk.Sdk
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import javax.inject.Inject

/**
 * The connection region kept in the active network space, the same space
 * whose client strategy the login and app api calls use
 */
class NetworkSpaceConnectionRegionStore(
    private val networkSpaceManagerProvider: NetworkSpaceManagerProvider,
) : ConnectionRegionStore {
    override fun get(): String =
        networkSpaceManagerProvider.getNetworkSpace()?.getIngressCountryCode() ?: UNDISCLOSED_REGION

    override fun set(countryCode: String): String =
        networkSpaceManagerProvider.getNetworkSpace()?.setIngressCountryCode(countryCode) ?: UNDISCLOSED_REGION
}

object SdkConnectionRegionCatalog : ConnectionRegionCatalog {
    override fun countryCodes(): List<String> = sdkStringListToList(Sdk.ingressCountryCodes())
    override fun dohUrls(countryCode: String): List<String> =
        sdkStringListToList(Sdk.ingressDohUrls(countryCode))
    override fun plainDnsServers(countryCode: String): List<String> =
        sdkStringListToList(Sdk.ingressPlainDnsServers(countryCode))
}

fun localizedCountryName(countryCode: String): String {
    val name = Locale("", countryCode.uppercase()).getDisplayCountry(Locale.getDefault())
    return name.ifEmpty { countryCode.uppercase() }
}

@HiltViewModel
class ConnectionRegionViewModel @Inject constructor(
    networkSpaceManagerProvider: NetworkSpaceManagerProvider,
) : ViewModel() {
    val model = ConnectionRegionModel(
        store = NetworkSpaceConnectionRegionStore(networkSpaceManagerProvider),
        catalog = SdkConnectionRegionCatalog,
        displayName = ::localizedCountryName,
    )
}

@Composable
private fun regionLabel(countryCode: String): String =
    if (countryCode == UNDISCLOSED_REGION) {
        stringResource(id = R.string.connection_region_undisclosed)
    } else {
        localizedCountryName(countryCode)
    }

/**
 * The optional login screen control: "Connecting from: Other / Undisclosed ▾"
 */
@Composable
fun ConnectionRegionLoginControl(
    modifier: Modifier = Modifier,
    viewModel: ConnectionRegionViewModel = hiltViewModel(),
) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    val model = viewModel.model

    Row(
        modifier = modifier
            .clickable {
                model.refresh()
                showPicker = true
            }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "${stringResource(id = R.string.connecting_from)}: ${regionLabel(model.selected)}",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted
        )
        Icon(
            Icons.Filled.ArrowDropDown,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(16.dp)
        )
    }

    if (showPicker) {
        ConnectionRegionPicker(model = model, dismiss = { showPicker = false })
    }
}

/**
 * The dns settings "Connection region" row
 */
@Composable
fun ConnectionRegionSettingsRow(
    viewModel: ConnectionRegionViewModel = hiltViewModel(),
) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    val model = viewModel.model

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            stringResource(id = R.string.connection_region),
            style = TextStyle(color = TextMuted)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MainTintedBackgroundBase, shape = RoundedCornerShape(12.dp))
                .clickable {
                    model.refresh()
                    showPicker = true
                }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                regionLabel(model.selected),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                modifier = Modifier.weight(1f)
            )
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = TextMuted)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            stringResource(id = R.string.connection_region_description),
            style = MaterialTheme.typography.bodySmall,
            color = TextFaint
        )
    }

    if (showPicker) {
        ConnectionRegionPicker(model = model, dismiss = { showPicker = false })
    }
}

/**
 * "Can't reach URnetwork — choose where you're connecting from", shown after a
 * network-level api failure. Opens the picker; never switches the region itself.
 */
@Composable
fun CantReachRegionPrompt(
    prompt: CantReachPrompt = CantReachPrompt.shared,
    viewModel: ConnectionRegionViewModel = hiltViewModel(),
) {
    val visible by prompt.visible.collectAsState()
    var showPicker by rememberSaveable { mutableStateOf(false) }

    if (visible && !showPicker) {
        AlertDialog(
            onDismissRequest = { prompt.dismiss() },
            text = { Text(stringResource(id = R.string.cant_reach_urnetwork_choose_region)) },
            confirmButton = {
                TextButton(onClick = {
                    prompt.dismiss()
                    viewModel.model.refresh()
                    showPicker = true
                }) {
                    Text(stringResource(id = R.string.connection_region))
                }
            },
            dismissButton = {
                TextButton(onClick = { prompt.dismiss() }) {
                    Text(stringResource(id = R.string.close))
                }
            }
        )
    }

    if (showPicker) {
        ConnectionRegionPicker(model = viewModel.model, dismiss = { showPicker = false })
    }
}

@Composable
fun ConnectionRegionPicker(
    model: ConnectionRegionModel,
    dismiss: () -> Unit,
) {
    val options = remember(model) { model.options() }
    val resolvers = model.resolvers()

    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(stringResource(id = R.string.where_are_you_connecting_from)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                for (option in options) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { model.select(option.countryCode) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = model.selected == option.countryCode,
                            onClick = { model.select(option.countryCode) }
                        )
                        Text(
                            option.displayName
                                ?: stringResource(id = R.string.connection_region_undisclosed)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    stringResource(id = R.string.connection_region_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextFaint
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (resolvers.usesDefaults) {
                    Text(
                        stringResource(id = R.string.connection_region_default_resolvers),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                } else {
                    ResolverList(
                        title = stringResource(id = R.string.connection_region_encrypted_resolvers),
                        values = resolvers.dohUrls
                    )
                    ResolverList(
                        title = stringResource(id = R.string.connection_region_fallback_resolvers),
                        values = resolvers.plainDnsServers
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = dismiss) {
                Text(stringResource(id = R.string.close))
            }
        }
    )
}

@Composable
private fun ResolverList(title: String, values: List<String>) {
    if (values.isEmpty()) {
        return
    }
    Text(title, style = MaterialTheme.typography.bodySmall, color = TextMuted)
    for (value in values) {
        Row {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                value,
                style = TextStyle(fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                color = TextFaint
            )
        }
    }
    Spacer(modifier = Modifier.height(6.dp))
}
