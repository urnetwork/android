package com.bringyour.network.ui.login

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bringyour.client.Client
import com.bringyour.client.NetworkCreateArgs
import com.bringyour.client.NetworkNameValidationViewController
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

    var username by mutableStateOf(TextFieldValue(""))
        private set

    val setUsername: (TextFieldValue) -> Unit = { tfv ->
        username = tfv
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

    val validateNetworkName: (String) -> Unit = { nn ->

        setIsValidatingNetworkName(true)

        networkNameValidationVc?.networkCheck(nn) { result, err ->
            viewModelScope.launch {

                if (err == null) {
                    if (result.available) {
                        networkNameIsValid = true
                        setNetworkNameErrorExists(false)
                    } else {
                        networkNameIsValid = false
                        setNetworkNameErrorExists(true)
                    }
                } else {
                    networkNameIsValid = false
                    setNetworkNameErrorExists(true)
                }

                setIsValidatingNetworkName(false)
            }
        }
    }

    val createNetworkArgs: (LoginCreateNetworkParams) -> NetworkCreateArgs = { params ->
        val args = NetworkCreateArgs()

        args.userName = username.text.trim()
        args.networkName = networkName.text.trim()
        args.terms = termsAgreed

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
        networkNameValidationVc = Client.newNetworkNameValidationViewController(
            networkSpaceManagerProvider.getNetworkSpace()?.api
        )
    }

}