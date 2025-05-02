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

@Composable
fun NoSolanaWalletsAlert(
    onDismiss: () -> Unit
) {
    AlertDialog(
        icon = {
            Icon(Icons.Default.Warning, contentDescription = "No wallets found")
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
                Text("Ok")
            }
        },
    )
}