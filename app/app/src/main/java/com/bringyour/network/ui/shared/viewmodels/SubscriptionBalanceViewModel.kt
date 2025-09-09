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
import com.bringyour.sdk.ReliabilityWindow
import com.bringyour.sdk.SubscriptionBalanceCallback
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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

    private var pollingJob: Job? = null
    private var pollingInterval: Long = 5000 // 5 seconds


    val setCurrentPlan: (Plan) -> Unit = { plan ->
        _currentPlan.value = plan
    }

    var isPollingSubscriptionBalance by mutableStateOf(false)
        private set

    private val _isCheckingSolanaTransaction = MutableStateFlow<Boolean>(false)
    val isCheckingSolanaTransaction: StateFlow<Boolean> = _isCheckingSolanaTransaction.asStateFlow()


    val isPolling: Boolean
        get() = _isCheckingSolanaTransaction.value || isPollingSubscriptionBalance



    private var isLoading = false

    var availableBalanceByteCount by mutableLongStateOf(0)
        private set

    var pendingBalanceByteCount by mutableLongStateOf(0)
        private set

    var usedBalanceByteCount by mutableLongStateOf(0)
        private set

    var isRefreshingSubscriptionBalance by mutableStateOf(false)
        private set

    val refreshSubscriptionBalance: () -> Unit = {
        if (!isRefreshingSubscriptionBalance) {
            isRefreshingSubscriptionBalance = true
            fetchSubscriptionBalance()
        }
    }

    val fetchSubscriptionBalance: () -> Unit = {

        Log.i(TAG, "fetchSubscriptionBalance called")

        if (!isLoading) {

            isLoading = true

            deviceManager.device?.api?.subscriptionBalance( SubscriptionBalanceCallback { result, err ->

                viewModelScope.launch {
                    if (err != null) {
                        Log.i(TAG, "error fetching subscription balance: $err")
                    } else {

                        result?.currentSubscription?.plan?.let { plan ->
                            setCurrentPlan(Plan.fromString(plan))
                        } ?: setCurrentPlan(Plan.Basic)

                        result?.currentSubscription?.store.let { store ->
                            _currentStore.value = store
                        }

                        availableBalanceByteCount = result.balanceByteCount
                        pendingBalanceByteCount = result.openTransferByteCount
                        usedBalanceByteCount = result.startBalanceByteCount - availableBalanceByteCount - pendingBalanceByteCount

                    }

                    isLoading = false
                    isRefreshingSubscriptionBalance = false
                }

            })

        }

    }

    private fun isSupporterWithBalance(): Boolean {
        return currentPlan.value == Plan.Supporter && availableBalanceByteCount > 0
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

    private fun stopPolling() {
        viewModelScope.launch {
            pollingJob?.cancel()
            pollingJob = null
            isPollingSubscriptionBalance = false
            _isCheckingSolanaTransaction.value = false
        }
    }

    init {
        fetchSubscriptionBalance()
    }

}
