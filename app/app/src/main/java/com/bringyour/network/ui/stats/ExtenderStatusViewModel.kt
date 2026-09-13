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
import com.bringyour.sdk.ExtenderStatus
import com.bringyour.sdk.Sub
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Publishes the device's extender status for the connect drawer's extender
 * panel (EXTENDER.md K4, K5).
 *
 * The status is read from the DEVICE, not the space: it describes the
 * directory whose dials the panel draws, which on other platforms lives in a
 * separate process. The sdk coalesces the change listener to one callback a
 * second, so there is no polling here and no further throttling.
 */
@HiltViewModel
class ExtenderStatusViewModel @Inject constructor(
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

    var panel by mutableStateOf(ExtenderPanelUi())
        private set

    init {
        processLifecycle.addObserver(this)
        subscriptionOwner.setForeground(
            processLifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        )
        // the device is (re)created asynchronously (login, network change),
        // so wire per device, every time
        removeDeviceChangeListener = deviceManager.addDeviceChangeListener { device ->
            viewModelScope.launch {
                panel = ExtenderPanelUi()
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
        subs.add(
            device.addExtenderStatusChangeListener { status ->
                // the sdk hands over a proxy of a value it already replaced;
                // read it here rather than on the ui dispatcher
                val next = extenderPanelUi(status)
                viewModelScope.launch {
                    if (subscribedDevice === device) {
                        panel = next
                    }
                }
            }
        )
        update(device)
    }

    private fun closeDeviceSubscription(device: DeviceLocal) {
        subs.forEach { it.close() }
        subs.clear()
        if (subscribedDevice === device) {
            subscribedDevice = null
        }
    }

    private fun update(device: DeviceLocal) {
        val next = extenderPanelUi(device.extenderStatus)
        viewModelScope.launch {
            if (subscribedDevice === device) {
                panel = next
            }
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

/**
 * The panel snapshot of one sdk status. A device that reports none — an older
 * device process, or a space with no extender directory — reads as the empty
 * panel rather than a missing one.
 */
private fun extenderPanelUi(status: ExtenderStatus?): ExtenderPanelUi {
    status ?: return ExtenderPanelUi()
    val extenders = mutableListOf<ExtenderUi>()
    val infos = status.extenders
    if (infos != null) {
        for (i in 0 until infos.len()) {
            val info = infos.get(i) ?: continue
            extenders.add(
                ExtenderUi(
                    ip = info.ip,
                    colorHex = info.colorHex,
                    inUse = info.inUse.toInt(),
                )
            )
        }
    }
    return ExtenderPanelUi(
        extenders = extenders,
        activeCount = status.activeCount.toInt(),
        reserveCount = status.reserveCount.toInt(),
        gossipState = status.gossipState,
        eventCountLastMinute = status.eventCountLastMinute.toInt(),
    )
}
