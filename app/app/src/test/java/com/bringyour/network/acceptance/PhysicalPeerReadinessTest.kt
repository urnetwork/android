package com.bringyour.network.acceptance

import org.junit.Assert.*
import org.junit.Test

class PhysicalPeerReadinessTest {
    private class Clock {
        var now = 0L
        val sleeps = mutableListOf<Long>()
        fun sleep(millis: Long) { sleeps += millis; now += millis }
    }
    private val peer = PhysicalProviderEvidence("expected-peer", "", false)
    private fun state(providers: List<PhysicalProviderEvidence> = listOf(peer)) =
        PhysicalPeerRouteState(true, true, true, peer.clientId, providers)

    @Test fun `intent and tunnel cannot spend the egress budget before exact peer admission`() {
        val clock = Clock()
        val timing = PhysicalPeerConnectTiming(0, 120_000)
        var probes = 0
        val result = runPhysicalPeerEgress(peer.clientId, timing, { clock.now }, clock::sleep,
            prepareConnection = { clock.now = 8_000 },
            routeState = { state(if (clock.now >= 23_000) listOf(peer) else emptyList()) },
            egressProof = {
                probes++
                check(clock.now >= 23_000) { "probe entered before independent window admission" }
                clock.now += 6_000
                "result"
            })
        assertEquals("result", result)
        assertEquals(1, probes)
        assertEquals(23_000L, timing.evidence(clock.now)["startupElapsedMs"])
        assertEquals(6_000L, timing.evidence(clock.now)["egressProofElapsedMs"])
        assertEquals(29_000L, timing.evidence(clock.now)["connectionElapsedMs"])
    }

    @Test fun `eligible peer needs no preceding application bytes`() {
        val clock = Clock()
        val timing = PhysicalPeerConnectTiming(0, 120_000)
        var probes = 0
        runPhysicalPeerEgress(peer.clientId, timing, { clock.now }, clock::sleep,
            {}, { state() }, { probes++ })
        assertEquals(1, probes)
        assertTrue(clock.sleeps.isEmpty())
        assertEquals(0L, timing.evidence(clock.now)["eligibleAfterMs"])
    }

    @Test fun `empty wrong mixed and changed destination states never admit a probe`() {
        val states = listOf(
            state(emptyList()), state(listOf(peer.copy(clientId = "wrong"))),
            state(listOf(peer, peer.copy(clientId = "wrong"))),
            state().copy(requestedPeerId = "new-destination"),
            state().copy(requestedPeerId = null),
            state().copy(controllerConnected = false),
            state().copy(connectEnabled = false), state().copy(tunnelStarted = false),
        )
        for (observed in states) {
            val clock = Clock()
            val timing = PhysicalPeerConnectTiming(0, 120_000)
            var probes = 0
            val error = assertThrows(PhysicalWaitTimeout::class.java) {
                runPhysicalPeerEgress(peer.clientId, timing, { clock.now }, clock::sleep,
                    {}, { observed }, { probes++ })
            }
            assertEquals(PhysicalWaitStage.PEER_VPN_CONNECTION, error.stage)
            assertEquals(120_000L, clock.now)
            assertEquals(0, probes)
            assertFalse(timing.evidence(clock.now).containsKey("eligibleAfterMs"))
            assertEquals(120_000L, timing.evidence(clock.now)["startupElapsedMs"])
        }
    }

    @Test fun `consent and changing intent share one absolute startup deadline`() {
        val clock = Clock()
        val timing = PhysicalPeerConnectTiming(0, 120_000)
        var probes = 0
        assertThrows(PhysicalWaitTimeout::class.java) {
            runPhysicalPeerEgress(peer.clientId, timing, { clock.now }, clock::sleep,
                { clock.now = 90_000 },
                { state(emptyList()).copy(controllerConnected = clock.now % 200L == 0L) },
                { probes++ })
        }
        assertEquals(120_000L, clock.now)
        assertEquals(300, clock.sleeps.size)
        assertEquals(0, probes)
    }

    @Test fun `late preparation or observation cannot admit past the original deadline`() {
        for (duringObservation in listOf(false, true)) {
            val clock = Clock()
            val timing = PhysicalPeerConnectTiming(0, 120_000)
            var probes = 0
            assertThrows(PhysicalWaitTimeout::class.java) {
                runPhysicalPeerEgress(peer.clientId, timing, { clock.now }, clock::sleep,
                    { if (!duringObservation) clock.now = 120_000 },
                    { if (duringObservation) clock.now = 120_000; state() },
                    { probes++ })
            }
            assertEquals(0, probes)
        }
    }

    @Test fun `an unhealthy twenty second egress attempt stays failed without retry`() {
        val clock = Clock()
        val timing = PhysicalPeerConnectTiming(0, 120_000)
        val failure = AssertionError("original egress deadline")
        var probes = 0
        val error = assertThrows(AssertionError::class.java) {
            runPhysicalPeerEgress(peer.clientId, timing, { clock.now }, clock::sleep,
                {}, { state() }, { probes++; clock.now += 20_000; throw failure })
        }
        assertSame(failure, error)
        assertEquals(1, probes)
        assertEquals(20_000L, timing.evidence(clock.now)["egressProofElapsedMs"])
    }

    @Test fun `startup success near its bound does not shorten or restart the egress proof`() {
        val clock = Clock()
        val timing = PhysicalPeerConnectTiming(0, 120_000)
        runPhysicalPeerEgress(peer.clientId, timing, { clock.now }, clock::sleep,
            { clock.now = 119_000 }, { state() }, { clock.now += 20_000 })
        assertEquals(119_000L, timing.evidence(clock.now)["startupElapsedMs"])
        assertEquals(20_000L, timing.evidence(clock.now)["egressProofElapsedMs"])
        assertEquals(139_000L, timing.evidence(clock.now)["connectionElapsedMs"])
    }

    @Test fun `cancellation before or during readiness cannot start a late probe`() {
        for (cancelAt in listOf(0L, 500L)) {
            val clock = Clock()
            val timing = PhysicalPeerConnectTiming(0, 120_000)
            var probes = 0
            assertThrows(InterruptedException::class.java) {
                runPhysicalPeerEgress(peer.clientId, timing, { clock.now }, clock::sleep,
                    {}, { state(emptyList()) }, { probes++ }, { clock.now >= cancelAt })
            }
            assertEquals(cancelAt, clock.now)
            assertEquals(0, probes)
        }
    }

    @Test fun `query cancellation and changed tunnel preserve the first error`() {
        for (failure in listOf(InterruptedException("canceled"), AssertionError("generation changed"))) {
            val clock = Clock()
            val timing = PhysicalPeerConnectTiming(0, 120_000)
            var probes = 0
            val error = assertThrows(Throwable::class.java) {
                runPhysicalPeerEgress(peer.clientId, timing, { clock.now }, clock::sleep,
                    {}, { state() }, { probes++; clock.now += 300; throw failure })
            }
            assertSame(failure, error)
            assertEquals(1, probes)
            assertEquals(300L, timing.evidence(clock.now)["egressProofFinishedAfterMs"])
        }
    }

    @Test fun `read errors remain bounded causes without entering finite timing metadata`() {
        val clock = Clock()
        val timing = PhysicalPeerConnectTiming(0, 250)
        val original = IllegalStateException("private-client-token")
        val error = assertThrows(PhysicalWaitTimeout::class.java) {
            runPhysicalPeerEgress(peer.clientId, timing, { clock.now }, clock::sleep,
                {}, { throw original }, { fail("unexpected probe") })
        }
        assertSame(original, error.cause)
        assertEquals(listOf(100L, 100L, 50L), clock.sleeps)
        assertEquals(setOf("schemaVersion", "startupTimeoutMs", "startupElapsedMs", "connectionElapsedMs",
            "commandElapsedMs", "connectionRequestedAfterCommandMs"),
            timing.evidence(clock.now).keys)
        assertEquals(250L, timing.evidence(clock.now)["connectionElapsedMs"])
    }

    @Test fun `full command elapsed includes discovery and later carrier proof without stale probe timing`() {
        val clock = Clock().apply { now = 5_000 }
        val timing = PhysicalPeerConnectTiming(5_000, 120_000, commandStartedAtMillis = 0)
        runPhysicalPeerEgress(peer.clientId, timing, { clock.now }, clock::sleep,
            { clock.now += 1_000 }, { state() }, { clock.now += 2_000 })
        clock.now += 3_000 // existing post-query carrier proof, separately visible
        assertEquals(5_000L, timing.evidence(clock.now)["connectionRequestedAfterCommandMs"])
        assertEquals(1_000L, timing.evidence(clock.now)["startupElapsedMs"])
        assertEquals(2_000L, timing.evidence(clock.now)["egressProofElapsedMs"])
        assertEquals(6_000L, timing.evidence(clock.now)["connectionElapsedMs"])
        assertEquals(11_000L, timing.evidence(clock.now)["commandElapsedMs"])
        val nextCommand = PhysicalPeerConnectTiming(clock.now, 120_000)
        assertFalse(nextCommand.evidence(clock.now).containsKey("eligibleAfterMs"))
        assertFalse(nextCommand.evidence(clock.now).containsKey("egressProofElapsedMs"))
    }

    @Test fun `successful connect survives probe overwrite without becoming current probe timing`() {
        val evidence = PhysicalPeerTimingEvidence()
        evidence.beginCommand()
        evidence.beginPeerConnect()
        val timing = PhysicalPeerConnectTiming(100, 120_000, 0)
        evidence.startConnect(timing)
        timing.eligible(300)
        timing.egressProofStarted(300)
        timing.egressProofFinished(500)
        val connected = evidence.snapshot(600, true)
        assertEquals(600L, connected.current?.get("commandElapsedMs"))
        assertNotNull(connected.lastConnect)
        assertEquals(1L, connected.lastConnect?.commandSequence)
        assertEquals(true, connected.lastConnect?.successful)

        evidence.beginCommand() // client-probe, then status/snapshot/finish commands
        assertEquals(2L, evidence.commandSequence)
        val probeRunning = evidence.snapshot(900)
        val probeComplete = evidence.snapshot(1_200, true)
        assertNull(probeRunning.current)
        assertNull(probeComplete.current)
        assertEquals(connected.lastConnect, probeRunning.lastConnect)
        assertEquals(connected.lastConnect, probeComplete.lastConnect)
        assertEquals(600L, probeComplete.lastConnect?.timing?.get("commandElapsedMs"))
        evidence.beginCommand()
        assertEquals(connected.lastConnect, evidence.snapshot(1_400, false).lastConnect)
    }

    @Test fun `new peer command clears prior evidence before request and records null timing failure`() {
        val evidence = PhysicalPeerTimingEvidence()
        evidence.beginCommand()
        evidence.beginPeerConnect()
        evidence.startConnect(PhysicalPeerConnectTiming(0, 120_000))
        assertNotNull(evidence.snapshot(10, true).lastConnect)
        evidence.beginCommand()
        evidence.beginPeerConnect() // before stop/configuration/discovery/consent
        assertNull(evidence.snapshot(20).current)
        assertNull(evidence.snapshot(20).lastConnect)
        val failedBeforeRequest = evidence.snapshot(30, false).lastConnect
        assertNotNull(failedBeforeRequest)
        assertEquals(2L, failedBeforeRequest?.commandSequence)
        assertEquals(false, failedBeforeRequest?.successful)
        assertNull(failedBeforeRequest?.timing)
        evidence.beginCommand()
        assertEquals(failedBeforeRequest, evidence.snapshot(40, true).lastConnect)
    }

    @Test fun `first terminal peer outcome is immutable under late completion and clock changes`() {
        for (firstSuccessful in listOf(false, true)) {
            val evidence = PhysicalPeerTimingEvidence()
            evidence.beginCommand()
            evidence.beginPeerConnect()
            val timing = PhysicalPeerConnectTiming(0, 120_000)
            evidence.startConnect(timing)
            timing.eligible(10)
            timing.egressProofStarted(10)
            timing.egressProofFinished(20)
            val first = evidence.snapshot(30, firstSuccessful)
            assertNotNull(first.lastConnect)
            timing.egressProofFinished(2_000) // a late outcome cannot revise retained evidence
            val late = evidence.snapshot(3_000, !firstSuccessful)
            assertEquals(first, late)
            assertEquals(firstSuccessful, late.lastConnect?.successful)
            assertEquals(10L, late.lastConnect?.timing?.get("egressProofElapsedMs"))
        }
    }

    @Test fun `canceled single proof is retained as failure with bounded numeric timing`() {
        val clock = Clock()
        val evidence = PhysicalPeerTimingEvidence()
        evidence.beginCommand()
        evidence.beginPeerConnect()
        val timing = PhysicalPeerConnectTiming(0, 120_000)
        evidence.startConnect(timing)
        val original = InterruptedException("private-error-and-url")
        var probes = 0
        val error = assertThrows(InterruptedException::class.java) {
            runPhysicalPeerEgress(peer.clientId, timing, { clock.now }, clock::sleep,
                {}, { state() }, { probes++; clock.now = 200; throw original })
        }
        assertSame(original, error)
        assertEquals(1, probes)
        val failed = evidence.snapshot(clock.now, false).lastConnect
        assertNotNull(failed)
        assertEquals(false, failed?.successful)
        assertEquals(200L, failed?.timing?.get("egressProofElapsedMs"))
        assertEquals(setOf("schemaVersion", "startupTimeoutMs", "startupElapsedMs", "connectionElapsedMs",
            "commandElapsedMs", "connectionRequestedAfterCommandMs", "eligibleAfterMs",
            "egressProofStartedAfterMs", "egressProofFinishedAfterMs", "egressProofElapsedMs"),
            failed?.timing?.keys)
        evidence.beginCommand()
        assertEquals(failed, evidence.snapshot(300, true).lastConnect)
    }

    @Test fun `unrelated commands never invent or finish peer evidence`() {
        val evidence = PhysicalPeerTimingEvidence()
        assertEquals(0L, evidence.commandSequence)
        for (outcome in listOf<Boolean?>(null, true, false)) {
            evidence.beginCommand()
            val status = evidence.snapshot(100, outcome)
            assertNull(status.current)
            assertNull(status.lastConnect)
        }
        assertEquals(3L, evidence.commandSequence)
    }

    @Test fun `new active connection replaces history without inheriting prior terminal outcome`() {
        val evidence = PhysicalPeerTimingEvidence()
        evidence.beginCommand()
        evidence.beginPeerConnect()
        evidence.startConnect(PhysicalPeerConnectTiming(0, 120_000))
        evidence.snapshot(10, false)
        evidence.beginCommand()
        evidence.beginPeerConnect()
        evidence.startConnect(PhysicalPeerConnectTiming(100, 120_000, 90))
        val running = evidence.snapshot(110)
        assertEquals(20L, running.current?.get("commandElapsedMs"))
        assertNull(running.lastConnect)
        val completed = evidence.snapshot(120, true)
        assertNotNull(completed.lastConnect)
        assertEquals(2L, completed.lastConnect?.commandSequence)
        assertEquals(true, completed.lastConnect?.successful)
        assertEquals(30L, completed.lastConnect?.timing?.get("commandElapsedMs"))
    }
}
