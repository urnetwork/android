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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

typealias ResetPasswordFunction = (
    userAuth: String,
    onSuccess: () -> Unit,
    onError: () -> Unit,
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

        val api = deviceManager.device?.api
        if (api != null) {
            api.authPasswordReset(args) { _, err ->
                viewModelScope.launch {

                    if (err != null) {
                        Log.i(TAG, "authPasswordReset error: ${err.message}")
                        onErr()
                    } else {
                        onSuccess()
                    }

                    isSendingResetPassLink = false
                }
            }
        } else {
            isSendingResetPassLink = false
            onErr()
        }
    }

}