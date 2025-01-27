package com.bringyour.network.ui.wallet

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import circle.programmablewallet.sdk.WalletSdk
import com.bringyour.sdk.Sdk
import com.bringyour.sdk.ValidateAddressCallback
import com.bringyour.sdk.WalletCircleTransferOutArgs
import com.bringyour.sdk.WalletViewController
import com.bringyour.network.DeviceManager
import com.bringyour.sdk.DeviceLocal
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CircleTransferViewModel @Inject constructor(
    private val deviceManager: DeviceManager,
): ViewModel() {

    private var walletVc: WalletViewController? = null
    private var byDevice: DeviceLocal? = null
    private var circleWalletSdk: WalletSdk? = null

    var transferAmountTextFieldValue by mutableStateOf(TextFieldValue(""))
        private set

    var sendToAddress by mutableStateOf(TextFieldValue(""))
        private set

    val setTransferAmount: (TextFieldValue, Double) -> Unit = { tfv, walletBalance ->

        if (tfv.text.all { char -> char.isDigit() || char == '.' } &&
            tfv.text.count { it == '.' } <= 1) {
            transferAmountTextFieldValue = tfv
        }

        val inputText = transferAmountTextFieldValue.text

        if (inputText.isNotEmpty()) {
            try {
                val inputAmount = inputText.toDouble()
                if (inputAmount > walletBalance) {
                    setTransferAmountValid(false)
                } else {
                    setTransferAmountValid(true)
                }
            } catch (e: NumberFormatException) {
                setTransferAmountValid(false)
            }
        } else {
            setTransferAmountValid(true)
        }

    }

    val setSendToAddress: (TextFieldValue) -> Unit = { tfv ->
        sendToAddress = tfv

        if (sendToAddress.text.length == 42) {
            validateWalletAddress(sendToAddress.text)
        } else {
            setIsSendToAddressValid(false)
        }
    }

    var transferAmountValid by mutableStateOf(false)
        private set

    val setTransferAmountValid: (Boolean) -> Unit = { isValid ->
        transferAmountValid = isValid
    }

    var isSendToAddressValidating by mutableStateOf(false)
        private set

    val setIsSendToAddressValidating: (Boolean) -> Unit = { isValidating ->
        isSendToAddressValidating = isValidating
    }

    var isSendToAddressValid by mutableStateOf(true)
        private set

    val setIsSendToAddressValid: (Boolean) -> Unit = { isValid ->
        isSendToAddressValid = isValid
    }

    var transferInProgress by mutableStateOf(false)
        private set

    val setTransferInProgress: (Boolean) -> Unit = { inProgress ->
        transferInProgress = inProgress
    }

    var transferError by mutableStateOf<String?>(null)
        private set

    val setTransferError: (String?) -> Unit = { msg ->
        transferError = msg
    }

    private val validateWalletAddress: (address: String) -> Unit = { address ->

        setIsSendToAddressValidating(true)

        viewModelScope.launch {

            val callback = ValidateAddressCallback { result ->

                setIsSendToAddressValid(result)

                setIsSendToAddressValidating(false)

            }

            walletVc?.validateAddress(address, "MATIC", callback)

        }
    }

    val transfer: (OnWalletExecute) -> Unit = { onExecute ->
        val args = WalletCircleTransferOutArgs()
        args.amountUsdcNanoCents = Sdk.usdToNanoCents(transferAmountTextFieldValue.text.toDouble())
        args.toAddress = sendToAddress.text
        // todo - this is missing in the design, need to add a terms checkbox
        args.terms = true

        setTransferInProgress(true)

        byDevice?.api?.walletCircleTransferOut(args) { result, error ->
            viewModelScope.launch {

                if (error != null) {
                    setTransferError(error.message)
                    setTransferInProgress(false)
                } else if (result.error != null) {
                    setTransferError(result.error.message)
                    setTransferInProgress(false)
                } else {
                    val userToken = result.userToken.userToken
                    val encryptionKey = result.userToken.encryptionKey
                    val challengeId = result.challengeId

                    circleWalletSdk?.let { walletSdk ->

                        onExecute(
                            walletSdk,
                            userToken,
                            encryptionKey,
                            challengeId
                        )
                    }

                }

            }

        }
    }

    init {
        walletVc = deviceManager.device?.openWalletViewController()
    }

}