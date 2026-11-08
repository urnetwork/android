package com.bringyour.network.ui.connect

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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

/**
 * The device's client limit hold, as the app shows it: the platform closed
 * the device for its network's concurrent client limit, and the device holds
 * off reconnecting (the sdk's ClientLimitStatus). MainApplication publishes it
 * while a connection is requested; the connect screen shows what to do, and a
 * local notification says it while the app is not in front. What to offer and
 * when the notification shows are the sdk's decisions, the same on every
 * platform (ClientLimitStatus.prompt and ClientLimitNoticeTracker); this file
 * holds only the words and the screen.
 */
data class ClientLimitHold(
    // Sdk.ClientLimitPromptUpgrade or Sdk.ClientLimitPromptRemoveClient
    val prompt: String,
) {
    /**
     * A free plan is offered UR Pro; a Pro plan, already the highest, is told
     * to free a slot. A device too old to send a prompt reads as free.
     */
    val offersUpgrade: Boolean
        get() = prompt != Sdk.ClientLimitPromptRemoveClient
}

/**
 * What to do: upgrade or disconnect another device on a free plan, sign out
 * of an unused device on Pro. The same words answer a sign-in the limit
 * refused.
 */
@StringRes
fun clientLimitMessageId(offersUpgrade: Boolean): Int =
    if (offersUpgrade) R.string.client_limit_upgrade_message else R.string.client_limit_remove_client_message

/**
 * The hold's notice, with the connect buttons: the title, what to do for the
 * plan, Get Pro when UR Pro lifts the limit (the upgrade screen every Get Pro
 * opens), Manage devices, where removing a device frees a slot for this one
 * (beside Get Pro on a free plan, alone on Pro), and Retry, which dials again
 * at once instead of waiting out the hold. Disconnect beside the notice stays
 * the way out; nothing here disconnects.
 */
@Composable
fun ClientLimitNotice(navController: NavController) {
    val application = LocalContext.current.applicationContext as? MainApplication ?: return
    val hold by application.clientLimitHold.collectAsState()
    val current = hold ?: return

    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            stringResource(id = R.string.client_limit_reached),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .testTag("acceptance.client_limit_title")
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            stringResource(id = clientLimitMessageId(current.offersUpgrade)),
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .testTag("acceptance.client_limit_notice")
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (current.offersUpgrade) {
                URButton(
                    onClick = { navController.navigate(Route.Upgrade) },
                    style = ButtonStyle.OUTLINE,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("acceptance.client_limit_upgrade")
                ) { buttonTextStyle ->
                    Text(
                        stringResource(id = R.string.get_pro),
                        style = buttonTextStyle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
            URButton(
                onClick = { navController.navigate(Route.ManageDevices) },
                style = ButtonStyle.OUTLINE,
                modifier = Modifier
                    .weight(1f)
                    .testTag("acceptance.client_limit_manage_devices")
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
        Spacer(modifier = Modifier.height(8.dp))
        // under them: the row keeps Get Pro and Manage devices readable on a
        // phone
        URButton(
            onClick = { application.retryClientLimit() },
            style = ButtonStyle.OUTLINE,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("acceptance.client_limit_retry")
        ) { buttonTextStyle ->
            Text(
                stringResource(id = R.string.retry),
                style = buttonTextStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}
