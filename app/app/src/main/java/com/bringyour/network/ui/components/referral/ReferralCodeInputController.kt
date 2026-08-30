package com.bringyour.network.ui.components.referral

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import com.bringyour.network.R
import com.bringyour.network.TAG
import com.bringyour.sdk.Api
import com.bringyour.sdk.ValidateReferralCodeArgs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Referral code entry state and validation, shared by the signup flows that
 * accept a referral code (the same behavior LoginCreateNetworkViewModel
 * implements inline).
 */
class ReferralCodeInputController(
    private val scope: CoroutineScope,
) {

    var code by mutableStateOf(TextFieldValue(""))
        private set

    val setCode: (TextFieldValue) -> Unit = { code = it }

    var isValid by mutableStateOf(false)
        private set

    var isValidating by mutableStateOf(false)
        private set

    // false until the first validation finishes, so a blank form shows no error
    var validationComplete by mutableStateOf(false)
        private set

    var isCapped by mutableStateOf(false)
        private set

    var supportingTextRes by mutableStateOf<Int?>(null)
        private set

    // a code the create call should carry
    val applied: Boolean get() = validationComplete && isValid && !isCapped

    fun validate(api: Api?, onComplete: (Boolean) -> Unit) {

        if (isValidating) {
            return
        }

        if (api == null) {
            isValid = false
            validationComplete = true
            updateSupportingText()
            onComplete(false)
            return
        }

        isValidating = true
        validationComplete = false

        val args = ValidateReferralCodeArgs()

        try {
            args.referralCode = code.text

            api.validateReferralCode(args) { result, err ->
                scope.launch {

                    if (err != null) {
                        Log.i(TAG, "validateReferralCode callback err: ${err.message}")
                        isValid = false
                    } else {
                        isValid = result?.isValid ?: false
                    }

                    isValidating = false
                    validationComplete = true
                    isCapped = result?.isCapped ?: false

                    updateSupportingText()

                    onComplete(isValid && !isCapped)
                }
            }
        } catch (e: Exception) {
            Log.i(TAG, "${e.message}")
            isValid = false
            isValidating = false
            validationComplete = true
            updateSupportingText()
            onComplete(false)
        }
    }

    private fun updateSupportingText() {

        var msgRes: Int? = null

        if (validationComplete) {

            if (!isValid) {
                msgRes = R.string.invalid_referral_code
            }

            if (isCapped) {
                msgRes = R.string.referral_code_capped
            }
        }

        supportingTextRes = msgRes
    }
}
