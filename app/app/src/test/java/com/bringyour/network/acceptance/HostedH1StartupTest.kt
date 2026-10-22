package com.bringyour.network.acceptance

import com.bringyour.network.NetworkSpaceApiAltStartup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Exact launch scope and the early binding used by the real instrumentation runner. */
class HostedH1StartupTest {
    private fun arguments(): Map<String, String> = mapOf(
        HOSTED_H1_POLICY_ARGUMENT to HOSTED_H1_POLICY,
        "class" to HOSTED_H1_SESSION_CLASS,
        "acceptanceBuildId" to "fixture-build",
    )

    @Test
    fun ordinaryApplicationAndIndependentOwnersKeepApiAltEnabled() {
        val ordinary = NetworkSpaceApiAltStartup()
        assertTrue(ordinary.construct { it })
        val hosted = NetworkSpaceApiAltStartup()
        startHostedH1Application(hosted) { assertFalse(hosted.construct { it }) }
        assertTrue(NetworkSpaceApiAltStartup().construct { it })
    }

    @Test
    fun hostedBindingPrecedesTheFirstApplicationConstruction() {
        requireHostedH1Startup(arguments()::get)
        val startup = NetworkSpaceApiAltStartup()
        var createCount = 0
        startHostedH1Application(startup) {
            startup.construct { apiAltEnabled ->
                assertFalse(apiAltEnabled)
                createCount += 1
            }
        }
        assertEquals(1, createCount)
    }

    @Test
    fun missingOrUnknownPolicyCannotStartTheHostedRunner() {
        for (policy in listOf(null, "", "h1", "hosted-h1-api-alt-off-v2")) {
            val inputs = arguments().toMutableMap()
            if (policy == null) inputs.remove(HOSTED_H1_POLICY_ARGUMENT) else inputs[HOSTED_H1_POLICY_ARGUMENT] = policy
            try {
                requireHostedH1Startup(inputs::get)
                fail("accepted policy=$policy")
            } catch (_: IllegalArgumentException) {
                // Exact explicit policy, never a default inferred from absence.
            }
        }
    }

    @Test
    fun localTestsOrBroadenedSelectionsCannotUseHostedPolicy() {
        for (selection in listOf(null, "LocalH3Test", "$HOSTED_H1_SESSION_CLASS#session", "$HOSTED_H1_SESSION_CLASS,LocalH3Test")) {
            val inputs = arguments().toMutableMap()
            if (selection == null) inputs.remove("class") else inputs["class"] = selection
            try {
                requireHostedH1Startup(inputs::get)
                fail("accepted class=$selection")
            } catch (_: IllegalArgumentException) {
                // Local unit/simulator selections retain their normal runner.
            }
        }
        val noBuild = arguments() - "acceptanceBuildId"
        try {
            requireHostedH1Startup(noBuild::get)
            fail("accepted an unbound build")
        } catch (_: IllegalArgumentException) {
            // Physical launch requires its existing build identity.
        }
    }

    @Test
    fun lateBindingCannotRelabelAnAlreadyConstructedManager() {
        val startup = NetworkSpaceApiAltStartup()
        assertTrue(startup.construct { it })
        var createCount = 0
        try {
            startHostedH1Application(startup) { createCount += 1 }
            fail("accepted a late binding")
        } catch (_: IllegalStateException) {
            assertEquals(0, createCount)
        }
    }

    @Test
    fun applicationInitializationRetryKeepsTheConsumedChoice() {
        val startup = NetworkSpaceApiAltStartup()
        var createCount = 0
        try {
            startHostedH1Application(startup) {
                startup.construct { enabled ->
                    assertFalse(enabled)
                    createCount += 1
                    error("fixture initialization failure")
                }
            }
            fail("initialization unexpectedly succeeded")
        } catch (_: IllegalStateException) {
            assertEquals(1, createCount)
        }
        assertFalse(startup.construct { it })
        try {
            startup.disableForHostedH1()
            fail("accepted a late policy write after failed initialization")
        } catch (_: IllegalStateException) {
            // Retry construction is allowed; changing its choice is not.
        }
    }

    @Test
    fun scopedHostedBindingIsExactAndPrecedesFirstManagerConstruction() {
        val inputs = arguments() + mapOf(
            HOSTED_H1_POLICY_ARGUMENT to HOSTED_H1_SCOPED_POLICY,
            HOSTED_H1_SCOPE_ARGUMENT to HOSTED_H1_QUALIFICATION_SCOPE,
        )
        requireHostedH1Startup(inputs::get)
        val startup = NetworkSpaceApiAltStartup()
        var createCount = 0
        startHostedH1Application(startup) {
            startup.construct { enabled ->
                assertFalse(enabled)
                createCount += 1
            }
        }
        assertEquals(1, createCount)
    }

    @Test
    fun scopedLaunchRejectsMissingForeignOrDiagnosticScopeBeforeConstruction() {
        for (change in listOf(
            mapOf(HOSTED_H1_SCOPE_ARGUMENT to null),
            mapOf(HOSTED_H1_SCOPE_ARGUMENT to "synthetic-other-scope"),
            mapOf("ioLoopDiagnostic" to "1"),
        )) {
            val inputs = (arguments() + mapOf(
                HOSTED_H1_POLICY_ARGUMENT to HOSTED_H1_SCOPED_POLICY,
                HOSTED_H1_SCOPE_ARGUMENT to HOSTED_H1_QUALIFICATION_SCOPE,
            )).toMutableMap()
            for ((name, value) in change) {
                if (value == null) inputs.remove(name) else inputs[name] = value
            }
            var createCount = 0
            try {
                requireHostedH1Startup(inputs::get)
                startHostedH1Application(NetworkSpaceApiAltStartup()) { createCount += 1 }
                fail("accepted unproven scoped launch")
            } catch (_: IllegalArgumentException) {
                assertEquals(0, createCount)
            }
        }
        for (policy in listOf("", HOSTED_H1_POLICY)) {
            try {
                requireHostedH1Scope(policy, HOSTED_H1_QUALIFICATION_SCOPE)
                fail("historical or ordinary policy acquired scoped eligibility")
            } catch (_: IllegalArgumentException) {
                // A version label on an old launch cannot supply new proof.
            }
        }
    }

    @Test
    fun scopedCommandsRefuseProvidersAlternateCarriersAndForcedMaintenance() {
        for ((verb, argument) in listOf(
            "phase" to "quiet-fixture", "connect" to "h1", "disconnect" to "",
            "snapshot" to "", "finish" to "",
        )) {
            requireScopedHostedH1Command(HOSTED_H1_SCOPED_POLICY, HOSTED_H1_QUALIFICATION_SCOPE, verb, argument)
        }
        for ((verb, argument) in listOf(
            "connect" to "auto", "connect" to "h3", "connect" to "direct",
            "provide" to "", "peer-connect" to "auto", "peer-platform-connect" to "h1",
            "free-memory" to "", "trim-memory" to "", "ioloop-clock" to "",
            "heap-profile" to "", "snapshot" to "h3",
        )) {
            try {
                requireScopedHostedH1Command(HOSTED_H1_SCOPED_POLICY, HOSTED_H1_QUALIFICATION_SCOPE, verb, argument)
                fail("scoped command reached effects: $verb/$argument")
            } catch (_: IllegalArgumentException) {
                // The real command dispatcher calls this before any command effect.
            }
        }
        requireScopedHostedH1Command(HOSTED_H1_POLICY, null, "provide", "")
        requireScopedHostedH1Command("", null, "connect", "auto")
    }
}
