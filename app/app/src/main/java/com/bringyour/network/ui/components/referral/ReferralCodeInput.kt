package com.bringyour.network.ui.components.referral

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import com.bringyour.network.R
import com.bringyour.network.ui.components.URTextInput

/**
 * The optional referral code field above Continue (support inbox 1698), as
 * the Windows sign-up shows it: always visible, labeled optional. Typing
 * checks the code after a pause (ReferralCodeInputController); Done checks it
 * at once.
 */
@Composable
fun ReferralCodeInput(
    referralCode: TextFieldValue,
    setReferralCode: (TextFieldValue) -> Unit,
    isValidating: Boolean,
    isRejected: Boolean,
    supportingTextRes: Int?,
    onDone: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    URTextInput(
        value = referralCode,
        onValueChange = setReferralCode,
        onDone = onDone,
        placeholder = stringResource(id = R.string.enter_a_bonus_referral_code),
        label = stringResource(id = R.string.referral_code_optional),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Characters,
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Ascii,
            imeAction = ImeAction.Done,
        ),
        isValidating = isValidating,
        isValid = !isRejected,
        supportingText = supportingTextRes?.let { stringResource(id = it) },
        enabled = enabled,
        modifier = modifier,
    )
}
