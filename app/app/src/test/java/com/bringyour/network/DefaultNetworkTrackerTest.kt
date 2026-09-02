package com.bringyour.network

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
}
