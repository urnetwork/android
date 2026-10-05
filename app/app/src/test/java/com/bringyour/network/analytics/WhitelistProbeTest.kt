package com.bringyour.network.analytics

import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
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
            runOnWorker = { it() },
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
            runOnWorker = {
                called = true
                it()
            },
            checkApiReachable = {
                called = true
                WhitelistProbeStep("api-reachable", true, "ok")
            },
            log = { called = true },
        )
        assertFalse(probe.maybeRun(isCellular = false, countryIso = "ru", connectFailed = true))
        assertFalse(called)
    }

    @Test
    fun twoConcurrentFailuresRunOneProbeAndWriteOneBlock() {
        // each failure's clock read waits for the other's, so both failures have
        // read the probe's state (no run yet) before either one can claim
        val bothRead = CountDownLatch(2)
        val stalledReads = AtomicInteger(0)
        val apiChecks = AtomicInteger(0)
        val blocks = Collections.synchronizedList(mutableListOf<String>())
        val probe = WhitelistProbe(
            nowMillis = {
                bothRead.countDown()
                if (!bothRead.await(30, TimeUnit.SECONDS)) {
                    stalledReads.incrementAndGet()
                }
                base
            },
            runOnWorker = { it() },
            checkApiReachable = {
                apiChecks.incrementAndGet()
                WhitelistProbeStep("api-reachable", false, "SocketTimeoutException after 5001ms")
            },
            log = { blocks.add(it) },
        )

        val claims = AtomicInteger(0)
        val failures = List(2) {
            thread {
                if (probe.maybeRun(isCellular = true, countryIso = "ru", connectFailed = true)) {
                    claims.incrementAndGet()
                }
            }
        }
        failures.forEach { it.join(30_000) }

        assertTrue(failures.none { it.isAlive })
        assertEquals(0, stalledReads.get())
        assertEquals(1, claims.get())
        assertEquals(1, apiChecks.get())
        // the one block is the probe's block as before
        val expected = """
            [whitelist-probe] cellular connect failure in RU
              [fail] api-reachable: SocketTimeoutException after 5001ms
              [skip] alt-whodis-udp53: needs a bindable connect/sdk whodis probe (not on sdk main)
              [skip] carrier-recursive-dns: no URnetwork recursive-resolvable zone (whodis dials the alt host directly)
              [skip] pilot-extender: no pilot domestic extender configured
        """.trimIndent()
        assertEquals(listOf(expected), blocks.toList())
    }

    @Test
    fun aFailureWhileTheProbeRunsIsDeclinedAndLeavesTheCoolDownAsItWas() {
        var clock = base
        val heldRuns = mutableListOf<() -> Unit>()
        val blocks = mutableListOf<String>()
        val probe = WhitelistProbe(
            nowMillis = { clock },
            runOnWorker = { heldRuns.add(it) },
            checkApiReachable = { WhitelistProbeStep("api-reachable", true, "HTTP 200 in 120ms") },
            log = { blocks.add(it) },
        )

        assertTrue(probe.maybeRun(isCellular = true, countryIso = "ru", connectFailed = true))
        // the claimed run is on the worker, not done on the caller's thread
        assertEquals(1, heldRuns.size)
        assertEquals(0, blocks.size)

        // a failure while it runs: declined, with no second worker
        clock = base + 60_000L
        assertFalse(probe.maybeRun(isCellular = true, countryIso = "ru", connectFailed = true))
        assertEquals(1, heldRuns.size)

        heldRuns.removeAt(0)()
        assertEquals(1, blocks.size)

        // the cool-down still counts from the first claim: declined until it
        // ends, claimed as soon as it does
        clock = base + WHITELIST_PROBE_COOL_DOWN_MILLIS - 1
        assertFalse(probe.maybeRun(isCellular = true, countryIso = "ru", connectFailed = true))
        clock = base + WHITELIST_PROBE_COOL_DOWN_MILLIS
        assertTrue(probe.maybeRun(isCellular = true, countryIso = "ru", connectFailed = true))
        heldRuns.removeAt(0)()
        assertEquals(2, blocks.size)
    }

    @Test
    fun aRunStillInFlightAfterTheCoolDownDeclinesASecondRun() {
        var clock = base
        val heldRuns = mutableListOf<() -> Unit>()
        val blocks = mutableListOf<String>()
        val probe = WhitelistProbe(
            nowMillis = { clock },
            runOnWorker = { heldRuns.add(it) },
            checkApiReachable = { WhitelistProbeStep("api-reachable", true, "HTTP 200 in 120ms") },
            log = { blocks.add(it) },
        )

        assertTrue(probe.maybeRun(isCellular = true, countryIso = "ru", connectFailed = true))
        // the cool-down is over but the first run has not ended: one at a time
        clock = base + WHITELIST_PROBE_COOL_DOWN_MILLIS
        assertFalse(probe.maybeRun(isCellular = true, countryIso = "ru", connectFailed = true))
        assertEquals(1, heldRuns.size)

        heldRuns.removeAt(0)()
        assertTrue(probe.maybeRun(isCellular = true, countryIso = "ru", connectFailed = true))
        heldRuns.removeAt(0)()
        assertEquals(2, blocks.size)
    }

    @Test
    fun aFailureAfterTheCoolDownRunsAgain() {
        var clock = base
        val workers = mutableListOf<Thread>()
        val blocks = Collections.synchronizedList(mutableListOf<String>())
        val probe = WhitelistProbe(
            nowMillis = { clock },
            runOnWorker = { workers.add(thread(block = it)) },
            checkApiReachable = { WhitelistProbeStep("api-reachable", true, "HTTP 200 in 120ms") },
            log = { blocks.add(it) },
        )

        assertTrue(probe.maybeRun(isCellular = true, countryIso = "ru", connectFailed = true))
        // the run ends its claim on its own worker thread
        workers[0].join(30_000)
        assertFalse(workers[0].isAlive)

        clock = base + WHITELIST_PROBE_COOL_DOWN_MILLIS
        assertTrue(probe.maybeRun(isCellular = true, countryIso = "ru", connectFailed = true))
        workers[1].join(30_000)
        assertFalse(workers[1].isAlive)

        assertEquals(2, workers.size)
        assertEquals(2, blocks.size)
    }

    @Test
    fun aRunThatThrowsEndsItsClaimAndKeepsTheCoolDown() {
        var clock = base
        var stepThrows = true
        val heldRuns = mutableListOf<() -> Unit>()
        val blocks = mutableListOf<String>()
        val probe = WhitelistProbe(
            nowMillis = { clock },
            runOnWorker = { heldRuns.add(it) },
            checkApiReachable = {
                check(!stepThrows) { "step failed" }
                WhitelistProbeStep("api-reachable", true, "HTTP 200 in 120ms")
            },
            log = { blocks.add(it) },
        )

        assertTrue(probe.maybeRun(isCellular = true, countryIso = "ru", connectFailed = true))
        val thrown = runCatching { heldRuns.removeAt(0)() }.exceptionOrNull()
        assertTrue(thrown is IllegalStateException)
        assertEquals(0, blocks.size)

        // the claim ended with the run; its cool-down did not
        clock = base + 60_000L
        assertFalse(probe.maybeRun(isCellular = true, countryIso = "ru", connectFailed = true))
        stepThrows = false
        clock = base + WHITELIST_PROBE_COOL_DOWN_MILLIS
        assertTrue(probe.maybeRun(isCellular = true, countryIso = "ru", connectFailed = true))
        heldRuns.removeAt(0)()
        assertEquals(1, blocks.size)
    }

    @Test
    fun aWorkerThatCannotTakeTheRunEndsTheClaimAndKeepsTheCoolDown() {
        var clock = base
        var workerRefuses = true
        val blocks = mutableListOf<String>()
        val probe = WhitelistProbe(
            nowMillis = { clock },
            runOnWorker = {
                if (workerRefuses) {
                    throw RejectedExecutionException("no worker")
                }
                it()
            },
            checkApiReachable = { WhitelistProbeStep("api-reachable", true, "HTTP 200 in 120ms") },
            log = { blocks.add(it) },
        )

        val thrown = runCatching {
            probe.maybeRun(isCellular = true, countryIso = "ru", connectFailed = true)
        }.exceptionOrNull()
        assertTrue(thrown is RejectedExecutionException)

        clock = base + 60_000L
        assertFalse(probe.maybeRun(isCellular = true, countryIso = "ru", connectFailed = true))
        workerRefuses = false
        clock = base + WHITELIST_PROBE_COOL_DOWN_MILLIS
        assertTrue(probe.maybeRun(isCellular = true, countryIso = "ru", connectFailed = true))
        assertEquals(1, blocks.size)
    }

    @Test
    fun aRunEndedTwiceDoesNotEndALaterClaim() {
        // a worker that runs the probe on the caller's thread and lets its
        // exception out: the run ends its claim, and maybeRun, seeing the
        // exception, ends it again after a later failure has claimed
        var clock = base
        val heldRuns = mutableListOf<() -> Unit>()
        val blocks = mutableListOf<String>()
        val laterClaims = mutableListOf<Boolean>()
        lateinit var probe: WhitelistProbe
        probe = WhitelistProbe(
            nowMillis = { clock },
            runOnWorker = { work ->
                if (clock == base) {
                    try {
                        work()
                    } finally {
                        clock = base + WHITELIST_PROBE_COOL_DOWN_MILLIS
                        laterClaims.add(
                            probe.maybeRun(isCellular = true, countryIso = "ru", connectFailed = true),
                        )
                    }
                } else {
                    heldRuns.add(work)
                }
            },
            checkApiReachable = {
                check(clock != base) { "step failed" }
                WhitelistProbeStep("api-reachable", true, "HTTP 200 in 120ms")
            },
            log = { blocks.add(it) },
        )

        val thrown = runCatching {
            probe.maybeRun(isCellular = true, countryIso = "ru", connectFailed = true)
        }.exceptionOrNull()
        assertTrue(thrown is IllegalStateException)
        assertEquals(listOf(true), laterClaims)
        assertEquals(1, heldRuns.size)

        // the later claim's run is still in flight: one at a time
        clock = base + 2L * WHITELIST_PROBE_COOL_DOWN_MILLIS
        assertFalse(probe.maybeRun(isCellular = true, countryIso = "ru", connectFailed = true))
        assertEquals(1, heldRuns.size)

        heldRuns.removeAt(0)()
        assertEquals(1, blocks.size)
    }
}
