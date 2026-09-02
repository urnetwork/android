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
    fun vpnServiceColdStartIsDetectedWithoutMisclassifyingOtherComponents() {
        val vpn = VPN_SERVICE_CLASS_NAME

        assertTrue(vpnServiceOwnsColdProcessStart(vpn, emptySet(), vpn))
        assertTrue(vpnServiceOwnsColdProcessStart(null, setOf(vpn), vpn))
        assertFalse(vpnServiceOwnsColdProcessStart("com.example.MainActivity", emptySet(), vpn))
        assertFalse(vpnServiceOwnsColdProcessStart(null, setOf("com.example.TileService"), vpn))
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

    @Test
    fun everyPreparedVpnLaunchUsesForegroundService() {
        assertEquals(
            VpnServiceLaunchDecision.START_FOREGROUND,
            decideVpnServiceLaunch(
                serviceActive = false,
                startPending = false,
                vpnPermissionRequired = false,
            ),
        )
    }

    @Test
    fun vpnNotificationChannelIdentityIsStable() {
        assertEquals("urnetwork", MainService.NOTIFICATION_CHANNEL_ID)
    }

    @Test
    fun onlyVpnConsentCanBlockPreparedForegroundLaunch() {
        assertEquals(
            VpnServiceLaunchDecision.REQUEST_VPN_PERMISSION,
            decideVpnServiceLaunch(
                serviceActive = false,
                startPending = false,
                vpnPermissionRequired = true,
            ),
        )
    }

    @Test
    fun activeOrPendingServiceIsIdempotent() {
        assertEquals(
            VpnServiceLaunchDecision.ALREADY_RUNNING,
            decideVpnServiceLaunch(true, startPending = false, vpnPermissionRequired = false),
        )
        assertEquals(
            VpnServiceLaunchDecision.ALREADY_RUNNING,
            decideVpnServiceLaunch(false, startPending = true, vpnPermissionRequired = false),
        )
    }

    @Test
    fun onlyTheCurrentUnadoptedStartAttemptTimesOut() {
        assertTrue(
            vpnServiceStartAttemptTimedOut(
                expectedGeneration = 4,
                currentGeneration = 4,
                serviceActive = true,
                serviceAdopted = false,
            ),
        )
        assertFalse(vpnServiceStartAttemptTimedOut(3, 4, true, false))
        assertFalse(vpnServiceStartAttemptTimedOut(4, 4, false, false))
        assertFalse(vpnServiceStartAttemptTimedOut(4, 4, true, true))
    }

    @Test
    fun failedStartRetriesOnlyInsideAForegroundEligibilityWindow() {
        assertTrue(
            vpnServiceStartRetryEligible(
                retryRequested = true,
                vpnRequired = true,
                serviceActive = false,
                startPending = false,
                foregroundActivityAvailable = true,
            ),
        )
        assertFalse(vpnServiceStartRetryEligible(false, true, false, false, true))
        assertFalse(vpnServiceStartRetryEligible(true, false, false, false, true))
        assertFalse(vpnServiceStartRetryEligible(true, true, true, false, true))
        assertFalse(vpnServiceStartRetryEligible(true, true, false, true, true))
        assertFalse(vpnServiceStartRetryEligible(true, true, false, false, false))
    }

    @Test
    fun foregroundStartRetryBackoffCapsWithoutOverflowingTheIndex() {
        assertEquals(1_000L, vpnServiceStartRetryDelayMillis(0))
        assertEquals(2_000L, vpnServiceStartRetryDelayMillis(1))
        assertEquals(5_000L, vpnServiceStartRetryDelayMillis(2))
        assertEquals(10_000L, vpnServiceStartRetryDelayMillis(3))
        assertEquals(30_000L, vpnServiceStartRetryDelayMillis(4))
        assertEquals(30_000L, vpnServiceStartRetryDelayMillis(Int.MAX_VALUE))
        assertEquals(1_000L, vpnServiceStartRetryDelayMillis(-1))
    }

    @Test
    fun tunnelRetryBackoffIsImmediateEnoughThenCapsAtThirtySeconds() {
        assertEquals(250L, tunnelRetryDelayMillis(0))
        assertEquals(1_000L, tunnelRetryDelayMillis(1))
        assertEquals(2_000L, tunnelRetryDelayMillis(2))
        assertEquals(5_000L, tunnelRetryDelayMillis(3))
        assertEquals(10_000L, tunnelRetryDelayMillis(4))
        assertEquals(30_000L, tunnelRetryDelayMillis(5))
        assertEquals(30_000L, tunnelRetryDelayMillis(500))
        assertEquals(250L, tunnelRetryDelayMillis(-1))
    }
}
