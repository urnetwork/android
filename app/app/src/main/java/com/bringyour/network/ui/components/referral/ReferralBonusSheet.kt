package com.bringyour.network.ui.components.referral

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.bringyour.network.R
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.components.URTextInput
import kotlinx.coroutines.delay

/**
 * Bottom sheet for entering a referral code during signup. When the code
 * validates, the sheet turns into the gold royal-welcome moment for a beat
 * before dismissing itself -- the referred user should feel the crown.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReferralBonusSheet(
    presented: Boolean,
    onDismiss: () -> Unit,
    referralCode: TextFieldValue,
    setReferralCode: (TextFieldValue) -> Unit,
    isValidating: Boolean,
    isValid: Boolean,
    isCapped: Boolean,
    validationComplete: Boolean,
    supportingTextRes: Int?,
    validate: ((Boolean) -> Unit) -> Unit,
) {

    if (!presented) {
        return
    }

    val sheetState = rememberModalBottomSheetState()
    val haptic = LocalHapticFeedback.current

    // flips to true the moment the code is accepted; shows the royal welcome
    // briefly, then dismisses the sheet
    var showRoyalWelcome by remember { mutableStateOf(false) }

    LaunchedEffect(showRoyalWelcome) {
        if (showRoyalWelcome) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            delay(2000)
            onDismiss()
        }
    }

    ModalBottomSheet(
        modifier = Modifier.wrapContentHeight(),
        sheetState = sheetState,
        onDismissRequest = { onDismiss() },
        dragHandle = {}
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .padding(bottom = 32.dp)
        ) {

            if (showRoyalWelcome) {

                Spacer(modifier = Modifier.height(16.dp))

                RoyalWelcomeContent()

                Spacer(modifier = Modifier.height(16.dp))

            } else {

                Row(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        stringResource(id = R.string.add_referral_extra_rewards),
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                URTextInput(
                    label = stringResource(id = R.string.referral_code),
                    value = referralCode,
                    onValueChange = setReferralCode,
                    supportingText = if (supportingTextRes != null) stringResource(id = supportingTextRes) else "",
                    isValidating = isValidating,
                    isValid = (!validationComplete || (isValid && !isCapped)),
                    enabled = !isValidating
                )

                Spacer(modifier = Modifier.height(16.dp))

                URButton(
                    onClick = {
                        validate { valid ->
                            if (valid) {
                                showRoyalWelcome = true
                            }
                        }
                    },
                    enabled = !isValidating && referralCode.text.isNotEmpty(),
                    isProcessing = isValidating
                ) { buttonTextStyle ->
                    Text(
                        stringResource(id = R.string.apply_bonus),
                        style = buttonTextStyle
                    )
                }
            }
        }
    }
}
