package com.bringyour.network.ui.shared.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bringyour.network.DeviceManager
import com.bringyour.network.TAG
import com.bringyour.sdk.ReliabilityWindow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NetworkReliabilityViewModel @Inject constructor(
    private val deviceManager: DeviceManager,
): ViewModel() {

    private var pollingJob: Job? = null
    private var pollingInterval: Long = 30_000 // every 30 seconds

    private val _reliabilityWindow = MutableStateFlow<ReliabilityWindow?>(null)
    val reliabilityWindow: StateFlow<ReliabilityWindow?> = _reliabilityWindow.asStateFlow()

    val fetchReliabilityWindow = {

        deviceManager.device?.api?.getNetworkReliability { result, err ->

            if (err != null) {
                Log.i(TAG, "getNetworkReliability error: ${err.message}")
                return@getNetworkReliability
            }

            if (result.error != null) {
                Log.i(TAG, "getNetworkReliability result error: ${result.error.message}")
                return@getNetworkReliability
            }

            viewModelScope.launch {
                _reliabilityWindow.value = result.reliabilityWindow
            }

        }
    }

    val pollReliabilityWindow: () -> Unit = {
        pollingJob = viewModelScope.launch {
            fetchReliabilityWindow()

            while (true) {
                delay(pollingInterval)
                fetchReliabilityWindow()
            }
        }
    }

    init {
        pollReliabilityWindow()
    }

    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
        pollingJob = null
    }


}