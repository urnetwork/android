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
}
