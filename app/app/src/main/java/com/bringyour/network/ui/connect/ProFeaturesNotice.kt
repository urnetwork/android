package com.bringyour.network.ui.connect

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.bringyour.network.MainApplication
import com.bringyour.network.R
import com.bringyour.network.ui.Route
import com.bringyour.network.ui.components.ButtonStyle
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.sdk.Sdk

/*
 * The network's Pro features in use, as the app shows them: SOCKS and
 * WireGuard proxies are part of Pro (owner decision 2026-11-11), so a network
 * without Pro whose hosted proxies hold them is told to upgrade before the
 * server enforces it, and that they stopped once it does (the sdk's
 * ProFeaturesStatus.prompt). The main screen shows a notice, which
 * MainApplication publishes (proFeaturesMainPrompt), and the device list shows
 * the prompt above the proxies it lists. What to say, when the notice shows
 * and how long the user's close holds are the sdk's decisions, the same on
 * every platform (ProFeaturesViewController, ProFeaturesNoticeTracker); this
 * file holds only the words and the screen. No local notification says it.
 */

/** The title: what stopped, once the server enforces Pro, else that Pro features are in use. */
@StringRes
fun proFeaturesTitleId(prompt: String): Int =
    if (prompt == Sdk.ProFeaturesPromptStopped) R.string.pro_features_stopped else R.string.pro_features_in_use

/** What it means and what to do: upgrade to keep SOCKS and WireGuard, or to turn them back on. */
@StringRes
fun proFeaturesMessageId(prompt: String): Int =
    if (prompt == Sdk.ProFeaturesPromptStopped) R.string.pro_features_stopped_message else R.string.pro_features_upgrade_message

/**
 * The main screen's notice, under the client limit's when both show: the title
 * and words for the prompt, Get Pro (the upgrade screen every Get Pro opens,
 * the client limit notice's too), Manage devices, which lists the network's
 * proxies, and a close that withdraws it while its prompt stays the same.
 * Nothing here disconnects or holds a connect back: the notice is about the
 * network's hosted proxies, not this device's connection.
 */
@Composable
fun ProFeaturesNotice(navController: NavController) {
    val application = LocalContext.current.applicationContext as? MainApplication ?: return
    val prompt by application.proFeaturesMainPrompt.collectAsState()
    if (prompt.isEmpty()) return

    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            // a title that wraps keeps the close at its first line
            verticalAlignment = Alignment.Top
        ) {
            Text(
                stringResource(id = proFeaturesTitleId(prompt)),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp)
                    .testTag("acceptance.pro_features_title")
            )
            // small; Compose still gives it a 48 dp touch target
            IconButton(
                onClick = { application.dismissProFeaturesNotice() },
                modifier = Modifier
                    .size(24.dp)
                    .testTag("acceptance.pro_features_close")
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(id = R.string.close),
                    tint = TextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            stringResource(id = proFeaturesMessageId(prompt)),
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .testTag("acceptance.pro_features_notice")
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            URButton(
                onClick = { navController.navigate(Route.Upgrade) },
                style = ButtonStyle.OUTLINE,
                modifier = Modifier
                    .weight(1f)
                    .testTag("acceptance.pro_features_upgrade")
            ) { buttonTextStyle ->
                Text(
                    stringResource(id = R.string.get_pro),
                    style = buttonTextStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
            URButton(
                onClick = { navController.navigate(Route.ManageDevices) },
                style = ButtonStyle.OUTLINE,
                modifier = Modifier
                    .weight(1f)
                    .testTag("acceptance.pro_features_manage_devices")
            ) { buttonTextStyle ->
                Text(
                    stringResource(id = R.string.manage_devices),
                    style = buttonTextStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}
