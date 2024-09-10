package com.bringyour.network.ui.wallet

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import circle.programmablewallet.sdk.WalletSdk
import com.bringyour.client.AccountWallet
import com.bringyour.client.BringYourDevice
import com.bringyour.client.ValidateAddressCallback
import com.bringyour.client.WalletViewController
import com.bringyour.network.ByDeviceManager
import com.bringyour.network.CircleWalletManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@HiltViewModel
class WalletViewModel @Inject constructor(
    private val byDeviceManager: ByDeviceManager,
    private val circleWalletManager: CircleWalletManager,
): ViewModel() {

    private var byDevice: BringYourDevice? = null
    private var walletVc: WalletViewController? = null
    private var circleWalletSdk: WalletSdk? = null

    var nextPayoutDateStr by mutableStateOf("")
        private set

    var addExternalWalletModalVisible by mutableStateOf(false)
        private set

    var circleWalletInProgress by mutableStateOf(false)
        private set

    val wallets = mutableListOf<AccountWallet>()

    val updateNextPayoutDateStr = {
        walletVc?.let { vc ->
            nextPayoutDateStr = vc.nextPayoutDate
        }
    }

    val openExternalWalletModal = {
        addExternalWalletModalVisible = true
    }

    val closeExternalWalletModal = {
        addExternalWalletModalVisible = false
    }

    var externalWalletAddress by mutableStateOf(TextFieldValue())
        private set

    var externalWalletAddressIsValid by mutableStateOf(WalletValidationState(
        solana = false,
        polygon = false
    ))

    val setExternaWalletAddress: (TextFieldValue) -> Unit = { address ->

        externalWalletAddress = address

        if (externalWalletAddress.text.length >= 42) {
            validateWalletAddress(externalWalletAddress.text, "MATIC")
            validateWalletAddress(externalWalletAddress.text, "SOL")
        }

    }

    val createCircleWallet: (OnWalletExecute) -> Unit = { onExecute ->

        if (!circleWalletInProgress) {
            circleWalletInProgress = true

            byDevice?.api()?.walletCircleInit { result, error ->
                runBlocking(Dispatchers.Main.immediate) {
                    circleWalletInProgress = false

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

    private val updateWallets = {

        walletVc?.let { vc ->
            val result = vc.wallets
            val n = result.len()

            val updatedWallets = mutableListOf<AccountWallet>()

            for (i in 0 until n) {
                val wallet = result.get(i)
                updatedWallets.add(wallet)
            }
            wallets.clear()
            wallets.addAll(updatedWallets)

        }

    }


    val validateWalletAddress: (address: String, chain: String) -> Unit = { address, chain ->

        viewModelScope.launch {

            val callback = ValidateAddressCallback { result ->

                if (chain == "MATIC") {
                    externalWalletAddressIsValid = externalWalletAddressIsValid.copy(polygon = result)
                } else if (chain == "SOL") {
                    externalWalletAddressIsValid = externalWalletAddressIsValid.copy(solana = result)
                }

            }

            walletVc?.validateAddress(address, chain, callback)

        }
    }

    val findWalletById: (String) -> AccountWallet? = { id ->
        walletVc?.filterWalletsById(id)
    }

    val addAccountWalletsListener = {

        walletVc?.let { vc ->
            vc.addAccountWalletsListener {
                updateWallets()
            }
        }

    }

    init {

        byDevice = byDeviceManager.getByDevice()

        walletVc = byDevice?.openWalletViewController()

        circleWalletSdk = circleWalletManager.getWalletSdk()

        updateNextPayoutDateStr()

        addAccountWalletsListener()

        walletVc?.start()
    }

    override fun onCleared() {
        super.onCleared()

        walletVc?.let {
            byDeviceManager.getByDevice()?.closeViewController(it)
        }
    }

}

data class WalletValidationState(
    var solana: Boolean = false,
    var polygon: Boolean = false
)

typealias OnWalletExecute = (walletSdk: WalletSdk, userToken: String, encryptionKey: String, challengeId: String) -> Unit
