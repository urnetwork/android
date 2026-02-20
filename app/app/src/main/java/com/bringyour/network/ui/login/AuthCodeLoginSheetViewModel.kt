package com.bringyour.network.ui.login

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bringyour.network.TAG
import com.bringyour.sdk.Api
import com.bringyour.sdk.AuthCodeLoginArgs
import com.bringyour.sdk.AuthCodeLoginResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

typealias AuthCodeLoginFunction = (
    api: Api?,
    onError: () -> Unit,
    onSuccess: (AuthCodeLoginResult) -> Unit
) -> Unit

@HiltViewModel
class AuthCodeLoginSheetViewModel @Inject constructor(): ViewModel() {

    var authCode by mutableStateOf(TextFieldValue())
        private set

    val setAuthCode: (TextFieldValue) -> Unit = {
        authCode = it
    }

    var isLoading by mutableStateOf(false)
        private set

    fun clearAuthCode() {
        authCode = TextFieldValue()
    }

    val authCodeLogin: AuthCodeLoginFunction = { api, onErr, onSuccess ->

        if (!isLoading) {

            isLoading = true

            val authCodeLoginArgs = AuthCodeLoginArgs()
            authCodeLoginArgs.authCode = authCode.text

            api?.authCodeLogin(authCodeLoginArgs) { result, error ->

                viewModelScope.launch {

                    if (error != null) {
                        Log.i(TAG, "authCodeLogin err: ${error.message}")
                        onErr()
                        isLoading = false
                        return@launch
                    }

                    if (result.error != null) {
                        Log.i(TAG, "authCodeLogin result.err: ${result.error.message}")
                        onErr()
                        isLoading = false
                        return@launch
                    }

                    onSuccess(result)
                    isLoading = false

                }
            } ?: run {
                Log.i(TAG, "authCodeLogin api not found")
                isLoading = false
                onErr()
            }
        }

    }

}