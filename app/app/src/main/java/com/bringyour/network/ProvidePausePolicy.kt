package com.bringyour.network

import com.bringyour.network.ui.shared.models.ProvidePowerMode

/**
 * Why providing is paused, in the order the policy checks its sources.
 */
enum class ProvidePauseReason {
    NONE,
    // no network matches the provide network mode (Wi-Fi only, or none at all)
    NO_NETWORK,
    BATTERY_SAVER,
    NOT_CHARGING,
}

/** Whether providing is paused, and the first source that pauses it. */
data class ProvidePauseDecision(
    val paused: Boolean,
    val reason: ProvidePauseReason,
) {
    companion object {
        val Providing = ProvidePauseDecision(paused = false, reason = ProvidePauseReason.NONE)
    }
}

/** The pause sources the decision reads, as the application last saw them. */
internal data class ProvidePauseFacts(
    /**
     * a network that matches the provide network mode is available
     */
    val networkAvailable: Boolean,
    /**
     * Battery Saver is on
     */
    val powerSave: Boolean,
    /**
     * the device is on external power
     */
    val charging: Boolean,
    /**
     * the battery level, 0..100; null when the device reports no battery
     */
    val batteryPct: Int?,
    val mode: ProvidePowerMode,
)

/**
 * The one decision `device.providePaused` is set from. Every pause source is
 * combined here, so the network callback finding a network cannot clear a
 * battery pause, and the charger cannot clear a network pause. A device
 * without a battery is always on external power.
 */
internal fun providePauseDecision(facts: ProvidePauseFacts): ProvidePauseDecision {
    val onExternalPower = facts.charging || facts.batteryPct == null
    val reason = when {
        !facts.networkAvailable -> ProvidePauseReason.NO_NETWORK
        facts.mode == ProvidePowerMode.ALWAYS -> ProvidePauseReason.NONE
        facts.powerSave -> ProvidePauseReason.BATTERY_SAVER
        facts.mode == ProvidePowerMode.CHARGING_ONLY && !onExternalPower -> ProvidePauseReason.NOT_CHARGING
        else -> ProvidePauseReason.NONE
    }
    return ProvidePauseDecision(paused = reason != ProvidePauseReason.NONE, reason = reason)
}

/**
 * The power facts in the sticky `ACTION_BATTERY_CHANGED`.
 */
internal data class BatteryPowerFacts(
    val charging: Boolean,
    val batteryPct: Int?,
) {
    companion object {
        // nothing read yet: never pause for power on a guess
        val Unknown = BatteryPowerFacts(charging = false, batteryPct = null)
    }
}

/**
 * Plugged in on any source counts as charging, also while the battery is
 * full or held at a charge limit (the status then reads full or not
 * charging). A device that reports no battery has no level.
 */
internal fun batteryPowerFacts(
    plugged: Int,
    present: Boolean,
    level: Int,
    scale: Int,
): BatteryPowerFacts {
    val batteryPct = if (present && 0 <= level && 0 < scale) {
        (level * 100 / scale).coerceIn(0, 100)
    } else {
        null
    }
    return BatteryPowerFacts(charging = 0 < plugged, batteryPct = batteryPct)
}
