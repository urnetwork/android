package com.bringyour.network.ui.settings.updateReferralNetworkBottomSheet

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
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
    referralNetworkName: String?,
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

    if (viewModel.displayUnlinkAlert) {
        AlertDialog(
            icon = {
                Icon(Icons.Filled.LinkOff, contentDescription = stringResource(id = R.string.unlink_referral_network))
            },
            title = {
                Text(text = stringResource(id = R.string.unlink_referral_network))
            },
            text = {
                Text(
                    text = stringResource(R.string.unlink_alert_description, referralNetworkName ?: "that network")
                )
            },
            onDismissRequest = {
                // onDismissRequest()
                viewModel.setDisplayUnlinkAlert(false)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.unlinkReferralNetwork(
                            onSuccess = {
                                viewModel.setDisplayUnlinkAlert(false)
                                onSuccess()
                            },
                            onError = {}
                        )
                    }
                ) {
                    Text(stringResource(id = R.string.unlink))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.setDisplayUnlinkAlert(false)
                    }
                ) {
                    Text(stringResource(id = R.string.cancel))
                }
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

            Row(
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier.weight(1f)
                ) {
                    URTextInput(
                        value = viewModel.referralCode,
                        onValueChange = { viewModel.setReferralCode(it) },
                        label = stringResource(id = R.string.enter_network_referral_code),
                        supportingText = viewModel.codeInputSupportingText,
                        onSend = { updateReferralNetwork() }
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = { updateReferralNetwork() },
                    enabled = !viewModel.isUpdatingReferralNetwork.collectAsState().value && viewModel.referralCode.text.length == 6
                ) {
                    Text(stringResource(id = R.string.update))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (referralNetworkName != null) {
                Text(
                    stringResource(id = R.string.unlink_referral_network),
                    style = TextStyle(
                        fontFamily = ppNeueBitBold,
                        fontSize = 24.sp
                    )
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier.weight(1f)
                    ) {
                        URTextInput(
                            value = TextFieldValue(referralNetworkName),
                            onValueChange = {},
                            enabled = false,
                            label = stringResource(id = R.string.current_network_referral)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            viewModel.setDisplayUnlinkAlert(true)
                        },
                    ) {
                        Text(stringResource(id = R.string.unlink))
                    }

                }

            }
        }
    }
}