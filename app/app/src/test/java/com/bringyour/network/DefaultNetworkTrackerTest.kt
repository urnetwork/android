package com.bringyour.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultNetworkTrackerTest {
    @Test
    fun initialDefaultIsOnlyABaseline() {
        val tracker = DefaultNetworkTracker<String>()

        assertFalse(tracker.onAvailable("wifi"))
    }

    @Test
    fun duplicateDefaultDoesNotRecover() {
        val tracker = DefaultNetworkTracker<String>()
        tracker.onAvailable("wifi")

        assertFalse(tracker.onAvailable("wifi"))
    }

    @Test
    fun directDefaultIdentityFlipRecovers() {
        val tracker = DefaultNetworkTracker<String>()
        tracker.onAvailable("wifi")

        assertTrue(tracker.onAvailable("cell"))
    }

    @Test
    fun lossThenSameIdentityReturnStillRecovers() {
        val tracker = DefaultNetworkTracker<String>()
        tracker.onAvailable("wifi")
        tracker.onLost("wifi")

        assertTrue(tracker.onAvailable("wifi"))
    }

    @Test
    fun staleLossCannotPoisonCurrentDefault() {
        val tracker = DefaultNetworkTracker<String>()
        tracker.onAvailable("wifi")
        tracker.onLost("cell")

        assertFalse(tracker.onAvailable("wifi"))
    }

    private fun link(interfaceName: String, vararg addresses: String) =
        PhysicalLinkSnapshot(interfaceName, addresses.toSet())

    @Test
    fun initialLinkIsOnlyABaseline() {
        val tracker = DefaultNetworkTracker<String>()
        tracker.onAvailable("wifi")

        assertFalse(tracker.onLinkPropertiesChanged("wifi", link("wlan0", "192.0.2.10")))
    }

    @Test
    fun addedAddressDoesNotStrandSockets() {
        val tracker = DefaultNetworkTracker<String>()
        tracker.onAvailable("wifi")
        tracker.onLinkPropertiesChanged("wifi", link("wlan0", "192.0.2.10"))

        assertFalse(
            tracker.onLinkPropertiesChanged("wifi", link("wlan0", "192.0.2.10", "2001:db8::10")),
        )
    }

    @Test
    fun removedAddressStrandsSockets() {
        val tracker = DefaultNetworkTracker<String>()
        tracker.onAvailable("wifi")
        tracker.onLinkPropertiesChanged("wifi", link("wlan0", "192.0.2.10", "2001:db8::10"))

        assertTrue(tracker.onLinkPropertiesChanged("wifi", link("wlan0", "2001:db8::10")))
    }

    @Test
    fun replacedInterfaceStrandsSockets() {
        val tracker = DefaultNetworkTracker<String>()
        tracker.onAvailable("cell")
        tracker.onLinkPropertiesChanged("cell", link("rmnet0", "198.51.100.7"))

        assertTrue(tracker.onLinkPropertiesChanged("cell", link("rmnet1", "198.51.100.7")))
    }

    @Test
    fun linkOfANetworkThatIsNotTheDefaultIsIgnored() {
        val tracker = DefaultNetworkTracker<String>()
        tracker.onAvailable("wifi")
        tracker.onLinkPropertiesChanged("wifi", link("wlan0", "192.0.2.10"))

        assertFalse(tracker.onLinkPropertiesChanged("cell", link("rmnet0", "198.51.100.7")))
        assertFalse(tracker.onLinkPropertiesChanged("cell", link("rmnet0")))
    }

    @Test
    fun newDefaultTakesItsFirstLinkAsTheBaseline() {
        val tracker = DefaultNetworkTracker<String>()
        tracker.onAvailable("wifi")
        tracker.onLinkPropertiesChanged("wifi", link("wlan0", "192.0.2.10"))
        tracker.onAvailable("cell")

        assertFalse(tracker.onLinkPropertiesChanged("cell", link("rmnet0", "198.51.100.7")))
    }

    @Test
    fun lossThenReturnTakesANewLinkBaseline() {
        val tracker = DefaultNetworkTracker<String>()
        tracker.onAvailable("wifi")
        tracker.onLinkPropertiesChanged("wifi", link("wlan0", "192.0.2.10"))
        tracker.onLost("wifi")
        tracker.onAvailable("wifi")

        assertFalse(tracker.onLinkPropertiesChanged("wifi", link("wlan0", "192.0.2.99")))
    }

    @Test
    fun currentFollowsOnlyTheLiveDefault() {
        val tracker = DefaultNetworkTracker<String>()
        assertFalse(tracker.isCurrent("wifi"))

        tracker.onAvailable("wifi")
        assertTrue(tracker.isCurrent("wifi"))
        assertFalse(tracker.isCurrent("cell"))

        tracker.onLost("wifi")
        assertFalse(tracker.isCurrent("wifi"))
    }
}

class DefaultNetworkQualityTrackerTest {
    private val wifiStrong = DefaultNetworkQuality(4, 100_000, 50_000)
    private val wifiWeak = DefaultNetworkQuality(1, 20_000, 10_000)

    @Test
    fun initialCapabilitiesAreOnlyABaseline() {
        val tracker = DefaultNetworkQualityTracker<String>()
        tracker.onAvailable("wifi")

        assertFalse(tracker.onCapabilitiesChanged("wifi", wifiStrong))
    }

    @Test
    fun duplicateCapabilitiesDoNotNotify() {
        val tracker = DefaultNetworkQualityTracker<String>()
        tracker.onAvailable("wifi")
        tracker.onCapabilitiesChanged("wifi", wifiStrong)

        assertFalse(tracker.onCapabilitiesChanged("wifi", wifiStrong))
    }

    @Test
    fun signalOrLinkEstimateChangeNotifies() {
        val tracker = DefaultNetworkQualityTracker<String>()
        tracker.onAvailable("wifi")
        tracker.onCapabilitiesChanged("wifi", wifiStrong)

        assertTrue(tracker.onCapabilitiesChanged("wifi", wifiWeak))
    }

    @Test
    fun networkSwitchEstablishesANewBaseline() {
        val tracker = DefaultNetworkQualityTracker<String>()
        tracker.onAvailable("wifi")
        tracker.onCapabilitiesChanged("wifi", wifiStrong)
        tracker.onAvailable("cell")

        assertFalse(tracker.onCapabilitiesChanged("cell", wifiWeak))
    }

    @Test
    fun staleNetworkCapabilitiesCannotNotify() {
        val tracker = DefaultNetworkQualityTracker<String>()
        tracker.onAvailable("wifi")
        tracker.onCapabilitiesChanged("wifi", wifiStrong, isCellular = false)
        tracker.onAvailable("cell")
        tracker.onCapabilitiesChanged("cell", wifiWeak, isCellular = false)

        assertFalse(tracker.onCapabilitiesChanged("wifi", wifiWeak, isCellular = true))
        assertFalse(tracker.currentIsCellular())
    }

    @Test
    fun lossThenReturnRequiresANewBaseline() {
        val tracker = DefaultNetworkQualityTracker<String>()
        tracker.onAvailable("wifi")
        tracker.onCapabilitiesChanged("wifi", wifiStrong)
        tracker.onLost("wifi")
        tracker.onAvailable("wifi")

        assertFalse(tracker.onCapabilitiesChanged("wifi", wifiWeak))
    }

    @Test
    fun radioJitterInsideOneBarDoesNotChangeTheSnapshot() {
        assertEquals(defaultNetworkSignalLevel(-72), defaultNetworkSignalLevel(-79))
        assertEquals(defaultNetworkBandwidthBucket(70_000), defaultNetworkBandwidthBucket(120_000))
    }

    @Test
    fun barAndCapacityBoundariesRemainVisible() {
        assertFalse(defaultNetworkSignalLevel(-81) == defaultNetworkSignalLevel(-79))
        assertFalse(defaultNetworkBandwidthBucket(60_000) == defaultNetworkBandwidthBucket(70_000))
    }

    @Test
    fun cellBarAndTypeCallbacksEachEstablishTheirOwnBaseline() {
        val tracker = DefaultCellularQualityTracker()

        assertFalse(tracker.onSignalLevel(4))
        assertFalse(tracker.onNetworkType(13, 0))
        assertFalse(tracker.onSignalLevel(4))
        assertFalse(tracker.onNetworkType(13, 0))
    }

    @Test
    fun cellBarOrTypeChangeNotifiesAfterBaseline() {
        val tracker = DefaultCellularQualityTracker()
        tracker.onSignalLevel(4)
        tracker.onNetworkType(13, 0)

        assertTrue(tracker.onSignalLevel(2))
        assertTrue(tracker.onNetworkType(13, 5))
    }

    @Test
    fun currentCellularStateFollowsOnlyTheLiveNetwork() {
        val tracker = DefaultNetworkQualityTracker<String>()
        tracker.onAvailable("cell")
        tracker.onCapabilitiesChanged("cell", wifiStrong, isCellular = true)
        assertTrue(tracker.currentIsCellular())

        tracker.onAvailable("wifi")
        assertFalse(tracker.currentIsCellular())
        tracker.onCapabilitiesChanged("wifi", wifiWeak, isCellular = false)
        assertFalse(tracker.currentIsCellular())
    }
}
