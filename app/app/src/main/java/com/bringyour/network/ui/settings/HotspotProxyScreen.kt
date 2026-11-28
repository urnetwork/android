package com.bringyour.network.ui.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.bringyour.hotspot.Hotspot
import com.bringyour.network.R
import com.bringyour.network.ui.components.ButtonStyle
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.components.URInlineErrorText
import com.bringyour.network.ui.components.URSwitch
import com.bringyour.network.ui.components.URTextInputLabel
import com.bringyour.network.ui.components.tabletReadableColumn
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.TopBarTitleTextStyle

/**
 * Account > Settings > Hotspot proxy: lets the devices on this phone's Wi-Fi
 * hotspot or USB tethering use URnetwork through an HTTP or SOCKS5 proxy on the
 * phone (HotspotProxyViewModel). Off until the user turns it on. While on, it
 * shows where it listens and the credentials to copy to the other device.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HotspotProxyScreen(
    navController: NavController,
    viewModel: HotspotProxyViewModel = hiltViewModel(),
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(id = R.string.hotspot_proxy),
                        style = TopBarTitleTextStyle
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
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
        HotspotProxyContent(
            status = viewModel.status,
            setEnabled = viewModel::setEnabled,
            regenerateCredentials = viewModel::regenerateCredentials,
            modifier = Modifier.padding(innerPadding),
        )
    }
}

@Composable
private fun HotspotProxyContent(
    status: HotspotProxyUi,
    setEnabled: (Boolean) -> Unit,
    regenerateCredentials: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val copy: (String) -> Unit = { text ->
        clipboardManager.setText(AnnotatedString(text))
        Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show()
    }

    Column(
        modifier = modifier
            .tabletReadableColumn()
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(PaddingValues(horizontal = 16.dp)),
    ) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(id = R.string.hotspot_proxy_share),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                modifier = Modifier.weight(1f),
            )
            // the switch's touch target adds 6 dp of its own
            Spacer(modifier = Modifier.width(10.dp))
            URSwitch(
                checked = status.enabled,
                enabled = status.available,
                toggle = { setEnabled(!status.enabled) },
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            stringResource(id = R.string.hotspot_proxy_description),
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
        )

        if (status.enabled) {
            Spacer(modifier = Modifier.height(24.dp))

            when (status.errorId) {
                Hotspot.HotspotProxyErrorNoHotspot -> Text(
                    stringResource(id = R.string.hotspot_proxy_waiting_for_hotspot),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted,
                )
                Hotspot.HotspotProxyErrorListenFailed -> URInlineErrorText(
                    stringResource(id = R.string.hotspot_proxy_listen_failed)
                )
            }

            status.endpoints.forEach { endpoint ->
                Spacer(modifier = Modifier.height(16.dp))
                HotspotProxyCopyRow(
                    label = stringResource(id = R.string.hotspot_proxy_http),
                    value = "${endpoint.host}:${endpoint.httpPort}",
                    copy = copy,
                )
                Spacer(modifier = Modifier.height(16.dp))
                HotspotProxyCopyRow(
                    label = stringResource(id = R.string.hotspot_proxy_socks),
                    value = "${endpoint.host}:${endpoint.socksPort}",
                    copy = copy,
                )
            }

            if (status.username.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                HotspotProxyCopyRow(
                    label = stringResource(id = R.string.hotspot_proxy_username),
                    value = status.username,
                    copy = copy,
                )
                Spacer(modifier = Modifier.height(16.dp))
                HotspotProxyCopyRow(
                    label = stringResource(id = R.string.password_label),
                    value = status.password,
                    copy = copy,
                )
            }

            if (status.listening) {
                Spacer(modifier = Modifier.height(24.dp))
                URButton(
                    onClick = { copy(status.connectionDetails) },
                    style = ButtonStyle.SECONDARY,
                ) { style ->
                    Text(stringResource(id = R.string.hotspot_proxy_copy_details), style = style)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            URButton(
                onClick = regenerateCredentials,
                style = ButtonStyle.OUTLINE,
                enabled = status.available,
            ) { style ->
                Text(stringResource(id = R.string.hotspot_proxy_new_password), style = style)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                stringResource(id = R.string.hotspot_proxy_new_password_note),
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

/** A label over a value that copies on tap. */
@Composable
private fun HotspotProxyCopyRow(
    label: String,
    value: String,
    copy: (String) -> Unit,
) {
    URTextInputLabel(text = label)
    Spacer(modifier = Modifier.height(4.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Color(0x1AFFFFFF),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { copy(value) }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            painter = painterResource(id = R.drawable.content_copy),
            contentDescription = stringResource(id = R.string.copy),
            tint = TextMuted,
            modifier = Modifier.width(16.dp)
        )
    }
}
