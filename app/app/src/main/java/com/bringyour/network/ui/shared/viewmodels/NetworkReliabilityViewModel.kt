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
import com.bringyour.sdk.ReliabilityWindow
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
class NetworkReliabilityViewModel @Inject constructor(
    private val deviceManager: DeviceManager,
): ViewModel(), DefaultLifecycleObserver {

    private var pollingJob: Job? = null
    private var pollingInterval: Long = 30_000 // every 30 seconds
    private val processLifecycle = ProcessLifecycleOwner.get().lifecycle
    private val foregroundWork = ForegroundWorkOwner(
        start = { pollReliabilityWindow() },
        stop = { stopPolling() },
    )

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
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            fetchReliabilityWindow()

            while (isActive) {
                delay(pollingInterval)
                fetchReliabilityWindow()
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
