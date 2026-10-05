package com.bringyour.network.ui.shared.models

enum class ConnectStatus {
    DISCONNECTED,
    CONNECTING,
    DESTINATION_SET,
    CONNECTED,
    // the sdk's CONNECT_FAILED: the connect window passed both of its outcome
    // deadlines with no provider added. The session is still standing and keeps
    // trying, so a provider that lands later moves it back to CONNECTING or
    // CONNECTED; until then the app shows the failure and offers Retry.
    CONNECT_FAILED;

    companion object {
        fun fromString(value: String): ConnectStatus? {
            return when (value.uppercase()) {
                "DISCONNECTED" -> DISCONNECTED
                "CONNECTING" -> CONNECTING
                "DESTINATION_SET" -> DESTINATION_SET
                "CONNECTED" -> CONNECTED
                "CONNECT_FAILED" -> CONNECT_FAILED
                else -> null
            }
        }

        fun toString(value: ConnectStatus): String {
            return when (value) {
                DISCONNECTED -> "DISCONNECTED"
                CONNECTING -> "CONNECTING"
                DESTINATION_SET -> "DESTINATION_SET"
                CONNECTED -> "CONNECTED"
                CONNECT_FAILED -> "CONNECT_FAILED"
            }
        }
    }
}
