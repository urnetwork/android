package com.bringyour.network.ui.shared.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bringyour.sdk.AuthPasswordResetArgs
import com.bringyour.network.ByDeviceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ResetPasswordViewModel @Inject constructor(
    private val byDeviceManager: ByDeviceManager,
): ViewModel() {

    var isSendingResetPassLink by mutableStateOf(false)
        private set

    var passwordResetError by mutableStateOf<String?>(null)
        private set

    var markPasswordResetAsSent by mutableStateOf(false)
        private set

    val sendResetLink: (String) -> Unit = { userAuth ->

        val args = AuthPasswordResetArgs()
        args.userAuth = userAuth.trim()

        val byDevice = byDeviceManager.byDevice
        byDevice?.api?.authPasswordReset(args) { _, err ->
            viewModelScope.launch {
                isSendingResetPassLink = false

                if (err != null) {
                    setPasswordResetError(err.message)
                } else {
                    setPasswordResetError(null)
                    markPasswordResetAsSent = true
                }
            }
        }
    }

    val setPasswordResetError: (String?) -> Unit = {
        passwordResetError = it
    }

    val setMarkPasswordResetAsSent: (Boolean) -> Unit = {
        markPasswordResetAsSent = it
    }

}