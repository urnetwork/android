package com.bringyour.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnServiceStartPolicyTest {
    private fun facts(
        stop: Boolean = false,
        start: Boolean = true,
        appActive: Boolean = false,
        required: Boolean = false,
        redelivered: Boolean = false,
        systemStart: Boolean = false,
        commandCompatible: Boolean = true,
    ) = VpnServiceStartFacts(
        stopRequested = stop,
        startRequested = start,
        appMarkedActive = appActive,
        deviceRequiresVpn = required,
        redelivered = redelivered,
        systemStart = systemStart,
        commandCompatible = commandCompatible,
    )

    @Test
    fun normalAppStartRunsWhenOptimisticStateIsActive() {
        assertEquals(
            VpnServiceStartDecision.RUN,
            decideVpnServiceStart(facts(appActive = true)),
        )
    }

    @Test
    fun coldProcessRedeliveryAdoptsTheRestoredDesiredVpn() {
        assertEquals(
            VpnServiceStartDecision.RUN,
            decideVpnServiceStart(facts(required = true, redelivered = true)),
        )
    }

    @Test
    fun retryBeforeTheFirstStartCommandCompletesUsesTheSameRestorePolicy() {
        // MainService folds Android's START_FLAG_RETRY into `redelivered`; the
        // policy is identical because both flags prove this is an OS retry, not
        // an arbitrary stale app intent.
        assertEquals(
            VpnServiceStartDecision.RUN,
            decideVpnServiceStart(facts(required = true, redelivered = true)),
        )
    }

    @Test
    fun redeliveryCannotResurrectAServiceTheDeviceNoLongerNeeds() {
        assertEquals(
            VpnServiceStartDecision.STOP,
            decideVpnServiceStart(facts(redelivered = true)),
        )
    }

    @Test
    fun explicitStopAlwaysWinsIncludingDuringRedelivery() {
        assertEquals(
            VpnServiceStartDecision.STOP,
            decideVpnServiceStart(
                facts(stop = true, appActive = true, required = true, redelivered = true),
            ),
        )
    }

    @Test
    fun staleOrdinaryIntentDoesNotRestartAColdProcessVpn() {
        assertEquals(
            VpnServiceStartDecision.STOP,
            decideVpnServiceStart(facts(required = true)),
        )
    }

    @Test
    fun alwaysOnSystemStartRunsEvenWhenAppStateIsIdle() {
        assertEquals(
            VpnServiceStartDecision.RUN,
            decideVpnServiceStart(facts(required = false, systemStart = true)),
        )
    }

    @Test
    fun explicitStopStillWinsOverAlwaysOnDelivery() {
        assertEquals(
            VpnServiceStartDecision.STOP,
            decideVpnServiceStart(facts(stop = true, systemStart = true)),
        )
    }

    @Test
    fun futureRedeliveredAppCommandIsRejected() {
        assertEquals(
            VpnServiceStartDecision.STOP,
            decideVpnServiceStart(
                facts(appActive = true, redelivered = true, commandCompatible = false),
            ),
        )
    }

    @Test
    fun commandProtocolAcceptsLegacyAndCurrentButRejectsFutureVersions() {
        assertTrue(vpnServiceCommandCompatible("app", 0))
        assertTrue(vpnServiceCommandCompatible("app", VPN_SERVICE_COMMAND_VERSION))
        assertFalse(vpnServiceCommandCompatible("app", VPN_SERVICE_COMMAND_VERSION + 1))
        assertTrue(vpnServiceCommandCompatible(null, Int.MAX_VALUE))
    }

    @Test
    fun untaggedFrameworkStartIsAlwaysOnAcrossAndroidVersions() {
        assertTrue(vpnServiceSystemStart(source = null, frameworkAlwaysOn = false))
        assertTrue(vpnServiceSystemStart(source = "app", frameworkAlwaysOn = true))
        assertFalse(vpnServiceSystemStart(source = "app", frameworkAlwaysOn = false))
    }

    @Test
    fun alwaysOnGuardsUntilADeviceAndProviderAreBothReady() {
        assertTrue(vpnAlwaysOnGuardRequired(true, deviceAvailable = false, providerConnected = false))
        assertTrue(vpnAlwaysOnGuardRequired(true, deviceAvailable = true, providerConnected = false))
        assertFalse(vpnAlwaysOnGuardRequired(true, deviceAvailable = true, providerConnected = true))
        assertFalse(vpnAlwaysOnGuardRequired(false, deviceAvailable = false, providerConnected = false))
    }

    @Test
    fun sharedDesiredStatePredicateMatchesAllThreeReasons() {
        assertTrue(vpnServiceRequired(provideEnabled = true, connectEnabled = false, routeLocal = true))
        assertTrue(vpnServiceRequired(provideEnabled = false, connectEnabled = true, routeLocal = true))
        assertTrue(vpnServiceRequired(provideEnabled = false, connectEnabled = false, routeLocal = false))
        assertFalse(vpnServiceRequired(provideEnabled = false, connectEnabled = false, routeLocal = true))
    }
}
