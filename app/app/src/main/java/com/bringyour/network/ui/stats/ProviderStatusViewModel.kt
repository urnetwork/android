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
import com.bringyour.network.VisibleDeviceControllerOwner
import com.bringyour.sdk.DeviceLocal
import com.bringyour.sdk.ProviderStatusViewController
import com.bringyour.sdk.Sub
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Publishes this device's provider status from the sdk's
 * `ProviderStatusViewController`: how often the network offered the device to
 * clients per minute over the last hour, the server's reason, and the numbers
 * it ranks the device by.
 *
 * The controller polls about once a minute. It is open only while providing
 * is enabled and the app is in the foreground, and it polls only while the
 * provider card's screen is visible (see `VisibleDeviceControllerOwner`).
 */
@HiltViewModel
class ProviderStatusViewModel @Inject constructor(
    private val deviceManager: DeviceManager,
) : ViewModel(), DefaultLifecycleObserver {

    private val subs = mutableListOf<Sub>()
    private var statusVc: ProviderStatusViewController? = null
    private var removeDeviceChangeListener: (() -> Unit)? = null
    private val processLifecycle = ProcessLifecycleOwner.get().lifecycle
    private val controllerOwner =
        VisibleDeviceControllerOwner<DeviceLocal, ProviderStatusViewController>(
            open = { openProviderStatus(it) },
            close = { device, vc -> closeProviderStatus(device, vc) },
            start = { it.start() },
            stop = { it.stop() },
        )

    var status by mutableStateOf(ProviderStatusUi.Empty)
        private set

    init {
        processLifecycle.addObserver(this)
        controllerOwner.setForeground(
            processLifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        )
        // a replaced device closes the former device's controller, which
        // clears the status
        removeDeviceChangeListener = deviceManager.addDeviceChangeListener { device ->
            viewModelScope.launch {
                controllerOwner.setDevice(device)
            }
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        controllerOwner.setForeground(true)
    }

    override fun onStop(owner: LifecycleOwner) {
        controllerOwner.setForeground(false)
    }

    /**
     * The same gate as the provider plots: never open while providing is
     * disabled.
     */
    fun setProvidingEnabled(enabled: Boolean) {
        controllerOwner.setEnabled(enabled)
    }

    /**
     * Whether the provider card's screen is visible: polls while it is, and
     * keeps the last snapshot while it is not.
     */
    fun setVisible(visible: Boolean) {
        controllerOwner.setVisible(visible)
    }

    /** Opens the device's controller and follows its polls. */
    private fun openProviderStatus(device: DeviceLocal): ProviderStatusViewController {
        val vc = device.openProviderStatusViewController()
        statusVc = vc
        // fires after every poll, success or failure, on an sdk thread
        subs.add(vc.addProviderStatusListener {
            viewModelScope.launch {
                update()
            }
        })
        update()
        return vc
    }

    /** Stops following [vc], stops and closes it, and clears its status. */
    private fun closeProviderStatus(device: DeviceLocal, vc: ProviderStatusViewController) {
        subs.forEach { it.close() }
        subs.clear()
        vc.stop()
        device.closeProviderStatusViewController(vc)
        if (statusVc === vc) {
            statusVc = null
            status = ProviderStatusUi.Empty
        }
    }

    /** Reads the open controller's state into [status]. */
    private fun update() {
        val vc = statusVc ?: return
        status = ProviderStatusUi.fromSdk(vc)
    }

    override fun onCleared() {
        removeDeviceChangeListener?.invoke()
        removeDeviceChangeListener = null
        processLifecycle.removeObserver(this)
        controllerOwner.close()
        super.onCleared()
    }
}
