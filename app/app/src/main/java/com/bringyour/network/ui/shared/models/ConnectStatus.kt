package com.bringyour.network.ui.shared.models

enum class ConnectStatus {
    DISCONNECTED,
    CONNECTING,
    DESTINATION_SET,
    CONNECTED;

    companion object {
        fun fromString(value: String): ConnectStatus? {
            return when (value.uppercase()) {
                "DISCONNECTED" -> DISCONNECTED
                "CONNECTING" -> CONNECTING
                "DESTINATION_SET" -> DESTINATION_SET
                "CONNECTED" -> CONNECTED
                else -> null
            }
        }

        fun toString(value: ConnectStatus): String {
            return when (value) {
                DISCONNECTED -> "DISCONNECTED"
                CONNECTING -> "CONNECTING"
                DESTINATION_SET -> "DESTINATION_SET"
                CONNECTED -> "CONNECTED"
            }
        }
    }
}