package com.bringyour.network.widgets

import android.content.Context
import com.bringyour.network.MainApplication
import com.bringyour.network.QuickConnect

/**
 * Everything a widget composition needs, read once per update. The on/off
 * question is answered by the live SDK device (in-process), never by the
 * snapshot, so a toggle from the tile or the app reads correctly before the
 * writer has published anything.
 */
class WidgetEntry(
    val nowMillis: Long,
    val tunnel: WidgetTunnelSnapshot,
    val balance: WidgetBalanceSnapshot?,
    /** The user asked for a connection (the tile's notion of on). */
    val isOn: Boolean,
    /** A signed-in device exists to drive. */
    val isConfigured: Boolean,
) {
    /** The tunnel snapshot is meaningful only while the tunnel that wrote it is up. */
    val showsTunnelData: Boolean get() = isOn && tunnel.tunnelActive

    companion object {
        /** Debug builds only: a flag file that switches the widgets to the sample entry. */
        const val SAMPLE_FLAG = "widgets/sample"

        fun load(context: Context): WidgetEntry {
            val app = context.applicationContext as? MainApplication
            val now = System.currentTimeMillis()
            if (com.bringyour.network.BuildConfig.DEBUG && java.io.File(context.filesDir, SAMPLE_FLAG).exists()) {
                return sample(now)
            }
            return WidgetEntry(
                nowMillis = now,
                tunnel = WidgetSnapshotStore.loadTunnel(context) ?: WidgetTunnelSnapshot.inactive(now),
                balance = WidgetSnapshotStore.loadBalance(context),
                isOn = app?.let { QuickConnect.isConnected(it) } ?: false,
                isConfigured = app?.let { QuickConnect.isConfigured(it) } ?: false,
            )
        }

        /** What the picker preview shows: a connected tunnel with a few providers and an hour of traffic. */
        fun sample(nowMillis: Long = System.currentTimeMillis()): WidgetEntry {
            val now = nowMillis / 1000
            val bucketSeconds = WidgetThroughputAccumulator.BUCKET_SECONDS
            val buckets = (0 until WidgetThroughputAccumulator.BUCKET_COUNT).map { i ->
                val start = ((now / bucketSeconds) - (WidgetThroughputAccumulator.BUCKET_COUNT - 1 - i)) * bucketSeconds
                val phase = i / 9.0
                val client = (6_000_000 + 5_000_000 * Math.sin(phase) + 2_000_000 * Math.sin(phase * 3.1)).toLong()
                val provider = (1_500_000 + 1_200_000 * Math.sin(phase * 0.7 + 1)).toLong()
                WidgetThroughputBucket(
                    start = start,
                    clientEgress = (client / 4).coerceAtLeast(0),
                    clientIngress = client.coerceAtLeast(0),
                    providerEgress = provider.coerceAtLeast(0),
                    providerIngress = (provider / 3).coerceAtLeast(0),
                )
            }
            val providers = listOf(
                WidgetProviderSnapshot("sample-1", "Japan", "jp", "Tokyo", "Tokyo", 35.68, 139.69, (now - 3 * 3600) * 1000, "F94144"),
                WidgetProviderSnapshot("sample-2", "Germany", "de", "Berlin", "Berlin", 52.52, 13.40, (now - 1500) * 1000, "663F46"),
                WidgetProviderSnapshot("sample-3", "Brazil", "br", "São Paulo", "São Paulo", -23.55, -46.63, (now - 240) * 1000, "43AA8B"),
            )
            val mib = 1024L * 1024L
            fun contract(id: String, used: Long, total: Long, rate: Long, stream: Boolean = false) =
                WidgetContractSnapshot(id, used, total, rate, stream)
            val contracts = listOf(
                WidgetContractPeerSnapshot(
                    "0199a2b4c6d8e0f2",
                    listOf(contract("s1", 12 * mib, 32 * mib, 2_400_000), contract("s2", 30 * mib, 32 * mib, 0)),
                    listOf(contract("r1", 3 * mib, 64 * mib, 8_100_000, stream = true)),
                    42 * mib, 3 * mib, nowMillis,
                ),
                WidgetContractPeerSnapshot(
                    "44f1a2b4c6d8e0f2",
                    listOf(contract("s3", 6 * mib, 16 * mib, 600_000)),
                    listOf(contract("r2", 15 * mib, 16 * mib, 0), contract("r3", 16 * mib, 16 * mib, 0)),
                    6 * mib, 31 * mib, nowMillis - 20_000,
                ),
                WidgetContractPeerSnapshot(
                    "9c3e5a7b9d1f3a5c", emptyList(), listOf(contract("r4", 1 * mib, 32 * mib, 0)),
                    0, 1 * mib, nowMillis - 300_000,
                ),
                WidgetContractPeerSnapshot(
                    "b7d9f1a3c5e7a9b1",
                    listOf(contract("s4", 8 * mib, 8 * mib, 0), contract("s5", 2 * mib, 8 * mib, 0)),
                    listOf(contract("r5", 4 * mib, 8 * mib, 0)),
                    10 * mib, 4 * mib, nowMillis - 900_000,
                ),
            )
            val tunnel = WidgetTunnelSnapshot(
                updatedAtMillis = nowMillis,
                tunnelActive = true,
                providing = true,
                location = WidgetLocationSnapshot("Japan", "jp", "", "", "Japan", false, false, 3, "F94144"),
                providers = providers,
                throughput = WidgetThroughputSnapshot(bucketSeconds, buckets),
                contracts = contracts,
            )
            val balance = WidgetBalanceSnapshot(
                updatedAtMillis = nowMillis,
                startBalanceByteCount = 24L * 1024 * 1024 * 1024,
                balanceByteCount = 15L * 1024 * 1024 * 1024,
                openTransferByteCount = 1L * 1024 * 1024 * 1024,
                isPro = false,
            )
            return WidgetEntry(nowMillis, tunnel, balance, isOn = true, isConfigured = true)
        }
    }
}
