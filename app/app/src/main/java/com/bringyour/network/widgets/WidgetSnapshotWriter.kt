package com.bringyour.network.widgets

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import com.bringyour.network.DeviceManager
import com.bringyour.sdk.ConnectLocation
import com.bringyour.sdk.ConnectedProviderLocationList
import com.bringyour.sdk.ContractDetailsViewController
import com.bringyour.sdk.ContractPeerRowList
import com.bringyour.sdk.DeviceLocal
import com.bringyour.sdk.ProviderLocationsViewController
import com.bringyour.sdk.Sdk
import com.bringyour.sdk.Sub
import com.bringyour.sdk.SubscriptionBalanceCallback

/**
 * Which widgets exist and how to re-render them. Implemented by the Glance
 * side; kept behind an interface so the writer stays free of widget
 * framework types.
 */
interface WidgetRefresh {
    fun hasWidgets(kind: String): Boolean
    fun reloadDashboard()
    fun reloadProviderGlobe()
    fun reloadContracts()
    fun reloadAll() {
        reloadDashboard()
        reloadProviderGlobe()
        reloadContracts()
    }
}

/**
 * Publishes what the Home Screen widgets show from the process that holds
 * the truth — on Android that is this process, next to the SDK device: the
 * connected location, the connected providers in the app's display order,
 * the per-minute throughput folded from the device's cumulative packet
 * counters, the open client contracts by peer, and, on a slow cadence, the
 * transfer balance. Everything is written to a snapshot file and the
 * widgets are asked to re-render, on a throttle.
 *
 * The Apple counterpart is extension/WidgetSnapshotWriter.swift. Two things
 * differ here: the SDK's own view controllers do the provider ordering and
 * the contract grouping (they are in-process), and they are opened only
 * while a widget that needs them is placed, because they are presentation
 * work rather than part of the packet path.
 *
 * All work happens on one background thread; SDK listener callbacks hop
 * onto it.
 */
class WidgetSnapshotWriter(
    private val context: Context,
    private val deviceManager: DeviceManager,
    private val refresh: WidgetRefresh,
) {

    companion object {
        private const val TAG = "WidgetSnapshotWriter"

        /** How often the snapshot file is rewritten while the tunnel is up. */
        const val WRITE_INTERVAL_MILLIS = 60_000L
        /** Routine widget re-render cadence while the tunnel is up. */
        const val ROUTINE_RELOAD_INTERVAL_MILLIS = 5 * 60_000L
        /** Floor between globe re-renders driven by providers joining or leaving. */
        const val PROVIDER_RELOAD_INTERVAL_MILLIS = 60_000L
        /** Floor between contracts re-renders driven by peers or contracts coming and going. */
        const val CONTRACT_RELOAD_INTERVAL_MILLIS = 60_000L
        /** Contract row events are coalesced to at most one snapshot per this interval. */
        const val CONTRACT_REFRESH_INTERVAL_MILLIS = 2_000L
        const val BALANCE_INTERVAL_MILLIS = 30 * 60_000L
        const val BALANCE_INITIAL_DELAY_MILLIS = 15_000L
        const val CONTRACT_PEER_LIMIT = 12
        const val CONTRACT_STACK_LIMIT = 6
    }

    private val thread = HandlerThread("widget-snapshot").apply { start() }
    private val handler = Handler(thread.looper)

    private var device: DeviceLocal? = null
    private val subs = ArrayList<Sub>()
    private var providerVc: ProviderLocationsViewController? = null
    private var providerSelectionSub: Sub? = null
    private var contractsVc: ContractDetailsViewController? = null
    private var contractRowsSub: Sub? = null

    private var accumulator = WidgetThroughputAccumulator(WidgetSnapshotStore.loadTunnel(context)?.throughput)
    private var location: WidgetLocationSnapshot? = null
    private var providers: List<WidgetProviderSnapshot> = emptyList()
    private var contracts: List<WidgetContractPeerSnapshot> = emptyList()
    private var providing = false
    private var connectEnabled = false
    private var lastWritten: WidgetTunnelSnapshot? = null
    private var contractRefreshPending = false
    private var lastContractRefreshAt = 0L

    private val routineReload = WidgetReloadThrottle(handler, ROUTINE_RELOAD_INTERVAL_MILLIS) { refresh.reloadAll() }
    private val providerReload = WidgetReloadThrottle(handler, PROVIDER_RELOAD_INTERVAL_MILLIS) { refresh.reloadProviderGlobe() }
    private val contractReload = WidgetReloadThrottle(handler, CONTRACT_RELOAD_INTERVAL_MILLIS) { refresh.reloadContracts() }

    private val writeTick = object : Runnable {
        override fun run() {
            if (device == null) return
            ensureControllers()
            if (write()) routineReload.request()
            handler.postDelayed(this, WRITE_INTERVAL_MILLIS)
        }
    }

    private val balanceTick = object : Runnable {
        override fun run() {
            refreshBalance()
            handler.postDelayed(this, BALANCE_INTERVAL_MILLIS)
        }
    }

    /** Follows the device for the life of the process. Call once. */
    fun start() {
        deviceManager.addDeviceChangeListener { device ->
            handler.post { attach(device) }
        }
    }

    /** A widget was added or removed: open or close the controllers it needs. */
    fun widgetsChanged() {
        handler.post {
            ensureControllers()
            write()
            refresh.reloadAll()
        }
    }

    /** The app fetched the balance: publish it for the dashboard. */
    fun publishBalance(snapshot: WidgetBalanceSnapshot) {
        handler.post {
            if (WidgetSnapshotStore.save(context, snapshot)) refresh.reloadDashboard()
        }
    }

    /** Logout: the widgets must not keep showing the signed-out account. */
    fun clear() {
        handler.post {
            detach()
            WidgetSnapshotStore.clear(context)
            accumulator = WidgetThroughputAccumulator()
            lastWritten = null
            refresh.reloadAll()
        }
    }

    // MARK: device

    private fun attach(device: DeviceLocal?) {
        detach()
        if (device == null) {
            write(active = false)
            refresh.reloadAll()
            return
        }
        this.device = device
        connectEnabled = device.connectEnabled
        providing = device.provideEnabled
        location = locationSnapshot(device.connectLocation)
        device.packetStats?.let { accumulator.recordClient(it.remoteEgressByteCount, it.remoteIngressByteCount) }
        device.providerPacketStats?.let {
            accumulator.recordProvider(
                it.localEgressByteCount + it.blockEgressByteCount,
                it.localIngressByteCount + it.blockIngressByteCount,
            )
        }

        subs += device.addConnectChangeListener { enabled ->
            handler.post {
                if (this.device !== device) return@post
                connectEnabled = enabled
                // connect / disconnect: re-render every surface at once
                write()
                refresh.reloadAll()
            }
        }
        subs += device.addConnectLocationChangeListener { connectLocation ->
            handler.post {
                if (this.device !== device) return@post
                val snapshot = locationSnapshot(connectLocation)
                if (snapshot == location) return@post
                location = snapshot
                write()
                refresh.reloadDashboard()
                providerReload.request(urgent = true)
            }
        }
        subs += device.addProvideChangeListener { provideEnabled ->
            handler.post {
                if (this.device !== device || providing == provideEnabled) return@post
                providing = provideEnabled
                write()
                routineReload.request(urgent = true)
            }
        }
        subs += device.addConnectedProviderLocationChangeListener {
            handler.post {
                if (this.device !== device) return@post
                refreshProviders()
            }
        }
        subs += device.addPacketStatsChangeListener { stats ->
            if (stats == null) return@addPacketStatsChangeListener
            val egress = stats.remoteEgressByteCount
            val ingress = stats.remoteIngressByteCount
            handler.post {
                if (this.device !== device) return@post
                accumulator.recordClient(egress, ingress)
            }
        }
        subs += device.addProviderPacketStatsChangeListener { stats ->
            if (stats == null) return@addProviderPacketStatsChangeListener
            val egress = stats.localEgressByteCount + stats.blockEgressByteCount
            val ingress = stats.localIngressByteCount + stats.blockIngressByteCount
            handler.post {
                if (this.device !== device) return@post
                accumulator.recordProvider(egress, ingress)
            }
        }

        ensureControllers()
        refreshProviders()
        write()
        refresh.reloadAll()
        handler.postDelayed(writeTick, WRITE_INTERVAL_MILLIS)
        handler.postDelayed(balanceTick, BALANCE_INITIAL_DELAY_MILLIS)
    }

    private fun detach() {
        handler.removeCallbacks(writeTick)
        handler.removeCallbacks(balanceTick)
        routineReload.cancel()
        providerReload.cancel()
        contractReload.cancel()
        subs.forEach { runCatching { it.close() } }
        subs.clear()
        closeProviderController()
        closeContractsController()
        device = null
    }

    // MARK: controllers, opened only for placed widgets

    private fun ensureControllers() {
        val device = this.device ?: return
        val wantGlobe = refresh.hasWidgets(WidgetKinds.PROVIDER_GLOBE)
        if (wantGlobe && providerVc == null) {
            providerVc = device.openProviderLocationsViewController()?.also { vc ->
                providerSelectionSub = vc.addSelectedProviderLocationChangeListener {
                    handler.post { if (this.device === device) refreshProviders() }
                }
            }
            refreshProviders()
        } else if (!wantGlobe && providerVc != null) {
            closeProviderController()
        }

        val wantContracts = refresh.hasWidgets(WidgetKinds.CONTRACTS)
        if (wantContracts && contractsVc == null) {
            contractsVc = device.openClientContractDetailsViewController()?.also { vc ->
                contractRowsSub = vc.addContractRowsListener {
                    handler.post { if (this.device === device) scheduleContractRefresh() }
                }
                // the widget always shows the most relevant rows, never a
                // scrolled-away frozen order
                vc.setAtTop(true)
                vc.start()
            }
            refreshContracts()
        } else if (!wantContracts && contractsVc != null) {
            closeContractsController()
            contracts = emptyList()
        }
    }

    private fun closeProviderController() {
        providerSelectionSub?.let { runCatching { it.close() } }
        providerSelectionSub = null
        providerVc?.let { vc -> device?.let { runCatching { it.closeViewController(vc) } } }
        providerVc = null
    }

    private fun closeContractsController() {
        contractRowsSub?.let { runCatching { it.close() } }
        contractRowsSub = null
        contractsVc?.let { vc -> device?.let { runCatching { it.closeViewController(vc) } } }
        contractsVc = null
    }

    // MARK: providers

    private fun refreshProviders() {
        val device = this.device ?: return
        // the view controller orders west to east about the centroid, as the
        // provider details view does; without one (no globe widget placed) the
        // device's oldest-first list still feeds the dashboard's count
        val list = providerVc?.providerLocations ?: device.connectedProviderLocations
        val snapshot = providerSnapshots(list)
        if (snapshot == providers) return
        providers = snapshot
        write()
        providerReload.request()
    }

    // MARK: contracts

    private fun scheduleContractRefresh() {
        if (contractRefreshPending) return
        val elapsed = System.currentTimeMillis() - lastContractRefreshAt
        val delay = (CONTRACT_REFRESH_INTERVAL_MILLIS - elapsed).coerceAtLeast(0)
        contractRefreshPending = true
        handler.postDelayed({
            contractRefreshPending = false
            refreshContracts()
        }, delay)
    }

    private fun refreshContracts() {
        val vc = contractsVc ?: return
        lastContractRefreshAt = System.currentTimeMillis()
        val snapshot = contractSnapshots(vc.contractRows)
        val membershipChanged = membership(snapshot) != membership(contracts)
        contracts = snapshot
        if (membershipChanged) {
            write()
            contractReload.request()
        }
    }

    private fun membership(peers: List<WidgetContractPeerSnapshot>): List<String> = peers.map { peer ->
        peer.id + ":" + peer.send.joinToString(",") { it.id } + "|" + peer.receive.joinToString(",") { it.id }
    }

    // MARK: snapshot

    /** Writes the current state; returns whether anything changed. */
    private fun write(active: Boolean? = null): Boolean {
        val tunnelActive = active ?: (device != null && (connectEnabled || providing))
        val snapshot = WidgetTunnelSnapshot(
            updatedAtMillis = System.currentTimeMillis(),
            tunnelActive = tunnelActive,
            providing = providing,
            location = location,
            providers = providers,
            throughput = accumulator.snapshot,
            contracts = contracts,
        )
        val changed = lastWritten?.copy(updatedAtMillis = snapshot.updatedAtMillis) != snapshot
        if (WidgetSnapshotStore.save(context, snapshot)) {
            lastWritten = snapshot
        } else {
            Log.w(TAG, "failed to write the tunnel snapshot")
        }
        return changed
    }

    private fun refreshBalance() {
        val api = device?.api ?: return
        api.subscriptionBalance(SubscriptionBalanceCallback { result, err ->
            if (err != null || result == null) return@SubscriptionBalanceCallback
            publishBalance(
                WidgetBalanceSnapshot(
                    updatedAtMillis = System.currentTimeMillis(),
                    startBalanceByteCount = result.startBalanceByteCount,
                    balanceByteCount = result.balanceByteCount,
                    openTransferByteCount = result.openTransferByteCount,
                    isPro = result.currentSubscription != null,
                )
            )
        })
    }

    // MARK: mapping

    private fun locationSnapshot(location: ConnectLocation?): WidgetLocationSnapshot? {
        if (location == null) return null
        val countryCode = location.countryCode.orEmpty().lowercase()
        return WidgetLocationSnapshot(
            name = location.name.orEmpty(),
            countryCode = countryCode,
            city = location.city.orEmpty(),
            region = location.region.orEmpty(),
            country = location.country.orEmpty(),
            bestAvailable = location.connectLocationId?.bestAvailable == true,
            networkPeer = location.networkPeer,
            providerCount = location.providerCount,
            colorHex = if (countryCode.isEmpty()) "" else Sdk.getColorHex(countryCode),
        )
    }

    private fun providerSnapshots(list: ConnectedProviderLocationList?): List<WidgetProviderSnapshot> {
        if (list == null) return emptyList()
        val out = ArrayList<WidgetProviderSnapshot>()
        for (i in 0 until list.len()) {
            val item = list.get(i) ?: continue
            // the same plot rule as the app: city centroid, else region
            var lat: Double? = null
            var lon: Double? = null
            if (item.hasCityCoordinates) {
                lat = item.cityLat
                lon = item.cityLon
            } else if (item.hasRegionCoordinates) {
                lat = item.regionLat
                lon = item.regionLon
            }
            val countryCode = item.countryCode.orEmpty().lowercase()
            out += WidgetProviderSnapshot(
                id = item.clientId?.string().orEmpty().ifEmpty { i.toString() },
                country = item.country.orEmpty(),
                countryCode = countryCode,
                region = item.region.orEmpty(),
                city = item.city.orEmpty(),
                lat = lat,
                lon = lon,
                connectedSinceMillis = item.connectedSinceMillis,
                colorHex = if (countryCode.isEmpty()) "" else Sdk.getColorHex(countryCode),
            )
        }
        return out
    }

    private fun contractSnapshots(rows: ContractPeerRowList?): List<WidgetContractPeerSnapshot> {
        if (rows == null) return emptyList()
        val out = ArrayList<WidgetContractPeerSnapshot>()
        for (i in 0 until rows.len()) {
            val row = rows.get(i) ?: continue
            if (row.closing) continue
            fun entries(list: com.bringyour.sdk.ContractEntryList?): List<WidgetContractSnapshot> {
                if (list == null) return emptyList()
                val entries = ArrayList<WidgetContractSnapshot>()
                for (j in 0 until minOf(list.len(), CONTRACT_STACK_LIMIT.toLong())) {
                    val e = list.get(j) ?: continue
                    entries += WidgetContractSnapshot(
                        id = e.contractId,
                        usedByteCount = e.usedByteCount,
                        totalByteCount = e.totalByteCount,
                        bitRate = e.bitRate,
                        hasStream = e.hasStream,
                    )
                }
                return entries
            }
            out += WidgetContractPeerSnapshot(
                id = row.clientId,
                send = entries(row.sendContracts),
                receive = entries(row.receiveContracts),
                sendByteCount = row.sendByteCount,
                receiveByteCount = row.receiveByteCount,
                lastActivityMillis = row.lastActivityMillis,
            )
            if (CONTRACT_PEER_LIMIT <= out.size) break
        }
        return out
    }
}

/**
 * Coalesces re-render requests so a chatty source cannot churn the launcher:
 * urgent requests go through at once, routine ones wait for the interval.
 */
class WidgetReloadThrottle(
    private val handler: Handler,
    private val intervalMillis: Long,
    private val reload: () -> Unit,
) {
    private var lastReloadAt = 0L
    private var pending: Runnable? = null

    fun request(urgent: Boolean = false) {
        val now = System.currentTimeMillis()
        if (urgent || intervalMillis <= now - lastReloadAt) {
            fire(now)
            return
        }
        if (pending != null) return
        val runnable = Runnable {
            pending = null
            fire(System.currentTimeMillis())
        }
        pending = runnable
        handler.postDelayed(runnable, intervalMillis - (now - lastReloadAt))
    }

    fun cancel() {
        pending?.let { handler.removeCallbacks(it) }
        pending = null
    }

    private fun fire(now: Long) {
        cancel()
        lastReloadAt = now
        reload()
    }
}
