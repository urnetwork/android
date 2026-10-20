package com.bringyour.network

internal data class AvailableNetworkChange(
    val available: Boolean,
    val availabilityChanged: Boolean,
    val topologyChanged: Boolean,
    /** A path returned after there was none, past the initial baseline. */
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
        var capabilities: PhysicalNetworkCapabilitiesSnapshot? = null,
        var blocked: Boolean? = null,
    )

    private val networks = LinkedHashMap<T, NetworkRecord>()

    // Whether the device's path state before the next delivery is known: from
    // a first delivery, or from a registration that found no network at all.
    private var baselineKnown = false

    val available: Boolean
        get() = networks.isNotEmpty()

    val size: Int
        get() = networks.size

    /**
     * The registration found no matching network, so the first one to arrive
     * returns the device from offline instead of describing the current state.
     */
    fun startedWithoutNetwork() {
        if (networks.isEmpty()) {
            baselineKnown = true
        }
    }

    /**
     * Only a path arriving while there was none requires recovery. A second
     * network joining leaves existing sockets on the path they use.
     */
    fun onAvailable(network: T): AvailableNetworkChange {
        val wasAvailable = available
        val added = !networks.containsKey(network)
        val recoveryRequired = added && !wasAvailable && baselineKnown
        if (added) {
            networks[network] = NetworkRecord()
            baselineKnown = true
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
            // A remaining network carries no stranded socket: sockets follow
            // the physical default, whose change is tracked on its own. If the
            // last path disappeared, defer recovery until one returns.
            recoveryRequired = false,
        )
    }

    /**
     * Android sends an initial capabilities callback immediately after
     * onAvailable. It is a baseline, not a path transition. A transport-set
     * change is reported for diagnostics only: the platform keeps a physical
     * network's transports fixed, and a real path move is a physical default
     * change. Validation/suspension recovery requests a health audit first so
     * transient captive-portal signals do not destructively churn an otherwise
     * healthy tunnel.
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
