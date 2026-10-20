package com.bringyour.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TunnelRecoveryPolicyTest {
    private val settings = TunnelRecoverySettings()

    private fun policy() = TunnelRecoveryPolicy(settings)

    @Test
    fun shippedDefaults() {
        assertEquals(350L, settings.transportRecoveryDebounceMillis)
        assertEquals(2_000L, settings.minimumTransportRecoveryIntervalMillis)
        assertEquals(350L, settings.wakeAuditDebounceMillis)
        assertEquals(5_000L, settings.wakeHealthGraceMillis)
    }

    @Test
    fun transportBurstsCoalesceReasonsAndInvalidateOlderGeneration() {
        val policy = policy()
        val first = policy.requestTransportRecovery(1_000, "available", true)
        val second = policy.requestTransportRecovery(1_100, "default", true)

        assertNull(policy.consumeTransportRecovery(first.generation, 1_350))
        val recovery = policy.consumeTransportRecovery(second.generation, 1_450)

        assertEquals(setOf("available", "default"), recovery?.reasons)
        assertTrue(recovery?.physicalPathChange == true)
    }

    @Test
    fun transportRecoveryHonorsDebounceAndMinimumInterval() {
        val policy = policy()
        val first = policy.requestTransportRecovery(1_000, "first", true)
        policy.consumeTransportRecovery(first.generation, 1_350)

        val tooSoon = policy.requestTransportRecovery(1_500, "second", true)
        val afterInterval = policy.requestTransportRecovery(4_000, "third", true)

        assertEquals(1_350L + settings.minimumTransportRecoveryIntervalMillis - 1_500L, tooSoon.delayMillis)
        assertEquals(settings.transportRecoveryDebounceMillis, afterInterval.delayMillis)
    }

    @Test
    fun nonDefaultTransportTimingIsHonored() {
        val policy = TunnelRecoveryPolicy(
            TunnelRecoverySettings(
                transportRecoveryDebounceMillis = 100L,
                minimumTransportRecoveryIntervalMillis = 500L,
            ),
        )
        val first = policy.requestTransportRecovery(1_000, "first", true)
        assertEquals(100L, first.delayMillis)
        policy.consumeTransportRecovery(first.generation, 1_100)

        assertEquals(300L, policy.requestTransportRecovery(1_300, "second", true).delayMillis)
        assertEquals(100L, policy.requestTransportRecovery(1_700, "third", true).delayMillis)
    }

    @Test
    fun defaultFlapsInsideTheMinimumIntervalShareOneDeferredRecovery() {
        // A kick cancels any dial in flight, so a default that flaps must not
        // re-dial per flip: each flip replaces the pending recovery, which
        // waits for the floor and then fires once for the last path.
        val policy = policy()
        val first = policy.requestTransportRecovery(1_000, RECOVERY_DEFAULT_NETWORK_CHANGED, true)
        assertNotNull(policy.consumeTransportRecovery(first.generation, 1_350))

        val flip = policy.requestTransportRecovery(1_600, RECOVERY_DEFAULT_NETWORK_CHANGED, true)
        val flipBack = policy.requestTransportRecovery(2_100, RECOVERY_DEFAULT_NETWORK_CHANGED, true)

        assertEquals(1_350L + settings.minimumTransportRecoveryIntervalMillis - 2_100L, flipBack.delayMillis)
        assertNull(policy.consumeTransportRecovery(flip.generation, 3_350))
        assertEquals(
            setOf(RECOVERY_DEFAULT_NETWORK_CHANGED),
            policy.consumeTransportRecovery(flipBack.generation, 3_350)?.reasons,
        )
    }

    @Test
    fun wakeSignalsCoalesceBeforeProbePass() {
        val policy = policy()
        val screen = policy.requestWakeAudit("screen-on")
        val unlock = policy.requestWakeAudit("user-present")

        assertEquals(settings.wakeAuditDebounceMillis, unlock.delayMillis)
        assertNull(policy.beginWakeAudit(screen.generation, 10))
        val audit = policy.beginWakeAudit(unlock.generation, 20)

        assertNotNull(audit)
        assertEquals(setOf("screen-on", "user-present"), audit?.reasons)
        assertEquals(settings.wakeHealthGraceMillis, audit?.delayMillis)
    }

    @Test
    fun nonDefaultWakeTimingIsHonored() {
        val policy = TunnelRecoveryPolicy(
            TunnelRecoverySettings(wakeAuditDebounceMillis = 25L, wakeHealthGraceMillis = 1_000L),
        )
        val signal = policy.requestWakeAudit("screen-on")

        assertEquals(25L, signal.delayMillis)
        assertEquals(1_000L, policy.beginWakeAudit(signal.generation, 100)?.delayMillis)
    }

    @Test
    fun healthyProviderWindowNeverResetsOnWake() {
        val policy = policy()
        val signal = policy.requestWakeAudit("screen-on")
        val audit = policy.beginWakeAudit(signal.generation, 1_000)!!

        assertEquals(
            WakeHealthDecision.PROVIDERS_HEALTHY,
            policy.evaluateWakeAudit(
                audit,
                WakeHealthFacts(true, physicalNetworkAvailable = true, providerCount = 1),
            ),
        )
    }

    @Test
    fun wakeFallbackRequiresRemoteIntentNetworkAndMissingProviders() {
        val policy = policy()
        val signal = policy.requestWakeAudit("screen-on")
        val audit = policy.beginWakeAudit(signal.generation, 1_000)!!

        assertEquals(
            WakeHealthDecision.RECOVER_TRANSPORTS,
            policy.evaluateWakeAudit(
                audit,
                WakeHealthFacts(true, physicalNetworkAvailable = true, providerCount = 0),
            ),
        )
        assertEquals(
            WakeHealthDecision.LOCAL_MODE,
            policy.evaluateWakeAudit(
                audit,
                WakeHealthFacts(false, physicalNetworkAvailable = true, providerCount = 0),
            ),
        )
        assertEquals(
            WakeHealthDecision.OFFLINE,
            policy.evaluateWakeAudit(
                audit,
                WakeHealthFacts(true, physicalNetworkAvailable = false, providerCount = 0),
            ),
        )
    }

    @Test
    fun physicalPathSignalDuringGraceOwnsRecovery() {
        val policy = policy()
        val signal = policy.requestWakeAudit("screen-on")
        val audit = policy.beginWakeAudit(signal.generation, 1_000)!!
        policy.requestTransportRecovery(1_200, "default-network", true)

        assertEquals(
            WakeHealthDecision.COVERED_BY_TRANSPORT_RECOVERY,
            policy.evaluateWakeAudit(
                audit,
                WakeHealthFacts(true, physicalNetworkAvailable = true, providerCount = 0),
            ),
        )
    }

    @Test
    fun completedNonPhysicalRecoveryDuringGraceAlsoCoversWake() {
        val policy = policy()
        val signal = policy.requestWakeAudit("screen-on")
        val audit = policy.beginWakeAudit(signal.generation, 1_000)!!
        val recovery = policy.requestTransportRecovery(1_100, "manual", false)
        policy.consumeTransportRecovery(recovery.generation, 1_450)

        assertEquals(
            WakeHealthDecision.COVERED_BY_TRANSPORT_RECOVERY,
            policy.evaluateWakeAudit(
                audit,
                WakeHealthFacts(true, physicalNetworkAvailable = true, providerCount = 0),
            ),
        )
    }

    @Test
    fun sleepAndDeviceResetInvalidatePendingWakeAudits() {
        val policy = policy()
        val firstSignal = policy.requestWakeAudit("screen-on")
        val first = policy.beginWakeAudit(firstSignal.generation, 1_000)!!
        policy.cancelWakeAudits()
        assertEquals(
            WakeHealthDecision.INVALIDATED,
            policy.evaluateWakeAudit(
                first,
                WakeHealthFacts(true, physicalNetworkAvailable = true, providerCount = 0),
            ),
        )

        val secondSignal = policy.requestWakeAudit("process-foreground")
        val second = policy.beginWakeAudit(secondSignal.generation, 2_000)!!
        policy.reset()
        assertEquals(
            WakeHealthDecision.INVALIDATED,
            policy.evaluateWakeAudit(
                second,
                WakeHealthFacts(true, physicalNetworkAvailable = true, providerCount = 0),
            ),
        )
        assertFalse(second.reasons.isEmpty())
    }
}
