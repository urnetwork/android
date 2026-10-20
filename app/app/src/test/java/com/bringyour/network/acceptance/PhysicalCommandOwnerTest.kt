package com.bringyour.network.acceptance

import org.junit.Assert.*
import org.junit.Test

/** Actual readiness timeout and mailbox custody, without a device or elapsed sleep. */
class PhysicalCommandOwnerTest {
    @Test fun `failed readiness remains failed after finish and normal cleanup`() {
        val owner = PhysicalCommandOwner()
        val evidence = PhysicalFailureEvidence()
        val events = mutableListOf<String>()
        var now = 0L
        var probes = 0
        var retained: PhysicalWaitTimeout? = null
        val error = assertThrows(PhysicalWaitTimeout::class.java) {
            try {
                assertFalse(owner.run(finish = false, command = {
                    runPhysicalPublicEgress(
                        expectedConnectionGeneration = 7,
                        timeoutMillis = 120_000,
                        nowMillis = { now },
                        sleepMillis = { now += it },
                        routeState = { PhysicalPublicRouteState(true, true, true, 7, 7, false, false, emptyList()) },
                        egressProof = { probes++; true },
                    )
                }, failed = {
                    retained = it
                    val fields = evidence.capture(it, "connect-h1", 1_000, now).statusFields()
                    assertEquals("PhysicalWaitTimeout", fields["errorType"])
                    assertEquals("wait-timeout", fields["failure"])
                    assertEquals("public-vpn-connection", fields["stage"])
                    events += "command-error"
                }))
                assertEquals(120_000L, now)
                assertEquals(0, probes)
                assertTrue(owner.run(finish = true, command = { events += "finish-complete"; true },
                    failed = { fail("finish must not publish the old error") }))
            } finally {
                events += "owner-joined"
            }
            owner.failure?.let { throw it }
        }
        assertSame(retained, error)
        assertSame(owner.failure, error)
        assertEquals(listOf("command-error", "finish-complete", "owner-joined"), events)
    }

    @Test fun `a failed owner refuses all later work without retrying its action`() {
        val owner = PhysicalCommandOwner()
        val timeout = PhysicalWaitTimeout(PhysicalWaitStage.PUBLIC_VPN_CONNECTION, 120_000, null)
        var calls = 0
        var errors = 0
        val failed: (PhysicalWaitTimeout) -> Unit = { assertSame(timeout, it); errors++ }
        assertFalse(owner.run(false, { calls++; throw timeout }, failed))
        repeat(3) { assertFalse(owner.run(false, { calls++; true }, failed)) }
        assertEquals(1, calls)
        assertEquals(4, errors)
        assertSame(timeout, owner.failure)
        assertTrue(owner.run(true, { calls++; true }, failed))
        assertEquals(2, calls)
        assertEquals(4, errors)
    }

    @Test fun `fatal and canceled work never becomes a terminal cleanup acknowledgment`() {
        for (failure in listOf(
            InterruptedException("synthetic cancel"),
            IllegalStateException("synthetic ledger failure"),
            IllegalArgumentException("synthetic invalid command"),
            AssertionError("synthetic fatal assertion"),
            OutOfMemoryError("synthetic allocation failure"),
        )) {
            val owner = PhysicalCommandOwner()
            val error = assertThrows(Throwable::class.java) {
                owner.run(false, { throw failure }, { fail("fatal command acquired finish-only authority") })
            }
            assertSame(failure, error)
            assertNull(owner.failure)
        }
        val owner = PhysicalCommandOwner()
        assertThrows(InterruptedException::class.java) {
            owner.run(false, { fail("canceled owner dispatched"); false }, { fail("canceled owner acknowledged") }, { true })
        }
        assertNull(owner.failure)
        var canceled = false
        val timeout = PhysicalWaitTimeout(PhysicalWaitStage.PUBLIC_VPN_CONNECTION, 120_000, null)
        val error = assertThrows(PhysicalWaitTimeout::class.java) {
            owner.run(false, { canceled = true; throw timeout }, { fail("cancellation published an ack") }, { canceled })
        }
        assertSame(timeout, error)
        assertNull(owner.failure)
    }

    @Test fun `failed finish and failed error publication still unwind without a success`() {
        for (priorFailure in listOf(false, true)) {
            val owner = PhysicalCommandOwner()
            val original = PhysicalWaitTimeout(PhysicalWaitStage.PUBLIC_VPN_CONNECTION, 120_000, null)
            if (priorFailure) assertFalse(owner.run(false, { throw original }, {}))
            val finishFailure = PhysicalWaitTimeout(PhysicalWaitStage.CLIENT_DISCONNECT, 120_000, null)
            val error = assertThrows(PhysicalWaitTimeout::class.java) {
                owner.run(true, { throw finishFailure }, { fail("failed finish claimed live cleanup authority") })
            }
            assertSame(finishFailure, error)
            if (priorFailure) assertSame(original, owner.failure) else assertNull(owner.failure)
        }
        val owner = PhysicalCommandOwner()
        val timeout = PhysicalWaitTimeout(PhysicalWaitStage.PUBLIC_VPN_CONNECTION, 120_000, null)
        val publicationFailure = IllegalStateException("synthetic status publication failed")
        val error = assertThrows(IllegalStateException::class.java) {
            owner.run(false, { throw timeout }, { throw publicationFailure })
        }
        assertSame(publicationFailure, error)
        assertSame(timeout, owner.failure)
    }

    @Test fun `healthy commands and finish retain their original result`() {
        val owner = PhysicalCommandOwner()
        var calls = 0
        assertFalse(owner.run(false, { calls++; false }, { fail("healthy command failed") }))
        assertTrue(owner.run(true, { calls++; true }, { fail("healthy finish failed") }))
        assertEquals(2, calls)
        assertNull(owner.failure)
    }
}
