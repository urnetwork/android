package com.bringyour.network.ui.stats

/**
 * Deterministic persistence policy for split-tunnel overrides and DNS settings.
 *
 * Guarantees:
 * 1. Offline mutations survive: when disconnected (device is null), mutations
 *    must be persisted to local storage (localState) so they are preserved
 *    across app restarts, system reboots, and app updates.
 * 2. Online mutations are applied live only: the device persists them itself,
 *    in order, on its serial local state queue. A second, synchronous write
 *    from the app races that queue and can land a stale or duplicated list.
 * 3. Fallback resolution: when reading active settings, the live device takes
 *    precedence; if the device is null or uninitialized, it falls back to
 *    persisted local storage, ensuring settings never appear wiped when offline.
 */
data class PersistenceWritePlan(
    val applyLive: Boolean,
    val persistToStorage: Boolean,
)

object SplitRulePersistencePolicy {

    /**
     * Determines where a settings or override write should be directed.
     * Every write lands in exactly one place: storage when offline, the live
     * device (which persists it) when online.
     */
    fun planWrite(isDeviceConnected: Boolean): PersistenceWritePlan {
        return PersistenceWritePlan(
            applyLive = isDeviceConnected,
            persistToStorage = !isDeviceConnected,
        )
    }

    /**
     * Resolves effective overrides following the hierarchy:
     * active viewController -> live device -> persisted local state -> empty fallback.
     */
    fun <T> resolveEffectiveOverrides(
        viewControllerOverrides: List<T>?,
        deviceOverrides: List<T>?,
        localStateOverrides: List<T>?,
    ): List<T> {
        return viewControllerOverrides
            ?: deviceOverrides
            ?: localStateOverrides
            ?: emptyList()
    }

    /**
     * Resolves effective DNS settings following the hierarchy:
     * live device -> persisted local state -> default settings.
     */
    fun <T> resolveEffectiveDns(
        liveSettings: T?,
        storedSettings: T?,
        defaultSettings: T,
    ): T {
        return liveSettings
            ?: storedSettings
            ?: defaultSettings
    }

    /**
     * Resolves effective ad/tracker blocker setting following the hierarchy:
     * live device -> persisted local state -> default false.
     */
    fun resolveEffectiveBlocker(
        liveBlocker: Boolean?,
        storedBlocker: Boolean?,
        defaultBlocker: Boolean = false,
    ): Boolean {
        return liveBlocker
            ?: storedBlocker
            ?: defaultBlocker
    }
}
