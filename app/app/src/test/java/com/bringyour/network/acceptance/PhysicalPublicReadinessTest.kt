package com.bringyour.network.acceptance

import org.junit.Assert.*
import org.junit.Test

class PhysicalPublicReadinessTest {
    private class Clock {
        var now = 0L
        val sleeps = mutableListOf<Long>()
        fun sleep(millis: Long) { sleeps += millis; now += millis }
    }
    private val provider = PhysicalProviderEvidence("synthetic-provider", "US", true)
    private fun ready() = PhysicalPublicRouteState(
        true, true, true, 7, 7, true, false, listOf(provider),
    )

    @Test fun `intent and tunnel cannot start public egress before window admission`() {
        val clock = Clock()
        var observations = 0
        var probes = 0
        val result = runPhysicalPublicEgress(7, 120_000, { clock.now }, clock::sleep,
            routeState = {
                assertEquals(0, probes)
                when (++observations) {
                    1 -> ready().copy(minSatisfied = false, providers = emptyList())
                    2 -> ready()
                    else -> error("unexpected readiness retry")
                }
            },
            egressProof = {
                assertEquals(2, observations)
                probes++
                "synthetic-result"
            })
        assertEquals("synthetic-result", result)
        assertEquals(1, probes)
        assertEquals(listOf(100L), clock.sleeps)
    }

    @Test fun `unready window and irrelevant providers cannot admit the probe`() {
        val states = listOf(
            ready().copy(minSatisfied = false), // Added but currently unsatisfied/warned.
            ready().copy(failed = true),
            ready().copy(providers = emptyList()),
            ready().copy(providers = listOf(provider.copy(countryCode = "CA"))),
            ready().copy(providers = listOf(provider, provider.copy(countryCode = "CA"))),
            ready().copy(providers = listOf(provider.copy(hasLocation = false))),
            ready().copy(providers = listOf(provider.copy(countryCode = ""))),
            ready().copy(controllerConnected = false),
            ready().copy(connectEnabled = false),
            ready().copy(tunnelStarted = false),
            ready().copy(generationBefore = 6),
            ready().copy(generationAfter = 8),
            ready().copy(generationBefore = 8, generationAfter = 8),
        )
        for (state in states) {
            val clock = Clock()
            val error = assertThrows(PhysicalWaitTimeout::class.java) {
                runPhysicalPublicEgress(7, 250, { clock.now }, clock::sleep,
                    { state }, { fail("unready state launched a probe: $state") })
            }
            assertEquals(PhysicalWaitStage.PUBLIC_VPN_CONNECTION, error.stage)
            assertEquals(250L, clock.now)
            assertEquals(listOf(100L, 100L, 50L), clock.sleeps)
        }
    }

    @Test fun `ready current US route requires no preceding carrier bytes`() {
        val clock = Clock()
        var probes = 0
        runPhysicalPublicEgress(7, 120_000, { clock.now }, clock::sleep,
            { ready().copy(providers = listOf(provider.copy(countryCode = "us"))) },
            { probes++ })
        assertEquals(1, probes)
        assertTrue(clock.sleeps.isEmpty())
    }

    @Test fun `existing deadline is not restarted and late readiness cannot launch`() {
        for (lateObservation in listOf(false, true)) {
            val clock = Clock().apply { now = 5_000 }
            var reads = 0
            val error = assertThrows(PhysicalWaitTimeout::class.java) {
                runPhysicalPublicEgress(7, 120_000, { clock.now }, clock::sleep,
                    {
                        reads++
                        if (lateObservation) { clock.now += 120_000; ready() }
                        else ready().copy(minSatisfied = false)
                    },
                    { fail("deadline admitted a probe") })
            }
            assertEquals(PhysicalWaitStage.PUBLIC_VPN_CONNECTION, error.stage)
            assertEquals(125_000L, clock.now)
            assertEquals(if (lateObservation) 1 else 1_200, reads)
        }
    }

    @Test fun `cancellation before or during observation preserves the no-probe boundary`() {
        for (duringRead in listOf(false, true)) {
            val clock = Clock()
            var canceled = !duringRead
            var reads = 0
            assertThrows(InterruptedException::class.java) {
                runPhysicalPublicEgress(7, 120_000, { clock.now }, clock::sleep,
                    { reads++; canceled = true; ready() },
                    { fail("canceled readiness launched a probe") }, { canceled })
            }
            assertEquals(if (duringRead) 1 else 0, reads)
            assertTrue(clock.sleeps.isEmpty())
        }
        val interruption = InterruptedException("synthetic cancellation")
        val error = assertThrows(InterruptedException::class.java) {
            runPhysicalPublicEgress(7, 120_000, { 0L }, { fail("cancellation retried") },
                { throw interruption }, { fail("cancellation launched a probe") })
        }
        assertSame(interruption, error)
    }

    @Test fun `read errors keep the original bound and a recovered route may proceed`() {
        val clock = Clock()
        val failure = IllegalStateException("synthetic read failure")
        val error = assertThrows(PhysicalWaitTimeout::class.java) {
            runPhysicalPublicEgress(7, 250, { clock.now }, clock::sleep,
                { throw failure }, { fail("failed reads launched a probe") })
        }
        assertSame(failure, error.cause)
        assertEquals(250L, clock.now)
        var reads = 0
        var probes = 0
        runPhysicalPublicEgress(7, 250, { clock.now }, clock::sleep,
            { if (++reads == 1) throw failure else ready() }, { probes++ })
        assertEquals(2, reads)
        assertEquals(1, probes)
        assertEquals(350L, clock.now)
    }

    @Test fun `fatal route errors preserve identity without retry or probe`() {
        val failures = listOf(
            OutOfMemoryError("synthetic fatal allocation"),
            LinkageError("synthetic fatal linkage"),
            AssertionError("synthetic fatal assertion"),
        )
        for (failure in failures) {
            val clock = Clock()
            var reads = 0
            var probes = 0
            val error = assertThrows(Error::class.java) {
                runPhysicalPublicEgress(7, 120_000, { clock.now }, clock::sleep,
                    { reads++; throw failure }, { probes++ })
            }
            assertSame(failure, error)
            assertEquals(1, reads)
            assertEquals(0, probes)
            assertTrue(clock.sleeps.isEmpty())
            assertEquals(0L, clock.now)
        }
    }

    @Test fun `probe failure stays exact and is never retried as readiness`() {
        val clock = Clock()
        val failure = AssertionError("synthetic probe failure")
        var probes = 0
        val error = assertThrows(AssertionError::class.java) {
            runPhysicalPublicEgress(7, 120_000, { clock.now }, clock::sleep,
                { ready() }, { probes++; clock.now += 45_000; throw failure })
        }
        assertSame(failure, error)
        assertEquals(1, probes)
        assertEquals(45_000L, clock.now)
        assertTrue(clock.sleeps.isEmpty())
    }

    @Test fun `timeout retains the last public predicates without mutable provider aliases`() {
        val clock = Clock()
        val providers = mutableListOf(provider, provider.copy(countryCode = "CA"))
        var reads = 0
        var probes = 0
        val error = assertThrows(PhysicalWaitTimeout::class.java) {
            runPhysicalPublicEgress(7, 250, { clock.now }, {
                clock.sleep(it)
                if (clock.now == 250L) providers.clear()
            }, {
                reads++
                if (reads < 3) ready().copy(minSatisfied = false)
                else ready().copy(
                    controllerConnected = false,
                    tunnelStarted = false,
                    generationBefore = 6,
                    generationAfter = 8,
                    minSatisfied = false,
                    failed = true,
                    providers = providers,
                )
            }, { probes++ })
        }
        assertEquals(3, reads)
        assertEquals(0, probes)
        assertEquals(250L, clock.now)
        assertTrue(providers.isEmpty())
        val fields = PhysicalFailureEvidence().capture(error, "connect-h3", 1_000, clock.now).statusFields()
        val expected = mapOf(
            "observationIndex" to 3L,
            "elapsedMs" to 200L,
            "expectedConnectionGeneration" to 7L,
            "generationBefore" to 6L,
            "generationAfter" to 8L,
            "controllerConnected" to false,
            "connectEnabled" to true,
            "tunnelStarted" to false,
            "minSatisfied" to false,
            "failed" to true,
            "providerCount" to 2,
            "usProvidersValid" to false,
        )
        assertEquals("timeout lost its last successful route read", expected, fields["publicReadiness"])
        assertFalse(fields.toString().contains("synthetic-provider"))
        assertNull(error.cause)
    }

    @Test fun `read failures retain only a preceding successful observation and exact cause`() {
        for (hasObservation in listOf(false, true)) {
            val clock = Clock()
            val failure = IllegalStateException("synthetic private route detail")
            var reads = 0
            val error = assertThrows(PhysicalWaitTimeout::class.java) {
                runPhysicalPublicEgress(7, 250, { clock.now }, clock::sleep, {
                    if (++reads == 1 && hasObservation) ready().copy(minSatisfied = false)
                    else throw failure
                }, { fail("failed reads must not launch a probe") })
            }
            assertSame(failure, error.cause)
            assertEquals(3, reads)
            assertEquals(250L, clock.now)
            val fields = PhysicalFailureEvidence().capture(error, "connect-h3", 1_000, clock.now).statusFields()
            if (hasObservation) {
                val observation = fields["publicReadiness"] as? Map<*, *>
                assertNotNull("failed reads lost the earlier timestamped observation", observation)
                assertEquals(1L, observation!!["observationIndex"])
                assertEquals(0L, observation["elapsedMs"])
                assertEquals(false, observation["minSatisfied"])
            } else {
                assertFalse(fields.containsKey("publicReadiness"))
            }
            assertFalse(fields.toString().contains("synthetic private route detail"))
        }
    }

    @Test fun `deadline before any route read does not invent an observation`() {
        var clockReads = 0
        val error = assertThrows(PhysicalWaitTimeout::class.java) {
            runPhysicalPublicEgress(7, 120_000,
                { if (++clockReads == 1) 0L else 120_000L },
                { fail("expired startup must not wait") },
                { throw AssertionError("expired startup must not read a route") },
                { fail("expired startup must not launch a probe") })
        }
        assertEquals(2, clockReads)
        assertNull(error.cause)
        val fields = PhysicalFailureEvidence().capture(error, "connect-h3", 1_000, 120_000).statusFields()
        assertFalse(fields.containsKey("publicReadiness"))
        assertEquals(120_000L, fields["timeoutMs"])
    }

    @Test fun `cancellation after a route read stays exact and has no timeout observation`() {
        val clock = Clock()
        val interruption = InterruptedException("synthetic cancellation after observation")
        var reads = 0
        val error = assertThrows(InterruptedException::class.java) {
            runPhysicalPublicEgress(7, 120_000, { clock.now }, clock::sleep, {
                if (++reads == 1) ready().copy(minSatisfied = false) else throw interruption
            }, { fail("canceled startup must not launch a probe") })
        }
        assertSame(interruption, error)
        assertEquals(2, reads)
        assertEquals(listOf(100L), clock.sleeps)
        assertEquals(100L, clock.now)
        val fields = PhysicalFailureEvidence().capture(error, "connect-h3", 1_000, clock.now).statusFields()
        assertEquals(setOf("errorType", "failurePhase", "failureTimeUnixMs", "failureElapsedRealtimeMs"), fields.keys)
    }

    @Test fun `full startup deadline retains one final observation rather than a history`() {
        val clock = Clock()
        var reads = 0
        val error = assertThrows(PhysicalWaitTimeout::class.java) {
            runPhysicalPublicEgress(7, 120_000, { clock.now }, clock::sleep,
                { reads++; ready().copy(minSatisfied = false) },
                { fail("unsatisfied full startup must not launch a probe") })
        }
        assertEquals(1_200, reads)
        assertEquals(120_000L, clock.now)
        val observation = PhysicalFailureEvidence().capture(error, "connect-h3", 1_000, clock.now)
            .statusFields()["publicReadiness"] as? Map<*, *>
        assertNotNull("full startup timeout omitted its final observation", observation)
        assertEquals(12, observation!!.size)
        assertEquals(1_200L, observation["observationIndex"])
        assertEquals(119_900L, observation["elapsedMs"])
        assertTrue(observation.values.all { it is Number || it is Boolean })
    }
}
