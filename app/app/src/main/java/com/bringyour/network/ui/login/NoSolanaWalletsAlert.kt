package com.bringyour.network.ui.login

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.bringyour.network.R

/**
 * No Solana wallet app on the device answered the Mobile Wallet Adapter request: none is
 * installed, or none of the installed ones answers it (as reported with Brave Wallet).
 * Signing in needs a wallet app's signature, so by default the alert says to install one.
 * The payout wallet can also be connected by its address: given [onEnterManually], the
 * alert points at the sheet's manual entry in that control's own words and offers it.
 */
@Composable
fun NoSolanaWalletsAlert(
    onDismiss: () -> Unit,
    onEnterManually: (() -> Unit)? = null,
) {
    AlertDialog(
        icon = {
            Icon(Icons.Default.Warning, contentDescription = null)
        },
        title = {
            Text(text = stringResource(id = R.string.no_solana_wallets_found))
        },
        text = {
            Text(
                text = stringResource(
                    id = if (onEnterManually == null) {
                        R.string.no_wallets_found_alert_content
                    } else {
                        R.string.no_wallets_found_enter_address_manually
                    }
                )
            )
        },
        onDismissRequest = {
            onDismiss()
        },
        confirmButton = {
            if (onEnterManually == null) {
                TextButton(
                    onClick = {
                        onDismiss()
                    }
                ) {
                    Text(stringResource(id = R.string.close))
                }
            } else {
                TextButton(onClick = onEnterManually) {
                    Text(stringResource(id = R.string.enter_address_manually))
                }
            }
        },
        dismissButton = {
            // a second button only when the first one opens manual entry
            if (onEnterManually != null) {
                TextButton(
                    onClick = {
                        onDismiss()
                    }
                ) {
                    Text(stringResource(id = R.string.close))
                }
            }
        },
    )
}
