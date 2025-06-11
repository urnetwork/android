package com.bringyour.network.ui.settings.updateReferralNetworkBottomSheet

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bringyour.network.R
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.components.URTextInput
import com.bringyour.network.ui.theme.ppNeueBitBold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateReferralNetworkBottomSheet(
    sheetState: SheetState,
    setIsPresenting: (Boolean) -> Unit,
    onSuccess: () -> Unit,
    onError: (String) -> Unit,
    viewModel: UpdateReferralNetworkBottomSheetViewModel = hiltViewModel()
) {

    val updateReferralNetwork = {
        viewModel.updateReferralNetwork(
            {
                // success
                onSuccess()
                viewModel.setReferralCode(TextFieldValue(""))
            },
            { errorMessage ->
                onError(errorMessage)
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = { setIsPresenting(false) },
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
        ) {
            Text(
                stringResource(id = R.string.update_referral_network),
                style = TextStyle(
                    fontFamily = ppNeueBitBold,
                    fontSize = 24.sp
                )
            )
            Spacer(modifier = Modifier.height(12.dp))

            URTextInput(
                value = viewModel.referralCode,
                onValueChange = {
                    viewModel.setReferralCode(it)
                },
                label = stringResource(id = R.string.enter_network_referral_code),
                supportingText = viewModel.codeInputSupportingText,
                onSend = {
                    updateReferralNetwork()
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            URButton(
                onClick = {
                    updateReferralNetwork()
                },
                enabled = !viewModel.isUpdatingReferralNetwork.collectAsState().value && viewModel.referralCode.text.length == 6
            ) { buttonTextStyle ->
                Text(
                    stringResource(id = R.string.update),
                    style = buttonTextStyle
                )
            }

        }
    }
}