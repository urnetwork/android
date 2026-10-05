package com.bringyour.network.analytics

import java.util.concurrent.atomic.AtomicReference

/**
 * A measurement probe for whitelist-only mobile networks (open bug P052,
 * section 16). On some Russian mobile carriers only allow-listed domestic IPs
 * and SNIs are routable, and every URnetwork ingress sits outside that list, so
 * a connect fails outright. This probe runs once after such a failure and writes
 * what it found to the app's local logs, which reach URnetwork only when the user
 * sends feedback with logs (the existing mechanism).
 *
 * It is a measurement, not a bypass: it only touches URnetwork-owned endpoints,
 * adds no new data destination, and is bounded (one run at a time and one per
 * cool-down, with a per-step timeout). The results decide which carrier is worth
 * building a pilot extender for; they do not change how the app connects.
 *
 * The trigger decision and the result formatting are pure, and the claim that
 * keeps concurrent failures to one run is tested with them (WhitelistProbeTest).
 */

/** The SIM/network country ISO that gates the probe (lower-case, TelephonyManager form). */
const val WHITELIST_PROBE_COUNTRY_ISO = "ru"

/** At most one probe run per this window, so a retry storm cannot flood the logs. */
const val WHITELIST_PROBE_COOL_DOWN_MILLIS = 30L * 60L * 1000L

/** Per-step network timeout, so the probe stays bounded on a dead path. */
const val WHITELIST_PROBE_HTTP_TIMEOUT_MILLIS = 5_000

/** The tag of the probe's log block, and of its lines in the sdk log (see AppDiagnosticLog). */
const val WHITELIST_PROBE_LOG_TAG = "whitelist-probe"

/**
 * Whether to run the probe once now.
 *
 * All of: the active data path is cellular, the connect attempt failed, the
 * network country is [WHITELIST_PROBE_COUNTRY_ISO], and the last run (if any) is
 * older than [coolDownMillis]. A clock that has gone backwards reads as still in
 * the cool-down, which is the safe side (it does not run).
 */
fun whitelistProbeShouldRun(
    isCellular: Boolean,
    countryIso: String?,
    connectFailed: Boolean,
    nowMillis: Long,
    lastRunMillis: Long?,
    coolDownMillis: Long = WHITELIST_PROBE_COOL_DOWN_MILLIS,
): Boolean {
    if (!isCellular || !connectFailed) {
        return false
    }
    if (countryIso?.trim()?.lowercase() != WHITELIST_PROBE_COUNTRY_ISO) {
        return false
    }
    if (lastRunMillis != null && nowMillis - lastRunMillis < coolDownMillis) {
        return false
    }
    return true
}

/**
 * One probe step's outcome. [ok] is true for reached, false for failed, and null
 * for a step that did not run ([detail] then says why).
 */
data class WhitelistProbeStep(
    val name: String,
    val ok: Boolean?,
    val detail: String,
)

/**
 * Formats the probe's steps as one stable local-log block. Local logs only; this
 * never leaves the device except through feedback-with-logs (the app writes it
 * to logcat and, line by line, to the sdk log that feedback uploads).
 */
fun formatWhitelistProbeLog(steps: List<WhitelistProbeStep>): String {
    val body = steps.joinToString("\n") { step ->
        val status = when (step.ok) {
            true -> "ok"
            false -> "fail"
            null -> "skip"
        }
        "  [$status] ${step.name}: ${step.detail}"
    }
    return "[$WHITELIST_PROBE_LOG_TAG] cellular connect failure in ${WHITELIST_PROBE_COUNTRY_ISO.uppercase()}\n$body"
}

/**
 * Runs the whitelist probe at most once per cool-down and one run at a time.
 * Safe for concurrent use.
 *
 * [maybeRun] claims a run on the caller's thread and hands only a claimed run
 * to [runOnWorker], which runs it off that thread. The claim checks the
 * trigger, the cool-down and the run in flight against one read of the probe's
 * state and moves the state to the new run with a compare-and-set of that read,
 * so failures reported at the same moment claim one run between them and its
 * block is written once. A failure while a run is in flight is declined, also
 * past the cool-down, and leaves the cool-down's start as it was. The claim
 * ends with its run, also when a step throws, and the cool-down still counts
 * from the claim.
 *
 * [checkApiReachable] performs step (a): a bounded reachability check of the
 * URnetwork api/platform host (the real implementation does an https GET of the
 * api `/status` endpoint). It is injected so the coordinator is unit testable
 * without network I/O. Step (b) alt whodis on UDP 53 needs a bindable
 * connect/sdk whodis probe, which sdk main does not have; step (d) needs a
 * configured pilot domestic extender, and none is configured; and step (c) a
 * recursive query to a URnetwork zone is not possible because no such
 * recursive-resolvable zone exists (whodis dials the alt host directly). They
 * are recorded as skipped with the reason.
 *
 * [log] receives the single formatted block, which the caller writes to the app
 * log. [nowMillis] is the clock.
 */
class WhitelistProbe(
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
    private val coolDownMillis: Long = WHITELIST_PROBE_COOL_DOWN_MILLIS,
    private val runOnWorker: (() -> Unit) -> Unit,
    private val checkApiReachable: () -> WhitelistProbeStep,
    private val log: (String) -> Unit,
) {
    /**
     * The start of the cool-down (the last claim, null before the first) and
     * whether that claim's run has not ended. Never changed in place: each
     * change is a new instance, so a compare-and-set against the instance that
     * was read covers both fields, and a claim's instance identifies its run.
     */
    private class State(
        val lastRunMillis: Long?,
        val running: Boolean,
    )

    private val state = AtomicReference(State(lastRunMillis = null, running = false))

    /**
     * Returns true when this failure claimed a run, which then runs on the
     * worker, and false when the trigger, the cool-down or a run in flight
     * declined it. A worker that cannot take the run gets the claim ended,
     * keeping the cool-down, and its exception is rethrown.
     */
    fun maybeRun(isCellular: Boolean, countryIso: String?, connectFailed: Boolean): Boolean {
        val claimed = claim(isCellular, countryIso, connectFailed) ?: return false
        try {
            runOnWorker { runClaimed(claimed) }
        } catch (e: Throwable) {
            release(claimed)
            throw e
        }
        return true
    }

    /**
     * Checks one read of the state and claims with a compare-and-set against
     * that read; a claim or release by another thread in between fails it, and
     * the check runs again on the new state. Returns the claimed state, or null
     * when declined.
     */
    private fun claim(isCellular: Boolean, countryIso: String?, connectFailed: Boolean): State? {
        while (true) {
            val current = state.get()
            if (current.running) {
                return null
            }
            val now = nowMillis()
            if (
                !whitelistProbeShouldRun(
                    isCellular = isCellular,
                    countryIso = countryIso,
                    connectFailed = connectFailed,
                    nowMillis = now,
                    lastRunMillis = current.lastRunMillis,
                    coolDownMillis = coolDownMillis,
                )
            ) {
                return null
            }
            val claimed = State(lastRunMillis = now, running = true)
            if (state.compareAndSet(current, claimed)) {
                return claimed
            }
        }
    }

    /** The claimed run, on the worker: the steps, then their one block. */
    private fun runClaimed(claimed: State) {
        try {
            val steps = listOf(
                checkApiReachable(),
                WhitelistProbeStep(
                    "alt-whodis-udp53",
                    null,
                    "needs a bindable connect/sdk whodis probe (not on sdk main)",
                ),
                WhitelistProbeStep(
                    "carrier-recursive-dns",
                    null,
                    "no URnetwork recursive-resolvable zone (whodis dials the alt host directly)",
                ),
                WhitelistProbeStep(
                    "pilot-extender",
                    null,
                    "no pilot domestic extender configured",
                ),
            )
            log(formatWhitelistProbeLog(steps))
        } finally {
            release(claimed)
        }
    }

    /**
     * Ends the run of [claimed], keeping its cool-down start. Only that claim's
     * own state is replaced, so ending it twice cannot end a later claim.
     */
    private fun release(claimed: State) {
        state.compareAndSet(
            claimed,
            State(lastRunMillis = claimed.lastRunMillis, running = false),
        )
    }
}
