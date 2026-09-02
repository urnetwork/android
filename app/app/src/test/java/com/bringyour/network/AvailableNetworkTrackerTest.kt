package com.bringyour.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AvailableNetworkTrackerTest {
    @Test
    fun trackerStartsUnavailable() {
        val tracker = AvailableNetworkTracker<String>()

        assertFalse(tracker.available)
        assertEquals(0, tracker.size)
    }

    @Test
    fun firstNetworkMakesTrackerAvailable() {
        val tracker = AvailableNetworkTracker<String>()

        val change = tracker.onAvailable("cell")

        assertTrue(change.available)
        assertTrue(change.availabilityChanged)
        assertTrue(change.topologyChanged)
        assertFalse(change.recoveryRequired)
        assertEquals(1, tracker.size)
    }

    @Test
    fun duplicateAvailableCallbackDoesNotChangeTopology() {
        val tracker = AvailableNetworkTracker<String>()
        tracker.onAvailable("cell")

        val change = tracker.onAvailable("cell")

        assertTrue(change.available)
        assertFalse(change.availabilityChanged)
        assertFalse(change.topologyChanged)
        assertFalse(change.recoveryRequired)
        assertEquals(1, tracker.size)
    }

    @Test
    fun losingOneOfTwoNetworksRemainsAvailable() {
        val tracker = AvailableNetworkTracker<String>()
        tracker.onAvailable("cell")
        tracker.onAvailable("wifi")

        val change = tracker.onLost("cell")

        assertTrue(change.available)
        assertFalse(change.availabilityChanged)
        assertTrue(change.topologyChanged)
        assertTrue(change.recoveryRequired)
        assertEquals(1, tracker.size)
    }

    @Test
    fun losingLastNetworkMakesTrackerUnavailable() {
        val tracker = AvailableNetworkTracker<String>()
        tracker.onAvailable("cell")

        val change = tracker.onLost("cell")

        assertFalse(change.available)
        assertTrue(change.availabilityChanged)
        assertTrue(change.topologyChanged)
        assertFalse(change.recoveryRequired)
        assertEquals(0, tracker.size)
    }

    @Test
    fun staleLostCallbackDoesNotChangeAvailability() {
        val tracker = AvailableNetworkTracker<String>()
        tracker.onAvailable("cell")

        val change = tracker.onLost("wifi")

        assertTrue(change.available)
        assertFalse(change.availabilityChanged)
        assertFalse(change.topologyChanged)
        assertFalse(change.recoveryRequired)
        assertEquals(1, tracker.size)
    }

    @Test
    fun initialLinkFingerprintDoesNotReportAChange() {
        val tracker = AvailableNetworkTracker<String>()
        tracker.onAvailable("cell")

        assertFalse(tracker.onLinkPropertiesChanged("cell", "route-a"))
    }

    @Test
    fun repeatedLinkFingerprintDoesNotReportAChange() {
        val tracker = AvailableNetworkTracker<String>()
        tracker.onAvailable("cell")
        tracker.onLinkPropertiesChanged("cell", "route-a")

        assertFalse(tracker.onLinkPropertiesChanged("cell", "route-a"))
    }

    @Test
    fun materialLinkFingerprintChangeIsReported() {
        val tracker = AvailableNetworkTracker<String>()
        tracker.onAvailable("cell")
        tracker.onLinkPropertiesChanged("cell", "route-a")

        assertTrue(tracker.onLinkPropertiesChanged("cell", "route-b"))
    }

    @Test
    fun linkCallbackForUnknownNetworkIsIgnored() {
        val tracker = AvailableNetworkTracker<String>()

        assertFalse(tracker.onLinkPropertiesChanged("stale", "route-a"))
    }

    @Test
    fun readdedNetworkGetsANewLinkBaseline() {
        val tracker = AvailableNetworkTracker<String>()
        tracker.onAvailable("cell")
        tracker.onLinkPropertiesChanged("cell", "route-a")
        tracker.onLost("cell")
        tracker.onAvailable("cell")

        assertFalse(tracker.onLinkPropertiesChanged("cell", "route-b"))
    }

    @Test
    fun networkReturningAfterTotalLossRequiresRecovery() {
        val tracker = AvailableNetworkTracker<String>()
        tracker.onAvailable("cell")
        tracker.onLost("cell")

        val change = tracker.onAvailable("cell")

        assertTrue(change.recoveryRequired)
    }

    private fun capabilities(
        validated: Boolean = true,
        notSuspended: Boolean = true,
        captivePortal: Boolean = false,
        partialConnectivity: Boolean = false,
        transports: Set<Int> = setOf(1),
    ) = PhysicalNetworkCapabilitiesSnapshot(
        validated = validated,
        notSuspended = notSuspended,
        captivePortal = captivePortal,
        partialConnectivity = partialConnectivity,
        transports = transports,
    )

    @Test
    fun initialCapabilitiesEstablishBaselineWithoutRecovery() {
        val tracker = AvailableNetworkTracker<String>()
        tracker.onAvailable("wifi")

        val change = tracker.onCapabilitiesChanged("wifi", capabilities())

        assertTrue(change.baseline)
        assertFalse(change.changed)
        assertFalse(change.recovered)
    }

    @Test
    fun suspensionAndValidationRecoveryAreReported() {
        val tracker = AvailableNetworkTracker<String>()
        tracker.onAvailable("wifi")
        tracker.onCapabilitiesChanged(
            "wifi",
            capabilities(validated = false, notSuspended = false),
        )

        val change = tracker.onCapabilitiesChanged("wifi", capabilities())

        assertTrue(change.changed)
        assertTrue(change.recovered)
        assertFalse(change.transportChanged)
    }

    @Test
    fun capabilityLossIsDegradedButNotRecovered() {
        val tracker = AvailableNetworkTracker<String>()
        tracker.onAvailable("wifi")
        tracker.onCapabilitiesChanged("wifi", capabilities())

        val change = tracker.onCapabilitiesChanged(
            "wifi",
            capabilities(validated = false, captivePortal = true),
        )

        assertTrue(change.degraded)
        assertFalse(change.recovered)
    }

    @Test
    fun transportIdentityChangeRequiresSocketRecovery() {
        val tracker = AvailableNetworkTracker<String>()
        tracker.onAvailable("network")
        tracker.onCapabilitiesChanged("network", capabilities(transports = setOf(1)))

        val change = tracker.onCapabilitiesChanged(
            "network",
            capabilities(transports = setOf(0)),
        )

        assertTrue(change.transportChanged)
    }

    @Test
    fun unblockAfterInitialBlockedBaselineIsRecovery() {
        val tracker = AvailableNetworkTracker<String>()
        tracker.onAvailable("wifi")
        assertTrue(tracker.onBlockedStatusChanged("wifi", true).baseline)

        val change = tracker.onBlockedStatusChanged("wifi", false)

        assertTrue(change.changed)
        assertTrue(change.recovered)
        assertFalse(change.degraded)
    }

    @Test
    fun attributesForLostOrUnknownNetworkAreIgnored() {
        val tracker = AvailableNetworkTracker<String>()

        assertFalse(tracker.onCapabilitiesChanged("stale", capabilities()).knownNetwork)
        assertFalse(tracker.onBlockedStatusChanged("stale", false).knownNetwork)
    }
}
