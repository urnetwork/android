package com.bringyour.network

/**
 * Writes an app diagnostic to logcat and into the sdk's log files.
 *
 * "Send feedback with logs" uploads only the sdk's glog files (sdk
 * DeviceLocal.UploadLogs), not logcat, so a diagnostic that support needs from
 * an uploaded log, such as the Private DNS mode (open bug P021) or the whitelist
 * probe results (P052), is written to both. [logcat] gets the text unchanged.
 * [sdkLog] (Sdk.logAppInfo in the app) gets it one line per call under a short
 * tag, because the sdk writes one bounded line per call, as
 * "[app][<tag>] <line>".
 *
 * Nothing new leaves the device: these are lines the app already writes to
 * logcat, and the glog files are uploaded only when the user sends feedback with
 * logs. A failing sdk write never fails the caller.
 *
 * Kept Android-free (the two writers are injected) so it is unit testable
 * (AppDiagnosticLogTest).
 */
class AppDiagnosticLog(
    private val logcat: (String) -> Unit,
    private val sdkLog: (tag: String, line: String) -> Unit,
) {
    /** Writes [text] to logcat as is, and to the sdk log line by line under [tag]. */
    fun info(tag: String, text: String) {
        logcat(text)
        runCatching {
            for (line in appDiagnosticLogLines(tag, text)) {
                sdkLog(tag, line)
            }
        }
    }
}

/**
 * The lines of [text] as they are written to the sdk log under [tag]: one per
 * line, blank lines dropped, and a line's leading "[tag]" removed with one space
 * after it, since the sdk prefixes each line with "[app][tag] ". Indentation is
 * kept.
 */
fun appDiagnosticLogLines(tag: String, text: String): List<String> {
    val prefix = "[$tag]"
    return text.lines()
        .map { line ->
            if (line.startsWith(prefix)) {
                line.removePrefix(prefix).removePrefix(" ")
            } else {
                line
            }
        }
        .filter { it.isNotBlank() }
}

/** The sdk log tag of the service's diagnostics. */
const val SERVICE_LOG_TAG = "service"

/**
 * The service's Private DNS line, written on every builder.establish() so that
 * uploaded logs show whether strict Private DNS (DoT) was in force for a
 * "connected but no DNS" report (see PrivateDnsMode).
 */
fun privateDnsModeLogLine(mode: PrivateDnsMode): String =
    "[$SERVICE_LOG_TAG]private dns mode=${mode.logValue()}"
