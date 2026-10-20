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
}
