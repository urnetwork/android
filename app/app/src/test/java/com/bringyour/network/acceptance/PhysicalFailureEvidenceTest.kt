package com.bringyour.network.acceptance

import com.bringyour.network.LoginStartupFailure
import com.bringyour.network.LoginStartupStage
import com.bringyour.network.LoginStartupState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class PhysicalFailureEvidenceTest {
    @Test
    fun `failure is captured before owned logout and rethrown unchanged after cleanup`() {
        val evidence = PhysicalFailureEvidence()
        val primary = AssertionError("synthetic egress failure at private-route.example")
        var state: LoginStartupState = LoginStartupState.Ready(1, "synthetic-client")
        var now = 1_000L
        var phase = "connect-h1"
        val events = mutableListOf<String>()
        val cleanupFailures = mutableListOf<String>()
        var first: PhysicalFailureSnapshot? = null
        val thrown = assertThrows(AssertionError::class.java) {
            try {
                throw primary
            } catch (error: Throwable) {
                first = evidence.capture(error, phase, 1_700_000_000_000L, now, 75,
                    PhysicalFailureOperation.EGRESS_PROBE)
                events.add("failure-captured")
                throw error
            } finally {
                closePhysicalMemoryOwner(
                    stop = { events.add("stop") },
                    joinDrainer = { events.add("join-drainer") },
                    logout = {
                        events.add("logout")
                        state = LoginStartupState.LoggedOut(1, "synthetic-client")
                        now += 200
                        phase = "synthetic-cleanup"
                    },
                    joinDevice = { throw IllegalStateException("synthetic cleanup failure") },
                    drainDeviceRing = { throw AssertionError("failed join must not drain") },
                    failed = { cleanupFailures.add(it) },
                )
            }
        }
        assertSame(primary, thrown)
        assertEquals(listOf("failure-captured", "stop", "join-drainer", "logout"), events)
        assertEquals(listOf("device-join"), cleanupFailures)
        assertEquals(LoginStartupState.LoggedOut(1, "synthetic-client"), state)
        val published = evidence.capture(thrown, phase, 1_700_000_000_200L, now, 275)
        assertSame(first, published)
        assertEquals("connect-h1", published.phase)
        assertEquals("egress-probe", published.stage)
        assertEquals("egress-probe-failed", published.failure)
        assertEquals(1_700_000_000_000L, published.timeUnixMs)
        assertEquals(1_000L, published.elapsedRealtimeMs)
        assertEquals(75L, published.commandElapsedMs)
        assertFalse(published.statusFields().toString().contains("private-route.example"))
    }

    @Test
    fun `password failure remains primary through every ambient startup transition`() {
        val ui = PasswordLoginDiscoveryEvidence(true, false, true)
        val primary = PasswordLoginFailureException(PasswordLoginStage.DISCOVERY,
            PasswordLoginFailure.DISCOVERY_FAILED, ui, IllegalStateException("synthetic private body"))
        val transitions = listOf(
            LoginStartupState.Failed(2, LoginStartupStage.CLIENT_ALLOCATION,
                LoginStartupFailure.AUTH_CLIENT_REQUEST_FAILED, "synthetic-client"),
            LoginStartupState.Pending(2, LoginStartupStage.AUTH_CLIENT),
            LoginStartupState.LoggedOut(2, "synthetic-client"),
        )
        for (state in transitions) {
            val evidence = PhysicalFailureEvidence()
            val captured = evidence.capture(primary, "startup", 100, 50)
            // The application may change to any of these states before the
            // outer catch; none is an input to the original error's classifier.
            assertEquals(2L, state.attemptId)
            val published = evidence.capture(primary, "synthetic-later-phase", 200, 150)
            assertSame(captured, published)
            assertEquals("auth-discovery", published.stage)
            assertEquals("auth-discovery-failed", published.failure)
            assertEquals(ui, published.loginUiBeforeTeardown)
            assertNotSame(ui, published.loginUiBeforeTeardown)
            assertEquals(mapOf("userFormVisible" to true, "passwordFormVisible" to false,
                "discoveryErrorVisible" to true), published.statusFields()["loginUiBeforeTeardown"])
            assertFalse(published.statusFields().toString().contains("synthetic private body"))
        }
    }

    @Test
    fun `typed waits retain their own deadline rather than cleanup duration or operation label`() {
        for (stage in PhysicalWaitStage.entries) {
            var now = 0L
            val error = assertThrows(PhysicalWaitTimeout::class.java) {
                waitForPhysicalCondition(stage, 300, { now }, { now += it }) { false }
            }
            val evidence = PhysicalFailureEvidence()
            val captured = evidence.capture(error, "provide", 1_000, now, now,
                PhysicalFailureOperation.EGRESS_PROBE)
            val published = evidence.capture(error, "synthetic-cleanup", 4_000, now + 3_000)
            assertSame(captured, published)
            assertEquals(stage.wireValue, published.stage)
            assertEquals("wait-timeout", published.failure)
            assertEquals(300L, published.timeoutMillis)
            assertEquals(300L, published.elapsedRealtimeMs)
            assertEquals(300L, published.statusFields()["timeoutMs"])
        }
    }

    @Test
    fun `only errors created by the actual startup throw sites receive startup attribution`() {
        val failed = LoginStartupState.Failed(3, LoginStartupStage.CLIENT_JWT_VALIDATION,
            LoginStartupFailure.CLIENT_JWT_INVALID, "synthetic-client")
        val errors = listOf(
            Triple(LoginStartupFailureException(failed), "client-jwt-validation", "client-jwt-invalid"),
            Triple(CleanupLedgerFailureException(IllegalStateException("synthetic ledger detail")),
                "client-allocation", "cleanup-ledger-persistence-failed"),
            Triple(PhysicalLoginLoggedOutException(), "logout", "auth-logout"),
            Triple(PhysicalLoginStartupTimeout(LoginStartupState.Pending(3, LoginStartupStage.AUTH_CLIENT), 500),
                "auth-client", "startup-timeout"),
            Triple(PhysicalLoginStartupTimeout(LoginStartupState.Idle, 500), "startup", "startup-timeout"),
        )
        for ((error, stage, failure) in errors) {
            val snapshot = PhysicalFailureEvidence().capture(error, "startup", 1_000, 500,
                operation = PhysicalFailureOperation.EGRESS_PROBE)
            assertEquals(stage, snapshot.stage)
            assertEquals(failure, snapshot.failure)
            if (error is PhysicalLoginStartupTimeout) {
                assertEquals(500L, snapshot.timeoutMillis)
                assertSame(error, assertThrows(AssertionError::class.java) {
                    throwLoginStartupTimeout(LoginStartupState.Idle, error) {
                        throw AssertionError("idle must not capture pending stacks")
                    }
                })
            }
        }
    }

    @Test
    fun `generic readiness connect and provider errors are not inferred from messages or nested causes`() {
        for (phase in listOf("startup", "ready", "connect-h1", "provide", "stop-provide")) {
            val error = AssertionError("auth-logout startup-timeout synthetic-account",
                PhysicalWaitTimeout(PhysicalWaitStage.PUBLIC_VPN_CONNECTION, 500, null))
            val snapshot = PhysicalFailureEvidence().capture(error, phase, 1_000, 500)
            assertEquals(phase, snapshot.phase)
            assertNull(snapshot.stage)
            assertNull(snapshot.failure)
            assertNull(snapshot.timeoutMillis)
            assertNull(snapshot.loginUiBeforeTeardown)
            assertEquals(setOf("errorType", "failurePhase", "failureTimeUnixMs", "failureElapsedRealtimeMs"),
                snapshot.statusFields().keys)
            assertFalse(snapshot.statusFields().toString().contains("synthetic-account"))
        }
    }

    @Test
    fun `first evidence is bound to exact throwable identity and cannot relabel another error`() {
        val evidence = PhysicalFailureEvidence()
        val firstError = AssertionError("same synthetic message")
        val first = evidence.capture(firstError, "connect-h1", 1_000, 500,
            operation = PhysicalFailureOperation.EGRESS_PROBE)
        val nextError = AssertionError("same synthetic message")
        val next = evidence.capture(nextError, "provide", 2_000, 1_500)
        assertNotSame(first, next)
        assertEquals("provide", next.phase)
        assertNull(next.stage)
        assertNull(next.failure)
        assertEquals(2_000L, next.timeUnixMs)
    }

    @Test
    fun `public timeout predicates survive owned cleanup and repeated capture unchanged`() {
        val evidence = PhysicalFailureEvidence()
        val providers = mutableListOf(PhysicalProviderEvidence("synthetic-private-provider", "US", true))
        var now = 0L
        var phase = "connect-h3"
        var first: PhysicalFailureSnapshot? = null
        var capturedError: Throwable? = null
        val events = mutableListOf<String>()
        val error = assertThrows(PhysicalWaitTimeout::class.java) {
            try {
                runPhysicalPublicEgress(7, 250, { now }, { now += it }, {
                    PhysicalPublicRouteState(true, true, true, 7, 7, false, false, providers)
                }, { throw AssertionError("unsatisfied route must not launch a probe") })
            } catch (failure: Throwable) {
                capturedError = failure
                first = evidence.capture(failure, phase, 1_000 + now, now, now)
                events.add("failure-captured")
                throw failure
            } finally {
                closePhysicalMemoryOwner(
                    stop = { events.add("stop") },
                    joinDrainer = { events.add("join-drainer") },
                    logout = {
                        events.add("logout")
                        providers.clear()
                        now += 2_000
                        phase = "synthetic-cleanup"
                    },
                    joinDevice = { events.add("join-device") },
                    drainDeviceRing = { events.add("drain") },
                    failed = { throw AssertionError("healthy cleanup failed: $it") },
                )
            }
        }
        assertSame(capturedError, error)
        assertEquals(listOf("failure-captured", "stop", "join-drainer", "logout", "join-device", "drain"), events)
        val published = evidence.capture(error, phase, 1_000 + now, now, now)
        assertSame(first, published)
        assertEquals("connect-h3", published.phase)
        assertEquals(1_250L, published.timeUnixMs)
        assertEquals(250L, published.elapsedRealtimeMs)
        assertEquals(250L, published.commandElapsedMs)
        assertEquals(250L, published.timeoutMillis)
        val fields = published.statusFields()
        assertEquals(setOf("errorType", "failurePhase", "failureTimeUnixMs", "failureElapsedRealtimeMs",
            "commandElapsedMs", "stage", "failure", "timeoutMs", "publicReadiness"), fields.keys)
        assertEquals(mapOf(
            "observationIndex" to 3L, "elapsedMs" to 200L,
            "expectedConnectionGeneration" to 7L, "generationBefore" to 7L, "generationAfter" to 7L,
            "controllerConnected" to true, "connectEnabled" to true, "tunnelStarted" to true,
            "minSatisfied" to false, "failed" to false, "providerCount" to 1, "usProvidersValid" to true,
        ), fields["publicReadiness"])
        assertFalse(fields.toString().contains("synthetic-private-provider"))
        assertNotSame(fields["publicReadiness"], published.statusFields()["publicReadiness"])
    }

    @Test
    fun `public evidence never leaks into later peer login generic or cancellation failures`() {
        val evidence = PhysicalFailureEvidence()
        var now = 0L
        val publicError = assertThrows(PhysicalWaitTimeout::class.java) {
            runPhysicalPublicEgress(7, 100, { now }, { now += it },
                { PhysicalPublicRouteState(true, true, true, 7, 7, false, false, emptyList()) },
                { throw AssertionError("unsatisfied route must not launch a probe") })
        }
        val otherErrors = listOf(
            PhysicalWaitTimeout(PhysicalWaitStage.PEER_VPN_CONNECTION, 100, null),
            PhysicalWaitTimeout(PhysicalWaitStage.PUBLIC_VPN_CONNECTION, 100, null),
            PasswordLoginFailureException(PasswordLoginStage.DISCOVERY,
                PasswordLoginFailure.DISCOVERY_FAILED, PasswordLoginDiscoveryEvidence(true, false, true)),
            AssertionError("synthetic unrelated failure", publicError),
            InterruptedException("synthetic cancellation"),
        )
        for (other in otherErrors) {
            val publicSnapshot = evidence.capture(publicError, "connect-h3", 1_000, 100)
            assertEquals(1L, (publicSnapshot.statusFields()["publicReadiness"] as? Map<*, *>)?.get("observationIndex"))
            val otherSnapshot = evidence.capture(other, "synthetic-other-phase", 2_000, 200)
            assertNotSame(publicSnapshot, otherSnapshot)
            assertFalse(otherSnapshot.statusFields().containsKey("publicReadiness"))
        }
    }
}
