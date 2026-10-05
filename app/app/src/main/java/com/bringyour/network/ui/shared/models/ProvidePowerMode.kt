package com.bringyour.network.ui.shared.models

import com.bringyour.network.R

/**
 * When this device provides while it runs on battery. Stored with the app,
 * not the network space (see `ProvidePauseState`): it is about this device's
 * battery, not the account.
 */
enum class ProvidePowerMode {
    ALWAYS,
    // the default
    PAUSE_IN_BATTERY_SAVER,
    // provide only on external power
    CHARGING_ONLY;

    companion object {
        val DEFAULT = PAUSE_IN_BATTERY_SAVER

        /** The mode a stored value names, or null for any other value. */
        fun fromString(value: String?): ProvidePowerMode? {
            return when (value?.lowercase()) {
                "always" -> ALWAYS
                "pause_in_battery_saver" -> PAUSE_IN_BATTERY_SAVER
                "charging_only" -> CHARGING_ONLY
                else -> null
            }
        }

        /** The value [value] is stored as. */
        fun toString(value: ProvidePowerMode): String {
            return when (value) {
                ALWAYS -> "always"
                PAUSE_IN_BATTERY_SAVER -> "pause_in_battery_saver"
                CHARGING_ONLY -> "charging_only"
            }
        }

        /** The picker's label for [value]. */
        fun toStringResourceId(value: ProvidePowerMode): Int {
            return when (value) {
                ALWAYS -> R.string.provide_power_mode_always
                PAUSE_IN_BATTERY_SAVER -> R.string.provide_power_mode_pause_in_battery_saver
                CHARGING_ONLY -> R.string.provide_power_mode_charging_only
            }
        }
    }
}
