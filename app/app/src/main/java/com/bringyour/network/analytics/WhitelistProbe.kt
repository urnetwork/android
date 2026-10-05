package com.bringyour.network.analytics

/**
 * A measurement probe for whitelist-only mobile networks (open bug P052,
 * section 16). On some Russian mobile carriers only allow-listed domestic IPs
 * and SNIs are routable, and every URnetwork ingress sits outside that list, so
 * a connect fails outright. This probe runs once after such a failure and writes
 * what it found to the app's local logs, which reach URnetwork only when the user
 * sends feedback with logs (the existing mechanism).
 *
 * It is a measurement, not a bypass: it only touches URnetwork-owned endpoints,
 * adds no new data destination, and is bounded (one run per cool-down, with a
 * per-step timeout). The results decide which carrier is worth building a pilot
 * extender for; they do not change how the app connects.
 *
 * The trigger decision and the result formatting are pure (WhitelistProbeTest).
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
 * Runs the whitelist probe at most once per cool-down.
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
    private val checkApiReachable: () -> WhitelistProbeStep,
    private val log: (String) -> Unit,
) {
    @Volatile
    private var lastRunMillis: Long? = null

    /** Returns true when the probe ran, false when the trigger or the cool-down declined it. */
    fun maybeRun(isCellular: Boolean, countryIso: String?, connectFailed: Boolean): Boolean {
        val now = nowMillis()
        if (
            !whitelistProbeShouldRun(
                isCellular = isCellular,
                countryIso = countryIso,
                connectFailed = connectFailed,
                nowMillis = now,
                lastRunMillis = lastRunMillis,
                coolDownMillis = coolDownMillis,
            )
        ) {
            return false
        }
        lastRunMillis = now
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
        return true
    }
}
