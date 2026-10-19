package com.bringyour.network.acceptance

import org.junit.Assert.*
import org.junit.Test

class PhysicalPublicWindowEvidenceTest {
    private fun window(reason: String? = "evaluating", evaluations: Long = 1) = PhysicalPublicWindowSnapshot(
        targetSize = 4,
        stallReason = reason,
        providerStateInEvaluation = evaluations,
        providerStateEvaluationFailed = 2,
        providerStateNotAdded = 3,
        providerStateAdded = 0,
        providerStateRemoved = 5,
    )

    private fun route(before: PhysicalPublicWindowSnapshot?, after: PhysicalPublicWindowSnapshot?) =
        PhysicalPublicRouteState(
            controllerConnected = true,
            connectEnabled = true,
            tunnelStarted = true,
            generationBefore = 7,
            generationAfter = 7,
            minSatisfied = false,
            failed = false,
            providers = emptyList(),
            windowBefore = before,
            windowAfter = after,
        )

    @Test fun `timeout retains exact last bracketed SDK window counters through cleanup`() {
        var now = 0L
        var reads = 0L
        val evidence = PhysicalFailureEvidence()
        val before = window("providers-unresponsive", 8)
        val after = window("platform-unreachable", 9)
        val error = assertThrows(PhysicalWaitTimeout::class.java) {
            runPhysicalPublicEgress(7, 250, { now }, { now += it }, {
                if (++reads == 3L) route(before, after)
                else route(window(), window())
            }, { fail("unadmitted window must not launch egress") })
        }
        assertEquals(3L, reads)
        assertEquals(250L, now)
        val captured = evidence.capture(error, "connect-h1", 1_250, now, now)
        val published = evidence.capture(error, "synthetic-cleanup", 4_000, 3_000, 3_000)
        assertSame(captured, published)
        val fields = published.statusFields()["publicReadiness"] as Map<*, *>
        assertEquals(3L, fields["observationIndex"])
        assertEquals(200L, fields["elapsedMs"])
        assertEquals(before.statusFields(), fields["windowBefore"])
        assertEquals(after.statusFields(), fields["windowAfter"])
        assertEquals(mapOf(
            "targetSize" to 4L, "stallReason" to "platform-unreachable",
            "providerStateInEvaluation" to 9L, "providerStateEvaluationFailed" to 2L,
            "providerStateNotAdded" to 3L, "providerStateAdded" to 0L, "providerStateRemoved" to 5L,
        ), fields["windowAfter"])
        assertEquals(14, fields.size)
    }

    @Test fun `later read failures preserve preceding window counters and exact cause`() {
        var now = 0L
        var reads = 0
        val observed = window("rate-limited", 6)
        val cause = IllegalStateException("synthetic private route detail")
        val error = assertThrows(PhysicalWaitTimeout::class.java) {
            runPhysicalPublicEgress(7, 250, { now }, { now += it }, {
                if (++reads == 1) route(null, observed) else throw cause
            }, { fail("failed readiness must not launch egress") })
        }
        assertSame(cause, error.cause)
        assertEquals(3, reads)
        assertEquals(250L, now)
        val fields = checkNotNull(error.publicReadiness).statusFields()
        assertEquals(1L, fields["observationIndex"])
        assertEquals(0L, fields["elapsedMs"])
        assertFalse(fields.containsKey("windowBefore"))
        assertEquals(observed.statusFields(), fields["windowAfter"])
        assertFalse(fields.toString().contains("synthetic private route detail"))
    }

    @Test fun `unknown stall text is omitted and every existing finite reason stays exact`() {
        for (reason in listOf("", "evaluating", "platform-unreachable", "providers-unresponsive", "rate-limited", "auth-failing")) {
            val observed = window(reason)
            assertEquals(reason, observed.stallReason)
            assertEquals(reason, observed.statusFields()["stallReason"])
        }
        for (reason in listOf(null, "synthetic-provider.private.example", "auth-failing\nsynthetic private detail")) {
            val observed = window(reason)
            assertNull(observed.stallReason)
            assertFalse(observed.statusFields().containsKey("stallReason"))
            assertEquals(6, observed.statusFields().size)
            assertTrue(observed.statusFields().values.all { it is Long })
        }
    }

    @Test fun `diagnostic counters do not add another readiness condition`() {
        var reads = 0
        var probes = 0
        runPhysicalPublicEgress(7, 250, { 0L }, { fail("ready route must not wait") }, {
            reads++
            route(window("auth-failing"), window("platform-unreachable")).copy(
                minSatisfied = true,
                providers = listOf(PhysicalProviderEvidence("synthetic-provider", "US", true)),
            )
        }, { probes++ })
        assertEquals(1, reads)
        assertEquals(1, probes)
    }

    @Test fun `full deadline retains one final bounded window pair without a history`() {
        var now = 0L
        var reads = 0L
        val error = assertThrows(PhysicalWaitTimeout::class.java) {
            runPhysicalPublicEgress(7, 120_000, { now }, { now += it }, {
                reads++
                route(window(evaluations = reads), window(evaluations = reads + 1))
            }, { fail("unadmitted window must not launch egress") })
        }
        assertEquals(1_200L, reads)
        assertEquals(120_000L, now)
        val fields = checkNotNull(error.publicReadiness).statusFields()
        assertEquals(1_200L, fields["observationIndex"])
        assertEquals(119_900L, fields["elapsedMs"])
        assertEquals(14, fields.size)
        val before = fields["windowBefore"] as Map<*, *>
        val after = fields["windowAfter"] as Map<*, *>
        assertEquals(7, before.size)
        assertEquals(7, after.size)
        assertEquals(1_200L, before["providerStateInEvaluation"])
        assertEquals(1_201L, after["providerStateInEvaluation"])
        assertFalse(fields.values.any { it is Collection<*> })
    }
}
