package com.bringyour.network.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bringyour.network.R
import com.bringyour.network.ui.connect.outOfBalanceKind
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.widgets.WidgetSnapshotStore

/**
 * Set by the upgrade destination when a start connect blocked by insufficient
 * balance opened it (upgradeShowsFreeRefresh): closes the screen to wait for
 * the free data. Every flavor's upgrade screen shows the header, so the lines
 * and the button need no per-flavor change. The blocked connect itself waits
 * on the balance and is retried once it is back (BalanceRecovery).
 */
val LocalUpgradeWaitForRefresh = compositionLocalOf<(() -> Unit)?> { null }

@Composable
fun UpgradeScreenHeader() {
    Column {

        Spacer(modifier = Modifier.height(32.dp))

        // "Get Pro", the same title the Apple sheet and the account card use;
        // this used to read "Become a URnetwork Supporter".
        Text(
            stringResource(id = R.string.get_pro),
            style = MaterialTheme.typography.headlineLarge
        )

        // No explainer under the title: the screen is the title and the two
        // plan options. A blocked connect is the exception: the upgrade must
        // not read as the only way back, so it says when the free data
        // refreshes and offers to wait for it.
        val waitForRefresh = LocalUpgradeWaitForRefresh.current
        if (waitForRefresh != null) {

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                stringResource(id = R.string.insufficient_balance_refreshes_in, rememberFreeRefreshCountdown()),
                style = MaterialTheme.typography.bodyLarge,
                color = TextMuted
            )

            // reserved data may come back before the refresh; used up does not
            val context = LocalContext.current
            val balanceChanges by WidgetSnapshotStore.changes.collectAsState()
            val accountBalance = remember(balanceChanges) { WidgetSnapshotStore.loadBalance(context) }
            val kindText = outOfBalanceKindText(
                outOfBalanceKind(accountBalance),
                accountBalance?.openTransferByteCount ?: 0L,
            )
            if (kindText != null) {

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    kindText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextMuted
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            URButton(
                onClick = waitForRefresh,
                style = ButtonStyle.OUTLINE,
                modifier = Modifier.testTag("acceptance.upgrade_wait_for_refresh")
            ) { buttonTextStyle ->
                Text(
                    stringResource(id = R.string.wait_for_refresh),
                    style = buttonTextStyle
                )
            }
        }
    }
}
