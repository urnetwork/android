package com.bringyour.network.ui.shared.models

enum class ProvideNetworkMode {
    WIFI,
    ALL;

    companion object {
        fun fromString(value: String): ProvideNetworkMode? {
            return when (value.lowercase()) {
                "wifi" -> WIFI
                "all" -> ALL
                else -> null
            }
        }

        fun toString(value: ProvideNetworkMode): String {
            return when (value) {
                WIFI -> "wifi"
                ALL -> "all"
            }
        }
    }
}