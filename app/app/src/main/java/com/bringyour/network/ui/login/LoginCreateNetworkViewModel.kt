package com.bringyour.network.ui.login

import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bringyour.network.NetworkSpaceManagerProvider
import com.bringyour.network.TAG
import com.bringyour.network.ui.components.referral.ReferralCodeInputController
import com.bringyour.network.ui.components.referral.apiReferralCodeChecker
import com.bringyour.sdk.NetworkCreateArgs
import com.bringyour.sdk.NetworkNameValidationViewController
import com.bringyour.sdk.Sdk
import com.bringyour.sdk.WalletAuthArgs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginCreateNetworkViewModel @Inject constructor(
    private val networkSpaceManagerProvider: NetworkSpaceManagerProvider,
): ViewModel() {

    private var networkNameValidationVc: NetworkNameValidationViewController? = null

    var emailOrPhone by mutableStateOf(TextFieldValue(""))
        private set

    val setEmailOrPhone: (TextFieldValue) -> Unit = { tfv ->
        emailOrPhone = tfv
    }

    var networkNameCheckState by mutableStateOf(NetworkNameCheckState.EMPTY)
        private set

    /** the name may be submitted: available, or the check failed and network create re-checks it */
    val networkNameIsValid: Boolean
        get() = networkNameCheckState.allowsCreate

    /** the check answered that the name is taken */
    val networkNameErrorExists: Boolean
        get() = networkNameCheckState == NetworkNameCheckState.UNAVAILABLE

    /** the check errored or never answered; the name was not judged */
    val networkNameCheckFailed: Boolean
        get() = networkNameCheckState == NetworkNameCheckState.FAILED

    val isValidatingNetworkName: Boolean
        get() = networkNameCheckState == NetworkNameCheckState.CHECKING

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

    /** The sign-up page's "Periodic product updates" line, on until the user turns it off. */
    var productUpdates by mutableStateOf(true)
        private set

    val setProductUpdates: (Boolean) -> Unit = { on ->
        productUpdates = on
    }

    val setTermsAgreed:(Boolean) -> Unit = { ta ->
        termsAgreed = ta
    }

    /**
     * The optional referral code, always visible above Continue: typing
     * checks it, and the create call carries [ReferralCodeInputController.createCode].
     */
    val referralInput = ReferralCodeInputController(
        viewModelScope,
        apiReferralCodeChecker { networkSpaceManagerProvider.getNetworkSpace()?.api },
    )

    var networkNameSupportingText by mutableStateOf("")
        private set

    val setNetworkNameSupportingText: (String) -> Unit = { msg ->
        networkNameSupportingText = msg
    }

    private val networkNameCheck = NetworkNameCheck(
        check = { nn, onResult ->
            networkNameValidationVc?.networkCheck(nn) { result, err ->
                viewModelScope.launch {
                    if (err != null) {
                        Log.i(TAG, "network name check failed: ${err.message}")
                    }
                    onResult(if (err == null) result?.available else null)
                }
            } ?: onResult(null)
        },
        schedule = { delayMillis, action ->
            val job = viewModelScope.launch {
                delay(delayMillis)
                action()
            }
            val cancel: () -> Unit = { job.cancel() }
            cancel
        },
        onStateChange = { state ->
            networkNameCheckState = state
        },
    )

    val validateNetworkName: (String) -> Unit = { nn ->
        networkNameCheck.validate(nn)
    }

    val createNetworkArgs: (LoginCreateNetworkParams) -> NetworkCreateArgs = { params ->
        val args = NetworkCreateArgs()

        args.userName = ""
        args.networkName = networkName.text.trim()
        args.terms = termsAgreed
        args.productUpdatesOptOut = !productUpdates
        args.verifyOtpNumeric = true

        referralInput.createCode?.let { code ->
            args.referralCode = code
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
            is LoginCreateNetworkParams.LoginCreateWalletParams -> {
                val walletAuth = WalletAuthArgs()
                walletAuth.publicKey = Uri.decode(params.publicKey)
                walletAuth.signature = Uri.decode(params.signature)
                walletAuth.message = Uri.decode(params.signedMessage)
                // walletAuth.blockchain = "solana"
                walletAuth.blockchain = Uri.decode(params.blockchain)
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

    override fun onCleared() {
        super.onCleared()
        networkNameValidationVc?.close()
    }
}