package com.bringyour.network.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WhitelistProbeTest {
    private val base = 1_000_000L

    @Test
    fun runsOnCellularRuFailureWithNoPriorRun() {
        assertTrue(
            whitelistProbeShouldRun(
                isCellular = true,
                countryIso = "ru",
                connectFailed = true,
                nowMillis = base,
                lastRunMillis = null,
            ),
        )
    }

    @Test
    fun countryIsoIsCaseAndWhitespaceInsensitive() {
        assertTrue(
            whitelistProbeShouldRun(true, "  RU ", true, base, null),
        )
    }

    @Test
    fun doesNotRunOffCellular() {
        assertFalse(whitelistProbeShouldRun(false, "ru", true, base, null))
    }

    @Test
    fun doesNotRunWhenConnectSucceeded() {
        assertFalse(whitelistProbeShouldRun(true, "ru", false, base, null))
    }

    @Test
    fun doesNotRunOutsideRu() {
        assertFalse(whitelistProbeShouldRun(true, "us", true, base, null))
        assertFalse(whitelistProbeShouldRun(true, null, true, base, null))
        assertFalse(whitelistProbeShouldRun(true, "", true, base, null))
    }

    @Test
    fun coolDownBlocksASecondRun() {
        // 10 minutes after a run, still inside the 30-minute cool-down
        assertFalse(
            whitelistProbeShouldRun(true, "ru", true, base + 10L * 60_000L, base),
        )
    }

    @Test
    fun runsAgainAfterCoolDown() {
        assertTrue(
            whitelistProbeShouldRun(true, "ru", true, base + WHITELIST_PROBE_COOL_DOWN_MILLIS, base),
        )
    }

    @Test
    fun clockGoingBackwardsStaysInCoolDown() {
        assertFalse(whitelistProbeShouldRun(true, "ru", true, base - 1, base))
    }

    @Test
    fun formattingMarksRunFailedAndSkippedSteps() {
        val log = formatWhitelistProbeLog(
            listOf(
                WhitelistProbeStep("api-reachable", true, "200 in 120ms"),
                WhitelistProbeStep("alt-whodis-udp53", false, "timeout"),
                WhitelistProbeStep("pilot-extender", null, "none configured"),
            ),
        )
        val expected = """
            [whitelist-probe] cellular connect failure in RU
              [ok] api-reachable: 200 in 120ms
              [fail] alt-whodis-udp53: timeout
              [skip] pilot-extender: none configured
        """.trimIndent()
        assertEquals(expected, log)
    }

    @Test
    fun coordinatorRunsOnceThenRespectsCoolDown() {
        var clock = base
        val runs = mutableListOf<String>()
        val probe = WhitelistProbe(
            nowMillis = { clock },
            checkApiReachable = { WhitelistProbeStep("api-reachable", true, "ok") },
            log = { runs.add(it) },
        )

        assertTrue(probe.maybeRun(isCellular = true, countryIso = "ru", connectFailed = true))
        // within the cool-down: no second run
        clock = base + 60_000L
        assertFalse(probe.maybeRun(isCellular = true, countryIso = "ru", connectFailed = true))
        // after the cool-down: runs again
        clock = base + WHITELIST_PROBE_COOL_DOWN_MILLIS + 1
        assertTrue(probe.maybeRun(isCellular = true, countryIso = "ru", connectFailed = true))

        assertEquals(2, runs.size)
        assertTrue(runs[0].contains("[ok] api-reachable: ok"))
    }

    @Test
    fun coordinatorDoesNotRunOrLogWhenNotTriggered() {
        var called = false
        val probe = WhitelistProbe(
            nowMillis = { base },
            checkApiReachable = {
                called = true
                WhitelistProbeStep("api-reachable", true, "ok")
            },
            log = { called = true },
        )
        assertFalse(probe.maybeRun(isCellular = false, countryIso = "ru", connectFailed = true))
        assertFalse(called)
    }
}
