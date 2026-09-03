package com.bringyour.network.ui.wallet

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bringyour.network.R
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.components.URLearnMoreText
import com.bringyour.network.ui.components.URTextInput
import com.bringyour.network.ui.theme.Amber
import com.bringyour.network.ui.theme.Red
import com.bringyour.network.ui.theme.SheetBlack
import com.bringyour.network.ui.theme.TextMuted

/**
 * Manual coldkey entry. The address is validated before anything else happens
 * (syntax locally, then the unauthenticated server check); a blocked address never
 * leaves the device. Continuing still signs through the ur.io bridge so the server
 * can verify ownership.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectWalletSheet(
    address: TextFieldValue,
    onAddressChange: (TextFieldValue) -> Unit,
    validation: AddressValidation,
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
                stringResource(id = R.string.connect_bittensor_wallet),
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(modifier = Modifier.height(8.dp))

            // plain text, then a "Learn more" link to the protocol site
            URLearnMoreText(
                text = stringResource(id = R.string.wallet_not_retroactive),
                linkText = stringResource(id = R.string.learn_more),
                url = UR_XYZ_URL,
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            URTextInput(
                value = address,
                onValueChange = onAddressChange,
                label = stringResource(id = R.string.bittensor_wallet),
                placeholder = "",
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Done
                ),
                onDone = {
                    if (validation.canContinue) {
                        onContinue()
                    }
                },
                isValidating = validation is AddressValidation.Checking,
                isValid = validation !is AddressValidation.InvalidSyntax && validation !is AddressValidation.Blocked,
                supportingText = when (validation) {
                    AddressValidation.InvalidSyntax -> stringResource(id = R.string.invalid_ss58_address)
                    AddressValidation.Checking -> stringResource(id = R.string.checking_wallet_address)
                    is AddressValidation.Blocked -> stringResource(id = R.string.wallet_blocked)
                    else -> null
                }
            )

            when (validation) {
                is AddressValidation.LooksNew -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        stringResource(id = R.string.wallet_looks_new_warning),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Amber
                    )
                }
                is AddressValidation.Unavailable -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        stringResource(id = R.string.chain_rpc_unreachable),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Red
                    )
                }
                else -> {}
            }

            Spacer(modifier = Modifier.height(24.dp))

            URButton(
                onClick = onContinue,
                enabled = validation.canContinue
            ) { buttonTextStyle ->
                Text(stringResource(id = R.string.connect), style = buttonTextStyle)
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
