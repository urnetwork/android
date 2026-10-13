package com.bringyour.network.ui.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bringyour.network.R
import com.bringyour.network.ui.components.ButtonStyle
import com.bringyour.network.ui.components.SectionLoadError
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.components.URTextInput
import com.bringyour.network.ui.theme.MainTintedBackgroundBase
import com.bringyour.network.ui.theme.Red
import com.bringyour.network.ui.theme.SheetBlack
import com.bringyour.network.ui.theme.TextFaint
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.URNetworkTheme

/**
 * The payout consent block, under the Settlement v2 tile: without a consent,
 * why to sign and Sign with wallet; with one, the coldkey paid, Change
 * wallet and when a change takes effect. Sign and Change start the same
 * steps. While the wallet signs, the waiting line; after a refusal, its line.
 */
@Composable
fun SnConsentBlock(
    tile: SnConsentTile,
    onSign: () -> Unit,
    onRetry: () -> Unit,
    shortSs58: (String) -> String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MainTintedBackgroundBase, RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Text(
            stringResource(id = R.string.sn_consent_title),
            style = MaterialTheme.typography.bodyLarge
        )

        when {
            tile.unavailable -> SectionLoadError(onRetry = onRetry)
            tile.loading -> ConsentSpinner()
            tile.activeColdkeySs58 != null -> {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    stringResource(id = R.string.sn_consent_active, shortSs58(tile.activeColdkeySs58)),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                URButton(
                    onClick = onSign,
                    style = ButtonStyle.OUTLINE,
                    enabled = tile.actionEnabled,
                    isProcessing = tile.signing
                ) { buttonTextStyle ->
                    Text(stringResource(id = R.string.sn_consent_change), style = buttonTextStyle)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    stringResource(id = R.string.sn_consent_change_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
            else -> {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    stringResource(id = R.string.sn_consent_none),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    stringResource(id = R.string.sn_consent_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
                Spacer(modifier = Modifier.height(16.dp))
                URButton(
                    onClick = onSign,
                    enabled = tile.actionEnabled,
                    isProcessing = tile.signing
                ) { buttonTextStyle ->
                    Text(stringResource(id = R.string.sn_consent_sign), style = buttonTextStyle)
                }
            }
        }

        if (tile.signing) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                stringResource(id = R.string.sn_consent_signing),
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )
        }

        tile.outcome?.let { outcome ->
            val context = LocalContext.current
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                when (outcome) {
                    is SnConsentOutcome.WalletError ->
                        snConsentWalletErrorText(outcome, bittensorWalletDisplayName(outcome.walletId)) { res, walletName ->
                            if (walletName == null) context.getString(res) else context.getString(res, walletName)
                        }
                    is SnConsentOutcome.Failed -> outcome.detail ?: stringResource(id = R.string.login_error)
                    else -> stringResource(id = snConsentOutcomeRes(outcome) ?: R.string.login_error)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Red
            )
        }
    }
}

/**
 * The coldkey the consent will name, after the wallet is chosen: empty, since
 * the payout coldkey is a deliberate choice and may differ from the wallet
 * the network signs in with. Continue checks it locally, then asks for the
 * challenge.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnConsentColdkeySheet(
    entry: SnConsentColdkeyEntry,
    onAddressChange: (String) -> Unit,
    onContinue: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SheetBlack,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Text(
                stringResource(id = R.string.sn_consent_title),
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                stringResource(id = R.string.sn_consent_body),
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )
            Spacer(modifier = Modifier.height(16.dp))
            URTextInput(
                value = TextFieldValue(entry.address, TextRange(entry.address.length)),
                onValueChange = { onAddressChange(it.text) },
                label = stringResource(id = R.string.bittensor_wallet),
                placeholder = stringResource(id = R.string.earnings_address_placeholder),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Done
                ),
                onDone = onContinue,
                isValid = !entry.invalid,
                supportingText = if (entry.invalid) stringResource(id = R.string.invalid_ss58_address) else null,
            )
            Spacer(modifier = Modifier.height(24.dp))
            URButton(
                onClick = onContinue,
                enabled = entry.address.isNotBlank(),
            ) { buttonTextStyle ->
                Text(stringResource(id = R.string.continue_txt), style = buttonTextStyle)
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ConsentSpinner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            color = TextMuted,
            trackColor = TextFaint,
            strokeWidth = 2.dp
        )
    }
}

@Preview
@Composable
private fun SnConsentNonePreview() {
    URNetworkTheme {
        SnConsentBlock(
            tile = snConsentTile(
                SnConsentState(SnConsentLoad.Loaded(SnConsentStatusState(active = false, coldkeySs58 = "")), networkCredential = true),
                BittensorProofStage.Hidden
            ),
            onSign = {},
            onRetry = {},
            shortSs58 = { com.bringyour.network.utils.Ss58.short(it) }
        )
    }
}

@Preview
@Composable
private fun SnConsentActivePreview() {
    URNetworkTheme {
        SnConsentBlock(
            tile = snConsentTile(
                SnConsentState(
                    SnConsentLoad.Loaded(SnConsentStatusState(active = true, coldkeySs58 = SampleProtocolSource.SAMPLE_COLDKEY)),
                    networkCredential = true,
                    outcome = SnConsentOutcome.Expired
                ),
                BittensorProofStage.Hidden
            ),
            onSign = {},
            onRetry = {},
            shortSs58 = { com.bringyour.network.utils.Ss58.short(it) }
        )
    }
}
