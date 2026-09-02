package com.bringyour.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkPerformancePolicyTest {
    @Test
    fun dataSaverOnlyDegradesAMeteredActivePath() {
        assertFalse(
            dataSaverDegradesPerformance(
                activeNetworkMetered = false,
                restriction = BackgroundDataRestriction.ENABLED,
            ),
        )
        assertFalse(
            dataSaverDegradesPerformance(
                activeNetworkMetered = true,
                restriction = BackgroundDataRestriction.DISABLED,
            ),
        )
        assertTrue(
            dataSaverDegradesPerformance(
                activeNetworkMetered = true,
                restriction = BackgroundDataRestriction.ENABLED,
            ),
        )
        assertTrue(
            dataSaverDegradesPerformance(
                activeNetworkMetered = true,
                restriction = BackgroundDataRestriction.ALLOWLISTED,
            ),
        )
    }

    @Test
    fun everyHostPressureLayerCanRelaxLiveness() {
        val baseline = HostPerformanceFacts(false, false, false, false)
        assertFalse(hostPerformanceDegraded(baseline))
        assertTrue(hostPerformanceDegraded(baseline.copy(powerSave = true)))
        assertTrue(hostPerformanceDegraded(baseline.copy(thermalDegraded = true)))
        assertTrue(hostPerformanceDegraded(baseline.copy(dataSaverDegraded = true)))
        assertTrue(hostPerformanceDegraded(baseline.copy(defaultNetworkDegraded = true)))
    }

    @Test
    fun bandwidthConstraintCapabilityHonorsPlatformAndExtensionVersions() {
        assertFalse(supportsBandwidthConstraintCapability(33, 99))
        assertFalse(supportsBandwidthConstraintCapability(34, 15))
        assertTrue(supportsBandwidthConstraintCapability(34, 16))
        assertTrue(supportsBandwidthConstraintCapability(36, 0))
    }

    @Test
    fun currentDefaultPressureTransitionsAreTracked() {
        val tracker = DefaultNetworkPressureTracker<String>()
        val healthy = DefaultNetworkPressure(true, true)
        val congested = DefaultNetworkPressure(false, true)
        val constrained = DefaultNetworkPressure(true, false)

        assertFalse(tracker.onAvailable("wifi"))
        assertFalse(tracker.onCapabilitiesChanged("wifi", healthy))
        assertTrue(tracker.onCapabilitiesChanged("wifi", congested))
        assertTrue(tracker.degraded)
        assertFalse(tracker.onCapabilitiesChanged("wifi", constrained))
        assertTrue(tracker.degraded)
        assertTrue(tracker.onCapabilitiesChanged("wifi", healthy))
        assertFalse(tracker.degraded)
    }

    @Test
    fun aNewDefaultDropsPressureFromTheFormerPathUntilItsOwnBaseline() {
        val tracker = DefaultNetworkPressureTracker<String>()
        tracker.onAvailable("cell")
        tracker.onCapabilitiesChanged("cell", DefaultNetworkPressure(false, true))

        assertTrue(tracker.onAvailable("wifi"))
        assertFalse(tracker.degraded)
        assertFalse(
            tracker.onCapabilitiesChanged("cell", DefaultNetworkPressure(false, false)),
        )
        assertFalse(tracker.degraded)
    }

    @Test
    fun staleLossCannotClearCurrentDefaultPressure() {
        val tracker = DefaultNetworkPressureTracker<String>()
        tracker.onAvailable("wifi")
        tracker.onCapabilitiesChanged("wifi", DefaultNetworkPressure(false, true))

        assertFalse(tracker.onLost("cell"))
        assertTrue(tracker.degraded)
        assertTrue(tracker.onLost("wifi"))
        assertFalse(tracker.degraded)
    }
}
