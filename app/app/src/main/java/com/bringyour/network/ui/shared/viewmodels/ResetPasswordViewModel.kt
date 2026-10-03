package com.bringyour.network.ui.shared.viewmodels

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bringyour.sdk.AuthPasswordResetArgs
import com.bringyour.network.DeviceManager
import com.bringyour.network.TAG
import com.bringyour.network.ui.login.VerifySendError
import com.bringyour.network.ui.login.VerifySendNotice
import com.bringyour.network.ui.login.passwordResetNotice
import com.bringyour.network.ui.login.toVerifySendError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

typealias ResetPasswordFunction = (
    userAuth: String,
    onSuccess: () -> Unit,
    // the server's send error, or null when the request failed
    onError: (VerifySendError?) -> Unit,
) -> Unit

@HiltViewModel
class ResetPasswordViewModel @Inject constructor(
    private val deviceManager: DeviceManager,
): ViewModel() {

    var isSendingResetPassLink by mutableStateOf(false)
        private set

    val sendResetLink: ResetPasswordFunction = { userAuth, onSuccess, onErr ->

        isSendingResetPassLink = true

        val args = AuthPasswordResetArgs()
        args.userAuth = userAuth.trim()
        // a rate limit or failed send comes back in `result.error`, with the retry time
        args.resultErrors = true

        val api = deviceManager.device?.api
        if (api != null) {
            api.authPasswordReset(args) { result, err ->
                viewModelScope.launch {

                    val error = result?.error?.toVerifySendError()
                    if (err != null || result == null) {
                        Log.i(TAG, "authPasswordReset error: ${err?.message}")
                        onErr(null)
                    } else if (passwordResetNotice(false, error) != VerifySendNotice.Sent) {
                        Log.i(TAG, "authPasswordReset not sent: ${error?.code}")
                        onErr(error)
                    } else {
                        onSuccess()
                    }

                    isSendingResetPassLink = false
                }
            }
        } else {
            isSendingResetPassLink = false
            onErr(null)
        }
    }

}