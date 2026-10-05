package com.bringyour.network.ui.connect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectFailurePolicyTest {
    private val start = 1_000_000L
    private val timeout = CONNECT_FAILURE_TIMEOUT_MILLIS

    private fun failed(
        connectRequested: Boolean = true,
        status: String? = "CONNECTING",
        window: Int = 0,
        startedAt: Long? = start,
        now: Long = start + timeout,
    ) = connectAttemptFailed(connectRequested, status, window, startedAt, now)

    @Test
    fun neverFailedWhileTheUserDoesNotWantToBeConnected() {
        // a disconnect is never a failure, even with the sdk verdict or the bound elapsed
        assertFalse(failed(connectRequested = false))
        assertFalse(failed(connectRequested = false, status = CONNECTION_STATUS_CONNECT_FAILED))
    }

    @Test
    fun sdkConnectFailedFailsAtOnce() {
        assertTrue(failed(status = CONNECTION_STATUS_CONNECT_FAILED, now = start))
    }

    @Test
    fun neverFailedOnceConnected() {
        assertFalse(failed(status = CONNECTION_STATUS_CONNECTED))
    }

    @Test
    fun aProviderInTheWindowIsNotAFailure() {
        assertFalse(failed(window = 1))
    }

    @Test
    fun noProviderPastTheTimeBoundFails() {
        assertTrue(failed(status = "CONNECTING", now = start + timeout))
        assertTrue(failed(status = "DESTINATION_SET", now = start + timeout))
    }

    @Test
    fun noProviderInsideTheTimeBoundIsNotYetAFailure() {
        assertFalse(failed(now = start + timeout - 1))
    }

    @Test
    fun noAttemptStartIsNotAFailure() {
        assertFalse(failed(startedAt = null))
    }

    @Test
    fun aDisconnectedOrUnknownStatusIsNoAttempt() {
        // e.g. the status flipping to DISCONNECTED a moment before connectEnabled clears
        assertFalse(failed(status = "DISCONNECTED"))
        assertFalse(failed(status = null))
        assertFalse(failed(status = "SOMETHING_NEW"))
    }

    @Test
    fun attemptStatuses() {
        assertTrue(isConnectAttemptStatus("CONNECTING"))
        assertTrue(isConnectAttemptStatus("DESTINATION_SET"))
        assertTrue(isConnectAttemptStatus(CONNECTION_STATUS_CONNECT_FAILED))
        assertFalse(isConnectAttemptStatus(CONNECTION_STATUS_CONNECTED))
        assertFalse(isConnectAttemptStatus("DISCONNECTED"))
        assertFalse(isConnectAttemptStatus(null))
    }

    @Test
    fun monitorResetsWhenTheStatusLeavesTheAttempt() {
        val monitor = ConnectFailureMonitor()
        monitor.observe(true, "CONNECTING", 0, start)
        // disconnected while connectEnabled is still set: the attempt ends
        assertFalse(monitor.observe(true, "DISCONNECTED", 0, start + timeout))
        assertNull(monitor.timeoutAtMillis())
        // a later attempt starts its own clock
        assertFalse(monitor.observe(true, "CONNECTING", 0, start + timeout + 5))
        assertEquals(start + timeout + 5 + timeout, monitor.timeoutAtMillis())
    }

    @Test
    fun monitorReportsOncePerFailedAttempt() {
        val monitor = ConnectFailureMonitor()
        assertFalse(monitor.observe(true, "CONNECTING", 0, start))
        assertFalse(monitor.observe(true, "CONNECTING", 0, start + timeout - 1))
        assertTrue(monitor.observe(true, "CONNECTING", 0, start + timeout))
        // the same failed attempt is not reported again
        assertFalse(monitor.observe(true, CONNECTION_STATUS_CONNECT_FAILED, 0, start + timeout + 1))
    }

    @Test
    fun monitorReportsTheSdkVerdictImmediately() {
        val monitor = ConnectFailureMonitor()
        assertFalse(monitor.observe(true, "CONNECTING", 0, start))
        assertTrue(monitor.observe(true, CONNECTION_STATUS_CONNECT_FAILED, 0, start + 1))
    }

    @Test
    fun monitorRearmsAfterTheUserDisconnects() {
        val monitor = ConnectFailureMonitor()
        monitor.observe(true, "CONNECTING", 0, start)
        assertTrue(monitor.observe(true, CONNECTION_STATUS_CONNECT_FAILED, 0, start + 1))
        // the user gives up, then connects again: a new attempt with a new clock
        assertFalse(monitor.observe(false, "DISCONNECTED", 0, start + 2))
        assertFalse(monitor.observe(true, "CONNECTING", 0, start + 10))
        assertFalse(monitor.observe(true, "CONNECTING", 0, start + 10 + timeout - 1))
        assertTrue(monitor.observe(true, "CONNECTING", 0, start + 10 + timeout))
    }

    @Test
    fun monitorClockRestartsAfterAConnection() {
        val monitor = ConnectFailureMonitor()
        monitor.observe(true, "CONNECTING", 0, start)
        assertFalse(monitor.observe(true, CONNECTION_STATUS_CONNECTED, 3, start + 5))
        // providers lost later: the bound counts from when connecting resumed
        assertFalse(monitor.observe(true, "CONNECTING", 0, start + 100))
        assertFalse(monitor.observe(true, "CONNECTING", 0, start + 100 + timeout - 1))
        assertTrue(monitor.observe(true, "CONNECTING", 0, start + 100 + timeout))
    }

    @Test
    fun monitorExposesThePendingTimeBound() {
        val monitor = ConnectFailureMonitor()
        assertNull(monitor.timeoutAtMillis())
        monitor.observe(true, "CONNECTING", 0, start)
        assertEquals(start + timeout, monitor.timeoutAtMillis())
        monitor.observe(true, "CONNECTING", 0, start + timeout)
        // reported: nothing left to wait for
        assertNull(monitor.timeoutAtMillis())
        monitor.reset()
        assertNull(monitor.timeoutAtMillis())
    }
}
