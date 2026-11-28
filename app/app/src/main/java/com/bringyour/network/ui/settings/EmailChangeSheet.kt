package com.bringyour.network.ui.settings

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.bringyour.network.MainApplication
import com.bringyour.network.R
import com.bringyour.network.ui.components.ButtonStyle
import com.bringyour.network.ui.components.FoldSafeModalBottomSheet
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.components.URInlineErrorText
import com.bringyour.network.ui.components.URTextInput
import com.bringyour.network.ui.components.URTextInputLabel
import com.bringyour.network.ui.components.rememberFoldSafeSheetState
import com.bringyour.network.ui.theme.Green
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.sdk.AuthEmailChangeArgs
import com.bringyour.sdk.AuthEmailChangeConfirmArgs
import com.bringyour.sdk.Sdk
import kotlinx.coroutines.launch

/*
 * The account's verified email change (Settings › Sign-in methods › Change
 * email), offered while the account has an email sign-in
 * (NetworkUser.EmailSignIn). The server sends a code to the current email and
 * another to the new one, and the email sign-in changes only once both are
 * confirmed; the password stays the same. The sdk checks the new address
 * (ValidateEmailChangeAddress), sends the routes with the network credential
 * and normalizes the codes; the sheet only words the server's refusals
 * (emailChangeRefusalRes). Closing the sheet while its codes wait cancels the
 * change (fire and forget), so those codes stop working.
 */

/** The email change sheet's steps. */
enum class EmailChangeStep {
    // the new address
    ENTER_EMAIL,
    // the code sent to each address
    ENTER_CODES,
    // the email sign-in is the new address
    DONE,
}

/**
 * The store key for an email change refusal (AuthEmailChangeError.Code), null
 * for a code this build does not know, which shows the server's message.
 */
@StringRes
fun emailChangeRefusalRes(code: String): Int? = when (code) {
    Sdk.AuthEmailChangeErrorCodeOwnerOnly -> R.string.email_change_sign_in_required
    Sdk.AuthEmailChangeErrorCodeNoEmail -> R.string.email_change_no_email
    Sdk.AuthEmailChangeErrorCodeEmailUnverified -> R.string.email_change_email_unverified
    Sdk.AuthEmailChangeErrorCodeInvalidEmail -> R.string.email_change_invalid_email
    Sdk.AuthEmailChangeErrorCodeSameEmail -> R.string.email_change_same_email
    Sdk.AuthEmailChangeErrorCodeEmailInUse -> R.string.email_change_email_in_use
    Sdk.AuthEmailChangeErrorCodeRateLimited -> R.string.account_too_many_tries
    Sdk.AuthEmailChangeErrorCodeSendFailed -> R.string.account_code_send_failed
    Sdk.AuthEmailChangeErrorCodeNoChange -> R.string.email_change_expired
    Sdk.AuthEmailChangeErrorCodeCodeInvalid -> R.string.account_code_invalid
    Sdk.AuthEmailChangeErrorCodeTooManyTries -> R.string.email_change_too_many_tries
    else -> null
}

// a refusal's words: the store's for a known code, else the server's message
private fun emailChangeRefusalText(context: Context, code: String, message: String): String =
    emailChangeRefusalRes(code)?.let { context.getString(it) }
        ?: message.ifBlank { context.getString(R.string.something_went_wrong) }

/**
 * The email change: the new address, then the two codes, then done.
 * `onChanged` runs once the change completed, to read the network user again
 * so the new email shows; the sheet stays open on its done step.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmailChangeSheet(
    onDismiss: () -> Unit,
    onChanged: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberFoldSafeSheetState()

    var step by remember { mutableStateOf(EmailChangeStep.ENTER_EMAIL) }
    // a request is out
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var newEmail by remember { mutableStateOf(TextFieldValue("")) }
    // where the codes went: the current email as the server masked it, and the new one
    var currentEmailSent by remember { mutableStateOf("") }
    var newEmailSent by remember { mutableStateOf("") }
    var currentCode by remember { mutableStateOf(TextFieldValue("")) }
    var newCode by remember { mutableStateOf(TextFieldValue("")) }
    // a side whose code the server confirmed is done for the rest of the request
    var needsCurrentCode by remember { mutableStateOf(true) }
    var needsNewCode by remember { mutableStateOf(true) }
    var changedEmail by remember { mutableStateOf("") }
    var othersSignedOut by remember { mutableStateOf(false) }

    val codesEntered = (needsCurrentCode || needsNewCode) &&
        (!needsCurrentCode || currentCode.text.isNotBlank()) &&
        (!needsNewCode || newCode.text.isNotBlank())

    val sendCodes: () -> Unit = sendCodes@{
        if (busy) {
            return@sendCodes
        }
        error = null
        val address = newEmail.text.trim()
        // the sdk's local check first; the server checks again, and whether the address is in use
        if (Sdk.validateEmailChangeAddress(address).isNotEmpty()) {
            error = context.getString(R.string.email_change_invalid_email)
            return@sendCodes
        }
        val api = (context.applicationContext as? MainApplication)?.api
        if (api == null) {
            error = context.getString(R.string.something_went_wrong)
            return@sendCodes
        }
        // the route needs the network credential, which the sdk sends; without
        // one it refuses before sending (ErrNetworkCredentialRequired)
        if (!api.hasNetworkCredential()) {
            error = context.getString(R.string.email_change_sign_in_required)
            return@sendCodes
        }
        busy = true
        val args = AuthEmailChangeArgs()
        args.newEmail = address
        api.authEmailChange(args) { result, err ->
            scope.launch {
                busy = false
                val refusal = result?.error
                if (err != null || result == null) {
                    error = context.getString(R.string.something_went_wrong)
                } else if (refusal != null) {
                    error = emailChangeRefusalText(context, refusal.code, refusal.message)
                } else {
                    currentEmailSent = result.currentEmail
                    newEmailSent = result.newEmail
                    currentCode = TextFieldValue("")
                    newCode = TextFieldValue("")
                    needsCurrentCode = true
                    needsNewCode = true
                    step = EmailChangeStep.ENTER_CODES
                }
            }
        }
    }

    val confirmCodes: () -> Unit = confirmCodes@{
        if (busy || !codesEntered) {
            return@confirmCodes
        }
        error = null
        val api = (context.applicationContext as? MainApplication)?.api
        if (api == null) {
            error = context.getString(R.string.something_went_wrong)
            return@confirmCodes
        }
        if (!api.hasNetworkCredential()) {
            error = context.getString(R.string.email_change_sign_in_required)
            return@confirmCodes
        }
        busy = true
        val args = AuthEmailChangeConfirmArgs()
        // a side already confirmed sends no code; the sdk normalizes the others
        args.currentCode = if (needsCurrentCode) currentCode.text else ""
        args.newCode = if (needsNewCode) newCode.text else ""
        api.authEmailChangeConfirm(args) { result, err ->
            scope.launch {
                busy = false
                if (err != null || result == null) {
                    error = context.getString(R.string.something_went_wrong)
                    return@launch
                }
                val refusal = result.error
                if (refusal != null && refusal.endsRequest()) {
                    // the request ended (expired, too many wrong codes, or the new
                    // address was taken meanwhile): start again from the new address
                    error = emailChangeRefusalText(context, refusal.code, refusal.message)
                    step = EmailChangeStep.ENTER_EMAIL
                    return@launch
                }
                if (result.complete) {
                    changedEmail = result.email
                    othersSignedOut = result.otherSessionsSignedOut
                    step = EmailChangeStep.DONE
                    onChanged()
                    return@launch
                }
                // a right code confirmed its side, which stays done; a wrong one keeps its field
                needsCurrentCode = needsCurrentCode && result.needsCurrentCode()
                needsNewCode = needsNewCode && result.needsNewCode()
                if (refusal != null) {
                    error = emailChangeRefusalText(context, refusal.code, refusal.message)
                }
            }
        }
    }

    // closing the sheet while its codes wait ends the change, so they stop working
    val close: () -> Unit = {
        if (step == EmailChangeStep.ENTER_CODES) {
            (context.applicationContext as? MainApplication)?.api?.authEmailChangeCancel { _, _ -> }
        }
        onDismiss()
    }

    FoldSafeModalBottomSheet(
        onDismissRequest = close,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                stringResource(id = R.string.email_change_title),
                style = MaterialTheme.typography.headlineSmall
            )

            Spacer(modifier = Modifier.height(4.dp))

            when (step) {
                EmailChangeStep.ENTER_EMAIL -> {
                    Text(
                        stringResource(id = R.string.email_change_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    URTextInput(
                        value = newEmail,
                        onValueChange = {
                            newEmail = it
                            error = null
                        },
                        label = stringResource(id = R.string.email_change_new_email),
                        placeholder = stringResource(id = R.string.your_email_com),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Send
                        ),
                        onSend = sendCodes,
                        enabled = !busy,
                    )
                    URInlineErrorText(error)
                    Spacer(modifier = Modifier.height(16.dp))
                    URButton(
                        onClick = sendCodes,
                        enabled = !busy && newEmail.text.isNotBlank(),
                        isProcessing = busy
                    ) { buttonTextStyle ->
                        Text(stringResource(id = R.string.email_change_send_codes), style = buttonTextStyle)
                    }
                }
                EmailChangeStep.ENTER_CODES -> {
                    Text(
                        stringResource(id = R.string.email_change_codes_sent, currentEmailSent, newEmailSent),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    EmailChangeCodeField(
                        address = currentEmailSent,
                        code = currentCode,
                        onCodeChange = {
                            currentCode = it
                            error = null
                        },
                        confirmed = !needsCurrentCode,
                        enabled = !busy,
                        imeAction = ImeAction.Next,
                    )
                    EmailChangeCodeField(
                        address = newEmailSent,
                        code = newCode,
                        onCodeChange = {
                            newCode = it
                            error = null
                        },
                        confirmed = !needsNewCode,
                        enabled = !busy,
                        imeAction = ImeAction.Send,
                        onSend = confirmCodes,
                    )
                    URInlineErrorText(error)
                    Spacer(modifier = Modifier.height(16.dp))
                    URButton(
                        onClick = confirmCodes,
                        enabled = !busy && codesEntered,
                        isProcessing = busy
                    ) { buttonTextStyle ->
                        Text(stringResource(id = R.string.email_change), style = buttonTextStyle)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    URButton(
                        onClick = close,
                        style = ButtonStyle.OUTLINE,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) { buttonTextStyle ->
                        Text(stringResource(id = R.string.cancel), style = buttonTextStyle)
                    }
                }
                EmailChangeStep.DONE -> {
                    Text(
                        stringResource(id = R.string.email_change_done, changedEmail),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        stringResource(
                            id = if (othersSignedOut) R.string.email_change_others_signed_out else R.string.email_change_others_hint
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    URButton(onClick = onDismiss) { buttonTextStyle ->
                        Text(stringResource(id = R.string.done), style = buttonTextStyle)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * One address's code: its field, numeric, or once the server confirmed it,
 * the code checked as done.
 */
@Composable
private fun EmailChangeCodeField(
    address: String,
    code: TextFieldValue,
    onCodeChange: (TextFieldValue) -> Unit,
    confirmed: Boolean,
    enabled: Boolean,
    imeAction: ImeAction,
    onSend: () -> Unit = {},
) {
    val label = stringResource(id = R.string.email_change_code_sent_to, address)
    if (confirmed) {
        URTextInputLabel(text = label)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = Green,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                code.text,
                style = MaterialTheme.typography.bodyLarge,
                color = TextMuted
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
    } else {
        URTextInput(
            value = code,
            onValueChange = onCodeChange,
            label = label,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = imeAction
            ),
            onSend = onSend,
            enabled = enabled,
        )
    }
}
