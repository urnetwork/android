package com.bringyour.network.ui.shared.viewmodels

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bringyour.network.DeviceManager
import com.bringyour.network.TAG
import com.bringyour.sdk.SubscriptionBalanceCallback
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

    private var pollingJob: Job? = null
    private var pollingInterval: Long = 5000 // 5 seconds in milliseconds


    val setCurrentPlan: (Plan) -> Unit = { plan ->
        _currentPlan.value = plan
    }

    var isPollingSubscriptionBalance by mutableStateOf(false)
        private set

    private var isLoading = false

    private var activeBalanceByteCount: Long = 0

    val fetchSubscriptionBalance: () -> Unit = {

        if (!isLoading) {

            isLoading = true

            deviceManager.device?.api?.subscriptionBalance( SubscriptionBalanceCallback { result, err ->

                runBlocking(Dispatchers.Main.immediate) {

                    if (err != null) {
                        Log.i(TAG, "error fetching subscription balance: $err")
                    } else {

                        result?.currentSubscription?.plan?.let { plan ->
                            setCurrentPlan(Plan.fromString(plan))
                            Log.i(TAG, "current plan: $plan")
                        } ?: setCurrentPlan(Plan.Basic)

                        activeBalanceByteCount = result.balanceByteCount
                    }

                    isLoading = false

                }

            })

        }


    }

    private fun isSupporterWithBalance(): Boolean {
        return currentPlan.value == Plan.Supporter && activeBalanceByteCount > 0
    }

    val pollSubscriptionBalance: () -> Unit = {

        if (!isPollingSubscriptionBalance) {

            isPollingSubscriptionBalance = true

            // Start polling in a coroutine
            pollingJob = viewModelScope.launch {
                // Initial fetch
                fetchSubscriptionBalance()

                // Check if we can stop polling
                if (isSupporterWithBalance()) {
                    stopPolling()
                    return@launch
                }

                // Continue polling at intervals
                while (isPollingSubscriptionBalance && isActive) {
                    delay(pollingInterval)
                    fetchSubscriptionBalance()

                    if (isSupporterWithBalance()) {
                        stopPolling()
                        break
                    }
                }
            }
        }
    }

    private fun stopPolling() {
        viewModelScope.launch {
            pollingJob?.cancel()
            pollingJob = null
            isPollingSubscriptionBalance = false
        }
    }

    init {
        fetchSubscriptionBalance()
    }

}
