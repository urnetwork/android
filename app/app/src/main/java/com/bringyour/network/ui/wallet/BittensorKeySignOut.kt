package com.bringyour.network.ui.wallet

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bringyour.network.MainApplication
import com.bringyour.network.R
import com.bringyour.network.ui.components.ButtonStyle
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.components.URDialog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/*
 * The payout key outside Earnings (apple/BTEASY.md, Edge cases): a sign out
 * or an account deletion while the network's key isn't saved warns first,
 * with Back up first, which opens Earnings with the Key screen, as the key's
 * one notification does. The key survives a sign out, kept by network id, and
 * comes back when that network signs in again; an account deletion takes it
 * with the account. With no unsaved key a sign out asks nothing, as before.
 */

const val ACCOUNT_LOGOUT_BACK_UP_FIRST_TAG = "acceptance.account.logout.backUpFirst"
const val ACCOUNT_LOGOUT_KEY_WARNING_TAG = "acceptance.account.logout.keyWarning"

/**
 * A request for Earnings with the Key screen open: Back up first, or the
 * reminder's notification (QuickConnectActivity.ROUTE_EARNINGS_KEY). The nav
 * host goes to Earnings once per request, and Earnings opens the Key screen
 * once its flow is ready. Held for the process, like the consent's returns.
 */
object BittensorKeyScreenRequests {
    private val _navigation = MutableStateFlow(false)
    val navigation: StateFlow<Boolean> = _navigation.asStateFlow()

    private val _screen = MutableStateFlow(false)
    val screen: StateFlow<Boolean> = _screen.asStateFlow()

    fun request() {
        _screen.value = true
        _navigation.value = true
    }

    fun consumeNavigation() {
        _navigation.value = false
    }

    /** The waiting request for the Key screen, taken once. */
    fun take(): Boolean {
        val requested = _screen.value
        _screen.value = false
        return requested
    }
}

/** The signed-in network's payout key, as the sign out and account deletion confirmations ask about it. */
object BittensorKeySignOut {

    /** The signed-in network's id, "" when signed out. */
    fun networkId(context: Context): String =
        (context.applicationContext as? MainApplication)?.deviceManager?.jwtFlow?.value?.networkId?.toString().orEmpty()

    /** The network has a key on this device whose phrase is not known to be saved: the sdk says. */
    fun needsBackup(context: Context, networkId: String = networkId(context)): Boolean =
        AndroidBittensorLocalKeyStore.shared(context).needsBackup(networkId)

    /**
     * After an account deletion: the key and its notes go with the network,
     * and its reminder with them. False if the key could not be shown gone.
     */
    fun forgetNetwork(context: Context, networkId: String): Boolean {
        BittensorKeyReminderNotice.withdraw(context)
        return AndroidBittensorLocalKeyStore.shared(context).forgetNetwork(networkId)
    }
}

/**
 * The sign out's confirmation while the key isn't saved (the mocks' 3f): Back
 * up first opens the Key screen instead of signing out.
 */
@Composable
fun BittensorKeySignOutDialog(
    onBackUpFirst: () -> Unit,
    onSignOut: () -> Unit,
    onDismiss: () -> Unit,
) {
    URDialog(
        visible = true,
        onDismiss = onDismiss,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(id = R.string.bittensor_key_sign_out_title_2),
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                stringResource(id = R.string.bittensor_key_sign_out_message_2),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(16.dp))
            URButton(
                onClick = onBackUpFirst,
                modifier = Modifier.testTag(ACCOUNT_LOGOUT_BACK_UP_FIRST_TAG),
            ) { buttonTextStyle ->
                Text(stringResource(id = R.string.bittensor_key_back_up_first), style = buttonTextStyle)
            }
            Spacer(modifier = Modifier.height(8.dp))
            URButton(
                onClick = onSignOut,
                style = ButtonStyle.WARNING,
                modifier = Modifier.testTag(ACCOUNT_LOGOUT_KEY_WARNING_TAG),
            ) { buttonTextStyle ->
                Text(stringResource(id = R.string.sign_out), style = buttonTextStyle)
            }
            Spacer(modifier = Modifier.height(8.dp))
            URButton(
                onClick = onDismiss,
                style = ButtonStyle.OUTLINE,
                modifier = Modifier.fillMaxWidth(),
            ) { buttonTextStyle ->
                Text(stringResource(id = R.string.cancel), style = buttonTextStyle)
            }
        }
    }
}
