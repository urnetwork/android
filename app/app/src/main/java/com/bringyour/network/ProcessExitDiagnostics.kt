package com.bringyour.network

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import java.time.Instant

/*
 * Why the app's earlier processes ended, for "send feedback with logs".
 *
 * "The app closes by itself" (support 8190) can be a crash, an ANR, a low-memory
 * or vendor battery-manager kill, or the user swiping the app away, and only the
 * system knows which. Android 11+ keeps a short history of each process's exit
 * (ActivityManager.getHistoricalProcessExitReasons). At app start the records
 * newer than the last one logged are written to the diagnostic log, one line
 * each, so an uploaded log answers the question.
 *
 * Nothing new leaves the device: the lines go to logcat and the sdk log files,
 * which are uploaded only when the user sends feedback with logs (see
 * AppDiagnosticLog).
 *
 * The record, the names and the lines are Android-free so they are unit
 * testable (ProcessExitDiagnosticsTest); [logProcessExitReasons] is the thin
 * reader.
 */

/** The sdk log tag of the process exit diagnostics. */
const val PROCESS_EXIT_LOG_TAG = "process-exit"

/** The longest system description kept on a line; the system's text can be a whole report. */
const val PROCESS_EXIT_DESCRIPTION_MAX_LENGTH = 200

/** How many records to ask the system for. It keeps a few per process. */
private const val PROCESS_EXIT_RECORD_COUNT = 16

private const val PROCESS_EXIT_PREFS_NAME = "process_exit_diagnostics"
private const val PROCESS_EXIT_LOGGED_THROUGH_KEY = "logged_through_millis"

/** One historical exit record, as ApplicationExitInfo reports it. */
data class ProcessExitRecord(
    val timestampMillis: Long,
    val processName: String,
    val reason: Int,
    val status: Int,
    val importance: Int,
    val pssKb: Long,
    val rssKb: Long,
    val description: String?,
)

/** ApplicationExitInfo.REASON_* by value (API 30 through 34), so a log line reads without a lookup. */
fun processExitReasonName(reason: Int): String = when (reason) {
    0 -> "unknown"
    1 -> "exit_self"
    2 -> "signaled"
    3 -> "low_memory"
    4 -> "crash"
    5 -> "crash_native"
    6 -> "anr"
    7 -> "initialization_failure"
    8 -> "permission_change"
    9 -> "excessive_resource_usage"
    10 -> "user_requested"
    11 -> "user_stopped"
    12 -> "dependency_died"
    13 -> "other"
    14 -> "freezer"
    15 -> "package_state_change"
    16 -> "package_updated"
    else -> "reason_$reason"
}

/** ActivityManager.RunningAppProcessInfo.IMPORTANCE_* by value: where the process stood when it ended. */
fun processImportanceName(importance: Int): String = when (importance) {
    100 -> "foreground"
    125 -> "foreground_service"
    200 -> "visible"
    230 -> "perceptible"
    300 -> "service"
    325 -> "top_sleeping"
    350 -> "cant_save_state"
    400 -> "cached"
    1000 -> "gone"
    else -> "importance_$importance"
}

/**
 * The log lines for the records newer than [loggedThroughMillis], oldest first,
 * and the watermark to store: the newest timestamp logged, or
 * [loggedThroughMillis] when nothing is new. A record at or before the watermark
 * was logged by an earlier start, so a restart does not repeat the history.
 */
fun processExitLogLines(
    records: List<ProcessExitRecord>,
    loggedThroughMillis: Long,
): Pair<List<String>, Long> {
    val newRecords = records
        .filter { loggedThroughMillis < it.timestampMillis }
        .sortedBy { it.timestampMillis }
    val lines = newRecords.map { processExitLogLine(it) }
    val newestMillis = newRecords.maxOfOrNull { it.timestampMillis } ?: loggedThroughMillis
    return lines to maxOf(newestMillis, loggedThroughMillis)
}

/** One record as one line: when, which process, why, where it stood, and the memory it held. */
fun processExitLogLine(record: ProcessExitRecord): String {
    val description = record.description
        ?.replace(Regex("\\s+"), " ")
        ?.trim()
        ?.take(PROCESS_EXIT_DESCRIPTION_MAX_LENGTH)
        ?.takeIf { it.isNotEmpty() }
    return buildString {
        append("[").append(PROCESS_EXIT_LOG_TAG).append("]exit at ")
        append(Instant.ofEpochMilli(record.timestampMillis))
        append(" process=").append(record.processName)
        append(" reason=").append(processExitReasonName(record.reason))
        append("(").append(record.reason).append(")")
        append(" status=").append(record.status)
        append(" importance=").append(processImportanceName(record.importance))
        append(" pss_kb=").append(record.pssKb)
        append(" rss_kb=").append(record.rssKb)
        if (description != null) {
            append(" description=\"").append(description).append("\"")
        }
    }
}

/**
 * Writes the exit records newer than the last start's to [diagnosticLog]. Only
 * Android 11+ keeps them; earlier releases log nothing. Never throws: this runs
 * in Application.onCreate, and a diagnostic must not be what breaks a launch.
 */
fun logProcessExitReasons(
    context: Context,
    activityManager: ActivityManager?,
    diagnosticLog: AppDiagnosticLog,
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || activityManager == null) {
        return
    }
    runCatching {
        logProcessExitReasonsSinceLastStart(context, activityManager, diagnosticLog)
    }
}

@RequiresApi(Build.VERSION_CODES.R)
private fun logProcessExitReasonsSinceLastStart(
    context: Context,
    activityManager: ActivityManager,
    diagnosticLog: AppDiagnosticLog,
) {
    val prefs = context.getSharedPreferences(PROCESS_EXIT_PREFS_NAME, Context.MODE_PRIVATE)
    val loggedThroughMillis = prefs.getLong(PROCESS_EXIT_LOGGED_THROUGH_KEY, 0L)
    val records = activityManager
        .getHistoricalProcessExitReasons(context.packageName, 0, PROCESS_EXIT_RECORD_COUNT)
        .map { exitInfo ->
            ProcessExitRecord(
                timestampMillis = exitInfo.timestamp,
                processName = exitInfo.processName,
                reason = exitInfo.reason,
                status = exitInfo.status,
                importance = exitInfo.importance,
                pssKb = exitInfo.pss,
                rssKb = exitInfo.rss,
                description = exitInfo.description,
            )
        }
    val (lines, watermark) = processExitLogLines(records, loggedThroughMillis)
    for (line in lines) {
        diagnosticLog.info(PROCESS_EXIT_LOG_TAG, line)
    }
    if (watermark != loggedThroughMillis) {
        prefs.edit().putLong(PROCESS_EXIT_LOGGED_THROUGH_KEY, watermark).apply()
    }
}
