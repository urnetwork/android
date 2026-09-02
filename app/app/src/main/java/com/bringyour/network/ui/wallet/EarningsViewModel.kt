package com.bringyour.network.ui.wallet

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bringyour.network.DeviceManager
import com.bringyour.network.ui.login.BITTENSOR_SIGN_PURPOSE_CONNECT
import com.bringyour.network.ui.login.launchBittensorSignMessage
import com.bringyour.network.ui.login.requestBittensorChallenge
import com.bringyour.sdk.DeviceLocal
import com.bringyour.sdk.VerifySeekerNftHolderArgs
import com.solana.publickey.SolanaPublicKey
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Intent extras the login activity forwards after the ur.io bridge signs a connect challenge. */
object SnWalletConnectExtras {
    const val ADDRESS = "SN_WALLET_CONNECT_ADDRESS"
    const val SIGNATURE = "SN_WALLET_CONNECT_SIGNATURE"
    const val MESSAGE = "SN_WALLET_CONNECT_MESSAGE"
    const val ERROR = "SN_WALLET_CONNECT_ERROR"
}

sealed class WalletConnectState {
    object Idle : WalletConnectState()
    object RequestingChallenge : WalletConnectState()
    object AwaitingSignature : WalletConnectState()
    data class Validating(val address: String) : WalletConnectState()
    object InvalidAddress : WalletConnectState()
    data class LooksNew(
        val address: String,
        val signature: String,
        val message: String,
        val detail: String?,
    ) : WalletConnectState()
    data class Blocked(val detail: String?) : WalletConnectState()
    data class Connecting(val address: String) : WalletConnectState()
    data class Connected(val wallet: SnWalletState) : WalletConnectState()
    data class Failed(val detail: String?) : WalletConnectState()

    val busy: Boolean
        get() = this is RequestingChallenge || this is AwaitingSignature || this is Validating || this is Connecting
}

/** Validation of a manually entered address, in the order the checks run. */
sealed class AddressValidation {
    object Empty : AddressValidation()
    object InvalidSyntax : AddressValidation()
    object Checking : AddressValidation()
    object Ok : AddressValidation()
    data class LooksNew(val detail: String?) : AddressValidation()
    data class Blocked(val detail: String?) : AddressValidation()
    data class Unavailable(val detail: String?) : AddressValidation()

    val canContinue: Boolean
        get() = this is Ok || this is LooksNew
}

enum class ClaimProgressStatus { PENDING, SENT, CONFIRMED, FAILED }

data class ClaimProgress(
    val epoch: Long,
    val amountRao: Long,
    val status: ClaimProgressStatus,
    val txHash: String?,
    val message: String?,
)

sealed class ClaimDialogState {
    object Loading : ClaimDialogState()
    data class Ready(
        val claims: List<EpochClaim>,
        val totalRao: Long,
        val gasKey: SnGasKeyState?,
        val gasTao: Double?,
    ) : ClaimDialogState()
    data class NeedsGas(
        val gasKey: SnGasKeyState?,
        val gasTao: Double,
        val totalRao: Long,
    ) : ClaimDialogState()
    data class Sending(val progress: List<ClaimProgress>) : ClaimDialogState()
    data class Finished(val progress: List<ClaimProgress>, val confirmedRao: Long) : ClaimDialogState()
    data class Unavailable(val detail: String?) : ClaimDialogState()
    object NoWallet : ClaimDialogState()
}

@HiltViewModel
class EarningsViewModel @Inject constructor(
    private val deviceManager: DeviceManager,
) : ViewModel() {

    private var byDevice: DeviceLocal? = null
    private var removeDeviceChangeListener: (() -> Unit)? = null
    private var removeWalletListener: (() -> Unit)? = null
    private var source: EarningsProtocolSource = NoProtocolSource

    // ---- wallet

    private val _wallet = MutableStateFlow<SnWalletState?>(null)
    val wallet: StateFlow<SnWalletState?> = _wallet.asStateFlow()

    // the first read finished, so the UI can tell "no wallet" from "not loaded yet"
    private val _walletLoaded = MutableStateFlow(false)
    val walletLoaded: StateFlow<Boolean> = _walletLoaded.asStateFlow()

    private val _connectState = MutableStateFlow<WalletConnectState>(WalletConnectState.Idle)
    val connectState: StateFlow<WalletConnectState> = _connectState.asStateFlow()

    // set when the bridge returns; the nav host opens the earnings screen and clears it
    private val _pendingEarningsNavigation = MutableStateFlow(false)
    val pendingEarningsNavigation: StateFlow<Boolean> = _pendingEarningsNavigation.asStateFlow()

    // ---- manual address entry

    var manualAddress by mutableStateOf(TextFieldValue(""))
        private set

    private val _manualValidation = MutableStateFlow<AddressValidation>(AddressValidation.Empty)
    val manualValidation: StateFlow<AddressValidation> = _manualValidation.asStateFlow()

    var isPresentedManualSheet by mutableStateOf(false)
        private set

    private var manualValidationJob: Job? = null

    // ---- protocol data

    private val _gasKey = MutableStateFlow<SnGasKeyState?>(null)
    val gasKey: StateFlow<SnGasKeyState?> = _gasKey.asStateFlow()

    private val _gasBalance = MutableStateFlow<SnGasBalanceState?>(null)
    val gasBalance: StateFlow<SnGasBalanceState?> = _gasBalance.asStateFlow()

    private val _claims = MutableStateFlow<List<EpochClaim>>(emptyList())
    val claims: StateFlow<List<EpochClaim>> = _claims.asStateFlow()

    private val _totalClaimableRao = MutableStateFlow(0L)
    val totalClaimableRao: StateFlow<Long> = _totalClaimableRao.asStateFlow()

    private val _claimsError = MutableStateFlow<String?>(null)
    val claimsError: StateFlow<String?> = _claimsError.asStateFlow()

    private val _epochs = MutableStateFlow<List<AccountEpoch>>(emptyList())
    val epochs: StateFlow<List<AccountEpoch>> = _epochs.asStateFlow()

    private val _epochsLoaded = MutableStateFlow(false)
    val epochsLoaded: StateFlow<Boolean> = _epochsLoaded.asStateFlow()

    private val _head = MutableStateFlow<SnHeadState?>(null)
    val head: StateFlow<SnHeadState?> = _head.asStateFlow()

    private val _claimDialog = MutableStateFlow<ClaimDialogState?>(null)
    val claimDialog: StateFlow<ClaimDialogState?> = _claimDialog.asStateFlow()

    var isRefreshing by mutableStateOf(false)
        private set

    val protocolAvailable: Boolean
        get() = source.available

    // ---- seeker (points multiplier only; no effect on alpha)

    private val _isSeekerHolder = MutableStateFlow(false)
    val isSeekerHolder: StateFlow<Boolean> = _isSeekerHolder.asStateFlow()

    var isVerifyingSeekerHolder by mutableStateOf(false)
        private set

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

    // ---- formatting (the SDK owns the math once linked)

    fun formatAlpha(rao: Long): String = source.formatAlpha(rao)

    fun formatShareBps(shareBps: Long): String = source.formatShareBps(shareBps)

    fun shortSs58(address: String): String = source.shortSs58(address)

    fun explorerTxUrl(txHash: String): String = source.explorerTxUrl(txHash)

    // ---- refresh

    val refresh: () -> Unit = {
        viewModelScope.launch {
            refreshAll(userInitiated = true)
        }
    }

    private suspend fun refreshAll(userInitiated: Boolean) {
        if (userInitiated) {
            isRefreshing = true
        }
        try {
            coroutineScope {
                launch { refreshWallet() }
                launch { refreshEpochs() }
                launch { refreshHead() }
            }
            if (_wallet.value != null) {
                refreshClaims()
            }
        } finally {
            if (userInitiated) {
                isRefreshing = false
            }
        }
    }

    private suspend fun refreshWallet() {
        val s = source
        if (!s.available) {
            _wallet.value = null
            _walletLoaded.value = true
            return
        }
        // the cached copy first, then the server's
        s.currentWallet()?.let { _wallet.value = it }
        s.fetchWallet()
            .onSuccess { _wallet.value = it }
            .onFailure { Log.i(TAG, "fetch wallet: ${it.message}") }
        _walletLoaded.value = true
    }

    private suspend fun refreshEpochs() {
        source.accountEpochs()
            .onSuccess { _epochs.value = it.sortedByDescending { e -> e.epoch } }
            .onFailure { Log.i(TAG, "account epochs: ${it.message}") }
        _epochsLoaded.value = true
    }

    private suspend fun refreshHead() {
        source.head()
            .onSuccess { _head.value = it }
            .onFailure { Log.i(TAG, "head: ${it.message}") }
    }

    private suspend fun refreshClaims() {
        val s = source
        if (!s.available || _wallet.value == null) {
            _claims.value = emptyList()
            _totalClaimableRao.value = 0
            return
        }
        _gasKey.value = s.gasKey()
        s.gasBalance()
            .onSuccess { _gasBalance.value = it }
            .onFailure { Log.i(TAG, "gas balance: ${it.message}") }
        s.claims()
            .onSuccess {
                _claims.value = it.claims
                _totalClaimableRao.value = it.totalClaimableRao
                _claimsError.value = null
            }
            .onFailure {
                Log.i(TAG, "claims: ${it.message}")
                _claimsError.value = it.message
            }
    }

    // ---- wallet connect through the ur.io bridge

    /**
     * Ask the server for a single-use challenge and open the bridge to sign it with a
     * Bittensor wallet. `address` binds the challenge to a manually entered coldkey.
     */
    fun connectWithBridge(context: Context, address: String? = null) {
        val api = byDevice?.api
        if (api == null) {
            _connectState.value = WalletConnectState.Failed(null)
            return
        }
        _connectState.value = WalletConnectState.RequestingChallenge
        viewModelScope.launch {
            requestBittensorChallenge(api, address)
                .onSuccess { message ->
                    val launched = launchBittensorSignMessage(context, message, BITTENSOR_SIGN_PURPOSE_CONNECT)
                    _connectState.value = if (launched) {
                        WalletConnectState.AwaitingSignature
                    } else {
                        WalletConnectState.Failed(null)
                    }
                }
                .onFailure {
                    Log.i(TAG, "wallet challenge: ${it.message}")
                    _connectState.value = WalletConnectState.Failed(it.message)
                }
        }
    }

    /** The bridge came back with a signed challenge (forwarded by the login activity). */
    fun onWalletSigned(address: String, signature: String, message: String) {
        _pendingEarningsNavigation.value = true
        viewModelScope.launch {
            awaitDevice()
            validateAndConnect(address, signature, message)
        }
    }

    fun onWalletSignFailed(detail: String?) {
        _pendingEarningsNavigation.value = true
        _connectState.value = WalletConnectState.Failed(detail)
    }

    fun consumeEarningsNavigation() {
        _pendingEarningsNavigation.value = false
    }

    fun dismissConnectState() {
        _connectState.value = WalletConnectState.Idle
    }

    /**
     * The screen came back without a signed redirect (the user closed the bridge tab):
     * a completed signature arrives through a fresh MainActivity, so waiting is over.
     */
    fun onScreenResumed() {
        val s = _connectState.value
        if (s is WalletConnectState.AwaitingSignature || s is WalletConnectState.RequestingChallenge) {
            _connectState.value = WalletConnectState.Idle
        }
    }

    fun continueAfterLooksNew() {
        val s = _connectState.value as? WalletConnectState.LooksNew ?: return
        viewModelScope.launch {
            connect(s.address, s.signature, s.message)
        }
    }

    private suspend fun awaitDevice() {
        var attempts = 0
        while (byDevice == null && attempts < 50) {
            delay(100)
            attempts += 1
        }
    }

    /**
     * Every address goes through the same gate before anything else: local syntax,
     * then the unauthenticated server check (blocked wallets are never sent anywhere).
     */
    private suspend fun validateAndConnect(address: String, signature: String, message: String) {
        val a = address.trim()
        val s = source
        if (!s.validateSs58(a)) {
            _connectState.value = WalletConnectState.InvalidAddress
            return
        }
        _connectState.value = WalletConnectState.Validating(a)
        val validation = s.validateWallet(a).getOrElse { e ->
            Log.i(TAG, "validate wallet: ${e.message}")
            _connectState.value = WalletConnectState.Failed(e.message)
            return
        }
        when {
            validation.banned -> _connectState.value = WalletConnectState.Blocked(validation.message)
            !validation.validSyntax -> _connectState.value = WalletConnectState.InvalidAddress
            !validation.existsOnChain ->
                _connectState.value = WalletConnectState.LooksNew(a, signature, message, validation.message)
            else -> connect(a, signature, message)
        }
    }

    private suspend fun connect(address: String, signature: String, message: String) {
        _connectState.value = WalletConnectState.Connecting(address)
        source.connectWallet(address, signature, message)
            .onSuccess { w ->
                _wallet.value = w
                _walletLoaded.value = true
                _connectState.value = WalletConnectState.Connected(w)
                refreshClaims()
            }
            .onFailure {
                Log.i(TAG, "connect wallet: ${it.message}")
                _connectState.value = WalletConnectState.Failed(it.message)
            }
    }

    // ---- manual address entry (validated, then signed through the bridge)

    fun openManualSheet() {
        manualAddress = TextFieldValue("")
        _manualValidation.value = AddressValidation.Empty
        isPresentedManualSheet = true
    }

    fun closeManualSheet() {
        manualValidationJob?.cancel()
        manualValidationJob = null
        isPresentedManualSheet = false
    }

    fun updateManualAddress(value: TextFieldValue) {
        manualAddress = value
        manualValidationJob?.cancel()
        val a = value.text.trim()
        if (a.isEmpty()) {
            _manualValidation.value = AddressValidation.Empty
            return
        }
        if (!source.validateSs58(a)) {
            _manualValidation.value = AddressValidation.InvalidSyntax
            return
        }
        _manualValidation.value = AddressValidation.Checking
        manualValidationJob = viewModelScope.launch {
            delay(350)
            source.validateWallet(a)
                .onSuccess { v ->
                    _manualValidation.value = when {
                        v.banned -> AddressValidation.Blocked(v.message)
                        !v.validSyntax -> AddressValidation.InvalidSyntax
                        !v.existsOnChain -> AddressValidation.LooksNew(v.message)
                        else -> AddressValidation.Ok
                    }
                }
                .onFailure {
                    _manualValidation.value = AddressValidation.Unavailable(it.message)
                }
        }
    }

    fun continueManual(context: Context) {
        if (!_manualValidation.value.canContinue) {
            return
        }
        val a = manualAddress.text.trim()
        closeManualSheet()
        connectWithBridge(context, a)
    }

    // ---- claims (the SDK signs and sends; the app only shows progress)

    fun openClaimDialog() {
        if (_wallet.value == null) {
            _claimDialog.value = ClaimDialogState.NoWallet
            return
        }
        _claimDialog.value = ClaimDialogState.Loading
        viewModelScope.launch {
            loadClaimDialog()
        }
    }

    fun closeClaimDialog() {
        _claimDialog.value = null
    }

    fun retryClaimDialog() {
        if (_claimDialog.value == null) {
            return
        }
        _claimDialog.value = ClaimDialogState.Loading
        viewModelScope.launch {
            loadClaimDialog()
        }
    }

    private suspend fun loadClaimDialog() {
        refreshClaims()
        if (_claimDialog.value == null) {
            return
        }
        val error = _claimsError.value
        if (error != null) {
            _claimDialog.value = ClaimDialogState.Unavailable(error)
            return
        }
        val claimable = _claims.value.filter { it.status == EpochClaimStatus.CLAIMABLE }
        val total = claimable.sumOf { it.amountRao }
        val gasTao = _gasBalance.value?.tao
        _claimDialog.value = if (claimable.isNotEmpty() && gasTao != null && gasTao < MIN_GAS_TAO) {
            ClaimDialogState.NeedsGas(_gasKey.value, gasTao, total)
        } else {
            ClaimDialogState.Ready(claimable, total, _gasKey.value, gasTao)
        }
    }

    fun claimAll() {
        val ready = _claimDialog.value as? ClaimDialogState.Ready ?: return
        if (ready.claims.isEmpty()) {
            return
        }
        val progress = ready.claims.map {
            ClaimProgress(it.epoch, it.amountRao, ClaimProgressStatus.PENDING, null, null)
        }
        _claimDialog.value = ClaimDialogState.Sending(progress)
        source.claim(ready.claims.map { it.epoch }) { event ->
            viewModelScope.launch {
                onClaimEvent(event)
            }
        }
    }

    private suspend fun onClaimEvent(event: ClaimEvent) {
        val sending = _claimDialog.value as? ClaimDialogState.Sending ?: return
        when (event) {
            is ClaimEvent.Sent -> _claimDialog.value = ClaimDialogState.Sending(
                sending.progress.map {
                    if (it.epoch == event.epoch) it.copy(status = ClaimProgressStatus.SENT, txHash = event.txHash) else it
                }
            )
            is ClaimEvent.Confirmed -> _claimDialog.value = ClaimDialogState.Sending(
                sending.progress.map {
                    if (it.epoch == event.epoch) {
                        it.copy(status = ClaimProgressStatus.CONFIRMED, txHash = event.txHash, amountRao = event.amountRao)
                    } else {
                        it
                    }
                }
            )
            is ClaimEvent.Failed -> _claimDialog.value = ClaimDialogState.Sending(
                sending.progress.map {
                    if (it.epoch == event.epoch) it.copy(status = ClaimProgressStatus.FAILED, message = event.message) else it
                }
            )
            ClaimEvent.Done -> {
                val progress = sending.progress.map {
                    if (it.status == ClaimProgressStatus.PENDING) {
                        it.copy(status = ClaimProgressStatus.FAILED)
                    } else {
                        it
                    }
                }
                val allNeedGas = progress.isNotEmpty() && progress.all {
                    it.status == ClaimProgressStatus.FAILED && claimFailureCode(it.message) == SN_CODE_NEEDS_GAS
                }
                if (allNeedGas) {
                    val gasTao = source.gasBalance().getOrNull()?.tao ?: 0.0
                    _gasBalance.update { it?.copy(tao = gasTao) ?: SnGasBalanceState("0", gasTao) }
                    _claimDialog.value = ClaimDialogState.NeedsGas(_gasKey.value, gasTao, progress.sumOf { it.amountRao })
                } else {
                    _claimDialog.value = ClaimDialogState.Finished(
                        progress,
                        progress.filter { it.status == ClaimProgressStatus.CONFIRMED }.sumOf { it.amountRao }
                    )
                }
                refreshClaims()
            }
        }
    }

    // ---- seeker

    val verifySeekerHolder: (
        SolanaPublicKey,
        String,
        String,
        (String) -> Unit
    ) -> Unit = verifySeekerHolder@{ publicKey, message, signature, onError ->

        if (isVerifyingSeekerHolder) {
            return@verifySeekerHolder
        }
        isVerifyingSeekerHolder = true
        val device = byDevice
        val api = device?.api
        if (device == null || api == null) {
            isVerifyingSeekerHolder = false
            return@verifySeekerHolder
        }

        val args = VerifySeekerNftHolderArgs()
        args.publicKey = publicKey.address
        args.signature = signature
        args.message = message

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
                } else {
                    val errorMessage = result?.error?.message
                        ?: "No Seeker NFT found in wallet ...${publicKey.address.takeLast(7)}"
                    onError(errorMessage)
                }
                isVerifyingSeekerHolder = false
            }
        }
    }

    // the verified Seeker flag is stored server-side with the account's wallets
    private fun restoreSeekerHolder(device: DeviceLocal) {
        val api = device.api ?: return
        api.getAccountWallets { result, error ->
            if (error != null || result == null) {
                return@getAccountWallets
            }
            val wallets = result.wallets
            val n = wallets?.len() ?: 0
            var holder = false
            for (i in 0 until n) {
                if (wallets.get(i).hasSeekerToken) {
                    holder = true
                }
            }
            viewModelScope.launch {
                if (byDevice === device) {
                    _isSeekerHolder.value = holder
                }
            }
        }
    }

    // ---- device lifecycle

    private fun setupDevice(device: DeviceLocal?) {
        removeWalletListener?.invoke()
        removeWalletListener = null
        byDevice = device
        source = if (device == null) NoProtocolSource else createProtocolSource(device)

        _wallet.value = null
        _walletLoaded.value = false
        _gasKey.value = null
        _gasBalance.value = null
        _claims.value = emptyList()
        _totalClaimableRao.value = 0
        _claimsError.value = null
        _epochs.value = emptyList()
        _epochsLoaded.value = false
        _head.value = null
        _claimDialog.value = null
        _isSeekerHolder.value = false

        if (device == null) {
            _walletLoaded.value = true
            _epochsLoaded.value = true
            return
        }

        removeWalletListener = source.addWalletListener { w ->
            viewModelScope.launch {
                _wallet.value = w
                _walletLoaded.value = true
                refreshClaims()
            }
        }
        restoreSeekerHolder(device)
        viewModelScope.launch {
            refreshAll(userInitiated = false)
        }
    }

    private fun createProtocolSource(device: DeviceLocal): EarningsProtocolSource {
        if (EarningsDebugFlags.useSampleData) {
            return SampleProtocolSource(
                gasUnfunded = EarningsDebugFlags.sampleGasUnfunded,
                startConnected = !EarningsDebugFlags.sampleStartDisconnected,
            )
        }
        return SdkProtocolSource.create(device)
    }

    override fun onCleared() {
        removeDeviceChangeListener?.invoke()
        removeDeviceChangeListener = null
        removeWalletListener?.invoke()
        removeWalletListener = null
        byDevice = null
        super.onCleared()
    }

    companion object {
        private const val TAG = "EarningsViewModel"

        // SDK error codes (sdk/sn_util.go); the store has a string under the same name for each
        const val SN_CODE_NEEDS_GAS = "needs_gas"
        const val SN_CODE_EXPIRED = "claims_for_epoch_expired"
        const val SN_CODE_ALREADY_CLAIMED = "already_claimed"
        const val SN_CODE_RPC_UNREACHABLE = "chain_rpc_unreachable"
        const val SN_CODE_CHAIN_NOT_CONFIGURED = "chain_not_configured"

        /** SDK claim failures arrive as "<code>: <reason>"; the sample source sends bare codes. */
        fun claimFailureCode(message: String?): String? =
            message?.substringBefore(':')?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }

        // below this the vault call cannot pay for itself; the SDK reports "needs gas" too
        const val MIN_GAS_TAO = 0.002

        // what the funding hint suggests sending to the mirror address
        const val SUGGESTED_GAS_TAO = 0.01
    }
}
