package com.bringyour.network.ui.wallet

import android.icu.util.Calendar
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import circle.programmablewallet.sdk.WalletSdk
import com.bringyour.sdk.AccountPayment
import com.bringyour.sdk.AccountWallet
import com.bringyour.sdk.BringYourDevice
import com.bringyour.sdk.Sdk
import com.bringyour.sdk.Id
import com.bringyour.sdk.ValidateAddressCallback
import com.bringyour.sdk.WalletViewController
import com.bringyour.network.ByDeviceManager
import com.bringyour.network.CircleWalletManager
import com.bringyour.network.TAG
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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

    var unpaidMegaByteCount by mutableStateOf("")
        private set

    var isRetrievingSagaWallet by mutableStateOf(false)
        private set

    val setIsRetrievingSagaWallet: (Boolean) -> Unit = { ir ->
        isRetrievingSagaWallet = ir
    }

    private val _requestSagaWallet = MutableSharedFlow<Unit>()
    val requestSagaWallet: SharedFlow<Unit> = _requestSagaWallet.asSharedFlow()

    /**
     * Display a loading indicator when first loading wallets
     */
    var initializingWallets by mutableStateOf(true)
        private set

    val setInitializingWallets: (Boolean) -> Unit = { isInitializing ->
        initializingWallets = isInitializing
    }

    /**
     * Used when in the process of creating the users first wallet.
     * This creates a loading state between SetupWallet and WalletsList
     */
    var initializingFirstWallet by mutableStateOf(false)
        private set

    var isSettingPayoutWallet by mutableStateOf(false)
        private set

    var isRemovingWallet by mutableStateOf(false)
        private set

    val wallets = mutableListOf<AccountWallet>()

    var payouts by mutableStateOf(listOf<AccountPayment>())
        private set

    var totalPayoutAmount by mutableDoubleStateOf(0.0)
        private set

    var totalPayoutAmountInitialized by mutableStateOf(false)
        private set

    var circleWalletBalance by mutableDoubleStateOf(0.0)

    private var fetchBytesLastCheckedHour by mutableIntStateOf(0)

    var circleWalletExists by mutableStateOf(false)
        private set

    val setCircleWalletExists: (Boolean) -> Unit = { exists ->
        circleWalletExists = exists
    }

    val updateNextPayoutDateStr = {
        walletVc?.let { vc ->
            nextPayoutDateStr = vc.nextPayoutDate
        }
    }

    val getCurrentHour:() -> Int = {
        val calendar = Calendar.getInstance()
        calendar.get(Calendar.HOUR_OF_DAY)
    }

    /**
     * For refreshing wallets screen
     * including payouts and unpaid bytes
     */
    var isRefreshingWallets by mutableStateOf(false)
        private set

    private val setIsRefreshingWallets: (Boolean) -> Unit = { isRefreshing ->
        this.isRefreshingWallets = isRefreshing
    }

    /**
     * For freshing individual wallet
     * including payouts and Circle balance
     */
    var isRefreshingWallet by mutableStateOf(false)
        private set

    private val setIsRefreshingWallet: (Boolean) -> Unit = { isRefreshing ->
        this.isRefreshingWallet = isRefreshing
    }

    private var paymentsRefreshed = false
    private var transferStatsRefreshed = false

    val refreshWalletsInfo = {
        if (!isRefreshingWallets) {
            setIsRefreshingWallets(true)
            paymentsRefreshed = false
            transferStatsRefreshed = false

            walletVc?.fetchPayments()
            walletVc?.fetchTransferStats()
        }
    }

    val refreshWalletInfo: (Boolean) -> Unit = { isCircleWallet ->

        if (!isRefreshingWallet && !isRefreshingWallets && paymentsRefreshed) {
            setIsRefreshingWallet(true)
            paymentsRefreshed = false

            if (isCircleWallet) {
                fetchCircleWalletInfo()
            }

            walletVc?.fetchPayments()

        }
    }

    val openExternalWalletModal = {
        addExternalWalletModalVisible = true
    }

    val closeExternalWalletModal = {
        addExternalWalletModalVisible = false
    }

    val openRemoveWalletModal = {
        removeWalletModalVisible = true
    }

    val closeRemoveWalletModal = {
        removeWalletModalVisible = false
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
                        Log.i("WalletViewModel", "error is ${error.message}")
                        setCircleWalletInProgress(false)
                        setInitializingFirstWallet(false)
                        // todo display error
                        return@launch
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
        initializingFirstWallet = isInitializing
    }

    private val fetchCircleWalletInfo = {
        byDevice?.api?.subscriptionBalance { result, error ->
            if (error != null) {
                Log.i(TAG, "[wallet]fetch error = $error")
            } else {

                viewModelScope.launch {
                    result.walletInfo?.balanceUsdcNanoCents?.let {
                        Sdk.nanoCentsToUsd(
                            it
                        )
                    }?.let { setCircleWalletBalance(it) }

                }
            }
        }
    }

    val setCircleWalletBalance: (Double) -> Unit = { balance ->
        circleWalletBalance = balance
    }

    val updateWallets = {

        walletVc?.let { vc ->

            val result = vc.wallets
            val n = result.len()

            val updatedWallets = mutableListOf<AccountWallet>()

            setCircleWalletExists(false)

            for (i in 0 until n) {
                val wallet = result.get(i)
                updatedWallets.add(wallet)
                if (!wallet.circleWalletId.isNullOrEmpty()) {
                    setCircleWalletExists(true)
                } else {
                    Log.i("WalletViewModel", "no circle wallet found")
                }
            }

            if (circleWalletExists) {
                fetchCircleWalletInfo()
            }

            val prevWalletCount = wallets.count()

            wallets.clear()
            wallets.addAll(updatedWallets)

            if (initializingWallets) {
                setInitializingWallets(false)
            }

            if (prevWalletCount <= 0 && n > 0 && initializingFirstWallet) {
                setInitializingFirstWallet(false)
            }

            if (prevWalletCount != wallets.size && circleWalletInProgress) {
                setCircleWalletInProgress(false)
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
            initializingFirstWallet = true
        }

        if (chain != "") {
            walletVc?.addExternalWallet(externalWalletAddress.text, chain)
            setExternaWalletAddress(TextFieldValue(""))
        }

    }

    val findWalletById: (String) -> AccountWallet? = { id ->
        walletVc?.filterWalletsById(id)
    }

    val addAccountWalletsListener = {

        viewModelScope.launch {
            walletVc?.let { vc ->
                vc.addAccountWalletsListener {
                    updateWallets()
                }
            }
        }

    }

    val addExternalWalletProcessingListener = {
        viewModelScope.launch {
            walletVc?.addIsCreatingExternalWalletListener { isProcessing ->

                if (isProcessingExternalWallet && !isProcessing) {
                    closeExternalWalletModal()
                }

                isProcessingExternalWallet = isProcessing
            }
        }
    }

    val addPayoutWalletListener = {
        viewModelScope.launch {
            walletVc?.addPayoutWalletListener { id ->
                payoutWalletId = id

                if (isSettingPayoutWallet) {
                    isSettingPayoutWallet = false
                }
            }
        }
    }

    private val getPayouts = {

        walletVc?.let { vc ->
            val result = vc.accountPayments
            val n = result.len()

            val updatedPayouts = mutableListOf<AccountPayment>()

            var totalPayoutsUsdc: Double = 0.0

            for (i in 0 until n) {
                val payout = result.get(i)
                updatedPayouts.add(payout)

                totalPayoutsUsdc += String.format("%.4f", payout.tokenAmount).toDouble()
            }

            payouts = updatedPayouts
            totalPayoutAmount = totalPayoutsUsdc
            if (!totalPayoutAmountInitialized) {
                totalPayoutAmountInitialized = true
            }

            paymentsRefreshed = true

            // refreshing all wallets
            if (isRefreshingWallets && transferStatsRefreshed) {
                viewModelScope.launch {
                    setIsRefreshingWallets(false)
                }
            }

            // refreshing individual wallet
            if (isRefreshingWallet) {
                viewModelScope.launch {
                    setIsRefreshingWallet(false)
                }
            }
        }

    }

    val addPayoutsListener = {
        viewModelScope.launch {
            walletVc?.addPaymentsListener {
                getPayouts()
            }
        }
    }

    val setPayoutWallet: (Id) -> Unit = { walletId ->
        isSettingPayoutWallet = true
        walletVc?.updatePayoutWallet(walletId)
    }

    val removeWallet: (Id) -> Unit = { id ->
        walletVc?.removeWallet(id)
    }

    val addIsRemovingWalletListener = {
        viewModelScope.launch {
            walletVc?.addIsRemovingWalletListener { isRemoving ->
                isRemovingWallet = isRemoving
            }
        }
    }

    val pollWallets = {
        walletVc?.setIsPollingPayoutWallet(true)
        walletVc?.setIsPollingAccountWallets(true)
    }

    // this data depends on transfer_escrow_sweep
    // data is only updated once an hour
    val fetchTransferStats = {
        val currentHour = getCurrentHour()
        if (fetchBytesLastCheckedHour != currentHour) {
            walletVc?.fetchTransferStats()
            fetchBytesLastCheckedHour = currentHour
        }
    }

    val addUnpaidByteCountListener = {
        walletVc?.addUnpaidByteCountListener{ ubc ->
            unpaidMegaByteCount = String.format("%.4f", ubc / (1024.0 * 1024.0))
            transferStatsRefreshed = true
            if (isRefreshingWallets && paymentsRefreshed) {
                viewModelScope.launch(Dispatchers.Main) {
                    setIsRefreshingWallets(false)
                }
            }
        }
    }

    val connectSagaWallet:  () -> Unit = {

        if (!isRetrievingSagaWallet) {

            setIsRetrievingSagaWallet(true)
            viewModelScope.launch {
                _requestSagaWallet.emit(Unit)
            }

        }

    }

    val sagaWalletAddressRetrieved: (String?) -> Unit = { address ->
        if (address != null) {
            setExternaWalletAddress(TextFieldValue(address))
            // setExternalWalletAddress(TextFieldValue(address))
            // since this is taken directly from the saga,
            // we can mark this as true without calling our API to validate
            setExternalWalletAddressIsValid("SOL", true)

            createExternalWallet()
        }
        setIsRetrievingSagaWallet(false)
    }

    init {

        circleWalletSdk = circleWalletManager.circleWalletSdk

        byDevice = byDeviceManager.byDevice


        walletVc = byDevice?.openWalletViewController()

        updateNextPayoutDateStr()
        addAccountWalletsListener()
        addExternalWalletProcessingListener()
        addPayoutWalletListener()
        addPayoutsListener()
        addIsRemovingWalletListener()
        addUnpaidByteCountListener()

        viewModelScope.launch {
            walletVc?.start()
        }

        getPayouts()

        // updateWallets()
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
