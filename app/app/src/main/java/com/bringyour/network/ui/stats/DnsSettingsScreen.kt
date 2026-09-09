package com.bringyour.network.ui.stats

import com.bringyour.network.ui.components.tabletReadableColumn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.bringyour.network.R
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.components.URSwitch
import com.bringyour.network.ui.components.URTextInput
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.BlueMedium
import com.bringyour.network.ui.theme.Green
import com.bringyour.network.ui.theme.MainTintedBackgroundBase
import com.bringyour.network.ui.theme.TextFaint
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.TopBarTitleTextStyle
import com.bringyour.network.utils.isIpAddressValue
import com.bringyour.sdk.Sdk

/**
 * Editor for the device dns resolver settings.
 * Changes apply together with the update button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DnsSettingsScreen(
    navController: NavController,
    dnsSettingsViewModel: DnsSettingsViewModel = hiltViewModel(),
) {

    var draft by remember { mutableStateOf<DnsSettingsUi?>(null) }
    var original by remember { mutableStateOf<DnsSettingsUi?>(null) }

    LaunchedEffect(dnsSettingsViewModel.settings) {
        if (draft == null) {
            draft = dnsSettingsViewModel.settings ?: DnsSettingsUi()
            original = dnsSettingsViewModel.settings ?: DnsSettingsUi()
        }
    }

    val isDirty = draft != null && draft != original

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(id = R.string.custom_dns),
                        style = TopBarTitleTextStyle
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Black
                ),
            )
        },
        containerColor = Black
    ) { innerPadding ->

        val currentDraft = draft

        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {

            if (currentDraft == null) {
                return@Column
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .tabletReadableColumn()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {

                /**
                 * Recommendation panel when the connected country has one,
                 * otherwise a restore-to-most-secure panel
                 */
                val recommendation = dnsSettingsViewModel.recommendedSettings
                val defaults = dnsSettingsViewModel.defaultSettings
                if (recommendation != null) {
                    if (currentDraft == recommendation) {
                        // already on the regional recommendation
                        DnsStatusRow(
                            text = stringResource(id = R.string.dns_using_recommended),
                            countryCode = dnsSettingsViewModel.connectedCountryCode
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    MainTintedBackgroundBase,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .padding(16.dp)
                        ) {
                            Text(
                                stringResource(
                                    id = R.string.dns_recommendation_message,
                                    dnsSettingsViewModel.connectedCountryName
                                        ?: dnsSettingsViewModel.connectedCountryCode?.uppercase()
                                        ?: stringResource(id = R.string.this_region)
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // the connected country color
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .background(
                                            color = countryColor(dnsSettingsViewModel.connectedCountryCode ?: ""),
                                            shape = CircleShape
                                        )
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Box(modifier = Modifier.weight(1f)) {
                                    URButton(
                                        onClick = { draft = recommendation }
                                    ) { buttonTextStyle ->
                                        Text(
                                            stringResource(id = R.string.use_recommended_settings),
                                            style = buttonTextStyle
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                } else if (defaults != null) {
                    if (currentDraft == defaults) {
                        // already on the most secure defaults
                        DnsStatusRow(
                            text = stringResource(id = R.string.dns_using_secure),
                            countryCode = null
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    MainTintedBackgroundBase,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .padding(16.dp)
                        ) {
                            Text(
                                stringResource(id = R.string.dns_restore_secure_message),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            URButton(
                                onClick = { draft = defaults }
                            ) { buttonTextStyle ->
                                Text(
                                    stringResource(id = R.string.restore_most_secure_settings),
                                    style = buttonTextStyle
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                /**
                 * Resolver toggles
                 */
                SettingsGroup(title = stringResource(id = R.string.resolvers)) {
                    DnsToggleRow(
                        label = stringResource(id = R.string.dns_over_https),
                        detail = stringResource(id = R.string.remote_lowercase),
                        checked = currentDraft.enableRemoteDoh,
                        toggle = { draft = currentDraft.copy(enableRemoteDoh = !currentDraft.enableRemoteDoh) }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    DnsToggleRow(
                        label = stringResource(id = R.string.dns_over_https),
                        detail = stringResource(id = R.string.local_lowercase),
                        checked = currentDraft.enableLocalDoh,
                        toggle = { draft = currentDraft.copy(enableLocalDoh = !currentDraft.enableLocalDoh) }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    DnsToggleRow(
                        label = stringResource(id = R.string.unencrypted_dns),
                        detail = stringResource(id = R.string.remote_lowercase),
                        checked = currentDraft.enableRemoteDns,
                        toggle = { draft = currentDraft.copy(enableRemoteDns = !currentDraft.enableRemoteDns) }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    DnsToggleRow(
                        label = stringResource(id = R.string.unencrypted_dns),
                        detail = stringResource(id = R.string.local_lowercase),
                        checked = currentDraft.enableLocalDns,
                        toggle = { draft = currentDraft.copy(enableLocalDns = !currentDraft.enableLocalDns) }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    DnsToggleRow(
                        label = stringResource(id = R.string.local_dns_fallback),
                        detail = null,
                        checked = currentDraft.enableFallback,
                        toggle = { draft = currentDraft.copy(enableFallback = !currentDraft.enableFallback) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        stringResource(id = R.string.local_dns_fallback_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextFaint
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                /**
                 * Suggested regional servers
                 */
                if (dnsSettingsViewModel.regionalServers.isNotEmpty()) {

                    val connectedCountryCode = dnsSettingsViewModel.connectedCountryCode
                    val sortedServers = remember(dnsSettingsViewModel.regionalServers, connectedCountryCode) {
                        dnsSettingsViewModel.regionalServers.sortedWith(
                            compareByDescending<RegionalDnsSuggestionUi> { it.countryCode == connectedCountryCode }
                                .thenBy { it.countryCode }
                                .thenBy { it.name }
                        )
                    }

                    SettingsGroup(title = stringResource(id = R.string.suggested_remote_dns_servers)) {

                        sortedServers.forEachIndexed { index, server ->
                            if (0 < index) {
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                            RegionalServerRow(
                                server = server,
                                isConnectedCountry = server.countryCode == connectedCountryCode,
                                checked = currentDraft.remoteDnsIpv4.contains(server.ipv4),
                                toggle = {
                                    draft = if (currentDraft.remoteDnsIpv4.contains(server.ipv4)) {
                                        currentDraft.copy(
                                            remoteDnsIpv4 = currentDraft.remoteDnsIpv4 - server.ipv4
                                        )
                                    } else {
                                        // adding a suggestion also enables remote dns
                                        currentDraft.copy(
                                            remoteDnsIpv4 = currentDraft.remoteDnsIpv4 + server.ipv4,
                                            enableRemoteDns = true
                                        )
                                    }
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            stringResource(id = R.string.suggested_remote_dns_servers_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextFaint
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                /**
                 * Server lists
                 */
                ServerListGroup(
                    title = stringResource(id = R.string.remote_doh_urls),
                    ipv4 = currentDraft.remoteDohUrlsIpv4,
                    ipv6 = currentDraft.remoteDohUrlsIpv6,
                    validate = ::isValidDohUrl,
                    placeholder = "https://",
                    onChange = { v4, v6 ->
                        draft = currentDraft.copy(remoteDohUrlsIpv4 = v4, remoteDohUrlsIpv6 = v6)
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                ServerListGroup(
                    title = stringResource(id = R.string.local_doh_urls),
                    ipv4 = currentDraft.localDohUrlsIpv4,
                    ipv6 = currentDraft.localDohUrlsIpv6,
                    validate = ::isValidDohUrl,
                    placeholder = "https://",
                    onChange = { v4, v6 ->
                        draft = currentDraft.copy(localDohUrlsIpv4 = v4, localDohUrlsIpv6 = v6)
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                ServerListGroup(
                    title = stringResource(id = R.string.remote_dns_servers),
                    ipv4 = currentDraft.remoteDnsIpv4,
                    ipv6 = currentDraft.remoteDnsIpv6,
                    validate = ::isIpAddressValue,
                    placeholder = stringResource(id = R.string.ip_address),
                    onChange = { v4, v6 ->
                        draft = currentDraft.copy(remoteDnsIpv4 = v4, remoteDnsIpv6 = v6)
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                ServerListGroup(
                    title = stringResource(id = R.string.local_dns_servers),
                    ipv4 = currentDraft.localDnsIpv4,
                    ipv6 = currentDraft.localDnsIpv6,
                    validate = ::isIpAddressValue,
                    placeholder = stringResource(id = R.string.ip_address),
                    onChange = { v4, v6 ->
                        draft = currentDraft.copy(localDnsIpv4 = v4, localDnsIpv6 = v6)
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

            }

            // pinned below the scrolling content; on a tablet it is exactly as
            // wide as the content column above it, never the whole screen
            Column(
                modifier = Modifier
                    .tabletReadableColumn()
                    .padding(16.dp)
            ) {
                URButton(
                    onClick = {
                        draft?.let {
                            dnsSettingsViewModel.apply(it)
                            original = it
                        }
                    },
                    enabled = isDirty
                ) { buttonTextStyle ->
                    Text(
                        stringResource(id = R.string.update),
                        style = buttonTextStyle
                    )
                }
            }

        }
    }
}

private fun isValidDohUrl(value: String): Boolean {
    return value.startsWith("https://") && 8 < value.length
}

/**
 * A compact status row shown when the current settings already match a
 * suggested configuration, in place of its apply panel. A country color circle
 * marks the regional recommendation; a lock marks the most secure defaults.
 */
@Composable
private fun DnsStatusRow(
    text: String,
    countryCode: String?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MainTintedBackgroundBase,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (countryCode != null) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .background(
                        color = countryColor(countryCode),
                        shape = CircleShape
                    )
            )
        } else {
            Icon(
                Icons.Filled.Lock,
                contentDescription = null,
                tint = Green,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White
        )
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            title,
            style = TextStyle(color = TextMuted)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    MainTintedBackgroundBase,
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(16.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun DnsToggleRow(
    label: String,
    detail: String?,
    checked: Boolean,
    toggle: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White
            )
            if (detail != null) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        }
        URSwitch(
            checked = checked,
            toggle = toggle
        )
    }
}

@Composable
private fun RegionalServerRow(
    server: RegionalDnsSuggestionUi,
    isConnectedCountry: Boolean,
    checked: Boolean,
    toggle: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {

            if (isConnectedCountry) {
                // suggested for the connected country
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(
                            color = countryColor(server.countryCode),
                            shape = CircleShape
                        )
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        server.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        server.countryCode.uppercase(),
                        style = TextStyle(fontSize = 10.sp),
                        color = TextFaint
                    )
                }
                Text(
                    server.ipv4,
                    style = TextStyle(fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                    color = TextMuted
                )
            }

        }

        URSwitch(
            checked = checked,
            toggle = toggle
        )

    }
}

private fun countryColor(countryCode: String): Color {
    return try {
        Color(android.graphics.Color.parseColor("#${Sdk.getColorHex(countryCode)}"))
    } catch (e: Throwable) {
        TextMuted
    }
}

@Composable
private fun ServerListGroup(
    title: String,
    ipv4: List<String>,
    ipv6: List<String>,
    validate: (String) -> Boolean,
    placeholder: String,
    onChange: (List<String>, List<String>) -> Unit,
) {
    SettingsGroup(title = title) {

        EditableValueList(
            label = stringResource(id = R.string.ipv4),
            values = ipv4,
            validate = validate,
            placeholder = placeholder,
            onChange = { onChange(it, ipv6) }
        )

        Spacer(modifier = Modifier.height(12.dp))

        EditableValueList(
            label = stringResource(id = R.string.ipv6),
            values = ipv6,
            validate = validate,
            placeholder = placeholder,
            onChange = { onChange(ipv4, it) }
        )

    }
}

/**
 * An editable list of string values with inline add and remove
 */
@Composable
private fun EditableValueList(
    label: String,
    values: List<String>,
    validate: (String) -> Boolean,
    placeholder: String,
    onChange: (List<String>) -> Unit,
) {

    var newValue by remember { mutableStateOf(TextFieldValue("")) }

    val trimmed = newValue.text.trim()
    val canAdd = validate(trimmed) && !values.contains(trimmed)

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {

        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = TextFaint
        )

        for (value in values) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    value,
                    style = TextStyle(fontSize = 13.sp, fontFamily = FontFamily.Monospace),
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.Filled.Clear,
                    contentDescription = stringResource(id = R.string.remove),
                    tint = TextFaint,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable {
                            onChange(values - value)
                        }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Row(modifier = Modifier.weight(1f)) {
                URTextInput(
                    value = newValue,
                    onValueChange = { newValue = it },
                    placeholder = placeholder,
                    label = null,
                )
            }

            Icon(
                Icons.Filled.Add,
                contentDescription = stringResource(id = R.string.add),
                tint = if (canAdd) Green else TextFaint,
                modifier = Modifier
                    .size(20.dp)
                    .clickable(enabled = canAdd) {
                        onChange(values + trimmed)
                        newValue = TextFieldValue("")
                    }
            )

        }

    }
}
