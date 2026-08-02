package com.bringyour.network.ui.shared.viewmodels

import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bringyour.network.DeviceManager
import com.bringyour.network.ForegroundWorkOwner
import com.bringyour.network.TAG
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReferralCodeViewModel @Inject constructor(
    deviceManager: DeviceManager,
): ViewModel(), DefaultLifecycleObserver {

    private var pollingJob: Job? = null
    private var pollingInterval: Long = 30_000 // every 30 seconds
    private val processLifecycle = ProcessLifecycleOwner.get().lifecycle
    private val foregroundWork = ForegroundWorkOwner(
        start = { pollReferralCode() },
        stop = { stopPolling() },
    )

    private val _referralCode = MutableStateFlow<String>("")
    val referralCode: StateFlow<String> = _referralCode.asStateFlow()

    private val _totalReferralCount = MutableStateFlow<Long>(0)
    val totalReferralCount: StateFlow<Long> = _totalReferralCount.asStateFlow()

    val pollReferralCode: () -> Unit = {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            fetchReferralCode()

            while (isActive) {
                delay(pollingInterval)
                fetchReferralCode()
            }
        }
    }

    val fetchReferralCode: () -> Unit = {
        deviceManager.device?.api?.getNetworkReferralCode { result, error ->

            if (error != null) {
                Log.i(TAG, "Error getNetworkReferralCode: $error")
                return@getNetworkReferralCode
            }

            if (result.error != null) {
                Log.i(TAG, "Result error getNetworkReferralCode: ${result.error.message}")
                return@getNetworkReferralCode
            }

            viewModelScope.launch {
                _referralCode.value = result.referralCode
                _totalReferralCount.value = result.totalReferrals
            }
        }
    }

    init {
        processLifecycle.addObserver(this)
        foregroundWork.setForeground(
            processLifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        )
    }

    override fun onStart(owner: LifecycleOwner) {
        foregroundWork.setForeground(true)
    }

    override fun onStop(owner: LifecycleOwner) {
        foregroundWork.setForeground(false)
    }

    private fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    override fun onCleared() {
        processLifecycle.removeObserver(this)
        foregroundWork.close()
        super.onCleared()
    }

}
