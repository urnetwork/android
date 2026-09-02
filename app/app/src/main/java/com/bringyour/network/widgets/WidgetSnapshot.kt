package com.bringyour.network.widgets

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * What the Home Screen widgets render, and where it comes from. The Apple
 * counterpart is network/Shared/Widgets/WidgetSnapshots.swift; the field
 * names and semantics are kept identical so the two designs stay in step.
 *
 * Android is single-process: the app, the VPN service and the widgets'
 * update code all run next to the Go SDK, so unlike iOS nothing has to
 * cross a process boundary. The snapshot is still written to a file, for
 * two reasons: a widget renders from whatever was last pushed to the
 * launcher, and when the process comes back (a cold start from a widget
 * tap, a reboot) the last hour of throughput and the last known providers
 * are there to draw before the tunnel reports anything.
 */

object WidgetKinds {
    const val DASHBOARD = "dashboard"
    const val PROVIDER_GLOBE = "globe"
    const val CONTRACTS = "contracts"
}

@Serializable
data class WidgetLocationSnapshot(
    val name: String,
    val countryCode: String,
    val city: String,
    val region: String,
    val country: String,
    val bestAvailable: Boolean,
    val networkPeer: Boolean,
    val providerCount: Int,
    /** The SDK palette color for this location (six hex digits, no `#`). */
    val colorHex: String,
)

@Serializable
data class WidgetProviderSnapshot(
    /** The provider's client id. */
    val id: String,
    val country: String,
    val countryCode: String,
    val region: String,
    val city: String,
    /** Plot coordinates (city centroid, else region centroid); null when unknown. */
    val lat: Double? = null,
    val lon: Double? = null,
    val connectedSinceMillis: Long,
    val colorHex: String,
) {
    val plottable: Boolean get() = lat != null && lon != null
}

/** One fixed-width bucket of bytes moved, per side. */
@Serializable
data class WidgetThroughputBucket(
    /** Bucket start, unix seconds. */
    val start: Long,
    /** This device's own traffic through providers (the "remote" route). */
    val clientEgress: Long = 0,
    val clientIngress: Long = 0,
    /** Traffic relayed for others while providing (the provider counters' local + block routes). */
    val providerEgress: Long = 0,
    val providerIngress: Long = 0,
)

@Serializable
data class WidgetThroughputSnapshot(
    val bucketSeconds: Long,
    /** Oldest first. Missing buckets mean no traffic. */
    val buckets: List<WidgetThroughputBucket>,
) {
    companion object {
        val EMPTY = WidgetThroughputSnapshot(WidgetThroughputAccumulator.BUCKET_SECONDS, emptyList())
    }
}

/**
 * One open contract, un-aggregated: contracts are never paired (a peer's
 * send and receive contracts are many-to-many), as in the contract stacks
 * design.
 */
@Serializable
data class WidgetContractSnapshot(
    val id: String,
    val usedByteCount: Long,
    val totalByteCount: Long,
    val bitRate: Long,
    val hasStream: Boolean,
) {
    val isActive: Boolean get() = 0 < bitRate
}

/** One peer's open client contracts as two independent stacks, newest first. */
@Serializable
data class WidgetContractPeerSnapshot(
    val id: String,
    val send: List<WidgetContractSnapshot>,
    val receive: List<WidgetContractSnapshot>,
    val sendByteCount: Long,
    val receiveByteCount: Long,
    val lastActivityMillis: Long,
) {
    val isActive: Boolean get() = send.any { it.isActive } || receive.any { it.isActive }
    val bitRate: Long get() = send.sumOf { it.bitRate } + receive.sumOf { it.bitRate }
}

@Serializable
data class WidgetTunnelSnapshot(
    val version: Int = 1,
    val updatedAtMillis: Long,
    /** The tunnel was up when this was written. */
    val tunnelActive: Boolean,
    val providing: Boolean,
    val location: WidgetLocationSnapshot? = null,
    /** Connected providers in the app's display order (west to east, unplottable last). */
    val providers: List<WidgetProviderSnapshot> = emptyList(),
    val throughput: WidgetThroughputSnapshot = WidgetThroughputSnapshot.EMPTY,
    /** Open client contracts by peer, most relevant first (the SDK's row order). */
    val contracts: List<WidgetContractPeerSnapshot> = emptyList(),
) {
    companion object {
        fun inactive(nowMillis: Long = System.currentTimeMillis()) = WidgetTunnelSnapshot(
            updatedAtMillis = nowMillis,
            tunnelActive = false,
            providing = false,
        )
    }
}

@Serializable
data class WidgetBalanceSnapshot(
    val version: Int = 1,
    val updatedAtMillis: Long,
    val startBalanceByteCount: Long,
    val balanceByteCount: Long,
    val openTransferByteCount: Long,
    val isPro: Boolean,
) {
    /** The app's usage bar "used" segment. */
    val usedByteCount: Long
        get() = (startBalanceByteCount - balanceByteCount - openTransferByteCount).coerceAtLeast(0)
}

/**
 * Folds cumulative packet counters into fixed one-minute buckets. Resumed
 * from the persisted snapshot so a tunnel restart does not flatten the chart.
 */
class WidgetThroughputAccumulator(resuming: WidgetThroughputSnapshot? = null) {

    companion object {
        const val BUCKET_SECONDS = 60L
        /** One hour of history. */
        const val BUCKET_COUNT = 60
    }

    private val buckets = ArrayList<WidgetThroughputBucket>()
    private var lastClientEgress: Long? = null
    private var lastClientIngress: Long? = null
    private var lastProviderEgress: Long? = null
    private var lastProviderIngress: Long? = null

    init {
        if (resuming != null && resuming.bucketSeconds == BUCKET_SECONDS) {
            buckets.addAll(resuming.buckets.takeLast(BUCKET_COUNT))
        }
    }

    val snapshot: WidgetThroughputSnapshot
        get() = WidgetThroughputSnapshot(BUCKET_SECONDS, buckets.toList())

    @Synchronized
    fun recordClient(egress: Long, ingress: Long, nowMillis: Long = System.currentTimeMillis()) {
        val dEgress = delta(lastClientEgress, egress)
        val dIngress = delta(lastClientIngress, ingress)
        lastClientEgress = egress
        lastClientIngress = ingress
        if (dEgress <= 0 && dIngress <= 0) return
        val index = currentBucketIndex(nowMillis)
        val bucket = buckets[index]
        buckets[index] = bucket.copy(
            clientEgress = bucket.clientEgress + dEgress,
            clientIngress = bucket.clientIngress + dIngress,
        )
    }

    @Synchronized
    fun recordProvider(egress: Long, ingress: Long, nowMillis: Long = System.currentTimeMillis()) {
        val dEgress = delta(lastProviderEgress, egress)
        val dIngress = delta(lastProviderIngress, ingress)
        lastProviderEgress = egress
        lastProviderIngress = ingress
        if (dEgress <= 0 && dIngress <= 0) return
        val index = currentBucketIndex(nowMillis)
        val bucket = buckets[index]
        buckets[index] = bucket.copy(
            providerEgress = bucket.providerEgress + dEgress,
            providerIngress = bucket.providerIngress + dIngress,
        )
    }

    /**
     * A counter that went backwards is a restarted session: count the new
     * value as the delta. The first observation after a resume is a baseline.
     */
    private fun delta(last: Long?, value: Long): Long {
        if (last == null) return 0
        return if (value < last) value else value - last
    }

    private fun currentBucketIndex(nowMillis: Long): Int {
        val start = (nowMillis / 1000 / BUCKET_SECONDS) * BUCKET_SECONDS
        val last = buckets.lastOrNull()
        if (last != null && (last.start == start || start < last.start)) {
            // a late sample for an older bucket folds into the newest one
            return buckets.size - 1
        }
        buckets.add(WidgetThroughputBucket(start = start))
        while (BUCKET_COUNT < buckets.size) {
            buckets.removeAt(0)
        }
        return buckets.size - 1
    }
}

object WidgetSnapshotStore {

    private const val DIRECTORY = "widgets"
    private const val TUNNEL_FILE = "tunnel.json"
    private const val BALANCE_FILE = "balance.json"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private fun directory(context: Context): File = File(context.filesDir, DIRECTORY)

    fun loadTunnel(context: Context): WidgetTunnelSnapshot? =
        load(context, TUNNEL_FILE) { json.decodeFromString<WidgetTunnelSnapshot>(it) }

    fun loadBalance(context: Context): WidgetBalanceSnapshot? =
        load(context, BALANCE_FILE) { json.decodeFromString<WidgetBalanceSnapshot>(it) }

    fun save(context: Context, snapshot: WidgetTunnelSnapshot): Boolean =
        save(context, TUNNEL_FILE, json.encodeToString(snapshot))

    fun save(context: Context, snapshot: WidgetBalanceSnapshot): Boolean =
        save(context, BALANCE_FILE, json.encodeToString(snapshot))

    /** Removes both snapshots (logout). */
    fun clear(context: Context) {
        File(directory(context), TUNNEL_FILE).delete()
        File(directory(context), BALANCE_FILE).delete()
    }

    private fun <T> load(context: Context, name: String, decode: (String) -> T): T? {
        val file = File(directory(context), name)
        if (!file.exists()) return null
        return runCatching { decode(file.readText()) }.getOrNull()
    }

    private fun save(context: Context, name: String, text: String): Boolean {
        return runCatching {
            val dir = directory(context)
            dir.mkdirs()
            // whole-file, atomic: write next to it and rename over
            val tmp = File(dir, "$name.tmp")
            tmp.writeText(text)
            tmp.renameTo(File(dir, name))
        }.getOrDefault(false)
    }
}
