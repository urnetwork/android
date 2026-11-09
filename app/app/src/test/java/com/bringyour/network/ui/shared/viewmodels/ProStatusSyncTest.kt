package com.bringyour.network.ui.shared.viewmodels

import com.bringyour.network.ui.shared.models.ProvideControlMode
import org.junit.Assert.assertEquals
import org.junit.Test

// Whether to refresh the jwt is the sdk's decision now (SubscriptionProTracker,
// tested in the sdk): one refresh per stored jwt.
class ProStatusSyncTest {

    @Test
    fun aProChangeInEitherDirectionKeepsEveryProvideControlMode() {
        for (mode in ProvideControlMode.entries) {
            val sync = ProStatusSync.plan(provideControlMode = mode)
            assertEquals(mode, sync.provideControlMode)
        }
    }
}
