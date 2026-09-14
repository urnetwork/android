package com.bringyour.network.ui.wallet

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bringyour.network.DeviceManager
import com.bringyour.sdk.DeviceLocal
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The Solana (legacy USDC) payout wallet on the Earnings screen: connect it through a
 * wallet app or a validated manual address, show it with the USDC waiting, remove it.
 * Obtained per screen with `hiltViewModel()`, beside [EarningsViewModel], which it
 * leaves alone.
 */
@HiltViewModel
class SolanaWalletViewModel @Inject constructor(
    private val deviceManager: DeviceManager,
) : ViewModel() {

    private var byDevice: DeviceLocal? = null
    private var removeDeviceChangeListener: (() -> Unit)? = null
    private var source: LegacyWalletSource = NoLegacyWalletSource

    private val _legacy = MutableStateFlow(LegacyWalletUi())
    val legacy: StateFlow<LegacyWalletUi> = _legacy.asStateFlow()

    // the first round finished, so the UI can tell "no wallet" from "not loaded yet"
    private val _legacyLoaded = MutableStateFlow(false)
    val legacyLoaded: StateFlow<Boolean> = _legacyLoaded.asStateFlow()

    private val _connectState = MutableStateFlow<SolanaConnectState>(SolanaConnectState.Idle)
    val connectState: StateFlow<SolanaConnectState> = _connectState.asStateFlow()

    // kept here so a recomposition or the wallet app round trip cannot lose the step
    private val _sheetStep = MutableStateFlow(SolanaSheetStep.CHOOSE)
    val sheetStep: StateFlow<SolanaSheetStep> = _sheetStep.asStateFlow()

    var isPresentedSheet by mutableStateOf(false)
        private set

    var manualAddress by mutableStateOf(TextFieldValue(""))
        private set

    private val _manualValidation = MutableStateFlow<AddressValidation>(AddressValidation.Empty)
    val manualValidation: StateFlow<AddressValidation> = _manualValidation.asStateFlow()

    var isPresentedRemoveDialog by mutableStateOf(false)
        private set

    private var refreshJob: Job? = null
    private var manualValidationJob: Job? = null

    init {
        removeDeviceChangeListener = deviceManager.addDeviceChangeListener { device ->
            viewModelScope.launch {
                setupDevice(device)
            }
        }
        viewModelScope.launch {
            EarningsDebugFlags.version.drop(1).collect {
                setupDevice(byDevice)
            }
        }
    }

    // ---- refresh

    /** wallets, payout wallet and payments; a call that fails keeps what is shown */
    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            refreshLegacy()
        }
    }

    private suspend fun refreshLegacy() {
        val s = source
        if (!s.available) {
            _legacy.value = LegacyWalletUi()
            _legacyLoaded.value = true
            return
        }
        val (wallets, payoutWalletId, payments) = coroutineScope {
            val walletsRead = async { s.wallets() }
            val payoutWalletIdRead = async { s.payoutWalletId() }
            val paymentsRead = async { s.payments() }
            Triple(walletsRead.await(), payoutWalletIdRead.await(), paymentsRead.await())
        }
        if (source !== s) {
            return
        }
        wallets.onFailure { Log.i(TAG, "account wallets: ${it.message}") }
        payoutWalletId.onFailure { Log.i(TAG, "payout wallet: ${it.message}") }
        payments.onFailure { Log.i(TAG, "account payments: ${it.message}") }
        _legacy.update { ui ->
            LegacyWalletUi(
                wallets = wallets.getOrDefault(ui.wallets),
                payoutWalletId = if (payoutWalletId.isSuccess) payoutWalletId.getOrNull() else ui.payoutWalletId,
                payments = payments.getOrDefault(ui.payments),
            )
        }
        _legacyLoaded.value = true
    }

    // a change supersedes a refresh that started before it, so a stale read cannot land last
    private suspend fun refreshAfterChange() {
        refreshJob?.cancel()
        refreshJob = null
        refreshLegacy()
    }

    // ---- sheet

    fun openSheet() {
        if (_connectState.value.busy) {
            return
        }
        clearManualEntry()
        _sheetStep.value = SolanaSheetStep.CHOOSE
        _connectState.value = SolanaConnectState.Idle
        isPresentedSheet = true
    }

    /** a swipe, a tap outside or back; refused while a wallet app connect or a link is in flight */
    fun closeSheet() {
        if (_connectState.value.busy) {
            return
        }
        clearManualEntry()
        _sheetStep.value = SolanaSheetStep.CHOOSE
        _connectState.value = SolanaConnectState.Idle
        isPresentedSheet = false
    }

    fun showManualStep() {
        if (_connectState.value.busy) {
            return
        }
        _connectState.value = SolanaConnectState.Idle
        _sheetStep.value = SolanaSheetStep.MANUAL
    }

    fun showChooseStep() {
        if (_connectState.value.busy) {
            return
        }
        _connectState.value = SolanaConnectState.Idle
        _sheetStep.value = SolanaSheetStep.CHOOSE
    }

    private fun clearManualEntry() {
        manualValidationJob?.cancel()
        manualValidationJob = null
        manualAddress = TextFieldValue("")
        _manualValidation.value = AddressValidation.Empty
    }

    // ---- wallet app (the Mobile Wallet Adapter call needs the activity, so EarningsScreen makes it)

    /** false when something else is in flight, and the wallet app must not be opened */
    fun onWalletAppConnecting(): Boolean {
        if (_connectState.value.busy) {
            return false
        }
        _connectState.value = SolanaConnectState.ConnectingApp
        return true
    }

    fun onWalletAppConnected(address: String) {
        if (_connectState.value !is SolanaConnectState.ConnectingApp) {
            return
        }
        viewModelScope.launch {
            link(address)
        }
    }

    fun onWalletAppNotFound() {
        if (_connectState.value is SolanaConnectState.ConnectingApp) {
            _connectState.value = SolanaConnectState.NoWalletApp
        }
    }

    fun onWalletAppFailed(detail: String?) {
        val s = _connectState.value
        if (s is SolanaConnectState.ConnectingApp || !s.busy) {
            _connectState.value = SolanaConnectState.Failed(detail)
        }
    }

    /** the connect call ended without an answer (the screen left composition) */
    fun onWalletAppAbandoned() {
        if (_connectState.value is SolanaConnectState.ConnectingApp) {
            _connectState.value = SolanaConnectState.Idle
        }
    }

    fun dismissConnectState() {
        if (!_connectState.value.busy) {
            _connectState.value = SolanaConnectState.Idle
        }
    }

    // ---- manual address entry

    /** local syntax first, then the server check after a pause in typing */
    fun updateManualAddress(value: TextFieldValue) {
        val textChanged = value.text != manualAddress.text
        manualAddress = value
        if (!textChanged) {
            // a cursor or selection move
            return
        }
        if (_connectState.value is SolanaConnectState.Failed) {
            _connectState.value = SolanaConnectState.Idle
        }
        manualValidationJob?.cancel()
        manualValidationJob = null
        val a = value.text.trim()
        if (a.isEmpty()) {
            _manualValidation.value = AddressValidation.Empty
            return
        }
        val s = source
        if (!s.validateSolanaSyntax(a)) {
            _manualValidation.value = AddressValidation.InvalidSyntax
            return
        }
        _manualValidation.value = AddressValidation.Checking
        manualValidationJob = viewModelScope.launch {
            delay(MANUAL_VALIDATION_DEBOUNCE_MILLIS)
            val result = s.validateAddress(a)
            if (source !== s) {
                return@launch
            }
            _manualValidation.value = result.fold(
                onSuccess = { valid -> if (valid) AddressValidation.Ok else AddressValidation.InvalidSyntax },
                onFailure = { e ->
                    Log.i(TAG, "validate solana address: ${e.message}")
                    AddressValidation.Unavailable(e.message)
                },
            )
        }
    }

    fun continueManual() {
        if (_manualValidation.value !is AddressValidation.Ok || _connectState.value.busy) {
            return
        }
        val a = manualAddress.text.trim()
        viewModelScope.launch {
            link(a)
        }
    }

    /**
     * Create (or re-activate) the Solana wallet, then make it the payout wallet. The
     * server sets the payout wallet on create only when the network has none, and a
     * network can already hold another payout wallet, or a Seeker verification row.
     */
    private suspend fun link(address: String) {
        val s = source
        val a = address.trim()
        _connectState.value = SolanaConnectState.Linking(a)

        val walletId = s.addSolanaWallet(a).getOrElse { e ->
            Log.i(TAG, "add solana wallet: ${e.message}")
            if (source === s) {
                _connectState.value = SolanaConnectState.Failed(e.message)
            }
            return
        }
        // a failed read sets it anyway; setting the payout wallet is idempotent
        if (s.payoutWalletId().getOrNull() != walletId) {
            s.setPayoutWallet(walletId).onFailure { e ->
                Log.i(TAG, "set payout wallet: ${e.message}")
                if (source === s) {
                    _connectState.value = SolanaConnectState.Failed(e.message)
                }
                return
            }
        }
        if (source !== s) {
            return
        }

        // show the new payout wallet now; the refresh below replaces it with the server's rows
        val wallet = _legacy.value.wallets.firstOrNull { it.walletId == walletId }
            ?.copy(address = a, chain = LegacyChain.SOLANA)
            ?: LegacyWallet(walletId, a, LegacyChain.SOLANA, hasSeekerToken = false)
        _legacy.update { ui ->
            ui.copy(
                wallets = ui.wallets.filterNot { it.walletId == walletId } + wallet,
                payoutWalletId = walletId,
            )
        }
        _legacyLoaded.value = true
        clearManualEntry()
        _sheetStep.value = SolanaSheetStep.CHOOSE
        _connectState.value = SolanaConnectState.Linked(wallet)
        isPresentedSheet = false

        refreshAfterChange()
    }

    // ---- remove

    fun openRemoveDialog() {
        if (_legacy.value.payoutWallet == null || _connectState.value.busy) {
            return
        }
        _connectState.value = SolanaConnectState.Idle
        isPresentedRemoveDialog = true
    }

    fun closeRemoveDialog() {
        if (_connectState.value is SolanaConnectState.Removing) {
            return
        }
        isPresentedRemoveDialog = false
    }

    /** the server holds USDC payouts until another wallet is connected */
    fun removePayoutWallet() {
        val wallet = _legacy.value.payoutWallet ?: return
        if (_connectState.value.busy) {
            return
        }
        val s = source
        _connectState.value = SolanaConnectState.Removing
        viewModelScope.launch {
            s.removeWallet(wallet.walletId).onFailure { e ->
                Log.i(TAG, "remove wallet: ${e.message}")
                if (source === s) {
                    _connectState.value = SolanaConnectState.Failed(e.message)
                }
                return@launch
            }
            if (source !== s) {
                return@launch
            }
            _legacy.update { ui ->
                ui.copy(
                    wallets = ui.wallets.filterNot { it.walletId == wallet.walletId },
                    payoutWalletId = ui.payoutWalletId.takeIf { it != wallet.walletId },
                )
            }
            _connectState.value = SolanaConnectState.Removed
            isPresentedRemoveDialog = false

            refreshAfterChange()
        }
    }

    // ---- device lifecycle

    private fun setupDevice(device: DeviceLocal?) {
        refreshJob?.cancel()
        refreshJob = null
        manualValidationJob?.cancel()
        manualValidationJob = null

        byDevice = device
        source = when {
            device == null -> NoLegacyWalletSource
            EarningsDebugFlags.useSampleData -> SampleLegacyWalletSource(
                startConnected = EarningsDebugFlags.sampleSolanaWallet,
                pendingUsd = if (EarningsDebugFlags.sampleUsdcWaiting) {
                    SampleLegacyWalletSource.SAMPLE_USDC_WAITING
                } else {
                    0.0
                },
            )
            else -> SdkLegacyWalletSource(device)
        }

        _legacy.value = LegacyWalletUi()
        _legacyLoaded.value = false
        _connectState.value = SolanaConnectState.Idle
        _sheetStep.value = SolanaSheetStep.CHOOSE
        manualAddress = TextFieldValue("")
        _manualValidation.value = AddressValidation.Empty
        isPresentedSheet = false
        isPresentedRemoveDialog = false

        refresh()
    }

    override fun onCleared() {
        removeDeviceChangeListener?.invoke()
        removeDeviceChangeListener = null
        byDevice = null
        super.onCleared()
    }

    companion object {
        private const val TAG = "SolanaWalletViewModel"

        // the same pause the Bittensor manual entry waits
        const val MANUAL_VALIDATION_DEBOUNCE_MILLIS = 350L
    }
}
