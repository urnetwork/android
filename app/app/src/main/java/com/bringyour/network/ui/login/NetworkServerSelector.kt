package com.bringyour.network.ui.login

import android.util.Log
import androidx.compose.foundation.clickable
import java.util.Locale
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.bringyour.network.BuildConfig
import com.bringyour.network.MainApplication
import com.bringyour.network.R
import com.bringyour.network.TAG
import com.bringyour.network.ui.components.ButtonStyle
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.components.URDialog
import com.bringyour.network.ui.components.URInlineErrorText
import com.bringyour.network.ui.components.URTextInput
import com.bringyour.network.ui.theme.BlueMedium
import com.bringyour.network.ui.theme.TextFaint
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.Yellow
import com.bringyour.sdk.Sdk

private fun normalizeNetworkHost(raw: String): String {
    var value = raw.trim().lowercase(Locale.ROOT)
    val schemeSeparator = "://"
    if (value.contains(schemeSeparator)) {
        value = value.substringAfter(schemeSeparator)
    }
    value = value.substringBefore("/").substringBefore("?").substringBefore("#")
    value = value.substringAfter("@")
    // Strip a trailing :port when it looks like an IPv4 host:port or a
    // bracketed IPv6 literal like [2001:db8::1]:8080. A bare IPv6
    // address (no brackets) must not be mangled.
    if (value.startsWith("[") && value.contains("]:")) {
        // IPv6 with port: "[...]:port" — keep the brackets
        value = value.substringBeforeLast(":")
    } else if (!value.contains("[") && !value.contains(":")) {
        // Plain hostname — no colon, nothing to strip
    } else if (!value.contains("[")) {
        // IPv4 host:port — strip after the last colon
        value = value.substringBeforeLast(":")
    }
    return value.trim().trim('.')
}

private fun explicitScheme(raw: String): String? {
    val value = raw.trim()
    if (!value.contains("://")) {
        return null
    }
    return value.substringBefore("://").trim().lowercase().takeIf { it.isNotBlank() }
}

private fun normalizeApiUrl(raw: String): String {
    val value = raw.trim().trimEnd('/')
    if (value.isBlank()) {
        return ""
    }
    if (value.contains("://")) {
        return value
    }
    return "https://$value"
}

private fun normalizeConnectUrl(raw: String): String {
    val value = raw.trim().trimEnd('/')
    if (value.isBlank()) {
        return ""
    }
    if (value.contains("://")) {
        return value
    }
    return "wss://$value"
}

private fun hasInsecureScheme(raw: String, secureScheme: String): Boolean {
    return explicitScheme(raw)?.let { scheme ->
        scheme != secureScheme
    } ?: false
}

private fun derivedServiceUrl(
    hostName: String,
    migrationHostName: String,
    envName: String,
    scheme: String,
    service: String,
): String {
    val serviceHost = if (migrationHostName.isNotBlank()) {
        migrationHostName
    } else {
        hostName
    }
    val serviceHostName = if (envName == "main" || envName.isBlank()) {
        "$service.$serviceHost"
    } else {
        "$envName-$service.$serviceHost"
    }
    return "$scheme://$serviceHostName"
}

@Composable
fun NetworkServerSelector(
    enabled: Boolean = true,
) {
    val context = LocalContext.current
    val app = context.applicationContext as? MainApplication
    val manager = app?.networkSpaceManagerProvider?.getNetworkSpaceManager()
    val active = app?.networkSpaceManagerProvider?.getNetworkSpace()

    val officialHost = BuildConfig.BRINGYOUR_BUNDLE_HOST_NAME
    val officialMigrationHost = BuildConfig.BRINGYOUR_BUNDLE_MIGRATION_HOST_NAME
    val envName = BuildConfig.BRINGYOUR_BUNDLE_ENV_NAME
    val activeHost = active?.hostName ?: officialHost
    val currentApiUrl = active?.apiUrl ?: derivedServiceUrl(
        officialHost,
        officialMigrationHost,
        envName,
        "https",
        "api"
    )
    val currentConnectUrl = active?.platformUrl ?: derivedServiceUrl(
        officialHost,
        officialMigrationHost,
        envName,
        "wss",
        "connect"
    )
    val configuredApiUrl = active?.configuredApiUrl ?: ""
    val configuredConnectUrl = active?.configuredPlatformUrl ?: ""

    var isPresenting by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }
    val textColor = when {
        !enabled -> TextFaint
        isFocused -> BlueMedium
        else -> TextMuted
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(id = R.string.change_network_api),
            style = MaterialTheme.typography.bodySmall,
            color = textColor,
            modifier = Modifier
                .clickable(enabled = enabled) {
                    isPresenting = true
                }
                .onFocusChanged {
                    isFocused = it.isFocused
                }
                .focusable(enabled = enabled)
                .padding(8.dp)
        )
    }

    NetworkApiDialog(
        visible = isPresenting,
        enabled = enabled,
        managerAvailable = manager != null,
        initialHost = activeHost,
        officialHost = officialHost,
        officialMigrationHost = officialMigrationHost,
        envName = envName,
        currentApiUrl = currentApiUrl,
        currentConnectUrl = currentConnectUrl,
        configuredApiUrl = configuredApiUrl,
        configuredConnectUrl = configuredConnectUrl,
        onDismiss = {
            isPresenting = false
        },
        onApply = { host, apiUrl, connectUrl, setStatus ->
            val managerLocal = manager ?: run {
                setStatus(context.getString(R.string.network_api_manager_unavailable))
                return@NetworkApiDialog
            }

            val normalizedHost = normalizeNetworkHost(host)
            if (normalizedHost.isBlank()) {
                setStatus(context.getString(R.string.network_api_enter_domain))
                return@NetworkApiDialog
            }

            val normalizedOfficialHost = normalizeNetworkHost(officialHost)
            val isOfficial = normalizedHost == normalizedOfficialHost
            val normalizedApiUrl = normalizeApiUrl(apiUrl)
            val normalizedConnectUrl = normalizeConnectUrl(connectUrl)
            val hasExplicitUrls = normalizedApiUrl.isNotBlank() || normalizedConnectUrl.isNotBlank()

            try {
                val key = Sdk.newNetworkSpaceKey(normalizedHost, envName)
                val networkSpace = managerLocal.updateNetworkSpace(key) { values ->
                    values.envSecret = BuildConfig.BRINGYOUR_BUNDLE_ENV_SECRET
                    values.bundled = isOfficial && !hasExplicitUrls
                    values.netExposeServerIps = BuildConfig.BRINGYOUR_BUNDLE_NET_EXPOSE_SERVER_IPS
                    values.netExposeServerHostNames = BuildConfig.BRINGYOUR_BUNDLE_NET_EXPOSE_SERVER_HOST_NAMES
                    values.linkHostName = if (isOfficial) {
                        BuildConfig.BRINGYOUR_BUNDLE_LINK_HOST_NAME
                    } else {
                        normalizedHost
                    }
                    values.migrationHostName = if (isOfficial) {
                        BuildConfig.BRINGYOUR_BUNDLE_MIGRATION_HOST_NAME
                    } else {
                        ""
                    }
                    values.store = BuildConfig.BRINGYOUR_BUNDLE_STORE
                    values.wallet = BuildConfig.BRINGYOUR_BUNDLE_WALLET
                    values.ssoGoogle = BuildConfig.BRINGYOUR_BUNDLE_SSO_GOOGLE
                    values.apiUrl = normalizedApiUrl
                    values.platformUrl = normalizedConnectUrl
                }
                managerLocal.activeNetworkSpace = networkSpace
                setStatus(context.getString(R.string.network_api_switched_to, normalizedHost))
                Log.i(context.TAG, "Network API switched to host=$normalizedHost")
                isPresenting = false
            } catch (e: Exception) {
                Log.e(context.TAG, "Failed to switch Network API", e)
                setStatus(context.getString(R.string.network_api_failed, e.message ?: "unknown error"))
            }
        }
    )
}

@Composable
private fun NetworkApiDialog(
    visible: Boolean,
    enabled: Boolean,
    managerAvailable: Boolean,
    initialHost: String,
    officialHost: String,
    officialMigrationHost: String,
    envName: String,
    currentApiUrl: String,
    currentConnectUrl: String,
    configuredApiUrl: String,
    configuredConnectUrl: String,
    onDismiss: () -> Unit,
    onApply: (
        host: String,
        apiUrl: String,
        connectUrl: String,
        setStatus: (String) -> Unit,
    ) -> Unit,
) {
    var hostName by remember(visible, initialHost) {
        mutableStateOf(TextFieldValue(normalizeNetworkHost(initialHost.ifBlank { officialHost })))
    }
    var apiUrl by remember(visible, configuredApiUrl) { mutableStateOf(TextFieldValue(configuredApiUrl)) }
    var connectUrl by remember(visible, configuredConnectUrl) { mutableStateOf(TextFieldValue(configuredConnectUrl)) }
    var statusText by remember(visible) {
        mutableStateOf("")
    }

    val normalizedHost = normalizeNetworkHost(hostName.text)
    val normalizedOfficialHost = normalizeNetworkHost(officialHost)
    val activeHost = normalizedHost.ifBlank { officialHost }
    val activeMigrationHost = if (normalizeNetworkHost(activeHost) == normalizedOfficialHost) {
        officialMigrationHost
    } else {
        ""
    }
    val derivedApiUrl = derivedServiceUrl(activeHost, activeMigrationHost, envName, "https", "api")
    val derivedConnectUrl = derivedServiceUrl(activeHost, activeMigrationHost, envName, "wss", "connect")
    val showInsecureEndpointWarning =
        (apiUrl.text.isNotBlank() && hasInsecureScheme(apiUrl.text, "https")) ||
            (connectUrl.text.isNotBlank() && hasInsecureScheme(connectUrl.text, "wss"))

    URDialog(
        visible = visible,
        onDismiss = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = stringResource(id = R.string.change_network_api_title),
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(id = R.string.network_api_description),
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(id = R.string.network_api_current_api, currentApiUrl),
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(id = R.string.network_api_current_connect, currentConnectUrl),
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
            Spacer(modifier = Modifier.height(20.dp))

            URTextInput(
                value = hostName,
                onValueChange = { hostName = it },
                placeholder = officialHost,
                label = stringResource(id = R.string.network_api_domain_label),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Next
                ),
                supportingText = stringResource(id = R.string.network_api_domain_help),
                enabled = enabled && managerAvailable
            )

            URTextInput(
                value = apiUrl,
                onValueChange = { apiUrl = it },
                placeholder = derivedApiUrl,
                label = stringResource(id = R.string.network_api_api_url_label),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Next
                ),
                supportingText = stringResource(id = R.string.network_api_api_url_help),
                enabled = enabled && managerAvailable
            )

            URTextInput(
                value = connectUrl,
                onValueChange = { connectUrl = it },
                placeholder = derivedConnectUrl,
                label = stringResource(id = R.string.network_api_connect_url_label),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done
                ),
                supportingText = stringResource(id = R.string.network_api_connect_url_help),
                enabled = enabled && managerAvailable
            )

            if (showInsecureEndpointWarning) {
                Text(
                    text = stringResource(id = R.string.network_api_insecure_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = Yellow
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            URButton(
                style = ButtonStyle.SECONDARY,
                onClick = {
                    hostName = TextFieldValue(officialHost)
                    apiUrl = TextFieldValue("")
                    connectUrl = TextFieldValue("")
                    onApply(officialHost, "", "", { message -> statusText = message })
                },
                enabled = enabled && managerAvailable
            ) { buttonTextStyle ->
                Text(
                    text = stringResource(id = R.string.network_api_use_default),
                    style = buttonTextStyle
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            URButton(
                onClick = {
                    onApply(hostName.text, apiUrl.text, connectUrl.text, { message -> statusText = message })
                },
                enabled = enabled && managerAvailable
            ) { buttonTextStyle ->
                Text(
                    text = stringResource(id = R.string.network_api_apply),
                    style = buttonTextStyle
                )
            }

            if (!managerAvailable) {
                Spacer(modifier = Modifier.height(12.dp))
                URInlineErrorText(stringResource(id = R.string.network_api_manager_unavailable))
            }

            if (statusText.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        }
    }
}
