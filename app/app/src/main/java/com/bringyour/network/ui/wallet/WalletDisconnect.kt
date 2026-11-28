package com.bringyour.network.ui.wallet

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.bringyour.network.R
import com.bringyour.network.ui.components.ButtonStyle
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.components.URDialog
import com.bringyour.network.ui.components.URInlineErrorText
import com.bringyour.network.ui.components.URTextInput
import com.bringyour.sdk.Sdk

/*
 * Disconnecting the payout wallet from the Earnings screen's wallet card
 * (sdk api_sn_wallet_disconnect.go). Only a wallet connected without a signed
 * payout consent can be disconnected, which the card's wallet read says
 * (SnGetWalletResult.Disconnectable); the owner confirms it with a code the
 * server emails to the account's sign-in addresses. Afterwards the network has
 * no payout wallet, and its earnings can't be paid out until one is connected.
 */

/**
 * The disconnect's dialog: the confirmation, which asks for the code, then
 * the code the server emailed.
 */
sealed class WalletDisconnectDialogState {
    // what disconnecting means, and Email me a code (`sending` while it is asked for)
    data class Confirm(
        val sending: Boolean = false,
        val error: WalletDisconnectError? = null,
    ) : WalletDisconnectDialogState()

    // the code went to `sentTo`, masked: its field, and Disconnect wallet
    data class EnterCode(
        val sentTo: List<String>,
        val disconnecting: Boolean = false,
        val error: WalletDisconnectError? = null,
    ) : WalletDisconnectDialogState()
}

/**
 * A refused or failed disconnect step as the dialog words it: a store key
 * for the codes the app knows, else the server's message, else the generic
 * error.
 */
data class WalletDisconnectError(
    @StringRes val messageRes: Int?,
    val message: String = "",
) {
    companion object {
        // a request that failed, with no answer to word
        val generic = WalletDisconnectError(messageRes = R.string.something_went_wrong)

        fun of(refused: SnWalletDisconnectAnswer.Refused) = WalletDisconnectError(
            messageRes = walletDisconnectRefusalRes(refused.code),
            message = refused.message,
        )
    }
}

/**
 * The store key for a disconnect refusal (SnWalletDisconnectError.Code), null
 * for owner_only and the codes this build does not know, which show the
 * server's message. no_wallet has none either: the card reads the wallet
 * again instead.
 */
@StringRes
fun walletDisconnectRefusalRes(code: String): Int? = when (code) {
    Sdk.SnWalletDisconnectErrorCodeConsentInForce -> R.string.wallet_disconnect_consent_in_force
    Sdk.SnWalletDisconnectErrorCodeEmailRequired -> R.string.wallet_disconnect_email_required
    Sdk.SnWalletDisconnectErrorCodeRateLimited -> R.string.account_too_many_tries
    Sdk.SnWalletDisconnectErrorCodeSendFailed -> R.string.account_code_send_failed
    Sdk.SnWalletDisconnectErrorCodeCodeInvalid -> R.string.account_code_invalid
    Sdk.SnWalletDisconnectErrorCodeCodeExpired -> R.string.account_code_expired
    else -> null
}

@Composable
private fun walletDisconnectErrorText(error: WalletDisconnectError): String =
    error.messageRes?.let { stringResource(id = it) }
        ?: error.message.ifBlank { stringResource(id = R.string.something_went_wrong) }

/**
 * Disconnect your payout wallet?: what it means and Email me a code, then the
 * emailed code and Disconnect wallet. Cancel, or a tap outside, closes it.
 */
@Composable
fun WalletDisconnectDialog(
    state: WalletDisconnectDialogState,
    onSendCode: () -> Unit,
    onDisconnect: (code: String) -> Unit,
    onDismiss: () -> Unit,
) {
    URDialog(
        visible = true,
        onDismiss = onDismiss,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(id = R.string.wallet_disconnect_title),
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            when (state) {
                is WalletDisconnectDialogState.Confirm -> {
                    Text(
                        stringResource(id = R.string.wallet_disconnect_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                    state.error?.let { error ->
                        Spacer(modifier = Modifier.height(8.dp))
                        URInlineErrorText(walletDisconnectErrorText(error))
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    URButton(
                        onClick = onSendCode,
                        isProcessing = state.sending,
                    ) { buttonTextStyle ->
                        Text(stringResource(id = R.string.wallet_disconnect_send_code), style = buttonTextStyle)
                    }
                }
                is WalletDisconnectDialogState.EnterCode -> {
                    // kept through a wrong code; a new code request starts it empty
                    var code by remember { mutableStateOf(TextFieldValue("")) }
                    Text(
                        stringResource(id = R.string.wallet_disconnect_code_sent, state.sentTo.joinToString(", ")),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    URTextInput(
                        value = code,
                        onValueChange = { code = it },
                        label = stringResource(id = R.string.code),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        onDone = { onDisconnect(code.text) },
                        enabled = !state.disconnecting,
                    )
                    state.error?.let { error ->
                        URInlineErrorText(walletDisconnectErrorText(error))
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    URButton(
                        onClick = { onDisconnect(code.text) },
                        style = ButtonStyle.DESTRUCTIVE,
                        enabled = code.text.isNotBlank(),
                        isProcessing = state.disconnecting,
                    ) { buttonTextStyle ->
                        Text(stringResource(id = R.string.wallet_disconnect), style = buttonTextStyle)
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            URButton(
                onClick = onDismiss,
                style = ButtonStyle.OUTLINE,
                modifier = Modifier.fillMaxWidth()
            ) { buttonTextStyle ->
                Text(stringResource(id = R.string.cancel), style = buttonTextStyle)
            }
        }
    }
}
