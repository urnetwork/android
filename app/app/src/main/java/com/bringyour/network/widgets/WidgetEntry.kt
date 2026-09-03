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

        /**
         * The entry Account > Widgets renders: the real one whenever there is an
         * account to show and a tunnel snapshot has been written since install
         * or the last logout; otherwise the sample (a guest, or nothing published
         * yet), so the screen never shows an empty preview.
         */
        fun loadOrSample(context: Context): WidgetEntry {
            val entry = load(context)
            return if (entry.isConfigured && WidgetSnapshotStore.hasTunnelSnapshot(context)) entry else sample(entry.nowMillis)
        }

        /** What the picker preview shows: a connected tunnel with a few providers and an hour of traffic. */
        fun sample(nowMillis: Long = System.currentTimeMillis()): WidgetEntry {
            val now = nowMillis / 1000
            val bucketSeconds = WidgetThroughputAccumulator.BUCKET_SECONDS
            val count = WidgetThroughputAccumulator.BUCKET_COUNT
            // real traffic: a quiet floor with a few bursts that spike and decay
            val clientBursts = burstSeries(count, listOf(Burst(7, 5_200_000.0, 0.62), Burst(19, 2_400_000.0, 0.5), Burst(31, 8_100_000.0, 0.7), Burst(46, 3_600_000.0, 0.55), Burst(55, 1_500_000.0, 0.45)), floor = 60_000.0, seed = 17)
            val providerBursts = burstSeries(count, listOf(Burst(11, 1_300_000.0, 0.6), Burst(38, 900_000.0, 0.55), Burst(52, 1_900_000.0, 0.65)), floor = 25_000.0, seed = 41)
            // packets follow the bytes at real packet sizes: downloads ride in
            // near-full packets, uploads and acks are small, and a little
            // chatter keeps the packet line alive between bursts
            val chatter = burstSeries(count, emptyList(), 90.0, seed = 0x5eed7L)
            val buckets = (0 until count).map { i ->
                val start = ((now / bucketSeconds) - (count - 1 - i)) * bucketSeconds
                val client = clientBursts[i]
                val provider = providerBursts[i]
                val clientEgress = client * 0.18
                val providerIngress = provider * 0.3
                WidgetThroughputBucket(
                    start = start,
                    clientEgress = clientEgress.toLong(),
                    clientIngress = client.toLong(),
                    providerEgress = provider.toLong(),
                    providerIngress = providerIngress.toLong(),
                    clientEgressPackets = (clientEgress / 180.0 + chatter[i]).toLong(),
                    clientIngressPackets = (client / 1100.0 + chatter[i] * 0.6).toLong(),
                    providerEgressPackets = (provider / 1000.0 + chatter[i] * 0.5).toLong(),
                    providerIngressPackets = (providerIngress / 160.0 + chatter[i]).toLong(),
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
                provideMode = "auto",
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

/** A traffic burst in the sample series: starts at a bucket, peaks, and decays by `decay` per bucket. */
private class Burst(val at: Int, val peak: Double, val decay: Double)

/**
 * A bytes-per-bucket series shaped like real traffic: a low, jittery floor
 * with bursts that jump up and tail off. Deterministic for a given seed so
 * the preview never flickers.
 */
private fun burstSeries(count: Int, bursts: List<Burst>, floor: Double, seed: Long): DoubleArray {
    var state = seed
    fun noise(): Double {
        // a small linear congruential generator: enough for jitter, no randomness API needed
        state = (state * 6364136223846793005L + 1442695040888963407L)
        return ((state ushr 33) % 1000) / 1000.0
    }
    val series = DoubleArray(count) { floor * (0.6 + 0.8 * noise()) }
    for (burst in bursts) {
        var level = burst.peak
        var i = burst.at
        while (i < count && 0.02 * burst.peak < level) {
            series[i] += level * (0.85 + 0.3 * noise())
            level *= burst.decay
            i += 1
        }
        // a short ramp into the burst, one bucket before the peak
        if (0 < burst.at) series[burst.at - 1] += burst.peak * 0.3 * noise()
    }
    return series
}
