package com.bringyour.network.ui.shared.viewmodels

import com.bringyour.network.ui.shared.models.ProvideControlMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProStatusSyncTest {

    @Test
    fun upgradingToProKeepsEveryProvideControlMode() {
        for (mode in ProvideControlMode.entries) {
            val sync = ProStatusSync.plan(serverIsPro = true, jwtIsPro = false, provideControlMode = mode)
            assertEquals(mode, sync.provideControlMode)
            assertTrue(sync.refreshToken)
        }
    }

    @Test
    fun lapsingFromProKeepsTheProvideControlModeAndRefreshesTheToken() {
        for (mode in ProvideControlMode.entries) {
            val sync = ProStatusSync.plan(serverIsPro = false, jwtIsPro = true, provideControlMode = mode)
            assertEquals(mode, sync.provideControlMode)
            assertTrue(sync.refreshToken)
        }
    }

    @Test
    fun matchingProStatusChangesNothing() {
        for (pro in listOf(true, false)) {
            for (mode in ProvideControlMode.entries) {
                val sync = ProStatusSync.plan(serverIsPro = pro, jwtIsPro = pro, provideControlMode = mode)
                assertEquals(mode, sync.provideControlMode)
                assertFalse(sync.refreshToken)
            }
        }
    }
}
