package com.bringyour.network.acceptance

/** A private opt-in is never inferred from a build, role, or existing owner. */
internal fun physicalIoLoopDiagnosticRequested(argument: String?, privateDebug: Boolean): Boolean {
    require(argument == null || argument == "1") { "invalid native-loop diagnostic option" }
    if (argument == null) return false
    check(privateDebug) { "native-loop diagnostic requires a private debug build" }
    return true
}

/**
 * Snapshot joins run/fd owners between two actual main-thread construction fences.
 * The second fence drains done-posts queued before the joined native callbacks
 * returned. Every cleanup boundary is attempted; no error becomes completeness.
 */
internal fun finishPhysicalIoLoopDiagnostic(
    mainFence: () -> Unit,
    snapshot: () -> String,
    write: (String, Boolean) -> Unit,
    release: () -> Unit,
    failed: (String) -> Unit,
) {
    fun attempt(stage: String, action: () -> Unit): Boolean = try {
        action()
        true
    } catch (_: Throwable) {
        failed(stage)
        false
    }
    val before = attempt("ioloop-fence-before", mainFence)
    var native: String? = null
    attempt("ioloop-native-join") { native = snapshot() }
    val after = attempt("ioloop-fence-after", mainFence)
    native?.let { raw -> attempt("ioloop-receipt") { write(raw, before && after) } }
    attempt("ioloop-diagnostic-release", release)
}
