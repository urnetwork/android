package com.bringyour.network

internal const val BANDWIDTH_CONSTRAINT_CAPABILITY_API = 36
internal const val BANDWIDTH_CONSTRAINT_U_EXTENSION = 16

internal enum class BackgroundDataRestriction {
    DISABLED,
    ENABLED,
    ALLOWLISTED,
}

internal data class DefaultNetworkPressure(
    val notCongested: Boolean,
    val notBandwidthConstrained: Boolean,
) {
    val degraded: Boolean
        get() = !notCongested || !notBandwidthConstrained
}

internal data class HostPerformanceFacts(
    val powerSave: Boolean,
    val thermalDegraded: Boolean,
    val dataSaverDegraded: Boolean,
    val defaultNetworkDegraded: Boolean,
)

/** Data Saver affects this app only while its active path is metered. */
internal fun dataSaverDegradesPerformance(
    activeNetworkMetered: Boolean,
    restriction: BackgroundDataRestriction,
): Boolean = activeNetworkMetered && restriction != BackgroundDataRestriction.DISABLED

internal fun hostPerformanceDegraded(facts: HostPerformanceFacts): Boolean =
    facts.powerSave ||
        facts.thermalDegraded ||
        facts.dataSaverDegraded ||
        facts.defaultNetworkDegraded

/**
 * API 36 introduced the public constant; Android 14 also receives it through
 * U extension 16. Keeping the predicate pure makes the compatibility boundary
 * independently testable.
 */
internal fun supportsBandwidthConstraintCapability(
    sdkInt: Int,
    uExtensionVersion: Int,
): Boolean =
    sdkInt >= BANDWIDTH_CONSTRAINT_CAPABILITY_API ||
        (sdkInt >= 34 && uExtensionVersion >= BANDWIDTH_CONSTRAINT_U_EXTENSION)

/** Tracks pressure only on the app's current physical default network. */
internal class DefaultNetworkPressureTracker<T> {
    private var currentNetwork: T? = null
    private var pressure: DefaultNetworkPressure? = null

    val degraded: Boolean
        get() = pressure?.degraded == true

    /** Returns true when the effective degraded state changed. */
    fun onAvailable(network: T): Boolean {
        val previous = degraded
        if (currentNetwork != network) {
            currentNetwork = network
            pressure = null
        }
        return previous != degraded
    }

    /** Returns true when the effective degraded state changed. */
    fun onCapabilitiesChanged(network: T, next: DefaultNetworkPressure): Boolean {
        if (currentNetwork != network) return false
        val previous = degraded
        pressure = next
        return previous != degraded
    }

    /** Returns true when the effective degraded state changed. */
    fun onLost(network: T): Boolean {
        if (currentNetwork != network) return false
        val previous = degraded
        currentNetwork = null
        pressure = null
        return previous != degraded
    }
}
