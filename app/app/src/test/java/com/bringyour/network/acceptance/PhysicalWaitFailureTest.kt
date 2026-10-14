package com.bringyour.network.acceptance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class PhysicalWaitFailureTest {
    private class Clock {
        var now = 0L
        val sleeps = mutableListOf<Long>()
        fun sleep(millis: Long) {
            sleeps += millis
            now += millis
        }
    }

    @Test
    fun `every wait retains its finite stage and the original deadline`() {
        val expected = listOf("peer-traffic-counters", "client-disconnect", "provider-stop", "transport-policy",
            "us-country-pool", "public-vpn-connection", "us-provider-carrier-evidence", "same-network-provider",
            "connectable-peer", "peer-vpn-connection", "peer-carrier-evidence", "provider-traffic-counters")
        assertEquals(expected, PhysicalWaitStage.entries.map { it.wireValue })
        for (stage in PhysicalWaitStage.entries) {
            val clock = Clock()
            var attempts = 0
            val thrown = assertThrows(PhysicalWaitTimeout::class.java) {
                waitForPhysicalCondition(stage, 1_000, { clock.now }, clock::sleep) {
                    attempts += 1
                    false
                }
            }
            assertEquals(10, attempts)
            assertEquals(1_000L, clock.now)
            assertEquals(List(10) { 100L }, clock.sleeps)
            assertEquals("Timed out waiting for ${stage.description} after 1s", thrown.message)
            assertEquals(mapOf("stage" to stage.wireValue, "failure" to "wait-timeout"),
                physicalWaitFailureEvidence(thrown))
        }
    }

    @Test
    fun `the last retry error remains a cause but never enters status evidence`() {
        val clock = Clock()
        val errors = listOf(IllegalStateException("private-user"), IllegalArgumentException("private-token"))
        var attempts = 0
        val thrown = assertThrows(PhysicalWaitTimeout::class.java) {
            waitForPhysicalCondition(PhysicalWaitStage.PUBLIC_VPN_CONNECTION, 200, { clock.now }, clock::sleep) {
                throw errors[attempts++]
            }
        }
        assertSame(errors.last(), thrown.cause)
        assertEquals(mapOf("stage" to "public-vpn-connection", "failure" to "wait-timeout"),
            physicalWaitFailureEvidence(thrown))
    }

    @Test
    fun `fatal condition errors preserve identity without retry`() {
        val failures = listOf(
            OutOfMemoryError("synthetic fatal allocation"),
            LinkageError("synthetic fatal linkage"),
            AssertionError("synthetic fatal assertion"),
        )
        for (failure in failures) {
            val clock = Clock()
            var reads = 0
            val error = assertThrows(Error::class.java) {
                waitForPhysicalCondition(PhysicalWaitStage.PUBLIC_VPN_CONNECTION, 1_000,
                    { clock.now }, clock::sleep) { reads++; throw failure }
            }
            assertSame(failure, error)
            assertEquals(1, reads)
            assertEquals(emptyList<Long>(), clock.sleeps)
            assertEquals(0L, clock.now)
        }
    }

    @Test
    fun `condition interruption preserves identity without retry`() {
        val clock = Clock()
        val interruption = InterruptedException("synthetic cancellation")
        var reads = 0
        val error = assertThrows(InterruptedException::class.java) {
            waitForPhysicalCondition(PhysicalWaitStage.CLIENT_DISCONNECT, 1_000,
                { clock.now }, clock::sleep) { reads++; throw interruption }
        }
        assertSame(interruption, error)
        assertEquals(1, reads)
        assertEquals(emptyList<Long>(), clock.sleeps)
        assertEquals(0L, clock.now)
    }

    @Test
    fun `successful retry and immediate success retain no failure state`() {
        val clock = Clock()
        var attempts = 0
        waitForPhysicalCondition(PhysicalWaitStage.US_COUNTRY_POOL, 1_000, { clock.now }, clock::sleep) {
            when (++attempts) {
                1 -> throw IllegalStateException("private transient error")
                2 -> false
                else -> true
            }
        }
        assertEquals(3, attempts)
        assertEquals(200L, clock.now)
        waitForPhysicalCondition(PhysicalWaitStage.PUBLIC_VPN_CONNECTION, 1_000, { clock.now }, clock::sleep) { true }
        assertEquals(200L, clock.now)
        // Neither a message that resembles our timeout nor a nested typed cause
        // proves that a different failure was this wait's deadline.
        for (error in listOf(AssertionError("Timed out waiting for public VPN connection after 120s"),
            IllegalStateException("private", PhysicalWaitTimeout(PhysicalWaitStage.US_COUNTRY_POOL, 100, null)))) {
            assertNull(physicalWaitFailureEvidence(error))
        }
    }
}
