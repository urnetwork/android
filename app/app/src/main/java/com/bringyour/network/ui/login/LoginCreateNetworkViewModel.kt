package com.bringyour.network.ui.login

import android.net.Uri
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
import com.bringyour.network.TAG
import com.bringyour.sdk.Api
import com.bringyour.sdk.ValidateReferralCodeArgs
import com.bringyour.sdk.WalletAuthArgs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginCreateNetworkViewModel @Inject constructor(
    networkSpaceManagerProvider: NetworkSpaceManagerProvider,
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

    var presentBonusSheet by mutableStateOf(false)
        private set

    val setPresentBonusSheet: (Boolean) -> Unit = { pb ->
        presentBonusSheet = pb
    }

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

    private val _referralCode = mutableStateOf(TextFieldValue(""))
    val referralCode: TextFieldValue get() = _referralCode.value
    val setReferralCode: (TextFieldValue) -> Unit = { _referralCode.value = it }

    var isValidReferralCode by mutableStateOf(false)
        private set

    var isValidatingReferralCode by mutableStateOf(false)
        private set

    // this is used so we don't display an error state when the form is initialized with a blank value
    var referralValidationComplete by mutableStateOf(false)
        private set

    val validateReferralCode: (Api?, (Boolean) -> Unit) -> Unit = { api, onComplete ->

        if (!isValidatingReferralCode) {
            isValidatingReferralCode = true
            referralValidationComplete = false

            val args = ValidateReferralCodeArgs()


            try {
                args.referralCode = _referralCode.value.text

                api?.validateReferralCode(args) { result, err ->
                    viewModelScope.launch {

                        if (err != null) {
                            Log.i(TAG, "validateReferralCode callback err: ${err.message}")
                            isValidReferralCode = false
                        }

                        isValidReferralCode = result.isValid
                        isValidatingReferralCode = false
                        referralValidationComplete = true
                        onComplete(isValidReferralCode)
                    }
                }
            } catch (e: Exception) {
                Log.i(TAG, "${e.message}")
                isValidReferralCode = false
                isValidatingReferralCode = false
                referralValidationComplete = true
            }

        }

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

        if (!_referralCode.value.text.isEmpty()) {
            try {
                args.referralCode = Sdk.parseId(_referralCode.value.text)
            } catch (e: Exception) {
                Log.i(TAG, "error parsing referral code: ${e.message}")
            }
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
            is LoginCreateNetworkParams.LoginCreateSolanaParams -> {
                val walletAuth = WalletAuthArgs()
                walletAuth.publicKey = Uri.decode(params.publicKey)
                walletAuth.signature = Uri.decode(params.signature)
                walletAuth.message = Uri.decode(params.signedMessage)
                walletAuth.blockchain = "solana"
                args.walletAuth = walletAuth
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