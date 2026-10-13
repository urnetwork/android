package com.bringyour.network.acceptance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** ExtenderNetworkClientOffRunner's choice: which runs keep the app's live startup. */
class InstrumentationStartupTest {
    @Test
    fun aRunWithoutArgumentsStartsWithTheExtenderClientOff() {
        assertFalse(keepsLiveStartup { null })
    }

    @Test
    fun aRunWithOtherArgumentsStartsWithTheExtenderClientOff() {
        val arguments = mapOf(
            "class" to "com.bringyour.network.ui.wallet.WalletSectionTest",
            "repeat" to "2",
        )
        assertFalse(keepsLiveStartup(arguments::get))
    }

    @Test
    fun anAcceptanceRunKeepsTheLiveStartup() {
        // as the harness launches MainAcceptanceTest
        val arguments = mapOf(
            "class" to "com.bringyour.network.acceptance.EgressProbeRequestTest,com.bringyour.network.acceptance.MainAcceptanceTest",
            "acceptanceBuildId" to "fixture-build",
            "repeat" to "1",
        )
        assertTrue(keepsLiveStartup(arguments::get))
    }

    @Test
    fun theStartupMemoryDiagnosticKeepsTheLiveStartup() {
        // AndroidMemoryStartupTest opts in by the argument's presence alone
        assertTrue(keepsLiveStartup(mapOf("urnetworkMemoryStartupCheck" to "")::get))
    }

    @Test
    fun theLiveArgumentsAreTheTwoOptIns() {
        assertEquals(listOf("acceptanceBuildId", "urnetworkMemoryStartupCheck"), LIVE_STARTUP_ARGUMENTS)
    }
}
