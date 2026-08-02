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
import com.bringyour.sdk.ContractDetailsViewController
import com.bringyour.sdk.ContractEntryList
import com.bringyour.sdk.DeviceLocal
import com.bringyour.sdk.Sub
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * One contract, un-aggregated: its own used/total byte counts and bit rate.
 * Contracts are never paired -- a peer's send and receive contracts are
 * fundamentally many-to-many, so each is presented on its own. The contract id
 * is the stable identity a circle keeps for its whole insert/slide-off lifecycle.
 */
data class ContractEntryUi(
    val contractId: String,
    val usedByteCount: Long = 0,
    val totalByteCount: Long = 0,
    val bitRate: Long = 0,
    // a stream contract (its transfer path carries a stream id) -- drawn as a double
    // concentric outer ring so streams are visually distinct from direct contracts
    val hasStream: Boolean = false,
) {
    val isActive: Boolean get() = 0 < bitRate
}

/**
 * One peer client's open contracts, as two independent stacks: contracts
 * sending to the peer and contracts receiving from it, each newest first. No
 * cross-direction pairing or byte summing is done; the only derived per-row
 * numbers are the two bit-rate sums for the stack headers.
 */
data class ContractPeerRowUi(
    val clientId: String,
    // newest first
    val send: List<ContractEntryUi> = listOf(),
    val receive: List<ContractEntryUi> = listOf(),
    // cumulative bytes moved to / from this peer in the current run (accumulated
    // across the peer's contracts, reset when it goes idle), for the stack headers
    val sendByteCount: Long = 0,
    val receiveByteCount: Long = 0,
    // unix-millis of this peer's last byte movement (any contract with a positive
    // bit rate), or 0 if it has not moved bytes since appearing. The view
    // controller uses it to float rows with recent activity above idle ones; the
    // app just renders the order it is given.
    val lastActivityMillis: Long = 0,
    // the peer's last contract closed and the row is being ejected: it is kept
    // briefly (by the SDK view controller, with empty stacks) so its circles can
    // slide off, then it is removed
    val closing: Boolean = false,
)

/**
 * Thin adapter over the shared SDK ContractDetailsViewController. It opens the
 * single-feed view controller for this screen's mode (`provider` opens the
 * provider feed -- traffic relayed for remote clients -- otherwise the client
 * feed for this device's own traffic), subscribes to its row changes, and
 * republishes the rows and the "N new" pending count.
 *
 * ALL of the work -- per-peer grouping into two per-direction newest-first
 * stacks, the closing/eject lifecycle, AND the display ordering (the at-top
 * active-above-idle sort and the scrolled-away freeze that collects new rows as
 * pending) -- is done by the view controller (shared by every platform). The app
 * only reports its scroll position via [setAtTop] and renders the already-ordered
 * rows [rows] hands back. The listener is throttled inside the SDK, so this
 * re-reads on every callback.
 */
@HiltViewModel
class ContractStatsViewModel @Inject constructor(
    private val deviceManager: DeviceManager,
) : ViewModel(), DefaultLifecycleObserver {

    private var contractDetailsVc: ContractDetailsViewController? = null
    private val subs = mutableListOf<Sub>()
    private var started = false
    private var providerMode = false
    private var availableDevice: DeviceLocal? = null
    private var removeDeviceChangeListener: (() -> Unit)? = null
    private val processLifecycle = ProcessLifecycleOwner.get().lifecycle
    private val controllerOwner =
        ForegroundDeviceControllerOwner<DeviceLocal, ContractDetailsViewController>(
            open = { openContractDetailsViewController(it) },
            close = { device, vc -> closeContractDetailsViewController(device, vc) },
        )

    var rows by mutableStateOf<List<ContractPeerRowUi>>(listOf())
        private set

    // the view controller's "N new" pending count for this feed: rows that arrived
    // while scrolled away from the top and are not yet merged (0 while at the top)
    var pendingCount by mutableStateOf(0)
        private set

    init {
        processLifecycle.addObserver(this)
        controllerOwner.setForeground(
            processLifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        )
        removeDeviceChangeListener = deviceManager.addDeviceChangeListener { device ->
            viewModelScope.launch {
                availableDevice = device
                rows = listOf()
                pendingCount = 0
                if (started) {
                    controllerOwner.setDevice(device)
                }
            }
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        controllerOwner.setForeground(true)
    }

    override fun onStop(owner: LifecycleOwner) {
        controllerOwner.setForeground(false)
    }

    fun start(provider: Boolean) {
        if (started) {
            return
        }
        started = true
        providerMode = provider
        controllerOwner.setDevice(availableDevice)
    }

    private fun openContractDetailsViewController(
        device: DeviceLocal
    ): ContractDetailsViewController {
        // The client and provider lists are two instances of the same
        // single-feed controller; open the one for this screen's feed.
        val vc = if (providerMode) {
            device.openProviderContractDetailsViewController()
        } else {
            device.openClientContractDetailsViewController()
        }
        contractDetailsVc = vc

        subs.add(vc.addContractRowsListener {
            viewModelScope.launch {
                update()
            }
        })

        vc.start()
        update()
        return vc
    }

    private fun closeContractDetailsViewController(
        device: DeviceLocal,
        vc: ContractDetailsViewController,
    ) {
        subs.forEach { it.close() }
        subs.clear()
        vc.stop()
        device.closeViewController(vc)
        if (contractDetailsVc === vc) {
            contractDetailsVc = null
        }
    }

    /**
     * Report whether this feed's list is scrolled to the very top. The shared view
     * controller owns the ordering: at the top it re-sorts active rows above idle
     * ones; scrolled away it freezes the membership+order and collects newly
     * arrived rows into [pendingCount].
     */
    fun setAtTop(atTop: Boolean) {
        contractDetailsVc?.setAtTop(atTop)
    }

    private fun entries(list: ContractEntryList?): List<ContractEntryUi> {
        if (list == null) {
            return listOf()
        }
        val out = mutableListOf<ContractEntryUi>()
        val n = list.len()
        for (i in 0 until n) {
            val e = list.get(i) ?: continue
            out.add(
                ContractEntryUi(
                    contractId = e.contractId,
                    usedByteCount = e.usedByteCount,
                    totalByteCount = e.totalByteCount,
                    bitRate = e.bitRate,
                    hasStream = e.hasStream,
                )
            )
        }
        return out
    }

    private fun update() {
        val vc = contractDetailsVc ?: return

        // the FINAL, already-ordered rows (the activity sort and the scrolled-away
        // freeze are done inside the view controller); render them in order as-is
        val list = vc.contractRows

        val newRows = mutableListOf<ContractPeerRowUi>()
        if (list != null) {
            val n = list.len()
            for (i in 0 until n) {
                val r = list.get(i) ?: continue
                newRows.add(
                    ContractPeerRowUi(
                        clientId = r.clientId,
                        send = entries(r.sendContracts),
                        receive = entries(r.receiveContracts),
                        sendByteCount = r.sendByteCount,
                        receiveByteCount = r.receiveByteCount,
                        lastActivityMillis = r.lastActivityMillis,
                        closing = r.closing,
                    )
                )
            }
        }

        rows = newRows
        pendingCount = vc.pendingCount().toInt()
    }

    override fun onCleared() {
        removeDeviceChangeListener?.invoke()
        removeDeviceChangeListener = null
        processLifecycle.removeObserver(this)
        controllerOwner.close()
        super.onCleared()
    }
}
