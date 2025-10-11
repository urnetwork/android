package com.bringyour.network.ui.shared.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bringyour.network.DeviceManager
import com.bringyour.network.TAG
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReferralCodeViewModel @Inject constructor(
    deviceManager: DeviceManager,
): ViewModel() {

    private var pollingJob: Job? = null
    private var pollingInterval: Long = 30_000 // every 30 seconds

    private val _referralCode = MutableStateFlow<String>("")
    val referralCode: StateFlow<String> = _referralCode.asStateFlow()

    private val _totalReferralCount = MutableStateFlow<Long>(0)
    val totalReferralCount: StateFlow<Long> = _totalReferralCount.asStateFlow()

    val pollReferralCode: () -> Unit = {
        pollingJob = viewModelScope.launch {
            fetchReferralCode()

            while (true) {
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
        pollReferralCode()
    }

    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
        pollingJob = null
    }

}