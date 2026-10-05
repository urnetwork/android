package com.bringyour.network.ui.shared.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The connect view controller's status strings. CONNECT_FAILED (the connect
 * window passed both outcome deadlines with no provider added) used to fall
 * through to null, so the connect screen kept its last status, "Connecting to
 * providers", while nothing could be reached.
 */
class ConnectStatusTest {

    // every status the sdk's ConnectViewController reports (sdk
    // connect_view_controller.go ConnectionStatus)
    private val sdkStatuses = mapOf(
        "DISCONNECTED" to ConnectStatus.DISCONNECTED,
        "CONNECTING" to ConnectStatus.CONNECTING,
        "DESTINATION_SET" to ConnectStatus.DESTINATION_SET,
        "CONNECTED" to ConnectStatus.CONNECTED,
        "CONNECT_FAILED" to ConnectStatus.CONNECT_FAILED,
    )

    @Test
    fun connectFailedIsMapped() {
        assertEquals(ConnectStatus.CONNECT_FAILED, ConnectStatus.fromString("CONNECT_FAILED"))
        assertEquals(ConnectStatus.CONNECT_FAILED, ConnectStatus.fromString("connect_failed"))
    }

    @Test
    fun everySdkStatusIsMapped() {
        for ((value, status) in sdkStatuses) {
            assertEquals(value, status, ConnectStatus.fromString(value))
        }
    }

    @Test
    fun everyStatusRoundTrips() {
        for (status in ConnectStatus.entries) {
            assertEquals(status, ConnectStatus.fromString(ConnectStatus.toString(status)))
        }
        assertEquals(sdkStatuses.values.toSet(), ConnectStatus.entries.toSet())
    }

    @Test
    fun anUnknownStatusIsNotMapped() {
        assertNull(ConnectStatus.fromString(""))
        assertNull(ConnectStatus.fromString("FAILED"))
        assertNull(ConnectStatus.fromString("CONNECT FAILED"))
    }
}
