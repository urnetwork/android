package com.bringyour.network.ui.stats

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bringyour.network.DeviceManager
import com.bringyour.network.ForegroundDeviceControllerOwner
import com.bringyour.sdk.DeviceLocal
import com.bringyour.sdk.Sub
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Publishes the device ad and tracker blocker toggle and applies edits.
 * The device persists the toggle to local settings and restores it at
 * creation, so this only reads and writes the live device state.
 */
@HiltViewModel
class BlockerViewModel @Inject constructor(
    private val deviceManager: DeviceManager,
) : ViewModel(), DefaultLifecycleObserver {

    private val subs = mutableListOf<Sub>()
    private var subscribedDevice: DeviceLocal? = null
    private var removeDeviceChangeListener: (() -> Unit)? = null
    private val processLifecycle = ProcessLifecycleOwner.get().lifecycle
    private val subscriptionOwner =
        ForegroundDeviceControllerOwner<DeviceLocal, Unit>(
            open = { openDeviceSubscription(it) },
            close = { device, _ -> closeDeviceSubscription(device) },
        )

    var blockerEnabled by mutableStateOf(false)
        private set

    init {
        processLifecycle.addObserver(this)
        subscriptionOwner.setForeground(
            processLifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        )
        removeDeviceChangeListener = deviceManager.addDeviceChangeListener { device ->
            viewModelScope.launch {
                blockerEnabled = false
                subscriptionOwner.setDevice(device)
            }
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        subscriptionOwner.setForeground(true)
    }

    override fun onStop(owner: LifecycleOwner) {
        subscriptionOwner.setForeground(false)
    }

    private fun openDeviceSubscription(device: DeviceLocal) {
        subscribedDevice = device
        blockerEnabled = device.blockerEnabled
        subs.add(device.addBlockerEnabledChangeListener { enabled ->
            viewModelScope.launch {
                if (subscribedDevice === device) {
                    blockerEnabled = enabled
                }
            }
        })
    }

    private fun closeDeviceSubscription(device: DeviceLocal) {
        subs.forEach { it.close() }
        subs.clear()
        if (subscribedDevice === device) {
            subscribedDevice = null
        }
    }

    val setBlockerEnabled: (Boolean) -> Unit = { enabled ->
        deviceManager.device?.let { device ->
            device.blockerEnabled = enabled
            blockerEnabled = enabled
        }
    }

    override fun onCleared() {
        removeDeviceChangeListener?.invoke()
        removeDeviceChangeListener = null
        processLifecycle.removeObserver(this)
        subscriptionOwner.close()
        super.onCleared()
    }
}
