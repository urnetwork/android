package com.bringyour.network

/**
 * Tracks the app's preferred physical network, including loss -> replacement.
 * The first callback is a baseline; every later identity transition means
 * existing sockets may still be pinned to the former path.
 */
internal class DefaultNetworkTracker<T> {
    private var initialized = false
    private var current: T? = null
    private var lostSinceLastAvailable = false

    fun onAvailable(network: T): Boolean {
        val changed = initialized && (lostSinceLastAvailable || current != network)
        initialized = true
        current = network
        lostSinceLastAvailable = false
        return changed
    }

    fun onLost(network: T) {
        if (initialized && current == network) {
            current = null
            lostSinceLastAvailable = true
        }
    }
}

/** Radio and link estimates for one unchanged default network. */
internal data class DefaultNetworkQuality(
    val wifiSignalLevel: Int?,
    val downstreamBandwidthKbps: Int,
    val upstreamBandwidthKbps: Int,
)

/** Cell bars and displayed radio type arrive on separate telephony callbacks. */
internal class DefaultCellularQualityTracker {
    private var signalLevel: Int? = null
    private var networkType: Pair<Int, Int>? = null

    fun onSignalLevel(next: Int): Boolean {
        val previous = signalLevel
        signalLevel = next
        return previous != null && previous != next
    }

    fun onNetworkType(next: Int, nextOverride: Int): Boolean {
        val value = next to nextOverride
        val previous = networkType
        networkType = value
        return previous != null && previous != value
    }
}

/** Stable five-level bucket; raw dBm jitter must not remeasure every callback. */
internal fun defaultNetworkSignalLevel(signalStrength: Int): Int? = when {
    signalStrength == Int.MIN_VALUE -> null
    signalStrength < -90 -> 0
    signalStrength < -80 -> 1
    signalStrength < -70 -> 2
    signalStrength < -60 -> 3
    else -> 4
}

/** Power-of-two bands retain material capacity changes and ignore estimator noise. */
internal fun defaultNetworkBandwidthBucket(kbps: Int): Int =
    if (kbps <= 0) 0 else Integer.highestOneBit(kbps)

/**
 * Separates a quality update from a default-network identity transition. The
 * first capabilities callback is a baseline; only a changed snapshot on the
 * same live network requests estimator remeasurement.
 */
internal class DefaultNetworkQualityTracker<T> {
    private var current: T? = null
    private var quality: DefaultNetworkQuality? = null
    private var currentIsCellular = false

    fun onAvailable(network: T): Boolean {
        if (current != network) {
            current = network
            quality = null
            currentIsCellular = false
            return true
        }
        return false
    }

    fun onCapabilitiesChanged(
        network: T,
        next: DefaultNetworkQuality,
        isCellular: Boolean = false,
    ): Boolean {
        if (current != network) return false
        currentIsCellular = isCellular
        val previous = quality
        quality = next
        return previous != null && previous != next
    }

    fun currentIsCellular(): Boolean = current != null && currentIsCellular

    fun onLost(network: T): Boolean {
        if (current == network) {
            current = null
            quality = null
            currentIsCellular = false
            return true
        }
        return false
    }
}
