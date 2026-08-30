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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bringyour.network.R
import com.bringyour.network.ui.components.URTextInput
import com.bringyour.network.ui.components.referral.RoyalWelcomeContent
import com.bringyour.network.ui.theme.ppNeueBitBold
import kotlinx.coroutines.delay

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

    val haptic = LocalHapticFeedback.current

    // linking a referral network is the royal-welcome moment: show the gold
    // frog for a beat before handing back to the settings screen
    var showRoyalWelcome by remember { mutableStateOf(false) }

    LaunchedEffect(showRoyalWelcome) {
        if (showRoyalWelcome) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            delay(2000)
            onSuccess()
        }
    }

    val updateReferralNetwork = {
        viewModel.updateReferralNetwork(
            {
                // success
                viewModel.setReferralCode(TextFieldValue(""))
                showRoyalWelcome = true
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
        if (showRoyalWelcome) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .padding(bottom = 32.dp)
            ) {
                RoyalWelcomeContent()
            }
            return@ModalBottomSheet
        }

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