package com.bringyour.network.ui.login

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bringyour.sdk.Sdk
import com.bringyour.sdk.NetworkCreateArgs
import com.bringyour.sdk.NetworkNameValidationViewController
import com.bringyour.network.NetworkSpaceManagerProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginCreateNetworkViewModel @Inject constructor(
    networkSpaceManagerProvider: NetworkSpaceManagerProvider
): ViewModel() {

    private var networkNameValidationVc: NetworkNameValidationViewController? = null

    var emailOrPhone by mutableStateOf(TextFieldValue(""))
        private set

    val setEmailOrPhone: (TextFieldValue) -> Unit = { tfv ->
        emailOrPhone = tfv
    }


    var networkNameIsValid by mutableStateOf(false)
        private set

    var networkNameErrorExists by mutableStateOf(false)
        private set

    val setNetworkNameErrorExists: (Boolean) -> Unit = { errExists ->
        networkNameErrorExists = errExists
    }

    var isValidatingNetworkName by mutableStateOf(false)
        private set

    val setIsValidatingNetworkName: (Boolean) -> Unit = { iv ->
        isValidatingNetworkName = iv
    }

    var networkName by mutableStateOf(TextFieldValue(""))
        private set

    val setNetworkName: (TextFieldValue) -> Unit = { tfv ->
        networkName = tfv
    }

    var password by mutableStateOf(TextFieldValue(""))
        private set

    val setPassword: (TextFieldValue) -> Unit = { tfv ->
        password = tfv
    }

    var termsAgreed by mutableStateOf(false)
        private set

    val setTermsAgreed:(Boolean) -> Unit = { ta ->
        termsAgreed = ta
    }

    var networkNameSupportingText by mutableStateOf("")
        private set

    val setNetworkNameSupportingText: (String) -> Unit = { msg ->
        networkNameSupportingText = msg
    }

    val validateNetworkName: (String) -> Unit = { nn ->

        if (nn.isNotEmpty()) {

            if (nn.length < 6) {
                networkNameIsValid = false
                setNetworkNameErrorExists(false)
            } else {
                setIsValidatingNetworkName(true)

                networkNameValidationVc?.networkCheck(nn) { result, err ->
                    viewModelScope.launch {

                        if (err == null) {
                            if (result.available) {
                                Log.i("LoginCreateNetworkViewModel", "$nn is available")
                                networkNameIsValid = true
                                setNetworkNameErrorExists(false)
                            } else {
                                Log.i("LoginCreateNetworkViewModel", "$nn is unavailable")
                                networkNameIsValid = false
                                setNetworkNameErrorExists(true)
                            }
                        } else {
                            Log.i("LoginCreateNetworkViewModel", "$nn an error occurred")
                            networkNameIsValid = false
                            setNetworkNameErrorExists(true)
                        }

                        setIsValidatingNetworkName(false)
                    }
                }
            }
        } else {
            networkNameIsValid = false
            setNetworkNameErrorExists(false)
        }
    }

    val createNetworkArgs: (LoginCreateNetworkParams) -> NetworkCreateArgs = { params ->
        val args = NetworkCreateArgs()

        args.userName = ""
        args.networkName = networkName.text.trim()
        args.terms = termsAgreed

        if (params.referralCode != null) {
            args.referralCode = Sdk.parseId(params.referralCode)
        }

        when(params) {
            is LoginCreateNetworkParams.LoginCreateUserAuthParams -> {
                args.userAuth = emailOrPhone.text.trim()
                args.password = password.text
            }
            is LoginCreateNetworkParams.LoginCreateAuthJwtParams -> {
                args.authJwt = params.authJwt
                args.authJwtType = params.authJwtType
            }
        }

        args
    }

    init {
        networkNameValidationVc = Sdk.newNetworkNameValidationViewController(
            networkSpaceManagerProvider.getNetworkSpace()?.api
        )
    }

}