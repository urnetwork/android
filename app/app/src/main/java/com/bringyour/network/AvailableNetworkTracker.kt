package com.bringyour.network

internal data class AvailableNetworkChange(
    val available: Boolean,
    val availabilityChanged: Boolean,
    val topologyChanged: Boolean,
    /** A live path changed after the initial callback baseline. */
    val recoveryRequired: Boolean,
)

/**
 * Only the capability bits that materially describe whether a physical path
 * can carry a recovered tunnel. Keeping this Android-free makes transition
 * handling deterministic in local tests.
 */
internal data class PhysicalNetworkCapabilitiesSnapshot(
    val validated: Boolean,
    val notSuspended: Boolean,
    val captivePortal: Boolean,
    val partialConnectivity: Boolean,
    val transports: Set<Int>,
)

internal data class PhysicalNetworkAttributeChange(
    val knownNetwork: Boolean,
    val baseline: Boolean = false,
    val changed: Boolean = false,
    val transportChanged: Boolean = false,
    val recovered: Boolean = false,
    val degraded: Boolean = false,
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
    private data class NetworkRecord(
        var linkFingerprint: String? = null,
        var capabilities: PhysicalNetworkCapabilitiesSnapshot? = null,
        var blocked: Boolean? = null,
    )

    private val networks = LinkedHashMap<T, NetworkRecord>()
    private var hasSeenAvailableNetwork = false

    val available: Boolean
        get() = networks.isNotEmpty()

    val size: Int
        get() = networks.size

    fun onAvailable(network: T): AvailableNetworkChange {
        val wasAvailable = available
        val added = !networks.containsKey(network)
        val recoveryRequired = added && hasSeenAvailableNetwork
        if (added) {
            networks[network] = NetworkRecord()
            hasSeenAvailableNetwork = true
        }
        return AvailableNetworkChange(
            available = true,
            availabilityChanged = !wasAvailable,
            topologyChanged = added,
            recoveryRequired = recoveryRequired,
        )
    }

    fun onLost(network: T): AvailableNetworkChange {
        val wasAvailable = available
        val removed = networks.containsKey(network)
        if (removed) {
            networks.remove(network)
        }
        return AvailableNetworkChange(
            available = available,
            availabilityChanged = wasAvailable != available,
            topologyChanged = removed,
            // If another network remains, socket routing can move to it now.
            // If the last path disappeared, defer recovery until one returns.
            recoveryRequired = removed && available,
        )
    }

    /**
     * Returns true only for a material change after the initial fingerprint.
     * Android sends the initial LinkProperties immediately after onAvailable;
     * that establishes the baseline and must not cause a duplicate reconnect.
     */
    fun onLinkPropertiesChanged(network: T, fingerprint: String): Boolean {
        val record = networks[network] ?: run {
            return false
        }
        val previous = record.linkFingerprint
        record.linkFingerprint = fingerprint
        return previous != null && previous != fingerprint
    }

    /**
     * Android sends an initial capabilities callback immediately after
     * onAvailable. It is a baseline, not a path transition. Later transport
     * identity changes require a socket reset; validation/suspension recovery
     * requests a health audit first so transient captive-portal signals do not
     * destructively churn an otherwise healthy tunnel.
     */
    fun onCapabilitiesChanged(
        network: T,
        next: PhysicalNetworkCapabilitiesSnapshot,
    ): PhysicalNetworkAttributeChange {
        val record = networks[network]
            ?: return PhysicalNetworkAttributeChange(knownNetwork = false)
        val previous = record.capabilities
        record.capabilities = next
        if (previous == null) {
            return PhysicalNetworkAttributeChange(knownNetwork = true, baseline = true)
        }
        if (previous == next) {
            return PhysicalNetworkAttributeChange(knownNetwork = true)
        }

        val recovered =
            (!previous.validated && next.validated) ||
                (!previous.notSuspended && next.notSuspended) ||
                (previous.captivePortal && !next.captivePortal) ||
                (previous.partialConnectivity && !next.partialConnectivity)
        val degraded =
            (previous.validated && !next.validated) ||
                (previous.notSuspended && !next.notSuspended) ||
                (!previous.captivePortal && next.captivePortal) ||
                (!previous.partialConnectivity && next.partialConnectivity)
        return PhysicalNetworkAttributeChange(
            knownNetwork = true,
            changed = true,
            transportChanged = previous.transports != next.transports,
            recovered = recovered,
            degraded = degraded,
        )
    }

    /** API 29+'s initial blocked callback is also only a baseline. */
    fun onBlockedStatusChanged(network: T, blocked: Boolean): PhysicalNetworkAttributeChange {
        val record = networks[network]
            ?: return PhysicalNetworkAttributeChange(knownNetwork = false)
        val previous = record.blocked
        record.blocked = blocked
        if (previous == null) {
            return PhysicalNetworkAttributeChange(knownNetwork = true, baseline = true)
        }
        if (previous == blocked) {
            return PhysicalNetworkAttributeChange(knownNetwork = true)
        }
        return PhysicalNetworkAttributeChange(
            knownNetwork = true,
            changed = true,
            recovered = previous && !blocked,
            degraded = !previous && blocked,
        )
    }
}
