package com.bringyour.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TunnelRecoveryPolicyTest {
    @Test
    fun transportBurstsCoalesceReasonsAndInvalidateOlderGeneration() {
        val policy = TunnelRecoveryPolicy()
        val first = policy.requestTransportRecovery(1_000, "available", true)
        val second = policy.requestTransportRecovery(1_100, "default", true)

        assertNull(policy.consumeTransportRecovery(first.generation, 1_350))
        val recovery = policy.consumeTransportRecovery(second.generation, 1_450)

        assertEquals(setOf("available", "default"), recovery?.reasons)
        assertTrue(recovery?.physicalPathChange == true)
    }

    @Test
    fun transportRecoveryHonorsDebounceAndMinimumInterval() {
        val policy = TunnelRecoveryPolicy()
        val first = policy.requestTransportRecovery(1_000, "first", true)
        policy.consumeTransportRecovery(first.generation, 1_350)

        val tooSoon = policy.requestTransportRecovery(1_500, "second", true)
        val afterInterval = policy.requestTransportRecovery(4_000, "third", true)

        assertEquals(1_850L, tooSoon.delayMillis)
        assertEquals(TRANSPORT_RECOVERY_DEBOUNCE_MILLIS, afterInterval.delayMillis)
    }

    @Test
    fun wakeSignalsCoalesceBeforeProbePass() {
        val policy = TunnelRecoveryPolicy()
        val screen = policy.requestWakeAudit("screen-on")
        val unlock = policy.requestWakeAudit("user-present")

        assertNull(policy.beginWakeAudit(screen.generation, 10))
        val audit = policy.beginWakeAudit(unlock.generation, 20)

        assertNotNull(audit)
        assertEquals(setOf("screen-on", "user-present"), audit?.reasons)
        assertEquals(WAKE_HEALTH_GRACE_MILLIS, audit?.delayMillis)
    }

    @Test
    fun healthyProviderWindowNeverResetsOnWake() {
        val policy = TunnelRecoveryPolicy()
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
        val policy = TunnelRecoveryPolicy()
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
        val policy = TunnelRecoveryPolicy()
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
        val policy = TunnelRecoveryPolicy()
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
        val policy = TunnelRecoveryPolicy()
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
