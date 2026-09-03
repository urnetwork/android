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
import androidx.compose.ui.graphics.Color
import com.bringyour.network.ui.shared.models.ProvideControlMode
import com.bringyour.network.ui.shared.models.provideIndicatorDotColorFor
import com.bringyour.network.ui.shared.models.provideIndicatorRingColorFor
import com.bringyour.sdk.Sdk
import com.bringyour.network.ForegroundDeviceControllerOwner
import com.bringyour.sdk.ContractViewController
import com.bringyour.sdk.DeviceLocal
import com.bringyour.sdk.Sub
import com.bringyour.sdk.ThroughputPointList
import com.bringyour.sdk.TransportDistribution
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
 * One transport's slice of the window's remote traffic, ready to render as a
 * segment of the transport bar plus its legend entry. A mirror of the sdk's
 * `TransportShare`: every render value (share, cumulative boundary, whole
 * percent, used, enabled) is computed by the sdk view controller so the math
 * is shared and tested once for every platform.
 */
data class TransportShareUi(
    val transportType: TransportTypeUi,
    val egressBytes: Long = 0,
    val ingressBytes: Long = 0,
    val egressPackets: Long = 0,
    val ingressPackets: Long = 0,
    /**
     * fraction of the window's remote bytes, 0..1; 0 while idle
     */
    val share: Double = 0.0,
    /**
     * the right edge of the segment as a fraction of the bar width: the
     * cumulative share through this transport in stable order. Rendering every
     * segment from its neighbours' boundaries tiles exactly 100% of the bar
     */
    val boundary: Double = 0.0,
    /**
     * whole percent for the legend; the used percents sum to exactly 100.
     * A sliver can round to 0 while still used
     */
    val percent: Int = 0,
    /**
     * carried traffic in the window: draws a segment and a legend entry
     */
    val used: Boolean = false,
    /**
     * enabled by the transport settings; unused footer entry when idle
     */
    val enabled: Boolean = false,
) {
    val byteCount: Long
        get() = egressBytes + ingressBytes
}

/**
 * The window's remote traffic partitioned by the transport that carried it,
 * in the sdk's stable order with every transport present. Follows the same
 * window as the throughput points, so it drains to inactive as traffic ages
 * out. A mirror of the sdk's `TransportDistribution`.
 */
data class TransportDistributionUi(
    /**
     * stable order: h3, h1, dns, dnspump, p2p, unknown
     */
    val shares: List<TransportShareUi>,
    val byteCount: Long,
    /**
     * whether any transport carried traffic in the window
     */
    val active: Boolean,
) {
    /**
     * the segment boundaries in stable order, the vector the bar animates
     */
    val boundaries: List<Float>
        get() = shares.map { it.boundary.toFloat() }

    /**
     * the transports with traffic in the window, stable order
     */
    val used: List<TransportShareUi>
        get() = shares.filter { it.used }

    /**
     * the enabled transports without traffic in the window, stable order
     */
    val unused: List<TransportShareUi>
        get() = shares.filter { it.enabled && !it.used }

    companion object {
        val Empty = TransportDistributionUi(shares = listOf(), byteCount = 0, active = false)

        /**
         * maps the sdk distribution, dropping transport types this app does
         * not know (a newer sdk vocabulary)
         */
        fun fromSdk(distribution: TransportDistribution?): TransportDistributionUi {
            if (distribution == null) {
                return Empty
            }
            val shares = mutableListOf<TransportShareUi>()
            val list = distribution.shares
            if (list != null) {
                val n = list.len()
                for (i in 0 until n) {
                    val share = list.get(i) ?: continue
                    val transportType = TransportTypeUi.fromRawValue(share.transportType) ?: continue
                    shares.add(
                        TransportShareUi(
                            transportType = transportType,
                            egressBytes = share.egressByteCount,
                            ingressBytes = share.ingressByteCount,
                            egressPackets = share.egressPacketCount,
                            ingressPackets = share.ingressPacketCount,
                            share = share.share,
                            boundary = share.boundary,
                            percent = share.percent.toInt(),
                            used = share.used,
                            enabled = share.enabled,
                        )
                    )
                }
            }
            return TransportDistributionUi(
                shares = shares,
                byteCount = distribution.byteCount,
                active = distribution.active,
            )
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
     * the remote traffic of the window partitioned by transport, ready to
     * render (see `TransportDistributionUi`)
     */
    var clientTransportDistribution by mutableStateOf(TransportDistributionUi.Empty)
        private set

    var providerTransportDistribution by mutableStateOf(TransportDistributionUi.Empty)
        private set

    /**
     * false when the device has no provider (providing disabled)
     */
    var hasProviderStats by mutableStateOf(false)
        private set

    /**
     * The provide mode as the stats and earnings screens render it: the
     * control mode the user picked, and the live effective tier + pause the
     * indicator colors encode (the same functions settings uses).
     */
    var provideControlMode by mutableStateOf(ProvideControlMode.NEVER)
        private set
    var provideMode by mutableStateOf(Sdk.ProvideModeNone)
        private set
    var providePaused by mutableStateOf(false)
        private set
    val provideIndicatorColor: Color
        get() = provideIndicatorDotColorFor(provideMode, providePaused)
    val provideIndicatorRingColor: Color?
        get() = provideIndicatorRingColorFor(provideMode, providePaused)

    /**
     * Whether the provider plots show: the provide mode the user picked
     * (the same value the provide-mode row displays) must not be Never, and
     * the device must be publishing provider stats. With the mode on Never the
     * stats and earnings screens show the providing-disabled message instead,
     * whatever the device's live provide state says.
     */
    val providerStatsEnabled: Boolean
        get() = provideControlMode != ProvideControlMode.NEVER && hasProviderStats

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
        clientTransportDistribution = TransportDistributionUi.Empty
        providerTransportDistribution = TransportDistributionUi.Empty
        hasProviderStats = false
        provideControlMode = ProvideControlMode.NEVER
        provideMode = Sdk.ProvideModeNone
        providePaused = false
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
        // the distribution's enabled flags follow the device transport settings
        // (the view controller caches them from these same listeners). Re-read
        // the distributions on a policy change so the unused footer is right
        // while the window is idle and no throughput tick is due.
        subs.add(device.addTransportSettingsChangeListener {
            viewModelScope.launch {
                updateTransportDistributions()
            }
        })
        subs.add(device.addProviderTransportSettingsChangeListener {
            viewModelScope.launch {
                updateTransportDistributions()
            }
        })
        // the provide indicator follows the live effective tier
        device.addProvideModeChangeListener {
            viewModelScope.launch {
                refreshProvideState()
            }
        }?.let { subs.add(it) }
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
        updateTransportDistributions()
        hasProviderStats = vc.providerPacketStats != null
        refreshProvideState()
    }

    private fun refreshProvideState() {
        val device = viewControllerDevice ?: return
        provideControlMode = ProvideControlMode.fromString(device.provideControlMode)
            ?: ProvideControlMode.NEVER
        provideMode = device.provideMode
        providePaused = device.providePaused
    }

    /**
     * Publishes the window transport distributions from the same view
     * controller snapshot as the points. The distribution is inactive while
     * the window is idle; only a real change is published so an idle tick
     * doesn't retrigger the bar.
     */
    private fun updateTransportDistributions() {
        val vc = contractVc ?: return
        val clientDistribution = TransportDistributionUi.fromSdk(vc.transportDistribution)
        if (clientDistribution != clientTransportDistribution) {
            clientTransportDistribution = clientDistribution
        }
        val providerDistribution = TransportDistributionUi.fromSdk(vc.providerTransportDistribution)
        if (providerDistribution != providerTransportDistribution) {
            providerTransportDistribution = providerDistribution
        }
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
