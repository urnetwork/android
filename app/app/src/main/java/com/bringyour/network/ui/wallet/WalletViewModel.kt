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
import com.bringyour.sdk.AccountPayment
import com.bringyour.sdk.AccountWallet
import com.bringyour.sdk.DeviceLocal
import com.bringyour.sdk.Sdk
import com.bringyour.sdk.Id
import com.bringyour.sdk.ValidateAddressCallback
import com.bringyour.sdk.WalletViewController
import com.bringyour.network.DeviceManager
import com.bringyour.network.TAG
import com.bringyour.network.utils.formatDecimalString
import com.bringyour.network.utils.formatUnpaidByteCount
import com.bringyour.network.utils.roundToDecimals
import com.bringyour.sdk.ReliabilityWindow
import com.bringyour.sdk.VerifySeekerNftHolderArgs
import com.solana.publickey.SolanaPublicKey
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WalletViewModel @Inject constructor(
    private val deviceManager: DeviceManager,
): ViewModel() {

    private var byDevice: DeviceLocal? = null
    private var walletVc: WalletViewController? = null

    var nextPayoutDateStr by mutableStateOf("")
        private set

    var removeWalletModalVisible by mutableStateOf(false)
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

    private val _isSeekerHolder = MutableStateFlow<Boolean>(false)
    val isSeekerHolder: StateFlow<Boolean> = _isSeekerHolder.asStateFlow()

    var isVerifyingSeekerHolder by mutableStateOf(false)
        private set

    private val _wallets = MutableStateFlow<List<AccountWallet>>(emptyList())
    val wallets: StateFlow<List<AccountWallet>> = _wallets.asStateFlow()

    private val _payouts = MutableStateFlow<List<AccountPayment>>(emptyList())
    val payouts: StateFlow<List<AccountPayment>> = _payouts.asStateFlow()

    var totalPayoutAmount by mutableDoubleStateOf(0.0)
        private set

    var totalPayoutAmountInitialized by mutableStateOf(false)
        private set

    private var fetchBytesLastCheckedHour by mutableIntStateOf(0)

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
     * For refreshing individual wallet
     * including payouts
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

    val refreshWalletInfo: () -> Unit = {

        if (!isRefreshingWallet && !isRefreshingWallets && paymentsRefreshed) {
            setIsRefreshingWallet(true)
            paymentsRefreshed = false

            walletVc?.fetchPayments()

        }
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

    val setInitializingFirstWallet: (Boolean) -> Unit = { isInitializing ->
        initializingFirstWallet = isInitializing
    }

    val updateWallets = {

        walletVc?.let { vc ->

            val result = vc.wallets
            val n = result.len()

            val updatedWallets = mutableListOf<AccountWallet>()

            for (i in 0 until n) {
                val wallet = result.get(i)

                if (!_isSeekerHolder.value && wallet.hasSeekerToken) {
                    viewModelScope.launch {
                        _isSeekerHolder.value = true
                    }
                }

                if (wallet.circleWalletId.isNullOrEmpty()) {
                    updatedWallets.add(wallet)
                }

            }

            val prevWalletCount = _wallets.value.count()

            viewModelScope.launch {

                _wallets.update { updatedWallets }

                if (initializingWallets) {
                    setInitializingWallets(false)
                }

                if (prevWalletCount <= 0 && n > 0 && initializingFirstWallet) {
                    setInitializingFirstWallet(false)
                }

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

    val linkWallet: () -> Unit = {

        var chain: String = ""
        if (externalWalletAddressIsValid.solana) {
            chain = "SOL"
        } else if (externalWalletAddressIsValid.polygon) {
            chain = "MATIC"
        }

        if (_wallets.value.isEmpty()) {
            initializingFirstWallet = true
        }

        if (chain != "") {
            walletVc?.addExternalWallet(externalWalletAddress.text, chain)
            setExternaWalletAddress(TextFieldValue(""))
            setExternalWalletAddressIsValid(chain, false)
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

                payout.tokenAmount
                
                totalPayoutsUsdc += payout.tokenAmount.roundToDecimals(4)
            }

            _payouts.value = updatedPayouts
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

    val getPayoutById: (String) -> AccountPayment? = { id ->
        val payout = _payouts.value.find { payout ->
                payout.paymentId.string() == id
        }

        payout
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
            viewModelScope.launch(Dispatchers.Main) {
                unpaidMegaByteCount = formatUnpaidByteCount(ubc.toDouble())
                transferStatsRefreshed = true
                if (isRefreshingWallets && paymentsRefreshed) {
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

            linkWallet()
        }
        setIsRetrievingSagaWallet(false)
    }

    val verifySeekerHolder: (
        SolanaPublicKey,
        String,
        String,
        (String) -> Unit
    ) -> Unit = { publicKey, message, signature, onError ->

        if (!isVerifyingSeekerHolder) {
            isVerifyingSeekerHolder = true

            val args = VerifySeekerNftHolderArgs()
            args.publicKey = publicKey.string() // should be base58?
            args.signature = signature
            args.message = message

            byDevice?.api?.verifySeekerHolder(args) { result, error ->

                viewModelScope.launch {
                    Log.i(TAG, "[verifySeekerHolder] result = $result, error = $error")

                    if (error != null) {
                        return@launch
                    }

                    if (result != null && result.success) {
                        _isSeekerHolder.value = true
                        walletVc?.fetchAccountWallets()
                    } else {
                        val errorMessage = result?.error?.message ?: "No Seeker NFT found in wallet ...${publicKey.string().takeLast(7)}"
                        onError(errorMessage)
                    }
                    isVerifyingSeekerHolder = false
                }

            }
        }

    }

    init {

        byDevice = deviceManager.device
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
            deviceManager.device?.closeViewController(it)
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

