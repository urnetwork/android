package com.bringyour.network.widgets

import android.content.Context
import com.bringyour.network.MainApplication
import com.bringyour.network.QuickConnect
import kotlin.math.exp
import kotlin.math.roundToLong

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

        /**
         * What the picker preview and the onboarding widgets step show: a
         * connected tunnel with a few providers, an hour of client traffic and
         * providing set to never, so the provider block reads the way it does
         * for a fresh account.
         */
        fun sample(nowMillis: Long = System.currentTimeMillis()): WidgetEntry {
            val now = nowMillis / 1000
            val bucketSeconds = WidgetThroughputAccumulator.BUCKET_SECONDS
            val count = WidgetThroughputAccumulator.BUCKET_COUNT
            // real client traffic: an idle floor with a handful of sharp bursts;
            // the provider side stays empty because the sample provides never
            val clientBytes = sampleSeries(count, bucketSeconds, SAMPLE_BYTE_FLOOR, SAMPLE_BYTE_BURSTS, unit = 1024.0)
            val clientPackets = sampleSeries(count, bucketSeconds, SAMPLE_PACKET_FLOOR, SAMPLE_PACKET_BURSTS, unit = 1.0)
            val buckets = (0 until count).map { i ->
                val start = ((now / bucketSeconds) - (count - 1 - i)) * bucketSeconds
                // downloads dominate; the acks riding back are a tenth of the
                // bytes and one per two data packets
                WidgetThroughputBucket(
                    start = start,
                    clientEgress = (clientBytes[i] * SAMPLE_ACK_BYTE_SHARE).roundToLong(),
                    clientIngress = clientBytes[i].roundToLong(),
                    clientEgressPackets = (clientPackets[i] * SAMPLE_ACK_PACKET_SHARE).roundToLong(),
                    clientIngressPackets = clientPackets[i].roundToLong(),
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
                provideMode = "never",
                updatedAtMillis = nowMillis,
                tunnelActive = true,
                providing = false,
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

/** One burst in the sample series: a bell `amplitude * exp(-((t - center) / width)^2)` over the chart width t in [0, 1]. */
private class Burst(val center: Double, val width: Double, val amplitude: Double)

/** A burst center sitting exactly on sample bucket `bucket` of the window, so its peak lands on a bucket by construction. */
private fun onBucket(bucket: Int): Double = bucket.toDouble() / (WidgetThroughputAccumulator.BUCKET_COUNT - 1)

// The sample client line, shared with the Apple sample so both previews
// compute the same numbers: an idle floor with six sharp bursts, the tallest
// on the last-but-two bucket, in KiB/s and packets/s. The peaks the client
// row labels follow from the table: 410 KiB/s and 594 pkt/s at bucket 57.
private const val SAMPLE_BYTE_FLOOR = 6.0
private val SAMPLE_BYTE_BURSTS = listOf(
    Burst(onBucket(8), 0.012, 60.0), Burst(onBucket(15), 0.010, 330.0), Burst(onBucket(22), 0.012, 190.0),
    Burst(onBucket(27), 0.010, 160.0), Burst(onBucket(47), 0.015, 45.0), Burst(onBucket(57), 0.012, 404.0),
)
private const val SAMPLE_PACKET_FLOOR = 9.0
private val SAMPLE_PACKET_BURSTS = listOf(
    Burst(onBucket(8), 0.014, 110.0), Burst(onBucket(15), 0.011, 470.0), Burst(onBucket(22), 0.013, 300.0),
    Burst(onBucket(27), 0.012, 260.0), Burst(onBucket(47), 0.016, 90.0), Burst(onBucket(57), 0.013, 585.0),
)
/** Acks riding back against a download: a tenth of the bytes, one ack per two data packets. */
private const val SAMPLE_ACK_BYTE_SHARE = 0.1
private const val SAMPLE_ACK_PACKET_SHARE = 0.5

/**
 * A per-bucket series shaped like real traffic: the floor plus the bursts,
 * sampled once per bucket across the window (t = bucket / (count - 1)) and
 * turned into a bucket total by `unit` (bytes per KiB, or 1 for packets) times
 * the bucket length. Deterministic, so the preview never flickers.
 */
private fun sampleSeries(count: Int, bucketSeconds: Long, floor: Double, bursts: List<Burst>, unit: Double): DoubleArray =
    DoubleArray(count) { i ->
        val t = if (count <= 1) 0.0 else i.toDouble() / (count - 1)
        val rate = floor + bursts.sumOf { burst ->
            val d = (t - burst.center) / burst.width
            burst.amplitude * exp(-d * d)
        }
        rate * unit * bucketSeconds
    }
