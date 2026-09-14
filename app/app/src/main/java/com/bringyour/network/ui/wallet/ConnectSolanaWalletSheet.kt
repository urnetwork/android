package com.bringyour.network.ui.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bringyour.network.R
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.components.URTextInput
import com.bringyour.network.ui.theme.BlueMedium
import com.bringyour.network.ui.theme.Red
import com.bringyour.network.ui.theme.SheetBlack
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.URNetworkTheme

const val SOLANA_CONNECT_WALLET_APP_TAG = "solana-connect-wallet-app"
const val SOLANA_ENTER_MANUALLY_TAG = "solana-enter-manually"
const val SOLANA_MANUAL_CONNECT_TAG = "solana-manual-connect"
const val SOLANA_CONNECT_ERROR_TAG = "solana-connect-error"

/**
 * Connect the Solana wallet USDC payouts go to, in two steps: CHOOSE (a wallet app through
 * Mobile Wallet Adapter, or manual entry) and MANUAL (the address, checked locally and
 * then by the server). Stateless: the step, the address and every state live in
 * [SolanaWalletViewModel], and the caller makes the wallet app call because it holds the
 * activity's ActivityResultSender.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectSolanaWalletSheet(
    step: SolanaSheetStep,
    state: SolanaConnectState,
    address: TextFieldValue,
    onAddressChange: (TextFieldValue) -> Unit,
    validation: AddressValidation,
    onConnectWalletApp: () -> Unit,
    onEnterManually: () -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    onDismissState: () -> Unit,
    onDismiss: () -> Unit,
) {
    // while a wallet app connect or a link is in flight the sheet stays up: the sheet hides
    // itself before it reports a dismissal, so a swipe, a tap outside and back are refused
    // here rather than ignored later
    val busy = rememberUpdatedState(state.busy)
    val confirmValueChange = remember(busy) {
        { value: SheetValue -> value != SheetValue.Hidden || !busy.value }
    }
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = confirmValueChange,
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SheetBlack,
        properties = ModalBottomSheetProperties(
            shouldDismissOnBackPress = !state.busy,
            shouldDismissOnClickOutside = !state.busy,
        ),
    ) {
        ConnectSolanaWalletSheetContent(
            step = step,
            state = state,
            address = address,
            onAddressChange = onAddressChange,
            validation = validation,
            onConnectWalletApp = onConnectWalletApp,
            onEnterManually = onEnterManually,
            onBack = onBack,
            onContinue = onContinue,
            onDismissState = onDismissState,
        )
    }
}

@Composable
fun ConnectSolanaWalletSheetContent(
    step: SolanaSheetStep,
    state: SolanaConnectState,
    address: TextFieldValue,
    onAddressChange: (TextFieldValue) -> Unit,
    validation: AddressValidation,
    onConnectWalletApp: () -> Unit,
    onEnterManually: () -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    onDismissState: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        when (step) {
            SolanaSheetStep.CHOOSE -> {
                Text(
                    stringResource(id = R.string.connect_solana_wallet),
                    style = MaterialTheme.typography.bodyLarge
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    stringResource(id = R.string.connect_solana_wallet_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    stringResource(id = R.string.usdc_payouts_until_migration),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Seeker and Saga open the built-in wallet; elsewhere any Mobile Wallet Adapter wallet
                URButton(
                    onClick = onConnectWalletApp,
                    enabled = !state.busy,
                    isProcessing = state is SolanaConnectState.ConnectingApp || state is SolanaConnectState.Linking,
                    modifier = Modifier.testTag(SOLANA_CONNECT_WALLET_APP_TAG)
                ) { buttonTextStyle ->
                    Text(stringResource(id = R.string.connect_wallet), style = buttonTextStyle)
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    stringResource(id = R.string.enter_address_manually),
                    modifier = Modifier
                        .clickable(enabled = !state.busy) { onEnterManually() }
                        .padding(vertical = 4.dp)
                        .testTag(SOLANA_ENTER_MANUALLY_TAG),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (state.busy) TextMuted else BlueMedium
                )
            }

            SolanaSheetStep.MANUAL -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onBack,
                        enabled = !state.busy
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = stringResource(id = R.string.back)
                        )
                    }
                    Text(
                        stringResource(id = R.string.connect_solana_wallet),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                URTextInput(
                    value = address,
                    onValueChange = onAddressChange,
                    label = stringResource(id = R.string.usdc_wallet_address),
                    placeholder = stringResource(id = R.string.enter_a_solana_usdc_wallet_address),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Done
                    ),
                    onDone = {
                        if (validation is AddressValidation.Ok && !state.busy) {
                            onContinue()
                        }
                    },
                    isValidating = validation is AddressValidation.Checking,
                    // the field colors its supporting text by validity, so Unavailable counts too
                    isValid = validation !is AddressValidation.InvalidSyntax && validation !is AddressValidation.Unavailable,
                    supportingText = when (validation) {
                        AddressValidation.InvalidSyntax -> stringResource(id = R.string.invalid_solana_address)
                        AddressValidation.Checking -> stringResource(id = R.string.checking_wallet_address)
                        is AddressValidation.Unavailable -> stringResource(id = R.string.something_went_wrong)
                        else -> null
                    }
                )

                URButton(
                    onClick = onContinue,
                    enabled = validation is AddressValidation.Ok && !state.busy,
                    isProcessing = state is SolanaConnectState.Linking,
                    modifier = Modifier.testTag(SOLANA_MANUAL_CONNECT_TAG)
                ) { buttonTextStyle ->
                    Text(stringResource(id = R.string.connect), style = buttonTextStyle)
                }
            }
        }

        if (state is SolanaConnectState.Failed) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                solanaFailureText(state),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClickLabel = stringResource(id = R.string.dismiss)) { onDismissState() }
                    .testTag(SOLANA_CONNECT_ERROR_TAG),
                style = MaterialTheme.typography.bodyMedium,
                color = Red
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun SheetPreviewFrame(
    step: SolanaSheetStep,
    state: SolanaConnectState,
    address: String,
    validation: AddressValidation,
) {
    URNetworkTheme {
        Column(
            modifier = Modifier
                .background(SheetBlack)
                .padding(top = 24.dp)
        ) {
            ConnectSolanaWalletSheetContent(
                step = step,
                state = state,
                address = TextFieldValue(address),
                onAddressChange = {},
                validation = validation,
                onConnectWalletApp = {},
                onEnterManually = {},
                onBack = {},
                onContinue = {},
                onDismissState = {},
            )
        }
    }
}

@Preview
@Composable
private fun ConnectSolanaWalletChooseIdlePreview() {
    SheetPreviewFrame(SolanaSheetStep.CHOOSE, SolanaConnectState.Idle, "", AddressValidation.Empty)
}

@Preview
@Composable
private fun ConnectSolanaWalletChooseFailedPreview() {
    SheetPreviewFrame(
        SolanaSheetStep.CHOOSE,
        SolanaConnectState.Failed("User did not authorize signing"),
        "",
        AddressValidation.Empty
    )
}

@Preview
@Composable
private fun ConnectSolanaWalletManualCheckingPreview() {
    SheetPreviewFrame(
        SolanaSheetStep.MANUAL,
        SolanaConnectState.Idle,
        SampleLegacyWalletSource.SAMPLE_SOLANA_ADDRESS,
        AddressValidation.Checking
    )
}

@Preview
@Composable
private fun ConnectSolanaWalletManualOkPreview() {
    SheetPreviewFrame(
        SolanaSheetStep.MANUAL,
        SolanaConnectState.Idle,
        SampleLegacyWalletSource.SAMPLE_SOLANA_ADDRESS,
        AddressValidation.Ok
    )
}
