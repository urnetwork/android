package com.bringyour.network.ui.login

import android.content.Context
import android.util.Patterns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bringyour.client.AuthLoginArgs
import com.bringyour.client.AuthLoginResult
import com.bringyour.client.BringYourApi
import com.bringyour.network.NetworkSpaceManagerProvider
import com.bringyour.network.R
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    networkSpaceManagerProvider: NetworkSpaceManagerProvider
): ViewModel() {

    var userAuthInProgress by mutableStateOf(false)
        private set

    var googleAuthInProgress by mutableStateOf(false)
        private set

    var isValidUserAuth by mutableStateOf(false)
        private set

    var userAuth by mutableStateOf(TextFieldValue(""))

    var loginError by mutableStateOf<String?>(null)
        private set

    var createGuestModeInProgress by mutableStateOf(false)
        private set

    val setCreateGuestModeInProgress: (Boolean) -> Unit = { inProgress ->
        createGuestModeInProgress = inProgress
    }

    var guestModeLoginSuccess by mutableStateOf(false)
        private set

    val setGuestModeLoginSuccess: (Boolean) -> Unit = { success ->
        guestModeLoginSuccess = success
    }

    var guestModeOverlayBodyVisible by mutableStateOf(true)
        private set

    val setGuestModeOverlayBodyVisible: (Boolean) -> Unit = { visible ->
        guestModeOverlayBodyVisible = visible
    }

    val login: (
        ctx: Context,
        api: BringYourApi?,
        onLogin: (AuthLoginResult) -> Unit,
        onNewNetwork: (AuthLoginResult) -> Unit,
    ) -> Unit = { ctx, api, onLogin, onNewNetwork ->

        when {
            !isValidUserAuth -> {}
            else -> {
                userAuthInProgress = true

                val args = AuthLoginArgs()
                args.userAuth = userAuth.text.trim()

                api?.authLogin(args) { result, err ->
                    viewModelScope.launch {

                        if (err != null) {
                            setLoginError(err.message)
                        } else if (result.error != null) {
                            setLoginError(result.error.message)
                        } else if (result.authAllowed != null) {

                            if (result.authAllowed.contains("password")) {
                                // to the login password screen
                                setLoginError(null)

                                onLogin(result)
                            } else {
                                val authAllowed = mutableListOf<String>()
                                for (i in 0 until result.authAllowed.len()) {
                                    authAllowed.add(result.authAllowed.get(i))
                                }

                                setLoginError(ctx.getString(R.string.login_error_auth_allowed, authAllowed.joinToString(",")))
                            }
                        } else {
                            onNewNetwork(result)
                        }

                        // add a delay so when navigating to the next screen,
                        // the "get started" button doesn't flash enabled again
                        delay(200)
                        userAuthInProgress = false
                    }
                }
            }
        }
    }

    val allowGoogleSso = {
        networkSpaceManagerProvider.getNetworkSpace()?.ssoGoogle ?: false
    }

    val googleLogin: (
        context: Context,
        api: BringYourApi?,
        account: GoogleSignInAccount,
        onLogin: (AuthLoginResult) -> Unit,
        onCreateNetwork: (email: String?, authJwt: String?, userName: String) -> Unit,
    ) -> Unit = { ctx, api, account, onLogin, onCreateNetwork ->

        setGoogleAuthInProgress(true)

        val args = AuthLoginArgs()
        args.authJwt = account.idToken
        args.authJwtType = "google"

        api?.authLogin(args) { result, err ->
            viewModelScope.launch {
                // googleAuthInProgress = false

                if (err != null) {
                    setLoginError(err.message)
                    setGoogleAuthInProgress(false)
                } else if (result.error != null) {
                    setLoginError(result.error.message)
                    setGoogleAuthInProgress(false)
                } else if (result.network != null && result.network.byJwt.isNotEmpty()) {
                    setLoginError(null)

                    onLogin(result)
                    // googleAuthInProgress = true

                } else if (result.authAllowed != null) {
                    val authAllowed = mutableListOf<String>()
                    for (i in 0 until result.authAllowed.len()) {
                        authAllowed.add(result.authAllowed.get(i))
                    }

                    setLoginError(ctx.getString(R.string.login_error_auth_allowed, authAllowed.joinToString(",")))
                    setGoogleAuthInProgress(false)
                } else {
                    setLoginError(null)

                    val authJwt = account.idToken
                    val userName = result.userName

                    onCreateNetwork(
                        account.email,
                        authJwt,
                        userName
                    )
                }
            }
        }
    }

    val setLoginError: (String?) -> Unit = { msg ->
        loginError = msg
    }

    val setGoogleAuthInProgress: (Boolean) -> Unit = { inProgress ->
        googleAuthInProgress = inProgress
    }

    val setUserAuth: (TextFieldValue) -> Unit = { newValue ->

        val filteredText = newValue.text.filter { it != ' ' }
        val filteredTextFieldValue = newValue.copy(text = filteredText)

        userAuth = filteredTextFieldValue

        isValidUserAuth = userAuth.text.isNotEmpty() &&
                (Patterns.EMAIL_ADDRESS.matcher(userAuth.text).matches() ||
                        Patterns.PHONE.matcher(userAuth.text).matches())
    }

}