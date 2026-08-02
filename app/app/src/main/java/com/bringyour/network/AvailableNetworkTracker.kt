package com.bringyour.network

internal data class AvailableNetworkChange(
    val available: Boolean,
    val availabilityChanged: Boolean,
    val topologyChanged: Boolean,
)

/**
 * Tracks every network delivered by a passive ConnectivityManager callback.
 *
 * A regular network callback can own more than one matching network at once.
 * Treating the most recent callback as the only network makes an older
 * onLost callback incorrectly report the device offline while another path is
 * still usable.
 */
internal class AvailableNetworkTracker<T> {
    private val linkFingerprints = LinkedHashMap<T, String?>()

    val available: Boolean
        get() = linkFingerprints.isNotEmpty()

    val size: Int
        get() = linkFingerprints.size

    fun onAvailable(network: T): AvailableNetworkChange {
        val wasAvailable = available
        val added = !linkFingerprints.containsKey(network)
        if (added) {
            linkFingerprints[network] = null
        }
        return AvailableNetworkChange(
            available = true,
            availabilityChanged = !wasAvailable,
            topologyChanged = added,
        )
    }

    fun onLost(network: T): AvailableNetworkChange {
        val wasAvailable = available
        val removed = linkFingerprints.containsKey(network)
        if (removed) {
            linkFingerprints.remove(network)
        }
        return AvailableNetworkChange(
            available = available,
            availabilityChanged = wasAvailable != available,
            topologyChanged = removed,
        )
    }

    /**
     * Returns true only for a material change after the initial fingerprint.
     * Android sends the initial LinkProperties immediately after onAvailable;
     * that establishes the baseline and must not cause a duplicate reconnect.
     */
    fun onLinkPropertiesChanged(network: T, fingerprint: String): Boolean {
        if (!linkFingerprints.containsKey(network)) {
            return false
        }
        val previous = linkFingerprints[network]
        linkFingerprints[network] = fingerprint
        return previous != null && previous != fingerprint
    }
}
