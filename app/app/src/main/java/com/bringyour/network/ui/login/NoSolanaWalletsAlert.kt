package com.bringyour.network.ui.login

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.bringyour.network.R
import com.bringyour.network.ui.components.FoldSafeAlertDialog

/**
 * No Solana wallet app on the device answered the Mobile Wallet Adapter request: none is
 * installed, or none of the installed ones answers it (as reported with Brave Wallet).
 * Signing in needs a wallet app's signature, so the alert says to install one.
 */
@Composable
fun NoSolanaWalletsAlert(
    onDismiss: () -> Unit,
) {
    FoldSafeAlertDialog(
        icon = {
            Icon(Icons.Default.Warning, contentDescription = null)
        },
        title = {
            Text(text = stringResource(id = R.string.no_solana_wallets_found))
        },
        text = {
            Text(text = stringResource(id = R.string.no_wallets_found_alert_content))
        },
        onDismissRequest = {
            onDismiss()
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismiss()
                }
            ) {
                Text(stringResource(id = R.string.close))
            }
        },
    )
}
