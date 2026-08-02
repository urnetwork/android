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
import com.bringyour.sdk.ContractViewController
import com.bringyour.sdk.DeviceLocal
import com.bringyour.sdk.Sub
import com.bringyour.sdk.ThroughputPointList
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Throughput deltas for one route over one sample interval
 */
data class ThroughputSampleUi(
    val egressBytes: Long,
    val ingressBytes: Long,
    val egressPackets: Long,
    val ingressPackets: Long,
) {
    companion object {
        val Zero = ThroughputSampleUi(0, 0, 0, 0)
    }
}

/**
 * One throughput sample, split by route
 */
data class ThroughputPointUi(
    // sample end time, unix millis
    val timeMillis: Long,
    val remote: ThroughputSampleUi,
    val local: ThroughputSampleUi,
    val block: ThroughputSampleUi,
)

enum class ThroughputRoute {
    REMOTE,
    LOCAL,
    BLOCK;

    fun sample(point: ThroughputPointUi): ThroughputSampleUi {
        return when (this) {
            REMOTE -> point.remote
            LOCAL -> point.local
            BLOCK -> point.block
        }
    }
}

/**
 * Wraps the sdk contract view controller and publishes the live
 * client and provider throughput series
 */
@HiltViewModel
class ThroughputViewModel @Inject constructor(
    private val deviceManager: DeviceManager,
) : ViewModel(), DefaultLifecycleObserver {

    private var contractVc: ContractViewController? = null
    private val subs = mutableListOf<Sub>()
    private val processLifecycle = ProcessLifecycleOwner.get().lifecycle
    private var removeDeviceChangeListener: (() -> Unit)? = null
    private var viewControllerDevice: DeviceLocal? = null
    private val controllerOwner =
        ForegroundDeviceControllerOwner<DeviceLocal, ContractViewController>(
            open = { openLiveUpdates(it) },
            close = { device, vc -> closeLiveUpdates(device, vc) },
        )

    var clientPoints by mutableStateOf<List<ThroughputPointUi>>(listOf())
        private set

    var providerPoints by mutableStateOf<List<ThroughputPointUi>>(listOf())
        private set

    /**
     * false when the device has no provider (providing disabled)
     */
    var hasProviderStats by mutableStateOf(false)
        private set

    var windowSeconds by mutableStateOf(60L)
        private set

    init {
        processLifecycle.addObserver(this)
        controllerOwner.setForeground(
            processLifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        )
        removeDeviceChangeListener = deviceManager.addDeviceChangeListener { device ->
            viewModelScope.launch {
                setupDevice(device)
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
     * Stats are presentation work, not part of the packet path. Keep the SDK
     * controller open only while this app is visible so another foreground
     * app's first page load does not pay for JNI snapshots and Compose state.
     */
    private fun setupDevice(device: DeviceLocal?) {
        clientPoints = listOf()
        providerPoints = listOf()
        hasProviderStats = false
        controllerOwner.setDevice(device)
    }

    private fun openLiveUpdates(device: DeviceLocal): ContractViewController {
        val vc = device.openContractViewController()
        viewControllerDevice = device
        contractVc = vc
        windowSeconds = vc.windowDurationSeconds
        subs.add(vc.addThroughputListener {
            viewModelScope.launch {
                update()
            }
        })
        vc.start()
        update()
        return vc
    }

    private fun closeLiveUpdates(device: DeviceLocal, vc: ContractViewController) {
        subs.forEach { it.close() }
        subs.clear()
        vc.stop()
        device.closeViewController(vc)
        if (contractVc === vc) {
            contractVc = null
            viewControllerDevice = null
        }
    }

    private fun update() {
        val vc = contractVc ?: return
        clientPoints = mapPoints(vc.throughputPoints)
        providerPoints = mapPoints(vc.providerThroughputPoints)
        hasProviderStats = vc.providerPacketStats != null
    }

    private fun mapPoints(list: ThroughputPointList?): List<ThroughputPointUi> {
        if (list == null) {
            return listOf()
        }
        val points = mutableListOf<ThroughputPointUi>()
        val n = list.len()
        for (i in 0 until n) {
            val point = list.get(i) ?: continue
            points.add(
                ThroughputPointUi(
                    timeMillis = point.time,
                    remote = point.remote?.let {
                        ThroughputSampleUi(
                            it.egressByteCount,
                            it.ingressByteCount,
                            it.egressPacketCount,
                            it.ingressPacketCount
                        )
                    } ?: ThroughputSampleUi.Zero,
                    local = point.local?.let {
                        ThroughputSampleUi(
                            it.egressByteCount,
                            it.ingressByteCount,
                            it.egressPacketCount,
                            it.ingressPacketCount
                        )
                    } ?: ThroughputSampleUi.Zero,
                    block = point.block?.let {
                        ThroughputSampleUi(
                            it.egressByteCount,
                            it.ingressByteCount,
                            it.egressPacketCount,
                            it.ingressPacketCount
                        )
                    } ?: ThroughputSampleUi.Zero,
                )
            )
        }
        return points
    }

    override fun onCleared() {
        removeDeviceChangeListener?.invoke()
        removeDeviceChangeListener = null
        processLifecycle.removeObserver(this)
        controllerOwner.close()
        super.onCleared()
    }
}
