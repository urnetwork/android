package com.bringyour.network.ui.shared.viewmodels

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bringyour.network.DeviceManager
import com.bringyour.network.TAG
import com.bringyour.sdk.SubscriptionBalanceCallback
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlinx.coroutines.isActive

@HiltViewModel
class SubscriptionBalanceViewModel @Inject constructor(
    deviceManager: DeviceManager,
): ViewModel() {

    private val _currentPlan = MutableStateFlow(Plan.Basic)
    val currentPlan: StateFlow<Plan> get() = _currentPlan

    private val _currentStore = MutableStateFlow<String?>(null)
    val currentStore: StateFlow<String?> get() = _currentStore

    private val _isInitialized = MutableStateFlow<Boolean>(false)
    val isInitialized: StateFlow<Boolean> get() = _isInitialized

    /**
     * When actively polling for plan subscription change
     */
    private var pollingJob: Job? = null
    private var pollingInterval: Long = 5000 // 5 seconds

    /**
     * Background polling for available bytes
     */
    private var backgroundPollingJob: Job? = null


    val setCurrentPlan: (Plan) -> Unit = { plan ->
        _currentPlan.value = plan
    }

    var isPollingSubscriptionBalance by mutableStateOf(false)
        private set

    private val _isCheckingSolanaTransaction = MutableStateFlow<Boolean>(false)
    val isCheckingSolanaTransaction: StateFlow<Boolean> = _isCheckingSolanaTransaction.asStateFlow()

    val isPolling: Boolean
        get() = _isCheckingSolanaTransaction.value || isPollingSubscriptionBalance


    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _availableBalanceByteCount = MutableStateFlow<Long>(0)
    val availableBalanceByteCount: StateFlow<Long> get() = _availableBalanceByteCount

    var pendingBalanceByteCount by mutableLongStateOf(0)
        private set

    var usedBalanceByteCount by mutableLongStateOf(0)
        private set

    var isRefreshingSubscriptionBalance by mutableStateOf(false)
        private set

    private val _errorFetchingSubscriptionBalance = MutableStateFlow(false)
    val errorFetchingSubscriptionBalance: StateFlow<Boolean> = _errorFetchingSubscriptionBalance

    val setErrorReachingSubscriptionBalance: (Boolean) -> Unit = {
        _errorFetchingSubscriptionBalance.value = it
    }

    val refreshSubscriptionBalance: () -> Unit = {
        if (!isRefreshingSubscriptionBalance) {
            isRefreshingSubscriptionBalance = true
            fetchSubscriptionBalance()
        }
    }

    val fetchSubscriptionBalance: () -> Unit = {

        if (!_isLoading.value) {

            _isLoading.value = true

            deviceManager.device?.api?.subscriptionBalance( SubscriptionBalanceCallback { result, err ->

                viewModelScope.launch {
                    if (err != null) {
                        Log.i(TAG, "error fetching subscription balance: $err")

                        _isLoading.value = false
                        isRefreshingSubscriptionBalance = false
                        _errorFetchingSubscriptionBalance.value = true

                    } else {

                        result?.currentSubscription?.plan?.let { plan ->
                            setCurrentPlan(Plan.fromString(plan))
                        } ?: setCurrentPlan(Plan.Basic)

                        result?.currentSubscription?.store.let { store ->
                            _currentStore.value = store
                        }

                        _availableBalanceByteCount.value = result.balanceByteCount
                        pendingBalanceByteCount = result.openTransferByteCount
                        usedBalanceByteCount = result.startBalanceByteCount - result.balanceByteCount - pendingBalanceByteCount
                        _errorFetchingSubscriptionBalance.value = false
                        _isLoading.value = false
                    }

                    isRefreshingSubscriptionBalance = false
                    if (!_isInitialized.value) {
                        _isInitialized.value = true
                    }
                }

            })

        }

    }

    private fun isSupporterWithBalance(): Boolean {
        return currentPlan.value == Plan.Supporter && _availableBalanceByteCount.value > 0
    }

    /**
     * This is used when we have evidence of a payment (ie Stripe, Apple, Play)
     */
    fun pollSubscriptionBalance(maxDurationMs: Long = 120_000L) {
        if (isPolling) return

        isPollingSubscriptionBalance = true

        createPollingJob(maxDurationMs)
    }

    /**
     * When we regain focus from a wallet, and there is a solana payment reference id (in SolanaPaymentViewModel), start polling
     * This is different than pollSubscriptionBalance, as do not know if the user submitted a transaction or not
     * So we want to display a different pending message, and poll for a little less time
     */
    fun pollSolanaTransaction(maxDurationMs: Long = 20_000L) {
        if (isPolling) return

        _isCheckingSolanaTransaction.value = true

        createPollingJob(maxDurationMs)
    }

    val createPollingJob: (maxDurationMs: Long) -> Unit = { maxDurationMs ->
        pollingJob = viewModelScope.launch {
            val deadline = System.currentTimeMillis() + maxDurationMs

            fetchSubscriptionBalance()
            if (isSupporterWithBalance()) {
                stopPolling()
                return@launch
            }

            while (isPolling && isActive && System.currentTimeMillis() < deadline) {

                Log.i(TAG, "System.currentTimeMillis(): ${System.currentTimeMillis()}")
                Log.i(TAG, "deadline: $deadline")

                delay(pollingInterval)
                fetchSubscriptionBalance()
                if (isSupporterWithBalance()) {
                    stopPolling()
                    break
                }
            }

            if (isPolling) {
                Log.i(TAG, "polling timed out after ${maxDurationMs}ms")
                stopPolling()
            }
        }
    }

    val createBackgroundPollingJob: () -> Unit = {
        backgroundPollingJob = viewModelScope.launch {

            fetchSubscriptionBalance()

            while (true) {
                delay(60_000) // poll every minute
                fetchSubscriptionBalance()
                if (isSupporterWithBalance()) {
                    stopBackgroundPolling()
                    break
                }
            }
        }
    }

    private fun stopBackgroundPolling() {
        viewModelScope.launch {
            backgroundPollingJob?.cancel()
            backgroundPollingJob = null
        }
    }

    private fun stopPolling() {
        viewModelScope.launch {
            pollingJob?.cancel()
            pollingJob = null
            isPollingSubscriptionBalance = false
            _isCheckingSolanaTransaction.value = false
        }
    }

    init {
        createBackgroundPollingJob()
    }

    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
        pollingJob = null
        backgroundPollingJob?.cancel()
        backgroundPollingJob = null
    }

}
