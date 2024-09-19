package com.bringyour.network.ui.wallet

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import circle.programmablewallet.sdk.WalletSdk
import com.bringyour.client.AccountPayment
import com.bringyour.client.AccountWallet
import com.bringyour.client.BringYourDevice
import com.bringyour.client.Id
import com.bringyour.client.ValidateAddressCallback
import com.bringyour.client.WalletViewController
import com.bringyour.network.ByDeviceManager
import com.bringyour.network.CircleWalletManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
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

    var removeWalletModalVisible by mutableStateOf(false)
        private set

    var circleWalletInProgress by mutableStateOf(false)
        private set

    var isInitializingFirstWallet by mutableStateOf(false)
        private set

    var isSettingPayoutWallet by mutableStateOf(false)
        private set

    var isRemovingWallet by mutableStateOf(false)
        private set

    val wallets = mutableListOf<AccountWallet>()

    var payouts by mutableStateOf(listOf<AccountPayment>())
        private set

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

    val openRemoveWalletModal = {
        addExternalWalletModalVisible = true
    }

    val closeRemoveWalletModal = {
        addExternalWalletModalVisible = false
    }

    var externalWalletAddress by mutableStateOf(TextFieldValue())
        private set

    var externalWalletAddressIsValid by mutableStateOf(WalletValidationState(
        solana = false,
        polygon = false
    ))
        private set

    var isProcessingExternalWallet by mutableStateOf(false)
        private set

    var payoutWalletId by mutableStateOf<Id?>(null)
        private set

    val setExternaWalletAddress: (TextFieldValue) -> Unit = { address ->

        externalWalletAddress = address

        if (externalWalletAddress.text.length >= 42) {
            validateWalletAddress(externalWalletAddress.text, "MATIC")
            validateWalletAddress(externalWalletAddress.text, "SOL")
        }

    }

    val createCircleWallet: (OnWalletExecute) -> Unit = { onExecute ->

        if (!circleWalletInProgress) {

            setCircleWalletInProgress(true)
            setInitializingFirstWallet(true)

            byDevice?.api?.walletCircleInit { result, error ->
                viewModelScope.launch {
                    if (error != null) {
                        Log.i("WalletViewModel", "error is ${error?.message}")
                    }

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

    val setCircleWalletInProgress: (Boolean) -> Unit = { inProgress ->
        circleWalletInProgress = inProgress
    }

    val setInitializingFirstWallet: (Boolean) -> Unit = { isInitializing ->
        isInitializingFirstWallet = isInitializing
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

            val prevWalletCount = wallets.count()

            wallets.clear()
            wallets.addAll(updatedWallets)

            if (prevWalletCount <= 0 && n > 0) {
                isInitializingFirstWallet = false
            }

        }

    }


    private val validateWalletAddress: (address: String, chain: String) -> Unit = { address, chain ->

        viewModelScope.launch {

            val callback = ValidateAddressCallback { result ->

                setExternalWalletAddressIsValid(chain, result)

            }

            walletVc?.validateAddress(address, chain, callback)

        }
    }

    val setExternalWalletAddressIsValid: (chain: String, isValid: Boolean) -> Unit = { chain, isValid ->
        if (chain == "MATIC") {
            externalWalletAddressIsValid = externalWalletAddressIsValid.copy(polygon = isValid)
        } else if (chain == "SOL") {
            externalWalletAddressIsValid = externalWalletAddressIsValid.copy(solana = isValid)
        }
    }

    val createExternalWallet: () -> Unit = {

        var chain: String = ""
        if (externalWalletAddressIsValid.solana) {
            chain = "SOL"
        } else if (externalWalletAddressIsValid.polygon) {
            chain = "MATIC"
        }

        if (wallets.isEmpty()) {
            isInitializingFirstWallet = true
        }

        if (chain != "") {
            walletVc?.addExternalWallet(externalWalletAddress.text, chain)
        }

    }

    val findWalletById: (String) -> AccountWallet? = { id ->
        walletVc?.filterWalletsById(id)
    }

    val addAccountWalletsListener = {

        walletVc?.let { vc ->
            vc.addAccountWalletsListener {

                Log.i("WalletViewModel", "account wallets listener hit")

                updateWallets()
            }
        }

    }

    val addExternalWalletProcessingListener = {
        walletVc?.addIsCreatingExternalWalletListener { isProcessing ->

            if (isProcessingExternalWallet && !isProcessing) {
                closeExternalWalletModal()
            }

            isProcessingExternalWallet = isProcessing
        }
    }

    val addPayoutWalletListener = {
        walletVc?.addPayoutWalletListener { id ->
            payoutWalletId = id

            if (isSettingPayoutWallet) {
                isSettingPayoutWallet = false
            }
        }
    }

    private val getPayouts = {

        walletVc?.let { vc ->
            val result = vc.accountPayments
            val n = result.len()

            val updatedPayouts = mutableListOf<AccountPayment>()

            for (i in 0 until n) {
                val payout = result.get(i)
                updatedPayouts.add(payout)

            }

            payouts = updatedPayouts
        }

    }

    val addPayoutsListener = {
        walletVc?.addPayoutWalletListener {
            getPayouts()
        }
    }

    val setPayoutWallet: (Id) -> Unit = { walletId ->
        isSettingPayoutWallet = true
        walletVc?.setPayoutWallet(walletId)
    }

    val removeWallet: (Id) -> Unit = { id ->
        walletVc?.removeWallet(id)
    }

    val addIsRemovingWalletListener = {
        walletVc?.addIsRemovingWalletListener { isRemoving ->
            isRemovingWallet = isRemoving
        }
    }

    init {

        byDevice = byDeviceManager.byDevice

        walletVc = byDevice?.openWalletViewController()

        circleWalletSdk = circleWalletManager.getWalletSdk()

        updateNextPayoutDateStr()

        addAccountWalletsListener()
        addExternalWalletProcessingListener()
        addPayoutWalletListener()
        addPayoutsListener()
        addIsRemovingWalletListener()

        walletVc?.start()
    }

    override fun onCleared() {
        super.onCleared()

        walletVc?.let {
            byDeviceManager.byDevice?.closeViewController(it)
        }
    }

}

enum class Blockchain {
    POLYGON,
    SOLANA;

    companion object {
        fun fromString(value: String): Blockchain? {
            return when (value.uppercase()) {
                "POLYGON" -> POLYGON
                "MATIC" -> POLYGON
                "SOLANA" -> SOLANA
                "SOL" -> SOLANA
                else -> null
            }
        }
    }
}

data class WalletValidationState(
    var solana: Boolean = false,
    var polygon: Boolean = false
)

typealias OnWalletExecute = (walletSdk: WalletSdk, userToken: String, encryptionKey: String, challengeId: String) -> Unit
