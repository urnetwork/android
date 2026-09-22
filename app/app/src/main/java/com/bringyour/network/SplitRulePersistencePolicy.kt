package com.bringyour.network

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
 *
 * This object is the single source of truth for the two decisions
 * (where a write lands, and which source an effective value is read from)
 * that [DeviceManager] performs. Production goes through these functions so
 * the behavior is covered by the unit tests in this package.
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
     * Resolves the effective value of a setting from its two sources, honoring
     * the caller's notion of "live source present":
     *
     * - when [livePresent] is true, the live device value is used even when it
     *   is null or empty (an empty list is an answer, not a reason to fall
     *   back; the live device's own state is authoritative);
     * - when [livePresent] is false, the persisted local value is used, else
     *   null.
     *
     * The [livePresent] flag lets each caller express its original precedence
     * rule exactly: block action overrides key off "a device exists", while
     * dns resolver settings key off "the device reported a value".
     */
    fun <T> resolveEffective(
        livePresent: Boolean,
        live: T?,
        stored: T?,
    ): T? = if (livePresent) live else stored

    /**
     * Resolves the effective ad/tracker blocker setting: live device ->
     * persisted local state -> default (false).
     */
    fun resolveEffectiveBlocker(
        liveBlocker: Boolean?,
        storedBlocker: Boolean?,
        defaultBlocker: Boolean = false,
    ): Boolean = liveBlocker ?: storedBlocker ?: defaultBlocker
}