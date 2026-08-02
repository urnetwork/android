package com.bringyour.network.ui.wallet

import android.icu.util.Calendar
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bringyour.network.DeviceManager
import com.bringyour.network.ForegroundDeviceControllerOwner
import com.bringyour.network.TAG
import com.bringyour.network.utils.formatDecimalString
import com.bringyour.network.utils.formatUnpaidByteCount
import com.bringyour.network.utils.roundToDecimals
import com.bringyour.sdk.AccountPayment
import com.bringyour.sdk.AccountWallet
import com.bringyour.sdk.DeviceLocal
import com.bringyour.sdk.Id
import com.bringyour.sdk.ReliabilityWindow
import com.bringyour.sdk.Sdk
import com.bringyour.sdk.ValidateAddressCallback
import com.bringyour.sdk.VerifySeekerNftHolderArgs
import com.bringyour.sdk.WalletViewController
import com.solana.publickey.SolanaPublicKey
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.atomic.AtomicBoolean
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
): ViewModel(), DefaultLifecycleObserver {

    private var byDevice: DeviceLocal? = null
    private var walletVc: WalletViewController? = null
    private val subs = mutableListOf<com.bringyour.sdk.Sub>()
    private var removeDeviceChangeListener: (() -> Unit)? = null
    private val processLifecycle = ProcessLifecycleOwner.get().lifecycle
    private val controllerOwner =
        ForegroundDeviceControllerOwner<DeviceLocal, WalletViewController>(
            open = { openWalletViewController(it) },
            close = { device, vc -> closeWalletViewController(device, vc) },
        )

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

    private val paymentsRefreshed = AtomicBoolean(false)
    private val transferStatsRefreshed = AtomicBoolean(false)

    val refreshWalletsInfo = {
        if (!isRefreshingWallets) {
            setIsRefreshingWallets(true)
            paymentsRefreshed.set(false)
            transferStatsRefreshed.set(false)

            walletVc?.fetchPayments()
            walletVc?.fetchTransferStats()
        }
    }

    val refreshWalletInfo: () -> Unit = {

        if (!isRefreshingWallet && !isRefreshingWallets && paymentsRefreshed.get()) {
            setIsRefreshingWallet(true)
            paymentsRefreshed.set(false)

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
            validateWalletAddress(externalWalletAddress.text, "TAO")
        }

    }

    val setInitializingFirstWallet: (Boolean) -> Unit = { isInitializing ->
        initializingFirstWallet = isInitializing
    }

    val updateWallets = {
        walletVc?.let { updateWallets(it) }
    }

    private fun updateWallets(vc: WalletViewController) {
        viewModelScope.launch {
            if (walletVc !== vc) {
                return@launch
            }
            val result = vc.wallets
            val n = result.len()

            val updatedWallets = mutableListOf<AccountWallet>()
            var hasSeekerToken = false

            for (i in 0 until n) {
                val wallet = result.get(i)

                if (wallet.hasSeekerToken) {
                    hasSeekerToken = true
                }

                if (wallet.circleWalletId.isNullOrEmpty()) {
                    updatedWallets.add(wallet)
                }

            }

            if (hasSeekerToken && !_isSeekerHolder.value) {
                _isSeekerHolder.value = true
            }

            val prevWalletCount = _wallets.value.count()

            _wallets.update { updatedWallets }

            if (initializingWallets) {
                setInitializingWallets(false)
            }

            if (prevWalletCount <= 0 && n > 0 && initializingFirstWallet) {
                setInitializingFirstWallet(false)
            }
        }
    }


    private val validateWalletAddress: (address: String, chain: String) -> Unit = { address, chain ->

        viewModelScope.launch {

            val callback = ValidateAddressCallback { result ->
                viewModelScope.launch {
                    setExternalWalletAddressIsValid(chain, result)
                }
            }

            walletVc?.validateAddress(address, chain, callback)

        }
    }

    val setExternalWalletAddressIsValid: (chain: String, isValid: Boolean) -> Unit = { chain, isValid ->
        if (chain == "MATIC") {
            externalWalletAddressIsValid = externalWalletAddressIsValid.copy(polygon = isValid)
        } else if (chain == "SOL") {
            externalWalletAddressIsValid = externalWalletAddressIsValid.copy(solana = isValid)
        } else if (chain == "TAO") {
            externalWalletAddressIsValid = externalWalletAddressIsValid.copy(tao = isValid)
        }
    }

    val linkWallet: () -> Unit = {

        var chain: String = ""
        if (externalWalletAddressIsValid.solana) {
            chain = "SOL"
        } else if (externalWalletAddressIsValid.polygon) {
            chain = "MATIC"
        } else if (externalWalletAddressIsValid.tao) {
            chain = "TAO"
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

    private fun addAccountWalletsListener(vc: WalletViewController) {
        vc.addAccountWalletsListener {
            updateWallets(vc)
        }?.let { subs.add(it) }
    }

    private fun addExternalWalletProcessingListener(vc: WalletViewController) {
        vc.addIsCreatingExternalWalletListener { isProcessing ->
            viewModelScope.launch {
                if (walletVc !== vc) {
                    return@launch
                }
                isProcessingExternalWallet = isProcessing
            }
        }?.let { subs.add(it) }
    }

    private fun addPayoutWalletListener(vc: WalletViewController) {
        vc.addPayoutWalletListener { id ->
            viewModelScope.launch {
                if (walletVc !== vc) {
                    return@launch
                }
                payoutWalletId = id

                if (isSettingPayoutWallet) {
                    isSettingPayoutWallet = false
                }
            }
        }?.let { subs.add(it) }
    }

    private fun getPayouts(vc: WalletViewController) {
        viewModelScope.launch {
            if (walletVc !== vc) {
                return@launch
            }
            val result = vc.accountPayments
            val n = result.len()

            val updatedPayouts = mutableListOf<AccountPayment>()

            var totalPayoutsUsdc: Double = 0.0

            for (i in 0 until n) {
                val payout = result.get(i)
                updatedPayouts.add(payout)
                totalPayoutsUsdc += payout.tokenAmount.roundToDecimals(4)
            }

            _payouts.value = updatedPayouts
            totalPayoutAmount = totalPayoutsUsdc
            if (!totalPayoutAmountInitialized) {
                totalPayoutAmountInitialized = true
            }

            paymentsRefreshed.set(true)

            if (isRefreshingWallets && transferStatsRefreshed.get()) {
                setIsRefreshingWallets(false)
            }

            if (isRefreshingWallet) {
                setIsRefreshingWallet(false)
            }
        }
    }

    val getPayoutById: (String) -> AccountPayment? = { id ->
        val payout = _payouts.value.find { payout ->
                payout.paymentId.string() == id
        }

        payout
    }

    private fun addPayoutsListener(vc: WalletViewController) {
        vc.addPaymentsListener {
            getPayouts(vc)
        }?.let { subs.add(it) }
    }

    val setPayoutWallet: (Id) -> Unit = { walletId ->
        isSettingPayoutWallet = true
        walletVc?.updatePayoutWallet(walletId)
    }

    val removeWallet: (Id) -> Unit = { id ->
        walletVc?.removeWallet(id)
    }

    private fun addIsRemovingWalletListener(vc: WalletViewController) {
        vc.addIsRemovingWalletListener { isRemoving ->
            viewModelScope.launch {
                if (walletVc !== vc) {
                    return@launch
                }
                isRemovingWallet = isRemoving
            }
        }?.let { subs.add(it) }
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

    private fun addUnpaidByteCountListener(vc: WalletViewController) {
        vc.addUnpaidByteCountListener{ ubc ->
            viewModelScope.launch(Dispatchers.Main) {
                if (walletVc !== vc) {
                    return@launch
                }
                unpaidMegaByteCount = formatUnpaidByteCount(ubc.toDouble())
                transferStatsRefreshed.set(true)
                if (isRefreshingWallets && paymentsRefreshed.get()) {
                        setIsRefreshingWallets(false)
                }
            }
        }?.let { subs.add(it) }
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
    ) -> Unit = verifySeekerHolder@{ publicKey, message, signature, onError ->

        if (!isVerifyingSeekerHolder) {
            isVerifyingSeekerHolder = true
            val device = byDevice
            if (device == null) {
                isVerifyingSeekerHolder = false
                return@verifySeekerHolder
            }

            val args = VerifySeekerNftHolderArgs()
            args.publicKey = publicKey.address
            args.signature = signature
            args.message = message

            val api = device.api
            if (api == null) {
                isVerifyingSeekerHolder = false
                return@verifySeekerHolder
            }
            api.verifySeekerHolder(args) { result, error ->

                viewModelScope.launch {
                    if (byDevice !== device) {
                        return@launch
                    }
                    Log.i(TAG, "[verifySeekerHolder] result = $result, error = $error")

                    if (error != null) {
                        isVerifyingSeekerHolder = false
                        return@launch
                    }

                    if (result != null && result.success) {
                        _isSeekerHolder.value = true
                        walletVc?.fetchAccountWallets()
                    } else {
                        val errorMessage = result?.error?.message ?: "No Seeker NFT found in wallet ...${publicKey.address.takeLast(7)}"
                        onError(errorMessage)
                    }
                    isVerifyingSeekerHolder = false
                }

            }
        }

    }

    init {
        processLifecycle.addObserver(this)
        controllerOwner.setForeground(
            processLifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        )
        removeDeviceChangeListener = deviceManager.addDeviceChangeListener { device ->
            viewModelScope.launch {
                setupDevice(device)
            }
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        controllerOwner.setForeground(true)
    }

    override fun onStop(owner: LifecycleOwner) {
        controllerOwner.setForeground(false)
    }

    private fun setupDevice(device: DeviceLocal?) {
        byDevice = device
        _wallets.value = emptyList()
        _payouts.value = emptyList()
        payoutWalletId = null
        totalPayoutAmount = 0.0
        totalPayoutAmountInitialized = false
        initializingWallets = true
        controllerOwner.setDevice(device)
    }

    private fun openWalletViewController(device: DeviceLocal): WalletViewController {
        val vc = device.openWalletViewController()
        walletVc = vc
        nextPayoutDateStr = vc.nextPayoutDate
        addAccountWalletsListener(vc)
        addExternalWalletProcessingListener(vc)
        addPayoutWalletListener(vc)
        addPayoutsListener(vc)
        addIsRemovingWalletListener(vc)
        addUnpaidByteCountListener(vc)
        vc.start()
        getPayouts(vc)
        return vc
    }

    private fun closeWalletViewController(
        device: DeviceLocal,
        vc: WalletViewController,
    ) {
        subs.forEach { it.close() }
        subs.clear()
        vc.stop()
        device.closeViewController(vc)
        if (walletVc === vc) {
            walletVc = null
        }
    }

    override fun onCleared() {
        removeDeviceChangeListener?.invoke()
        removeDeviceChangeListener = null
        processLifecycle.removeObserver(this)
        controllerOwner.close()
        byDevice = null
        super.onCleared()
    }

}

enum class Blockchain {
    POLYGON,
    SOLANA,
    BITTENSOR;

    companion object {
        fun fromString(value: String): Blockchain? {
            return when (value.uppercase()) {
                "POLYGON" -> POLYGON
                "MATIC" -> POLYGON
                "SOLANA" -> SOLANA
                "SOL" -> SOLANA
                "TAO" -> BITTENSOR
                "BITTENSOR" -> BITTENSOR
                else -> null
            }
        }
    }
}

data class WalletValidationState(
    val solana: Boolean = false,
    val polygon: Boolean = false,
    val tao: Boolean = false
)
